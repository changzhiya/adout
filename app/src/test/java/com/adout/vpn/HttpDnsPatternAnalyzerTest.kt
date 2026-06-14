package com.adout.vpn

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HttpDnsPatternAnalyzerTest {

    @Before
    fun setup() {
        HttpDnsPatternAnalyzer.clear()
    }

    /**
     * Create a minimal valid DNS query payload.
     * DNS Header (12 bytes) + Query (QNAME: \x03www\x05baidu\x03com\x00 + QTYPE + QCLASS)
     */
    private fun createDnsQueryPayload(): ByteArray {
        return byteArrayOf(
            // DNS Header
            0x00, 0x01,  // Transaction ID: 1
            0x01, 0x00,  // Flags: standard query with RD
            0x00, 0x01,  // QDCOUNT: 1
            0x00, 0x00,  // ANCOUNT: 0
            0x00, 0x00,  // NSCOUNT: 0
            0x00, 0x00,  // ARCOUNT: 0
            // Query: www.baidu.com
            0x03,        // Label length: 3
            'w'.toByte(), 'w'.toByte(), 'w'.toByte(),
            0x05,        // Label length: 5
            'b'.toByte(), 'a'.toByte(), 'i'.toByte(), 'd'.toByte(), 'u'.toByte(),
            0x03,        // Label length: 3
            'c'.toByte(), 'o'.toByte(), 'm'.toByte(),
            0x00,        // End of QNAME
            0x00, 0x01,  // QTYPE: A record
            0x00, 0x01   // QCLASS: IN
        )
    }

    @Test
    fun `non-standard port with DNS pattern is suspicious`() {
        val payload = createDnsQueryPayload()
        val result = HttpDnsPatternAnalyzer.analyze("1.2.3.4", 80, payload)
        assertTrue(result.isSuspicious)
    }

    @Test
    fun `port 53 is not suspicious`() {
        val payload = createDnsQueryPayload()
        val result = HttpDnsPatternAnalyzer.analyze("1.2.3.4", 53, payload)
        assertFalse(result.isSuspicious)
    }

    @Test
    fun `repeated suspicious calls trigger block`() {
        val payload = createDnsQueryPayload()
        HttpDnsPatternAnalyzer.analyze("5.6.7.8", 80, payload)
        HttpDnsPatternAnalyzer.analyze("5.6.7.8", 80, payload)
        val result = HttpDnsPatternAnalyzer.analyze("5.6.7.8", 80, payload)
        assertTrue(result.shouldBlock)
    }

    @Test
    fun `empty payload is not suspicious`() {
        val payload = byteArrayOf()
        val result = HttpDnsPatternAnalyzer.analyze("1.2.3.4", 80, payload)
        assertFalse(result.isSuspicious)
    }

    @Test
    fun `clear resets all behavior`() {
        val payload = createDnsQueryPayload()
        HttpDnsPatternAnalyzer.analyze("1.2.3.4", 80, payload)
        HttpDnsPatternAnalyzer.clear()
        assertNull(HttpDnsPatternAnalyzer.getBehavior("1.2.3.4"))
    }

    @Test
    fun `short payload is not suspicious`() {
        // Payload shorter than 17 bytes should not be detected as DNS
        val payload = byteArrayOf(0x00, 0x01, 0x00, 0x00)
        val result = HttpDnsPatternAnalyzer.analyze("1.2.3.4", 80, payload)
        assertFalse(result.isSuspicious)
    }

    @Test
    fun `non-DNS payload is not suspicious`() {
        // HTTP-like payload (not DNS format)
        val payload = byteArrayOf(
            'G'.toByte(), 'E'.toByte(), 'T'.toByte(), ' '.toByte(),
            '/'.toByte(), ' '.toByte(), 'H'.toByte(), 'T'.toByte(),
            'T'.toByte(), 'P'.toByte(), '/'.toByte(), '1'.toByte(),
            '.'.toByte(), '1'.toByte(), '\r'.toByte(), '\n'.toByte(),
            '\r'.toByte()
        )
        val result = HttpDnsPatternAnalyzer.analyze("1.2.3.4", 80, payload)
        assertFalse(result.isSuspicious)
    }

    @Test
    fun `response packet is not suspicious`() {
        val payload = createDnsQueryPayload()
        // Set QR bit to 1 (response)
        payload[2] = (payload[2].toInt() or 0x80).toByte()
        val result = HttpDnsPatternAnalyzer.analyze("1.2.3.4", 80, payload)
        assertFalse(result.isSuspicious)
    }
}
