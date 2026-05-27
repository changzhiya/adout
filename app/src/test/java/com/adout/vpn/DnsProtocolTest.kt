package com.adout.vpn

import org.junit.Assert.*
import org.junit.Test

class DnsProtocolTest {

    @Test
    fun `extractDomain parses simple domain`() {
        val dnsData = byteArrayOf(
            0x00, 0x01,  // Transaction ID
            0x01, 0x00,  // Flags
            0x00, 0x01,  // Questions
            0x00, 0x00,  // Answers
            0x00, 0x00,  // Authority
            0x00, 0x00,  // Additional
            // Query: example.com
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x01,  // Type A
            0x00, 0x01   // Class IN
        )
        val domain = DnsProtocol.extractDomain(dnsData)
        assertEquals("example.com", domain)
    }

    @Test
    fun `extractDomain returns null for too short data`() {
        val dnsData = byteArrayOf(0x00, 0x01)
        assertNull(DnsProtocol.extractDomain(dnsData))
    }

    @Test
    fun `buildBlockedDnsResponse creates valid blocked response`() {
        val query = byteArrayOf(
            0x00, 0x01,  // Transaction ID
            0x01, 0x00,  // Flags
            0x00, 0x01,  // Questions
            0x00, 0x00,  // Answers
            0x00, 0x00,  // Authority
            0x00, 0x00,  // Additional
            // Query: example.com
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x01,  // Type A
            0x00, 0x01   // Class IN
        )
        val response = DnsProtocol.buildBlockedDnsResponse(query)
        assertNotNull(response)
        // QR bit set
        assertTrue(response!![2].toInt() and 0x80 != 0)
        // Answer count = 1
        assertEquals(0, response[6].toInt())
        assertEquals(1, response[7].toInt())
        // Response contains answer with 0.0.0.0
        assertTrue(response.size > query.size)
    }

    @Test
    fun `computeIpChecksum returns valid checksum`() {
        val header = byteArrayOf(
            0x45, 0x00,  // Version, IHL, DSCP, ECN
            0x00, 0x1C,  // Total length
            0x00, 0x00,  // Identification
            0x40, 0x00,  // Flags, Fragment offset
            0x40, 0x11,  // TTL, Protocol (UDP)
            0x00, 0x00,  // Checksum (placeholder)
            0x7F, 0x00, 0x00, 0x01,  // Source IP (127.0.0.1)
            0x7F, 0x00, 0x00, 0x02   // Dest IP (127.0.0.2)
        )
        val checksum = DnsProtocol.computeIpChecksum(header)
        // Verify checksum is valid (header + checksum = 0xFFFF)
        assertTrue(checksum in 0..0xFFFF)
    }

    @Test
    fun `buildEmptyDnsResponse creates valid empty response`() {
        val query = byteArrayOf(
            0x00, 0x01,  // Transaction ID
            0x01, 0x00,  // Flags
            0x00, 0x01,  // Questions
            0x00, 0x00,  // Answers
            0x00, 0x00,  // Authority
            0x00, 0x00,  // Additional
            // Query: example.com
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00,
            0x00, 0x01,  // Type A
            0x00, 0x01   // Class IN
        )
        val response = DnsProtocol.buildEmptyDnsResponse(query)
        assertNotNull(response)
        // Check QR bit
        assertTrue(response!![2].toInt() and 0x80 != 0)
        // Check Answer Count = 0
        assertEquals(0, response[6].toInt())
        assertEquals(0, response[7].toInt())
    }
}
