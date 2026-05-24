package com.adout.filter

import org.junit.Assert.*
import org.junit.Test

class FilterDownloaderTest {

    @Test
    fun `get filter URLs`() {
        val downloader = FilterDownloader()

        val urls = downloader.getFilterUrls()

        assertTrue(urls.isNotEmpty())
        assertTrue(urls.containsKey("mobile"))
        assertTrue(urls.containsKey("chinese"))
    }

    @Test
    fun `parse filter list content`() {
        val downloader = FilterDownloader()

        val content = """
            # Title: AdGuard Mobile Ads Filter
            # Homepage: https://github.com/AdguardTeam/AdGuardFilters
            ||ad.xiaomi.com^
            ||api.ad.xiaomi.com^
            ||e.qq.com^
            # Comment
            @@||important.example.com^
        """.trimIndent()

        val rules = downloader.parseFilterContent(content)

        assertEquals(4, rules.size) // 3 blacklist + 1 whitelist
        assertTrue(rules.contains("||ad.xiaomi.com^"))
        assertTrue(rules.contains("@@||important.example.com^"))
    }

    @Test
    fun `merge multiple filter lists`() {
        val downloader = FilterDownloader()

        val mobileRules = listOf("||ad.xiaomi.com^", "||e.qq.com^")
        val chineseRules = listOf("||ad.example.cn^", "||tracking.example.cn^")

        val merged = downloader.mergeFilters(mapOf(
            "mobile" to mobileRules,
            "chinese" to chineseRules
        ))

        assertEquals(4, merged.size)
    }

    @Test
    fun `get filter stats`() {
        val downloader = FilterDownloader()

        val filters = mapOf(
            "mobile" to listOf("||ad1.com^", "||ad2.com^"),
            "chinese" to listOf("||ad3.com^")
        )

        val stats = downloader.getFilterStats(filters)

        assertEquals(3, stats["total"])
        assertEquals(2, stats["mobile"])
        assertEquals(1, stats["chinese"])
    }

    @Test
    fun `parse filter content skips comments`() {
        val downloader = FilterDownloader()

        val content = """
            ! This is an AdGuard comment
            # This is a regular comment
            [Adblock Plus]
            ||ads.example.com^
        """.trimIndent()

        val rules = downloader.parseFilterContent(content)

        assertEquals(1, rules.size)
        assertEquals("||ads.example.com^", rules[0])
    }
}
