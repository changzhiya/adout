package com.adout.rule

import org.junit.Assert.*
import org.junit.Test

class RuleParserTest {

    @Test
    fun `parse blacklist rule`() {
        val ruleText = "||ads.example.com^"

        val rule = RuleParser.parse(ruleText)

        assertNotNull(rule)
        assertEquals(RuleParser.RuleType.BLACKLIST, rule?.type)
        assertEquals("ads.example.com", rule?.pattern)
    }

    @Test
    fun `parse whitelist rule`() {
        val ruleText = "@@||important.example.com^"

        val rule = RuleParser.parse(ruleText)

        assertNotNull(rule)
        assertEquals(RuleParser.RuleType.WHITELIST, rule?.type)
        assertEquals("important.example.com", rule?.pattern)
    }

    @Test
    fun `parse wildcard rule`() {
        val ruleText = "||*.adserver.com^"

        val rule = RuleParser.parse(ruleText)

        assertNotNull(rule)
        assertEquals(RuleParser.RuleType.BLACKLIST, rule?.type)
        assertEquals("*.adserver.com", rule?.pattern)
    }

    @Test
    fun `skip comments`() {
        val ruleText = "# This is a comment"

        val rule = RuleParser.parse(ruleText)

        assertNull(rule)
    }

    @Test
    fun `skip empty lines`() {
        val ruleText = ""

        val rule = RuleParser.parse(ruleText)

        assertNull(rule)
    }

    @Test
    fun `parse multiple rules`() {
        val rulesText = """
            ||ads.example.com^
            ||tracking.example.com^
            @@||important.example.com^
            # Comment
        """.trimIndent()

        val rules = RuleParser.parseMultiple(rulesText)

        assertEquals(3, rules.size)
        assertEquals(2, rules.count { it.type == RuleParser.RuleType.BLACKLIST })
        assertEquals(1, rules.count { it.type == RuleParser.RuleType.WHITELIST })
    }

    @Test
    fun `invalid rule returns null`() {
        val ruleText = "invalid rule format"

        val rule = RuleParser.parse(ruleText)

        assertNull(rule)
    }

    @Test
    fun `parse rule without caret`() {
        val ruleText = "||ads.example.com"

        val rule = RuleParser.parse(ruleText)

        assertNotNull(rule)
        assertEquals(RuleParser.RuleType.BLACKLIST, rule?.type)
        assertEquals("ads.example.com", rule?.pattern)
    }
}
