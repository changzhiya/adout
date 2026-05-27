package com.adout.vpn

import java.util.concurrent.ConcurrentHashMap

/**
 * Behavioral analyzer that identifies HttpDNS providers by detecting
 * DNS-like payloads on non-standard ports (e.g., port 80/443).
 *
 * This helps catch apps that bypass system DNS by sending raw DNS queries
 * over HTTP/TLS connections to known HttpDNS service IPs.
 */
object HttpDnsPatternAnalyzer {

    data class AnalysisResult(
        val isSuspicious: Boolean,
        val shouldBlock: Boolean
    )

    data class IpBehavior(
        var queryCount: Int = 0,
        var httpPortHits: Int = 0,
        var dnsLikePayloads: Int = 0,
        var lastUpdated: Long = System.currentTimeMillis()
    )

    private val suspiciousIps = ConcurrentHashMap<String, IpBehavior>()
    private const val SUSPICIOUS_THRESHOLD = 3
    private const val BLOCK_THRESHOLD = 10

    fun analyze(destIp: String, destPort: Int, payload: ByteArray): AnalysisResult {
        // 端口 53 是标准 DNS，无需分析
        if (destPort == 53) {
            return AnalysisResult(isSuspicious = false, shouldBlock = false)
        }

        // 检查是否为 DNS-like 载荷
        val isDnsLike = isDnsLikePayload(payload)

        if (isDnsLike && (destPort == 80 || destPort == 443 || destPort > 1024)) {
            // 可疑行为：非标准端口上出现 DNS-like 载荷
            val behavior = suspiciousIps.getOrPut(destIp) { IpBehavior() }
            behavior.queryCount++
            behavior.httpPortHits++
            behavior.dnsLikePayloads++
            behavior.lastUpdated = System.currentTimeMillis()

            return AnalysisResult(
                isSuspicious = true,
                shouldBlock = behavior.queryCount >= SUSPICIOUS_THRESHOLD
            )
        }

        return AnalysisResult(isSuspicious = false, shouldBlock = false)
    }

    private fun isDnsLikePayload(payload: ByteArray): Boolean {
        // 简单检查：DNS 查询通常以 0x00 0x01 开头（Transaction ID = 1）
        if (payload.size < 4) return false
        return payload[0] == 0x00.toByte() && payload[1] == 0x01.toByte()
    }

    fun getBehavior(ip: String): IpBehavior? = suspiciousIps[ip]

    fun clear() = suspiciousIps.clear()
}
