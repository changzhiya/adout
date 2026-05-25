package com.adout.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.adout.rule.RuleEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * TunnelManager - DNS-only traffic tunnel manager
 *
 * Handles DNS blocking by intercepting DNS packets (UDP port 53).
 * For blocked domains, returns 0.0.0.0 response.
 * For allowed domains, forwards to upstream DNS servers.
 * Non-DNS traffic is forwarded directly.
 *
 * DNS queries are processed asynchronously to prevent blocking.
 */
class TunnelManager(
    private val vpnInterface: ParcelFileDescriptor,
    private val ruleEngine: RuleEngine,
    private val vpnService: VpnService? = null
) {
    companion object {
        private const val TAG = "TunnelManager"
        // Use domestic DNS servers for reliability in China
        private val UPSTREAM_DNS = listOf("223.5.5.5", "119.29.29.29", "114.114.114.114")
        private const val DNS_TIMEOUT_MS = 2000
        private const val MAX_CONCURRENT_DNS = 50
    }

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val blockedCount = java.util.concurrent.atomic.AtomicLong(0)

    // DNS response cache: domain -> (response, timestamp)
    private val dnsCache = LinkedHashMap<String, Pair<ByteArray, Long>>(100, 0.75f, true)
    private val cacheLock = Any()
    private val CACHE_TTL = 300_000L // 5 minutes

    // Mutex to protect outputStream writes from concurrent coroutines
    private val writeMutex = Mutex()

    // Semaphore to limit concurrent DNS queries
    private val dnsSemaphore = kotlinx.coroutines.sync.Semaphore(MAX_CONCURRENT_DNS)

    private suspend fun <T> withDnsPermit(block: suspend () -> T): T {
        dnsSemaphore.acquire()
        try {
            return block()
        } finally {
            dnsSemaphore.release()
        }
    }

    fun start() {
        job = scope.launch {
            val inputStream = FileInputStream(vpnInterface.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface.fileDescriptor)

            val buffer = ByteBuffer.allocate(32767)

            try {
                while (isActive) {
                    buffer.clear()
                    val length = inputStream.read(buffer.array())

                    if (length > 0) {
                        buffer.limit(length)
                        processPacket(buffer, outputStream)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Tunnel error", e)
                }
            } finally {
                inputStream.close()
                outputStream.close()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun processPacket(buffer: ByteBuffer, outputStream: FileOutputStream) {
        try {
            val length = buffer.limit()
            if (length < 20) return

            val version = (buffer.get(0).toInt() and 0xF0) shr 4
            if (version != 4) return

            val protocol = buffer.get(9).toInt() and 0xFF

            if (protocol == 17) {
                processUdpPacket(buffer, outputStream)
            } else {
                // Non-UDP traffic forward directly
                scope.launch {
                    writeMutex.withLock {
                        outputStream.write(buffer.array(), 0, length)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing packet", e)
        }
    }

    private fun processUdpPacket(buffer: ByteBuffer, outputStream: FileOutputStream) {
        try {
            val length = buffer.limit()
            val ipHeaderLength = (buffer.get(0).toInt() and 0x0F) * 4

            if (length < ipHeaderLength + 8) return

            val destPort = ((buffer.get(ipHeaderLength + 2).toInt() and 0xFF) shl 8) or
                    (buffer.get(ipHeaderLength + 3).toInt() and 0xFF)

            if (destPort == 53) {
                // DNS query - process asynchronously
                val packetCopy = buffer.array().copyOf(length)
                val dnsDataStart = ipHeaderLength + 8
                val dnsDataLength = length - dnsDataStart

                if (dnsDataLength <= 0) return

                val dnsData = ByteArray(dnsDataLength)
                System.arraycopy(packetCopy, dnsDataStart, dnsData, 0, dnsDataLength)

                val domain = extractDomain(dnsData)
                Log.d(TAG, "DNS query for: ${domain ?: "unknown"}")

                scope.launch {
                    withDnsPermit {
                        processDnsAsync(packetCopy, dnsData, domain, ipHeaderLength, outputStream)
                    }
                }
            } else {
                // Non-DNS UDP traffic forward directly
                scope.launch {
                    writeMutex.withLock {
                        outputStream.write(buffer.array(), 0, length)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing UDP packet", e)
        }
    }

    /**
     * Process DNS query asynchronously in a separate coroutine
     */
    private suspend fun processDnsAsync(
        originalPacket: ByteArray,
        dnsData: ByteArray,
        domain: String?,
        ipHeaderLength: Int,
        outputStream: FileOutputStream
    ) {
        try {
            if (domain != null) {
                // Check if domain should be blocked
                if (ruleEngine.shouldBlock(domain)) {
                    blockedCount.incrementAndGet()
                    Log.i(TAG, "BLOCKED: $domain")

                    val response = buildBlockedDnsResponse(dnsData)
                    if (response != null) {
                        val responsePacket = buildDnsResponsePacketFromRaw(
                            originalPacket, response, ipHeaderLength
                        )
                        writeMutex.withLock {
                            outputStream.write(responsePacket)
                        }
                        return
                    }
                }

                // Check DNS cache
                val cached = getCachedDns(domain)
                if (cached != null) {
                    Log.d(TAG, "DNS cache hit: $domain")
                    val responsePacket = buildDnsResponsePacketFromRaw(
                        originalPacket, cached, ipHeaderLength
                    )
                    writeMutex.withLock {
                        outputStream.write(responsePacket)
                    }
                    return
                }
            }

            // Forward DNS query to upstream server
            val startTime = System.currentTimeMillis()
            val response = forwardDnsToUpstream(dnsData)
            val elapsed = System.currentTimeMillis() - startTime

            if (response != null) {
                if (elapsed > 1000) {
                    Log.w(TAG, "Slow DNS response for $domain: ${elapsed}ms")
                }

                // Cache the response
                if (domain != null) {
                    cacheDns(domain, response)
                }
                val responsePacket = buildDnsResponsePacketFromRaw(
                    originalPacket, response, ipHeaderLength
                )
                writeMutex.withLock {
                    outputStream.write(responsePacket)
                }
            } else {
                Log.w(TAG, "All upstream DNS servers failed for $domain")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing DNS async", e)
        }
    }

    /**
     * Forward DNS query to upstream DNS server using a protected socket
     */
    private fun forwardDnsToUpstream(dnsData: ByteArray): ByteArray? {
        for (upstream in UPSTREAM_DNS) {
            try {
                val socket = DatagramSocket()
                // Protect socket from VPN to avoid infinite loop
                val protected = vpnService?.protect(socket) ?: false
                if (!protected) {
                    Log.w(TAG, "Failed to protect socket for $upstream")
                }

                socket.soTimeout = DNS_TIMEOUT_MS

                val address = InetAddress.getByName(upstream)
                val packet = DatagramPacket(dnsData, dnsData.size, address, 53)
                socket.send(packet)

                val buffer = ByteArray(512)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                socket.close()

                return response.data.copyOf(response.length)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to forward DNS to $upstream: ${e.message}")
                continue
            }
        }
        return null
    }


    private fun extractDomain(dnsData: ByteArray): String? {
        return DnsProtocol.extractDomain(dnsData)
    }

    private fun buildBlockedDnsResponse(dnsQuery: ByteArray): ByteArray? {
        return DnsProtocol.buildBlockedDnsResponse(dnsQuery)
    }

    private fun buildDnsResponsePacketFromRaw(
        originalPacket: ByteArray,
        dnsResponse: ByteArray,
        ipHeaderLength: Int
    ): ByteArray {
        return DnsProtocol.buildDnsResponsePacketFromRaw(originalPacket, dnsResponse, ipHeaderLength)
    }

    private fun computeIpChecksum(header: ByteArray): Int {
        return DnsProtocol.computeIpChecksum(header)
    }

    fun getCachedDns(domain: String): ByteArray? {
        synchronized(cacheLock) {
            val entry = dnsCache[domain] ?: return null
            if (System.currentTimeMillis() - entry.second > CACHE_TTL) {
                dnsCache.remove(domain)
                return null
            }
            return entry.first
        }
    }

    fun cacheDns(domain: String, response: ByteArray) {
        synchronized(cacheLock) {
            while (dnsCache.size >= 200) {
                val oldest = dnsCache.entries.iterator()
                if (oldest.hasNext()) {
                    oldest.next()
                    oldest.remove()
                } else break
            }
            dnsCache[domain] = Pair(response, System.currentTimeMillis())
        }
    }

    fun getBlockedCount(): Long {
        return blockedCount.get()
    }

    /**
     * Clear DNS cache. Called when VPN starts to force re-resolution
     * of all domains through the VPN DNS.
     */
    fun clearDnsCache() {
        synchronized(cacheLock) {
            dnsCache.clear()
            Log.i(TAG, "DNS cache cleared")
        }
    }
}
