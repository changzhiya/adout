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

    // IP 段黑名单（广告/CDN 网段）- 使用 /24 更精确，减少误封风险
    private val IP_RANGES = listOf(
        IpRange("101.226.125.0", 24),   // 360 广告
        IpRange("180.76.76.0", 24),     // 百度广告
        IpRange("123.125.0.0", 16),     // 字节广告
    )

    data class IpRange(val baseIp: String, val prefixLength: Int) {
        private val baseLong: Long = ipToLong(baseIp)!!
        private val mask: Long = (-1L shl (32 - prefixLength))

        fun contains(ip: String): Boolean {
            val ipLong = ipToLong(ip) ?: return false
            return (ipLong and mask) == (baseLong and mask)
        }

        private fun ipToLong(ip: String): Long? {
            val parts = ip.split(".")
            if (parts.size != 4) return null
            return try {
                (parts[0].toLong() shl 24) or
                (parts[1].toLong() shl 16) or
                (parts[2].toLong() shl 8) or
                parts[3].toLong()
            } catch (e: NumberFormatException) {
                null
            }
        }
    }

    fun shouldBlock(ip: String): Boolean {
        if (ip in EXACT_IPS) return true
        return IP_RANGES.any { it.contains(ip) }
    }
}
