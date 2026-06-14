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
        // DNS 查询最小长度: Header (12 bytes) + Query (at least 5 bytes: 1 label + null + QTYPE + QCLASS)
        if (payload.size < 17) return false

        // Check DNS header flags (byte 2):
        // - QR bit (bit 7) should be 0 (query, not response)
        // - Opcode (bits 3-6) should be 0 (standard query)
        // - Z bits (bits 0-2) should be 0
        val flags = payload[2].toInt() and 0xFF
        if (flags and 0x80 != 0) return false  // QR = 1 (response)
        if (flags and 0x78 != 0) return false  // Opcode != 0 (not standard query)

        // Check RCODE (byte 3, lower 4 bits) should be 0 for queries
        val rcode = payload[3].toInt() and 0x0F
        if (rcode != 0) return false

        // QDCOUNT (bytes 4-5) should be >= 1
        val qdcount = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
        if (qdcount < 1) return false

        // ANCOUNT (bytes 6-7) should be 0 for queries
        val ancount = ((payload[6].toInt() and 0xFF) shl 8) or (payload[7].toInt() and 0xFF)
        if (ancount != 0) return false

        // NSCOUNT (bytes 8-9) should be 0 for queries
        val nscount = ((payload[8].toInt() and 0xFF) shl 8) or (payload[9].toInt() and 0xFF)
        if (nscount != 0) return false

        // ARCOUNT (bytes 10-11) - allow 0 or more (EDNS adds to additional)

        // Check that query name starts with a valid label length
        val labelLength = payload[12].toInt() and 0xFF
        if (labelLength == 0 || labelLength > 63) return false
        if (labelLength and 0xC0 == 0xC0) return false  // Compression pointer, not valid at start

        return true
    }

    fun getBehavior(ip: String): IpBehavior? = suspiciousIps[ip]

    fun clear() = suspiciousIps.clear()
}
