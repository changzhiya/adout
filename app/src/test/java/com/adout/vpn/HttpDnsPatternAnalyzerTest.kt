package com.adout.vpn

import org.junit.Assert.*
import org.junit.Test

class HttpDnsPatternAnalyzerTest {

    @Test
    fun `non-standard port with DNS pattern is suspicious`() {
        val payload = byteArrayOf(0x00, 0x01, 0x00, 0x00)  // 简化的 DNS 头
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
        // 连续 3 次命中特征
        HttpDnsPatternAnalyzer.analyze("5.6.7.8", 80, payload)
        HttpDnsPatternAnalyzer.analyze("5.6.7.8", 80, payload)
        val result = HttpDnsPatternAnalyzer.analyze("5.6.7.8", 80, payload)
        assertTrue(result.shouldBlock)
    }
}
