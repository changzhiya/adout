package com.adout.rule

class RuleEngine {

    private val blacklistPatterns = mutableListOf<String>()
    private val whitelistPatterns = mutableListOf<String>()
    private val blacklistMatcher = AhoCorasickMatcher()
    private val whitelistMatcher = AhoCorasickMatcher()
    private var isDirty = true

    fun addRule(ruleText: String) {
        val rule = RuleParser.parse(ruleText) ?: return

        when (rule.type) {
            RuleParser.RuleType.BLACKLIST -> blacklistPatterns.add(rule.pattern)
            RuleParser.RuleType.WHITELIST -> whitelistPatterns.add(rule.pattern)
        }

        isDirty = true
    }

    fun loadRules(rules: List<String>) {
        for (ruleText in rules) {
            addRule(ruleText)
        }
    }

    fun loadRulesFromText(rulesText: String) {
        val rules = RuleParser.parseMultiple(rulesText)
        for (rule in rules) {
            when (rule.type) {
                RuleParser.RuleType.BLACKLIST -> blacklistPatterns.add(rule.pattern)
                RuleParser.RuleType.WHITELIST -> whitelistPatterns.add(rule.pattern)
            }
        }
        isDirty = true
    }

    fun shouldBlock(domain: String): Boolean {
        if (isDirty) {
            rebuildMatchers()
        }

        val normalizedDomain = domain.lowercase().trim()

        // Whitelist takes priority
        if (matchesDomainBoundary(whitelistMatcher, normalizedDomain)) {
            return false
        }

        // Check blacklist
        return matchesDomainBoundary(blacklistMatcher, normalizedDomain)
    }

    /**
     * Check if any pattern in the matcher matches at a domain boundary.
     * This prevents false positives like "du.com" matching "baidu.com".
     *
     * A match at the end of search position `i` in domain `d` is valid if:
     * - The match starts at position 0 (exact domain match), OR
     * - The character before the match start is a '.' (subdomain boundary)
     */
    private fun matchesDomainBoundary(matcher: AhoCorasickMatcher, domain: String): Boolean {
        val results = matcher.search(domain)
        for (result in results) {
            val matchEnd = result.position
            val matchStart = matchEnd - result.pattern.length + 1

            if (matchStart == 0) {
                // Pattern matches from the start of domain
                // Check that the rest of the domain (if any) starts with '.'
                if (domain.length == result.pattern.length || domain[result.pattern.length] == '.') {
                    return true
                }
            } else if (domain[matchStart - 1] == '.') {
                // Pattern starts after a dot - valid subdomain boundary
                return true
            }
        }
        return false
    }

    fun clearRules() {
        blacklistPatterns.clear()
        whitelistPatterns.clear()
        blacklistMatcher.clear()
        whitelistMatcher.clear()
        isDirty = true
    }

    fun getRuleCount(): Int {
        return blacklistPatterns.size + whitelistPatterns.size
    }

    fun getBlacklistCount(): Int {
        return blacklistPatterns.size
    }

    fun getWhitelistCount(): Int {
        return whitelistPatterns.size
    }

    private fun rebuildMatchers() {
        blacklistMatcher.clear()
        whitelistMatcher.clear()

        for (pattern in blacklistPatterns) {
            blacklistMatcher.addPattern(pattern)
        }

        for (pattern in whitelistPatterns) {
            whitelistMatcher.addPattern(pattern)
        }

        blacklistMatcher.build()
        whitelistMatcher.build()

        isDirty = false
    }

    fun isBlocked(domain: String): Boolean {
        return shouldBlock(domain)
    }

    fun getMatchingRule(domain: String): String? {
        if (isDirty) {
            rebuildMatchers()
        }

        val normalizedDomain = domain.lowercase().trim()

        // Check whitelist first
        val whitelistResults = whitelistMatcher.search(normalizedDomain)
        for (result in whitelistResults) {
            val matchEnd = result.position
            val matchStart = matchEnd - result.pattern.length + 1
            if (matchStart == 0 || normalizedDomain[matchStart - 1] == '.') {
                return "WHITELIST: ${result.pattern}"
            }
        }

        // Then check blacklist
        val blacklistResults = blacklistMatcher.search(normalizedDomain)
        for (result in blacklistResults) {
            val matchEnd = result.position
            val matchStart = matchEnd - result.pattern.length + 1
            if (matchStart == 0 || normalizedDomain[matchStart - 1] == '.') {
                return "BLACKLIST: ${result.pattern}"
            }
        }

        return null
    }

    fun reloadRules(newRules: List<String>) {
        clearRules()
        loadRules(newRules)
    }

    fun reloadRulesFromText(rulesText: String) {
        clearRules()
        loadRulesFromText(rulesText)
    }
}
