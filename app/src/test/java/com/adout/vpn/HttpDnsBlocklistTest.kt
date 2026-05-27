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
        assertTrue(HttpDnsBlocklist.shouldBlock("101.226.1.1"))   // 360 广告
        assertTrue(HttpDnsBlocklist.shouldBlock("180.76.1.1"))    // 百度广告
        assertTrue(HttpDnsBlocklist.shouldBlock("123.125.1.1"))   // 字节广告
    }

    @Test
    fun `IP range match returns false for non-ad IP`() {
        assertFalse(HttpDnsBlocklist.shouldBlock("101.227.1.1"))  // 不在范围内
        assertFalse(HttpDnsBlocklist.shouldBlock("180.77.1.1"))   // 不在范围内
    }
}
