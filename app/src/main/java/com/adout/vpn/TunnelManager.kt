package com.adout.vpn

import android.os.ParcelFileDescriptor
import com.adout.rule.RuleEngine
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * TunnelManager - Traffic tunnel manager
 *
 * Uses DnsProxyWrapper to handle DNS requests,
 * avoiding implementing DNS protocol parsing ourselves.
 */
class TunnelManager(
    private val vpnInterface: ParcelFileDescriptor,
    private val ruleEngine: RuleEngine,
    private val dnsProxy: DnsProxyWrapper? = null
) {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var blockedCount = 0L

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
                    e.printStackTrace()
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
            // Check if it's an IP packet
            if (buffer.remaining() < 20) return

            val version = (buffer.get(0).toInt() and 0xF0) shr 4
            if (version != 4) return // Only handle IPv4

            // Get protocol type
            val protocol = buffer.get(9).toInt() and 0xFF

            // Check if it's UDP (protocol number 17)
            if (protocol == 17) {
                processUdpPacket(buffer, outputStream)
            } else {
                // Other protocols forward directly
                buffer.rewind()
                outputStream.write(buffer.array(), 0, buffer.remaining())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processUdpPacket(buffer: ByteBuffer, outputStream: FileOutputStream) {
        try {
            // IP header length
            val ipHeaderLength = (buffer.get(0).toInt() and 0x0F) * 4

            // UDP header
            val udpHeaderStart = ipHeaderLength
            if (buffer.remaining() < udpHeaderStart + 8) return

            val destPort = ((buffer.get(udpHeaderStart + 2).toInt() and 0xFF) shl 8) or
                    (buffer.get(udpHeaderStart + 3).toInt() and 0xFF)

            // Check if it's DNS request (port 53)
            if (destPort == 53) {
                processDnsPacket(buffer, ipHeaderLength, outputStream)
            } else {
                // Other UDP traffic forward directly
                buffer.rewind()
                outputStream.write(buffer.array(), 0, buffer.remaining())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processDnsPacket(
        buffer: ByteBuffer,
        ipHeaderLength: Int,
        outputStream: FileOutputStream
    ) {
        try {
            // Extract DNS data
            val dnsDataStart = ipHeaderLength + 8 // UDP header 8 bytes
            val dnsDataLength = buffer.remaining() - dnsDataStart

            if (dnsDataLength <= 0) return

            val dnsData = ByteArray(dnsDataLength)
            buffer.position(dnsDataStart)
            buffer.get(dnsData)

            // Use DnsProxyWrapper to extract domain
            val domain = dnsProxy?.extractDomain(dnsData)

            if (domain != null) {
                // Check if should block
                if (ruleEngine.shouldBlock(domain)) {
                    blockedCount++

                    // Use DnsProxyWrapper to handle request (will return 0.0.0.0)
                    val response = dnsProxy?.handleRequest(dnsData)

                    if (response != null) {
                        // Build response packet
                        val responsePacket = buildDnsResponsePacket(buffer, response, ipHeaderLength)
                        outputStream.write(responsePacket)
                        return
                    }
                }
            }

            // Don't block, forward directly
            buffer.rewind()
            outputStream.write(buffer.array(), 0, buffer.remaining())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildDnsResponsePacket(
        originalPacket: ByteBuffer,
        dnsResponse: ByteArray,
        ipHeaderLength: Int
    ): ByteArray {
        // Copy IP header
        val ipHeader = ByteArray(ipHeaderLength)
        originalPacket.rewind()
        originalPacket.get(ipHeader)

        // Modify IP header
        ipHeader[2] = ((ipHeaderLength + 8 + dnsResponse.size) shr 8).toByte() // Total length
        ipHeader[3] = ((ipHeaderLength + 8 + dnsResponse.size) and 0xFF).toByte()

        // Swap source and destination IP
        val tempIp = ByteArray(4)
        System.arraycopy(ipHeader, 12, tempIp, 0, 4)
        System.arraycopy(ipHeader, 16, ipHeader, 12, 4)
        System.arraycopy(tempIp, 0, ipHeader, 16, 4)

        // Build UDP header
        val udpHeader = ByteArray(8)
        udpHeader[0] = 0x00.toByte() // Source port high byte
        udpHeader[1] = 0x35.toByte() // Source port low byte (53)
        udpHeader[2] = originalPacket.get(ipHeaderLength).toByte() // Destination port high byte
        udpHeader[3] = originalPacket.get(ipHeaderLength + 1).toByte() // Destination port low byte
        udpHeader[4] = ((8 + dnsResponse.size) shr 8).toByte() // UDP length
        udpHeader[5] = ((8 + dnsResponse.size) and 0xFF).toByte()
        // UDP checksum set to 0 (optional)
        udpHeader[6] = 0x00
        udpHeader[7] = 0x00

        // Combine response packet
        val responsePacket = ByteArray(ipHeaderLength + 8 + dnsResponse.size)
        System.arraycopy(ipHeader, 0, responsePacket, 0, ipHeaderLength)
        System.arraycopy(udpHeader, 0, responsePacket, ipHeaderLength, 8)
        System.arraycopy(dnsResponse, 0, responsePacket, ipHeaderLength + 8, dnsResponse.size)

        return responsePacket
    }

    fun getBlockedCount(): Long {
        return blockedCount
    }
}
