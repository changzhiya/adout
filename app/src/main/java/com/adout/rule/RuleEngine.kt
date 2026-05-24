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
        if (whitelistMatcher.match(normalizedDomain) != null) {
            return false
        }

        // Check blacklist
        return blacklistMatcher.match(normalizedDomain) != null
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
        val whitelistMatch = whitelistMatcher.match(normalizedDomain)
        if (whitelistMatch != null) {
            return "WHITELIST: $whitelistMatch"
        }

        // Then check blacklist
        val blacklistMatch = blacklistMatcher.match(normalizedDomain)
        return if (blacklistMatch != null) {
            "BLACKLIST: $blacklistMatch"
        } else {
            null
        }
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
