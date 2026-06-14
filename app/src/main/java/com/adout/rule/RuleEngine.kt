package com.adout.rule

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class RuleEngine {

    private val blacklistPatterns = mutableListOf<String>()
    private val whitelistPatterns = mutableListOf<String>()
    private val blacklistMatcher = AhoCorasickMatcher()
    private val whitelistMatcher = AhoCorasickMatcher()

    @Volatile
    private var isDirty = true

    // ReadWriteLock: multiple readers allowed, single writer exclusive
    private val lock = ReentrantReadWriteLock()

    fun addRule(ruleText: String) {
        val rule = RuleParser.parse(ruleText) ?: return

        lock.write {
            when (rule.type) {
                RuleParser.RuleType.BLACKLIST -> blacklistPatterns.add(rule.pattern)
                RuleParser.RuleType.WHITELIST -> whitelistPatterns.add(rule.pattern)
            }
            isDirty = true
        }
    }

    fun loadRules(rules: List<String>) {
        lock.write {
            for (ruleText in rules) {
                val rule = RuleParser.parse(ruleText) ?: continue
                when (rule.type) {
                    RuleParser.RuleType.BLACKLIST -> blacklistPatterns.add(rule.pattern)
                    RuleParser.RuleType.WHITELIST -> whitelistPatterns.add(rule.pattern)
                }
            }
            isDirty = true
        }
    }

    fun loadRulesFromText(rulesText: String) {
        lock.write {
            val rules = RuleParser.parseMultiple(rulesText)
            for (rule in rules) {
                when (rule.type) {
                    RuleParser.RuleType.BLACKLIST -> blacklistPatterns.add(rule.pattern)
                    RuleParser.RuleType.WHITELIST -> whitelistPatterns.add(rule.pattern)
                }
            }
            isDirty = true
        }
    }

    fun shouldBlock(domain: String): Boolean {
        // Fast path: read lock for normal query
        lock.read {
            if (!isDirty) {
                val normalizedDomain = domain.lowercase().trim()

                // Whitelist takes priority
                if (matchesDomainBoundary(whitelistMatcher, normalizedDomain)) {
                    return false
                }

                // Check blacklist
                return matchesDomainBoundary(blacklistMatcher, normalizedDomain)
            }
        }

        // Slow path: need to rebuild - use write lock
        lock.write {
            // Double-check after acquiring write lock
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

            // Patterns starting with '.' are domain boundary markers themselves
            // (e.g., ".adserver.com" from wildcard rules), any AhoCorasick match is valid
            if (result.pattern.startsWith(".")) {
                return true
            }

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
        lock.write {
            blacklistPatterns.clear()
            whitelistPatterns.clear()
            blacklistMatcher.clear()
            whitelistMatcher.clear()
            isDirty = true
        }
    }

    fun getRuleCount(): Int {
        return lock.read { blacklistPatterns.size + whitelistPatterns.size }
    }

    fun getBlacklistCount(): Int {
        return lock.read { blacklistPatterns.size }
    }

    fun getWhitelistCount(): Int {
        return lock.read { whitelistPatterns.size }
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
        // Fast path: read lock for normal query
        lock.read {
            if (!isDirty) {
                return getMatchingRuleInternal(domain)
            }
        }

        // Slow path: need to rebuild - use write lock
        lock.write {
            if (isDirty) {
                rebuildMatchers()
            }
            return getMatchingRuleInternal(domain)
        }
    }

    private fun getMatchingRuleInternal(domain: String): String? {
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
        lock.write {
            clearRulesInternal()
            loadRulesInternal(newRules)
        }
    }

    fun reloadRulesFromText(rulesText: String) {
        lock.write {
            clearRulesInternal()
            loadRulesFromTextInternal(rulesText)
        }
    }

    private fun clearRulesInternal() {
        blacklistPatterns.clear()
        whitelistPatterns.clear()
        blacklistMatcher.clear()
        whitelistMatcher.clear()
        isDirty = true
    }

    private fun loadRulesInternal(rules: List<String>) {
        for (ruleText in rules) {
            val rule = RuleParser.parse(ruleText) ?: continue
            when (rule.type) {
                RuleParser.RuleType.BLACKLIST -> blacklistPatterns.add(rule.pattern)
                RuleParser.RuleType.WHITELIST -> whitelistPatterns.add(rule.pattern)
            }
        }
        isDirty = true
    }

    private fun loadRulesFromTextInternal(rulesText: String) {
        val rules = RuleParser.parseMultiple(rulesText)
        for (rule in rules) {
            when (rule.type) {
                RuleParser.RuleType.BLACKLIST -> blacklistPatterns.add(rule.pattern)
                RuleParser.RuleType.WHITELIST -> whitelistPatterns.add(rule.pattern)
            }
        }
        isDirty = true
    }
}
