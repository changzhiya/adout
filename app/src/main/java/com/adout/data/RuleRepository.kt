package com.adout.data

import android.content.Context
import android.util.Log
import com.adout.filter.FilterDownloader
import com.adout.filter.FilterParser
import com.adout.rule.RuleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RuleRepository - Rule repository management
 *
 * Integrates AdGuardFilters, provides unified rule access interface.
 * Supports:
 * - Built-in rules (hardcoded common ad domains)
 * - AdGuardFilters rules (downloaded from GitHub)
 * - Custom rules (user imported)
 */
class RuleRepository(private val context: Context? = null) {

    companion object {
        private const val TAG = "RuleRepository"
    }

    private val filterDownloader = FilterDownloader(context)

    // Built-in rules - Common domestic app ad domains
    private val builtInRules = listOf(
        // Xiaomi ads
        "||ad.xiaomi.com^",
        "||api.ad.xiaomi.com^",
        "||sdkconfig.ad.xiaomi.com^",
        "||ad.mi.com^",

        // Tencent ads
        "||e.qq.com^",
        "||mi.gdt.qq.com^",
        "||pgdt.gtimg.cn^",
        "||t.gdt.qq.com^",

        // Alibaba ads
        "||ad.alicdn.com^",
        "||mmstat.com^",
        "||atanx.alicdn.com^",

        // Baidu ads
        "||pos.baidu.com^",
        "||cpro.baidu.com^",
        "||hm.baidu.com^",

        // ByteDance ads
        "||pangolin-sdk-toutiao.com^",
        "||ad.toutiao.com^",
        "||ad.snssdk.com^",

        // Common ad domains
        "||googleadservices.com^",
        "||googlesyndication.com^",
        "||adservice.google.com^",
        "||pagead2.googlesyndication.com^",

        // Analytics tracking
        "||analytics.google.com^",
        "||www.google-analytics.com^",
        "||ssl.google-analytics.com^"
    )

    /**
     * Get built-in rules
     */
    fun getBuiltInRules(): List<String> {
        return builtInRules
    }

    /**
     * Download and get AdGuardFilters rules
     */
    suspend fun getAdGuardFilters(): List<String> = withContext(Dispatchers.IO) {
        try {
            val filters = filterDownloader.downloadAllFilters()
            val merged = filterDownloader.mergeFilters(filters)

            // Save to local cache
            filterDownloader.saveFiltersToCache(filters)

            Log.i(TAG, "Downloaded AdGuardFilters: ${merged.size} rules")
            merged
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download AdGuardFilters", e)
            // Try loading from cache
            loadFromCache()
        }
    }

    /**
     * Load rules from local cache
     */
    private suspend fun loadFromCache(): List<String> = withContext(Dispatchers.IO) {
        try {
            val filters = filterDownloader.loadFiltersFromCache()
            val merged = filterDownloader.mergeFilters(filters)

            if (merged.isNotEmpty()) {
                Log.i(TAG, "Loaded AdGuardFilters from cache: ${merged.size} rules")
                merged
            } else {
                Log.w(TAG, "No cached rules found, using built-in rules only")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load from cache", e)
            emptyList()
        }
    }

    /**
     * Merge all rule sources
     * @param includeAdGuard Whether to include AdGuardFilters rules
     * @param customRules Custom rule list
     * @return Merged rule list
     */
    suspend fun getAllRules(
        includeAdGuard: Boolean = true,
        customRules: List<String> = emptyList()
    ): List<String> = withContext(Dispatchers.IO) {
        val allRules = mutableSetOf<String>()

        // Add built-in rules
        allRules.addAll(builtInRules)

        // Add AdGuardFilters rules
        if (includeAdGuard) {
            val adGuardRules = getAdGuardFilters()
            allRules.addAll(adGuardRules)
        }

        // Add custom rules
        allRules.addAll(customRules)

        Log.i(TAG, "Total rules: ${allRules.size}")
        allRules.toList()
    }

    /**
     * Merge multiple rule sources
     */
    fun mergeRules(vararg ruleSources: List<String>): List<String> {
        val merged = mutableSetOf<String>()
        for (source in ruleSources) {
            merged.addAll(source)
        }
        return merged.toList()
    }

    /**
     * Filter valid rules
     */
    fun filterValidRules(rules: List<String>): List<String> {
        return rules.filter { RuleParser.isValidRule(it) }
    }

    /**
     * Get rule statistics
     */
    fun getRulesSummary(rules: List<String>): Map<String, Any> {
        val categorized = FilterParser.categorizeRules(rules)
        val summary = mutableMapOf<String, Any>()
        summary["total"] = rules.size
        summary["blacklist"] = categorized.blacklist.size
        summary["whitelist"] = categorized.whitelist.size
        summary["builtIn"] = builtInRules.size
        return summary
    }

    /**
     * Create default rules file content
     */
    fun createDefaultRulesFile(): String {
        return """
            # Adout Default Ad Rules
            # Last Updated: 2026-05-24
            # Source: Built-in rules + AdGuardFilters

            # Common domestic app ad domains
            ||ad.xiaomi.com^
            ||api.ad.xiaomi.com^
            ||sdkconfig.ad.xiaomi.com^
            ||ad.mi.com^

            # Tencent ads
            ||e.qq.com^
            ||mi.gdt.qq.com^
            ||pgdt.gtimg.cn^

            # Alibaba ads
            ||ad.alicdn.com^
            ||mmstat.com^

            # Baidu ads
            ||pos.baidu.com^
            ||cpro.baidu.com^

            # ByteDance ads
            ||pangolin-sdk-toutiao.com^
            ||ad.toutiao.com^

            # Common ad domains
            ||googleadservices.com^
            ||googlesyndication.com^
        """.trimIndent()
    }
}
