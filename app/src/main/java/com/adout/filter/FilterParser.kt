package com.adout.filter

import com.adout.rule.RuleParser

/**
 * FilterParser - Parses AdGuardFilters rule format.
 *
 * Delegates rule parsing to [RuleParser] to avoid duplication.
 * Provides additional categorization utility.
 */
object FilterParser {

    /**
     * Parse rule list, return structured rule objects.
     * Delegates to [RuleParser.parse].
     */
    fun parseRules(rules: List<String>): List<ParsedRule> {
        return rules.mapNotNull { parseRule(it) }
    }

    /**
     * Parse single rule via [RuleParser].
     */
    fun parseRule(ruleText: String): ParsedRule? {
        val parsed = RuleParser.parse(ruleText) ?: return null
        val type = when (parsed.type) {
            RuleParser.RuleType.BLACKLIST -> RuleType.BLACKLIST
            RuleParser.RuleType.WHITELIST -> RuleType.WHITELIST
        }
        return ParsedRule(type, parsed.pattern, parsed.originalText)
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
