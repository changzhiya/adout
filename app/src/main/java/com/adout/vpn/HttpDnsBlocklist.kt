package com.adout.vpn

object HttpDnsBlocklist {
    // 精确 IP 黑名单（已知 HttpDNS 服务商，不包含公共 DNS）
    private val EXACT_IPS = setOf(
        "203.107.1.1",      // 阿里云 HttpDNS
        "203.107.1.33",     // 阿里云 HttpDNS 备用
        "119.29.29.98",     // 腾讯 HttpDNS
        "101.226.125.115",  // 360 HttpDNS
        "180.76.76.76",     // 百度 HttpDNS
    )

    fun shouldBlock(ip: String): Boolean {
        return ip in EXACT_IPS
    }
}
