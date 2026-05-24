package com.adout.filter

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * FilterDownloader - Downloads rules from AdGuardFilters
 *
 * Uses AdGuard maintained filter lists:
 * - MobileAdsFilter: Mobile ad rules
 * - ChineseFilter: Chinese website rules
 * - BaseFilter: General ad rules
 */
class FilterDownloader(private val context: Context? = null) {

    companion object {
        private const val TAG = "FilterDownloader"

        // AdGuardFilters GitHub Raw URLs
        private const val MOBILE_FILTER_URL =
            "https://raw.githubusercontent.com/AdguardTeam/AdGuardFilters/master/MobileAdsFilter/rules.txt"
        private const val CHINESE_FILTER_URL =
            "https://raw.githubusercontent.com/AdguardTeam/AdGuardFilters/master/ChineseFilter/rules.txt"
        private const val BASE_FILTER_URL =
            "https://raw.githubusercontent.com/AdguardTeam/AdGuardFilters/master/BaseFilter/rules.txt"
        private const val SPYWARE_FILTER_URL =
            "https://raw.githubusercontent.com/AdguardTeam/AdGuardFilters/master/SpywareFilter/rules.txt"

        // Local cache file names
        private const val MOBILE_FILTER_CACHE = "mobile_filter.txt"
        private const val CHINESE_FILTER_CACHE = "chinese_filter.txt"
        private const val BASE_FILTER_CACHE = "base_filter.txt"
        private const val SPYWARE_FILTER_CACHE = "spyware_filter.txt"
    }

    /**
     * Get all filter source URLs
     */
    fun getFilterUrls(): Map<String, String> {
        return mapOf(
            "mobile" to MOBILE_FILTER_URL,
            "chinese" to CHINESE_FILTER_URL,
            "base" to BASE_FILTER_URL,
            "spyware" to SPYWARE_FILTER_URL
        )
    }

    /**
     * Download all filters
     * @return Map of filter name to rule list
     */
    suspend fun downloadAllFilters(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val filters = mutableMapOf<String, List<String>>()

        for ((name, url) in getFilterUrls()) {
            try {
                val content = downloadFromUrl(url)
                if (content != null) {
                    val rules = parseFilterContent(content)
                    filters[name] = rules
                    Log.i(TAG, "Downloaded $name filter: ${rules.size} rules")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download $name filter", e)
            }
        }

        filters
    }

    /**
     * Download a single filter file
     * @param url Filter file URL
     * @return File content
     */
    suspend fun downloadFromUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val content = reader.readText()
                reader.close()
                content
            } else {
                Log.e(TAG, "HTTP ${connection.responseCode} from $url")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from $url", e)
            null
        }
    }

    /**
     * Parse filter file content
     * @param content Filter file content
     * @return List of rules
     */
    fun parseFilterContent(content: String): List<String> {
        return content.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                !line.startsWith("!") && // AdGuard comments
                !line.startsWith("#") && // Regular comments
                !line.startsWith("[") && // Config sections
                isValidRule(line)
            }
    }

    /**
     * Merge multiple filter lists
     * @param filters Map of filter name to rule list
     * @return Merged rule list (deduplicated)
     */
    fun mergeFilters(filters: Map<String, List<String>>): List<String> {
        val merged = mutableSetOf<String>()
        for (rules in filters.values) {
            merged.addAll(rules)
        }
        return merged.toList()
    }

    /**
     * Validate if rule format is valid
     */
    private fun isValidRule(rule: String): Boolean {
        // Simple validation: rule should contain domain format
        return rule.contains(".") || rule.startsWith("@@") || rule.startsWith("/")
    }

    /**
     * Save filters to local cache
     */
    suspend fun saveFiltersToCache(filters: Map<String, List<String>>) = withContext(Dispatchers.IO) {
        if (context == null) return@withContext

        for ((name, rules) in filters) {
            try {
                val fileName = when (name) {
                    "mobile" -> MOBILE_FILTER_CACHE
                    "chinese" -> CHINESE_FILTER_CACHE
                    "base" -> BASE_FILTER_CACHE
                    "spyware" -> SPYWARE_FILTER_CACHE
                    else -> "${name}_filter.txt"
                }

                context.openFileOutput(fileName, Context.MODE_PRIVATE).use { output ->
                    output.write(rules.joinToString("\n").toByteArray())
                }

                Log.i(TAG, "Saved $name filter to cache: ${rules.size} rules")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save $name filter to cache", e)
            }
        }
    }

    /**
     * Load filters from local cache
     */
    suspend fun loadFiltersFromCache(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (context == null) return@withContext emptyMap()

        val filters = mutableMapOf<String, List<String>>()
        val filterNames = listOf("mobile", "chinese", "base", "spyware")

        for (name in filterNames) {
            try {
                val fileName = when (name) {
                    "mobile" -> MOBILE_FILTER_CACHE
                    "chinese" -> CHINESE_FILTER_CACHE
                    "base" -> BASE_FILTER_CACHE
                    "spyware" -> SPYWARE_FILTER_CACHE
                    else -> "${name}_filter.txt"
                }

                val content = context.openFileInput(fileName).bufferedReader().readText()
                val rules = content.lines().filter { it.isNotEmpty() }
                filters[name] = rules

                Log.i(TAG, "Loaded $name filter from cache: ${rules.size} rules")
            } catch (e: Exception) {
                // Cache file doesn't exist, skip
            }
        }

        filters
    }

    /**
     * Get filter statistics
     */
    fun getFilterStats(filters: Map<String, List<String>>): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        stats["total"] = filters.values.sumOf { it.size }
        for ((name, rules) in filters) {
            stats[name] = rules.size
        }
        return stats
    }
}
