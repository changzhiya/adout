package com.adout.vpn

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

    class IpBehavior {
        val queryCount = AtomicInteger(0)
        val httpPortHits = AtomicInteger(0)
        val dnsLikePayloads = AtomicInteger(0)
        @Volatile var lastUpdated: Long = System.currentTimeMillis()
    }

    private val suspiciousIps = ConcurrentHashMap<String, IpBehavior>()
    private const val BLOCK_THRESHOLD = 3

    fun analyze(destIp: String, destPort: Int, payload: ByteArray): AnalysisResult {
        // 端口 53 不分析
        if (destPort == 53) {
            return AnalysisResult(isSuspicious = false, shouldBlock = false)
        }

        // 检查是否为 DNS-like 载荷
        val isDnsLike = isDnsLikePayload(payload)

        if (isDnsLike && (destPort == 80 || destPort == 443 || destPort > 1024)) {
            // 可疑行为
            val behavior = suspiciousIps.getOrPut(destIp) { IpBehavior() }
            behavior.queryCount.incrementAndGet()
            behavior.httpPortHits.incrementAndGet()
            behavior.dnsLikePayloads.incrementAndGet()
            behavior.lastUpdated = System.currentTimeMillis()

            return AnalysisResult(
                isSuspicious = true,
                shouldBlock = behavior.queryCount.get() >= BLOCK_THRESHOLD
            )
        }

        return AnalysisResult(isSuspicious = false, shouldBlock = false)
    }

    private fun isDnsLikePayload(payload: ByteArray): Boolean {
        // 简单检查：DNS 查询通常以 0x00 0x01 开头（Transaction ID）
        if (payload.size < 4) return false
        return payload[0] == 0x00.toByte() && payload[1] == 0x01.toByte()
    }

    fun getBehavior(ip: String): IpBehavior? = suspiciousIps[ip]

    fun clear() = suspiciousIps.clear()
}
