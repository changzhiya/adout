package com.adout.vpn

/**
 * Pure DNS protocol functions for building and parsing DNS messages.
 * Extracted from TunnelManager for testability.
 */
object DnsProtocol {

    /**
     * Extract domain name from a DNS query message.
     */
    fun extractDomain(dnsData: ByteArray): String? {
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
     * Build a DNS response that returns 0.0.0.0 for blocked domain.
     */
    fun buildBlockedDnsResponse(dnsQuery: ByteArray): ByteArray? {
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
            return null
        }
    }

    /**
     * Compute IP header checksum (RFC 791).
     * Sum all 16-bit words, add carry, take one's complement.
     */
    fun computeIpChecksum(header: ByteArray): Int {
        var sum = 0
        var i = 0
        while (i < header.size) {
            val word = ((header[i].toInt() and 0xFF) shl 8) or
                    (if (i + 1 < header.size) (header[i + 1].toInt() and 0xFF) else 0)
            sum += word
            sum = (sum and 0xFFFF) + (sum shr 16)
            i += 2
        }
        return sum.inv() and 0xFFFF
    }

    /**
     * Build DNS response packet from raw original packet bytes.
     * Swaps IP src/dst, computes new checksum, builds UDP header.
     */
    fun buildDnsResponsePacketFromRaw(
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

        // Recalculate IP header checksum
        ipHeader[10] = 0
        ipHeader[11] = 0
        val checksum = computeIpChecksum(ipHeader)
        ipHeader[10] = (checksum shr 8).toByte()
        ipHeader[11] = (checksum and 0xFF).toByte()

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
}
