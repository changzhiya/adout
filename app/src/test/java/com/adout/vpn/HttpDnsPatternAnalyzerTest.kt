package com.adout.vpn

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HttpDnsPatternAnalyzerTest {

    @Before
    fun setup() {
        HttpDnsPatternAnalyzer.clear()
    }

    @Test
    fun `non-standard port with DNS pattern is suspicious`() {
        val payload = byteArrayOf(0x00, 0x01, 0x00, 0x00)
        val result = HttpDnsPatternAnalyzer.analyze("1.2.3.4", 80, payload)
        assertTrue(result.isSuspicious)
    }

    @Test
    fun `port 53 is not suspicious`() {
        val payload = byteArrayOf(0x00, 0x01, 0x00, 0x00)
        val result = HttpDnsPatternAnalyzer.analyze("1.2.3.4", 53, payload)
        assertFalse(result.isSuspicious)
    }

    @Test
    fun `repeated suspicious calls trigger block`() {
        val payload = byteArrayOf(0x00, 0x01, 0x00, 0x00)
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
        val payload = byteArrayOf(0x00, 0x01, 0x00, 0x00)
        HttpDnsPatternAnalyzer.analyze("1.2.3.4", 80, payload)
        HttpDnsPatternAnalyzer.clear()
        assertNull(HttpDnsPatternAnalyzer.getBehavior("1.2.3.4"))
    }
}
