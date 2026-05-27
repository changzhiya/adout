package com.adout.vpn

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class HttpDnsIntegrationTest {

    @Before
    fun setup() {
        HttpDnsBlocklist.clearDynamicIps()
        HttpDnsPatternAnalyzer.clear()
    }

    @Test
    fun `full flow - known HttpDNS IP is blocked`() {
        val interceptor = HttpDnsInterceptor()
        val payload = ByteBuffer.allocate(100)
        val result = interceptor.check("203.107.1.1", 80, payload)
        assertEquals(InterceptorResult.BLOCK, result)
        assertEquals(1, interceptor.getBlockedCount())
    }

    @Test
    fun `full flow - port 53 is passed through`() {
        val interceptor = HttpDnsInterceptor()
        val payload = ByteBuffer.allocate(100)
        val result = interceptor.check("1.2.3.4", 53, payload)
        assertEquals(InterceptorResult.UNKNOWN, result)
        assertEquals(0, interceptor.getBlockedCount())
    }

    @Test
    fun `full flow - dynamic IP detection`() {
        val interceptor = HttpDnsInterceptor()
        val payload = ByteBuffer.allocate(100)
        payload.put(0, 0x00.toByte())
        payload.put(1, 0x01.toByte())

        // 连续触发行为分析
        interceptor.check("9.8.7.6", 80, payload)
        interceptor.check("9.8.7.6", 80, payload)
        val result = interceptor.check("9.8.7.6", 80, payload)
        assertEquals(InterceptorResult.BLOCK, result)
        assertTrue(HttpDnsBlocklist.shouldBlock("9.8.7.6"))
    }

    @Test
    fun `full flow - IP range blocking`() {
        val interceptor = HttpDnsInterceptor()
        val payload = ByteBuffer.allocate(100)
        val result = interceptor.check("101.226.125.1", 80, payload)
        assertEquals(InterceptorResult.BLOCK, result)
    }

    @Test
    fun `full flow - normal IP passes through`() {
        val interceptor = HttpDnsInterceptor()
        val payload = ByteBuffer.allocate(100)
        val result = interceptor.check("8.8.8.8", 80, payload)
        assertEquals(InterceptorResult.UNKNOWN, result)
        assertEquals(0, interceptor.getBlockedCount())
    }
}
