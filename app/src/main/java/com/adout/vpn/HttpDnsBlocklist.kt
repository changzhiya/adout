package com.adout.vpn

import java.util.concurrent.ConcurrentHashMap

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

    // 动态黑名单 - 运行时自动发现的 HttpDNS IP
    private val dynamicIps = ConcurrentHashMap<String, Long>()
    private const val MAX_DYNAMIC_IPS = 1000
    private const val DYNAMIC_IP_TTL = 3600_000L  // 1 小时

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
        if (IP_RANGES.any { it.contains(ip) }) return true
        return isDynamicBlocked(ip)
    }

    fun addDynamicIp(ip: String) {
        addDynamicIp(ip, System.currentTimeMillis())
    }

    fun addDynamicIp(ip: String, timestamp: Long) {
        if (dynamicIps.size >= MAX_DYNAMIC_IPS) {
            clearExpired()
            if (dynamicIps.size >= MAX_DYNAMIC_IPS) {
                val oldest = dynamicIps.entries.minByOrNull { it.value }
                oldest?.let { dynamicIps.remove(it.key) }
            }
        }
        dynamicIps[ip] = timestamp
    }

    private fun isDynamicBlocked(ip: String): Boolean {
        val timestamp = dynamicIps[ip] ?: return false
        if (System.currentTimeMillis() - timestamp > DYNAMIC_IP_TTL) {
            dynamicIps.remove(ip)
            return false
        }
        return true
    }

    fun clearExpired() {
        val now = System.currentTimeMillis()
        dynamicIps.entries.removeIf { now - it.value > DYNAMIC_IP_TTL }
    }

    fun clearDynamicIps() {
        dynamicIps.clear()
    }
}
