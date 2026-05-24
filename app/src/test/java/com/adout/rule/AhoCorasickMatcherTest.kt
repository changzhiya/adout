package com.adout.rule

import org.junit.Assert.*
import org.junit.Test

class AhoCorasickMatcherTest {

    @Test
    fun `match single pattern`() {
        val matcher = AhoCorasickMatcher()
        matcher.addPattern("ads.example.com")

        val result = matcher.search("ads.example.com")

        assertTrue(result.isNotEmpty())
        assertEquals("ads.example.com", result[0].pattern)
    }

    @Test
    fun `match multiple patterns`() {
        val matcher = AhoCorasickMatcher()
        matcher.addPattern("ads.example.com")
        matcher.addPattern("tracking.example.com")
        matcher.addPattern("ad.doubleclick.net")

        val result = matcher.search("tracking.example.com")

        assertTrue(result.isNotEmpty())
        assertEquals("tracking.example.com", result[0].pattern)
    }

    @Test
    fun `no match returns empty`() {
        val matcher = AhoCorasickMatcher()
        matcher.addPattern("ads.example.com")

        val result = matcher.search("normal.example.com")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `match with wildcard pattern`() {
        val matcher = AhoCorasickMatcher()
        matcher.addPattern("*.adserver.com")

        val result = matcher.search("cdn.adserver.com")

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `case insensitive matching`() {
        val matcher = AhoCorasickMatcher()
        matcher.addPattern("Ads.Example.Com")

        val result = matcher.search("ads.example.com")

        assertTrue(result.isNotEmpty())
    }
}
