package com.adout.vpn

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class HttpDnsInterceptorTest {

    @Before
    fun setup() {
        HttpDnsBlocklist.clearDynamicIps()
        HttpDnsPatternAnalyzer.clear()
    }

    @Test
    fun `port 53 returns UNKNOWN`() {
        val interceptor = HttpDnsInterceptor()
        val buffer = ByteBuffer.allocate(0)
        val result = interceptor.check("1.2.3.4", 53, buffer)
        assertEquals(InterceptorResult.UNKNOWN, result)
    }

    @Test
    fun `known HttpDNS IP returns BLOCK`() {
        val interceptor = HttpDnsInterceptor()
        val buffer = ByteBuffer.allocate(0)
        val result = interceptor.check("203.107.1.1", 80, buffer)
        assertEquals(InterceptorResult.BLOCK, result)
    }

    @Test
    fun `unknown IP returns UNKNOWN`() {
        val interceptor = HttpDnsInterceptor()
        val buffer = ByteBuffer.allocate(0)
        val result = interceptor.check("8.8.8.8", 80, buffer)
        assertEquals(InterceptorResult.UNKNOWN, result)
    }
}
