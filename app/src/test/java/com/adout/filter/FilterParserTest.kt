package com.adout.filter

import org.junit.Assert.*
import org.junit.Test

class FilterParserTest {

    @Test
    fun `parse blacklist rule`() {
        val rule = FilterParser.parseRule("||ads.example.com^")

        assertNotNull(rule)
        assertEquals(FilterParser.RuleType.BLACKLIST, rule?.type)
        assertEquals("ads.example.com", rule?.pattern)
    }

    @Test
    fun `parse whitelist rule`() {
        val rule = FilterParser.parseRule("@@||important.example.com^")

        assertNotNull(rule)
        assertEquals(FilterParser.RuleType.WHITELIST, rule?.type)
        assertEquals("important.example.com", rule?.pattern)
    }

    @Test
    fun `skip comments`() {
        assertNull(FilterParser.parseRule("! This is a comment"))
        assertNull(FilterParser.parseRule("# This is a comment"))
        assertNull(FilterParser.parseRule(""))
    }

    @Test
    fun `categorize rules`() {
        val rules = listOf(
            "||ads.example.com^",
            "||tracking.example.com^",
            "@@||important.example.com^"
        )

        val categorized = FilterParser.categorizeRules(rules)

        assertEquals(2, categorized.blacklist.size)
        assertEquals(1, categorized.whitelist.size)
    }

    @Test
    fun `parse multiple rules`() {
        val rules = listOf(
            "||ads.example.com^",
            "! comment",
            "@@||whitelist.com^",
            "",
            "||tracking.example.com^"
        )

        val parsed = FilterParser.parseRules(rules)

        assertEquals(3, parsed.size)
    }

    @Test
    fun `parse rule without caret`() {
        val rule = FilterParser.parseRule("||ads.example.com")

        assertNotNull(rule)
        assertEquals(FilterParser.RuleType.BLACKLIST, rule?.type)
        assertEquals("ads.example.com", rule?.pattern)
    }
}
