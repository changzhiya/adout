package com.adout.rule

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RuleEngineTest {

    private lateinit var ruleEngine: RuleEngine

    @Before
    fun setUp() {
        ruleEngine = RuleEngine()
    }

    @Test
    fun `block domain in blacklist`() {
        ruleEngine.addRule("||ads.example.com^")

        val result = ruleEngine.shouldBlock("ads.example.com")

        assertTrue(result)
    }

    @Test
    fun `allow domain not in blacklist`() {
        ruleEngine.addRule("||ads.example.com^")

        val result = ruleEngine.shouldBlock("normal.example.com")

        assertFalse(result)
    }

    @Test
    fun `whitelist overrides blacklist`() {
        ruleEngine.addRule("||example.com^")
        ruleEngine.addRule("@@||important.example.com^")

        val result = ruleEngine.shouldBlock("important.example.com")

        assertFalse(result)
    }

    @Test
    fun `wildcard matching`() {
        ruleEngine.addRule("||*.adserver.com^")

        val result = ruleEngine.shouldBlock("cdn.adserver.com")

        assertTrue(result)
    }

    @Test
    fun `case insensitive matching`() {
        ruleEngine.addRule("||Ads.Example.Com^")

        val result = ruleEngine.shouldBlock("ads.example.com")

        assertTrue(result)
    }

    @Test
    fun `load multiple rules`() {
        val rules = listOf(
            "||ads.example.com^",
            "||tracking.example.com^",
            "@@||important.example.com^"
        )

        ruleEngine.loadRules(rules)

        assertTrue(ruleEngine.shouldBlock("ads.example.com"))
        assertTrue(ruleEngine.shouldBlock("tracking.example.com"))
        assertFalse(ruleEngine.shouldBlock("important.example.com"))
        assertFalse(ruleEngine.shouldBlock("normal.example.com"))
    }

    @Test
    fun `clear all rules`() {
        ruleEngine.addRule("||ads.example.com^")
        ruleEngine.clearRules()

        assertFalse(ruleEngine.shouldBlock("ads.example.com"))
    }

    @Test
    fun `get rule count`() {
        ruleEngine.addRule("||ads.example.com^")
        ruleEngine.addRule("||tracking.example.com^")

        val count = ruleEngine.getRuleCount()

        assertEquals(2, count)
    }

    @Test
    fun `hot reload rules`() {
        ruleEngine.addRule("||old-rule.com^")
        assertTrue(ruleEngine.shouldBlock("old-rule.com"))

        val newRules = listOf("||new-rule.com^")
        ruleEngine.reloadRules(newRules)

        assertFalse(ruleEngine.shouldBlock("old-rule.com"))
        assertTrue(ruleEngine.shouldBlock("new-rule.com"))
    }

    @Test
    fun `get blacklist and whitelist counts`() {
        ruleEngine.addRule("||ads.example.com^")
        ruleEngine.addRule("||tracking.example.com^")
        ruleEngine.addRule("@@||important.example.com^")

        assertEquals(2, ruleEngine.getBlacklistCount())
        assertEquals(1, ruleEngine.getWhitelistCount())
    }
}
