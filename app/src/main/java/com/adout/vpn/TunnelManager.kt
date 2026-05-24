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
    private var blockedCount = 0L

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
                    blockedCount++
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

    /**
     * Build a DNS response that returns 0.0.0.0 for blocked domain
     */
    private fun buildBlockedDnsResponse(dnsQuery: ByteArray): ByteArray? {
        try {
            val response = dnsQuery.copyOf()

            // Set QR bit (response), AA bit (authoritative), RD bit
            response[2] = (response[2].toInt() or 0x80 or 0x04 or 0x01).toByte()
            // Set RA bit
            response[3] = (response[3].toInt() or 0x80).toByte()

            // Set answer count to 1
            response[6] = 0x00
            response[7] = 0x01

            // Find end of query section
            var offset = 12
            while (offset < response.size && response[offset].toInt() != 0) {
                val len = response[offset].toInt() and 0xFF
                if (len and 0xC0 == 0xC0) {
                    offset += 2
                    break
                }
                offset += len + 1
            }
            if (offset < response.size && response[offset].toInt() == 0) {
                offset++ // Skip null terminator
            }
            offset += 4 // Skip QTYPE and QCLASS

            // Build answer section
            val answer = ByteArray(16)

            // Name pointer to query
            answer[0] = 0xC0.toByte()
            answer[1] = 0x0C.toByte()

            // Type A (IPv4)
            answer[2] = 0x00
            answer[3] = 0x01

            // Class IN
            answer[4] = 0x00
            answer[5] = 0x01

            // TTL (60 seconds)
            answer[6] = 0x00
            answer[7] = 0x00
            answer[8] = 0x00
            answer[9] = 0x3C.toByte()

            // Data length (4 bytes for IPv4)
            answer[10] = 0x00
            answer[11] = 0x04

            // IP: 0.0.0.0
            answer[12] = 0x00
            answer[13] = 0x00
            answer[14] = 0x00
            answer[15] = 0x00

            // Combine query + answer
            val result = response.copyOf(offset + answer.size)
            System.arraycopy(answer, 0, result, offset, answer.size)

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build blocked DNS response", e)
            return null
        }
    }

    private fun extractDomain(dnsData: ByteArray): String? {
        if (dnsData.size < 12) return null

        try {
            val domainParts = mutableListOf<String>()
            var offset = 12

            while (offset < dnsData.size) {
                val length = dnsData[offset].toInt() and 0xFF
                if (length == 0) break
                if (length and 0xC0 == 0xC0) break

                offset++
                if (offset + length > dnsData.size) return null

                val part = String(dnsData, offset, length)
                domainParts.add(part)
                offset += length
            }

            return if (domainParts.isEmpty()) null else domainParts.joinToString(".")
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Build DNS response packet from raw original packet bytes
     */
    private fun buildDnsResponsePacketFromRaw(
        originalPacket: ByteArray,
        dnsResponse: ByteArray,
        ipHeaderLength: Int
    ): ByteArray {
        val ipHeader = ByteArray(ipHeaderLength)
        System.arraycopy(originalPacket, 0, ipHeader, 0, ipHeaderLength)

        // Modify total length
        ipHeader[2] = ((ipHeaderLength + 8 + dnsResponse.size) shr 8).toByte()
        ipHeader[3] = ((ipHeaderLength + 8 + dnsResponse.size) and 0xFF).toByte()

        // Swap source and destination IP
        val tempIp = ByteArray(4)
        System.arraycopy(ipHeader, 12, tempIp, 0, 4)
        System.arraycopy(ipHeader, 16, ipHeader, 12, 4)
        System.arraycopy(tempIp, 0, ipHeader, 16, 4)

        // Build UDP header
        val udpHeader = ByteArray(8)
        udpHeader[0] = 0x00.toByte()
        udpHeader[1] = 0x35.toByte() // Source port 53
        udpHeader[2] = originalPacket[ipHeaderLength].toByte()
        udpHeader[3] = originalPacket[ipHeaderLength + 1].toByte()
        udpHeader[4] = ((8 + dnsResponse.size) shr 8).toByte()
        udpHeader[5] = ((8 + dnsResponse.size) and 0xFF).toByte()
        udpHeader[6] = 0x00
        udpHeader[7] = 0x00

        val responsePacket = ByteArray(ipHeaderLength + 8 + dnsResponse.size)
        System.arraycopy(ipHeader, 0, responsePacket, 0, ipHeaderLength)
        System.arraycopy(udpHeader, 0, responsePacket, ipHeaderLength, 8)
        System.arraycopy(dnsResponse, 0, responsePacket, ipHeaderLength + 8, dnsResponse.size)

        return responsePacket
    }

    private fun getCachedDns(domain: String): ByteArray? {
        synchronized(cacheLock) {
            val entry = dnsCache[domain] ?: return null
            if (System.currentTimeMillis() - entry.second > CACHE_TTL) {
                dnsCache.remove(domain)
                return null
            }
            return entry.first
        }
    }

    private fun cacheDns(domain: String, response: ByteArray) {
        synchronized(cacheLock) {
            if (dnsCache.size >= 200) {
                val oldest = dnsCache.entries.iterator()
                if (oldest.hasNext()) oldest.next()
                oldest.remove()
            }
            dnsCache[domain] = Pair(response, System.currentTimeMillis())
        }
    }

    fun getBlockedCount(): Long {
        return blockedCount
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
