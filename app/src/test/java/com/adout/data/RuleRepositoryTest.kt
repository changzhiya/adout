package com.adout.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RuleRepositoryTest {

    private lateinit var repository: RuleRepository

    @Before
    fun setUp() {
        repository = RuleRepository()
    }

    @Test
    fun `get built-in rules`() {
        val rules = repository.getBuiltInRules()

        assertTrue(rules.isNotEmpty())
        assertTrue(rules.any { it.contains("ads") })
    }

    @Test
    fun `merge rules from multiple sources`() {
        val builtInRules = repository.getBuiltInRules()
        val customRules = listOf(
            "||custom-ads.example.com^",
            "||custom-tracking.example.com^"
        )

        val allRules = repository.mergeRules(builtInRules, customRules)

        assertTrue(allRules.size > builtInRules.size)
        assertTrue(allRules.contains("||custom-ads.example.com^"))
    }

    @Test
    fun `filter invalid rules`() {
        val rules = listOf(
            "||valid-domain.com^",
            "invalid rule format",
            "# comment",
            "",
            "||another-valid.com^"
        )

        val validRules = repository.filterValidRules(rules)

        assertEquals(2, validRules.size)
    }

    @Test
    fun `get rules summary`() {
        val rules = listOf(
            "||ads.example.com^",
            "||tracking.example.com^",
            "@@||important.example.com^"
        )

        val summary = repository.getRulesSummary(rules)

        assertEquals(3, summary["total"])
        assertEquals(2, summary["blacklist"])
        assertEquals(1, summary["whitelist"])
    }

    @Test
    fun `built-in rules contain major ad domains`() {
        val rules = repository.getBuiltInRules()

        // Check for major ad network domains
        assertTrue(rules.any { it.contains("xiaomi") })
        assertTrue(rules.any { it.contains("qq.com") })
        assertTrue(rules.any { it.contains("baidu") })
        assertTrue(rules.any { it.contains("toutiao") })
    }

    @Test
    fun `create default rules file`() {
        val content = repository.createDefaultRulesFile()

        assertTrue(content.contains("||ad.xiaomi.com^"))
        assertTrue(content.contains("||e.qq.com^"))
    }
}
