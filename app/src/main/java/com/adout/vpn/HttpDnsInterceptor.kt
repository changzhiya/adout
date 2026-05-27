package com.adout.vpn

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

enum class InterceptorResult {
    BLOCK,    // 拦截，返回空响应
    ALLOW,    // 放行，正常转发
    UNKNOWN   // 未知，继续原有逻辑
}

class HttpDnsInterceptor {

    private val patternAnalyzer = HttpDnsPatternAnalyzer
    private val blockedCount = AtomicLong(0)

    fun check(destIp: String, destPort: Int, payload: ByteBuffer): InterceptorResult {
        // Fast path 1: port 53 uses existing DNS logic
        if (destPort == 53) {
            return InterceptorResult.UNKNOWN
        }

        // Fast path 2: exact IP match
        if (HttpDnsBlocklist.shouldBlock(destIp)) {
            blockedCount.incrementAndGet()
            return InterceptorResult.BLOCK
        }

        // Slow path: behavioral analysis
        val payloadArray = ByteArray(payload.remaining())
        payload.get(payloadArray)
        payload.rewind()

        val analysis = patternAnalyzer.analyze(destIp, destPort, payloadArray)
        if (analysis.shouldBlock) {
            HttpDnsBlocklist.addDynamicIp(destIp)
            blockedCount.incrementAndGet()
            return InterceptorResult.BLOCK
        }

        return InterceptorResult.UNKNOWN
    }

    fun getBlockedCount(): Long = blockedCount.get()
}
