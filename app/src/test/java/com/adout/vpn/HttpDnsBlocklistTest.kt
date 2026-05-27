package com.adout.vpn

import org.junit.Assert.*
import org.junit.Test

class HttpDnsBlocklistTest {

    @Test
    fun `exact IP match returns true for known HttpDNS IP`() {
        assertTrue(HttpDnsBlocklist.shouldBlock("203.107.1.1"))
        assertTrue(HttpDnsBlocklist.shouldBlock("119.29.29.98"))
        assertTrue(HttpDnsBlocklist.shouldBlock("101.226.125.115"))
    }

    @Test
    fun `exact IP match returns false for public DNS`() {
        assertFalse(HttpDnsBlocklist.shouldBlock("223.5.5.5"))
        assertFalse(HttpDnsBlocklist.shouldBlock("119.29.29.29"))
        assertFalse(HttpDnsBlocklist.shouldBlock("114.114.114.114"))
    }

    @Test
    fun `exact IP match returns false for unknown IP`() {
        assertFalse(HttpDnsBlocklist.shouldBlock("8.8.8.8"))
        assertFalse(HttpDnsBlocklist.shouldBlock("1.1.1.1"))
    }

    @Test
    fun `IP range match returns true for ad network IP`() {
        assertTrue(HttpDnsBlocklist.shouldBlock("101.226.125.50"))   // 360 广告 /24
        assertTrue(HttpDnsBlocklist.shouldBlock("180.76.76.50"))     // 百度广告 /24
        assertTrue(HttpDnsBlocklist.shouldBlock("123.125.1.1"))      // 字节广告 /16
    }

    @Test
    fun `IP range match returns false for non-ad IP`() {
        assertFalse(HttpDnsBlocklist.shouldBlock("101.226.126.1"))  // 不在 /24 范围内
        assertFalse(HttpDnsBlocklist.shouldBlock("180.76.77.1"))    // 不在 /24 范围内
        assertFalse(HttpDnsBlocklist.shouldBlock("101.227.1.1"))    // 完全不在范围内
        assertFalse(HttpDnsBlocklist.shouldBlock("180.77.1.1"))     // 完全不在范围内
    }

    @Test
    fun `IP range boundary - first IP in range`() {
        assertTrue(HttpDnsBlocklist.shouldBlock("101.226.125.0"))
    }

    @Test
    fun `IP range boundary - last IP in range`() {
        assertTrue(HttpDnsBlocklist.shouldBlock("101.226.125.255"))
    }

    @Test
    fun `IP range boundary - just outside range`() {
        assertFalse(HttpDnsBlocklist.shouldBlock("101.226.126.0"))
    }

    @Test
    fun `invalid IP format returns false`() {
        assertFalse(HttpDnsBlocklist.shouldBlock(""))
        assertFalse(HttpDnsBlocklist.shouldBlock("not-an-ip"))
        assertFalse(HttpDnsBlocklist.shouldBlock("1.2.3"))
    }

    @Test
    fun `dynamic IP can be added and blocked`() {
        HttpDnsBlocklist.addDynamicIp("1.2.3.4")
        assertTrue(HttpDnsBlocklist.shouldBlock("1.2.3.4"))
        // Clean up
        HttpDnsBlocklist.clearDynamicIps()
    }

    @Test
    fun `dynamic IP expires after TTL`() {
        HttpDnsBlocklist.addDynamicIp("5.6.7.8", System.currentTimeMillis() - 3700_000L)
        HttpDnsBlocklist.clearExpired()
        assertFalse(HttpDnsBlocklist.shouldBlock("5.6.7.8"))
    }

    @Test
    fun `dynamic IP does not expire before TTL`() {
        HttpDnsBlocklist.addDynamicIp("9.10.11.12")
        HttpDnsBlocklist.clearExpired()
        assertTrue(HttpDnsBlocklist.shouldBlock("9.10.11.12"))
        // Clean up
        HttpDnsBlocklist.clearDynamicIps()
    }
}
