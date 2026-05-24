package com.adout.filter

/**
 * FilterParser - Parses AdGuardFilters rule format
 *
 * Supports AdGuard rule syntax:
 * - Basic rules: ||domain^
 * - Whitelist rules: @@||domain^
 * - Comments: ! or # prefix
 * - Wildcards: *.domain.com
 */
object FilterParser {

    /**
     * Parse rule list, return structured rule objects
     */
    fun parseRules(rules: List<String>): List<ParsedRule> {
        return rules.mapNotNull { parseRule(it) }
    }

    /**
     * Parse single rule
     */
    fun parseRule(ruleText: String): ParsedRule? {
        val trimmed = ruleText.trim()

        // Skip empty lines and comments
        if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("#")) {
            return null
        }

        // Whitelist rules
        if (trimmed.startsWith("@@")) {
            val pattern = extractPattern(trimmed.substring(2))
            if (pattern != null) {
                return ParsedRule(
                    type = RuleType.WHITELIST,
                    pattern = pattern,
                    originalText = trimmed
                )
            }
        }

        // Blacklist rules
        val pattern = extractPattern(trimmed)
        if (pattern != null) {
            return ParsedRule(
                type = RuleType.BLACKLIST,
                pattern = pattern,
                originalText = trimmed
            )
        }

        return null
    }

    /**
     * Extract domain pattern from rule text
     */
    private fun extractPattern(ruleText: String): String? {
        val trimmed = ruleText.trim()

        // Handle ||domain^ format
        if (trimmed.startsWith("||") && trimmed.endsWith("^")) {
            return trimmed.substring(2, trimmed.length - 1)
        }

        // Handle ||domain format
        if (trimmed.startsWith("||")) {
            return trimmed.substring(2)
        }

        // Handle domain^ format
        if (trimmed.endsWith("^") && !trimmed.contains("*")) {
            return trimmed.substring(0, trimmed.length - 1)
        }

        // Handle wildcard format
        if (trimmed.contains("*") && !trimmed.contains(" ") && !trimmed.contains("/")) {
            return trimmed
        }

        // Handle simple domain format
        if (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.contains("/")) {
            return trimmed
        }

        return null
    }

    /**
     * Categorize rules by type
     */
    fun categorizeRules(rules: List<String>): CategorizedRules {
        val blacklist = mutableListOf<String>()
        val whitelist = mutableListOf<String>()

        for (rule in rules) {
            val parsed = parseRule(rule)
            when (parsed?.type) {
                RuleType.BLACKLIST -> blacklist.add(parsed.pattern)
                RuleType.WHITELIST -> whitelist.add(parsed.pattern)
                null -> { /* Skip invalid rules */ }
            }
        }

        return CategorizedRules(blacklist, whitelist)
    }

    data class ParsedRule(
        val type: RuleType,
        val pattern: String,
        val originalText: String
    )

    enum class RuleType {
        BLACKLIST,
        WHITELIST
    }

    data class CategorizedRules(
        val blacklist: List<String>,
        val whitelist: List<String>
    )
}
