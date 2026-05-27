package com.adout.rule

object RuleParser {

    enum class RuleType {
        BLACKLIST,
        WHITELIST
    }

    data class Rule(
        val type: RuleType,
        val pattern: String,
        val originalText: String
    )

    fun parse(ruleText: String): Rule? {
        val trimmed = ruleText.trim()

        // Skip empty lines and comments
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return null
        }

        // Whitelist rules
        if (trimmed.startsWith("@@")) {
            val pattern = extractPattern(trimmed.substring(2))
            if (pattern != null) {
                return Rule(
                    type = RuleType.WHITELIST,
                    pattern = pattern,
                    originalText = trimmed
                )
            }
        }

        // Blacklist rules
        val pattern = extractPattern(trimmed)
        if (pattern != null) {
            return Rule(
                type = RuleType.BLACKLIST,
                pattern = pattern,
                originalText = trimmed
            )
        }

        return null
    }

    fun parseMultiple(rulesText: String): List<Rule> {
        return rulesText.lines()
            .mapNotNull { parse(it) }
    }

    fun parseFromFile(content: String): List<Rule> {
        return parseMultiple(content)
    }

    private fun extractPattern(ruleText: String): String? {
        val trimmed = ruleText.trim()

        // Handle ||domain^ format (also handles ||*.domain^)
        if (trimmed.startsWith("||") && trimmed.endsWith("^")) {
            val domain = trimmed.substring(2, trimmed.length - 1)
            return if (domain.startsWith("*.")) {
                domain.substring(1) // *.domain -> .domain (dot is a boundary marker)
            } else {
                domain
            }
        }

        // Handle ||domain format
        if (trimmed.startsWith("||")) {
            return trimmed.substring(2)
        }

        // Handle domain^ format
        if (trimmed.endsWith("^") && !trimmed.contains("*")) {
            return trimmed.substring(0, trimmed.length - 1)
        }

        // Handle wildcard format (*.domain -> .domain)
        if (trimmed.contains("*") && !trimmed.contains(" ") && !trimmed.contains("/")) {
            return trimmed.replace("*", "")
        }

        // Handle simple domain format
        if (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.contains("/")) {
            return trimmed
        }

        return null
    }

    fun isValidRule(ruleText: String): Boolean {
        return parse(ruleText) != null
    }

    fun normalizePattern(pattern: String): String {
        var normalized = pattern.lowercase().trim()

        // Handle wildcard prefix - strip *. so ".adserver.com" matches both
        // exact "adserver.com" and subdomains like "cdn.adserver.com" via AhoCorasick
        if (normalized.startsWith("*.")) {
            normalized = normalized.substring(1) // keep the leading dot
        }

        return normalized
    }
}
