package com.dccleaner.app.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteBannerFetcherTest {
    @Test
    fun parsesShortBannerText() {
        val banner = RemoteBannerFetcher.parse(
            """{"enabled":true,"id":"short","text":"짧은 광고"}"""
        )

        assertEquals("short", banner?.id)
        assertEquals("짧은 광고", banner?.text)
    }

    @Test
    fun parsesLongMultilineBannerTextWithoutTruncation() {
        val expected = "첫 번째로 표시할 긴 광고 내용입니다.\n\n두 번째 문단도 모두 표시합니다."
        val banner = RemoteBannerFetcher.parse(
            """{"enabled":true,"id":"long","text":"첫 번째로 표시할 긴 광고 내용입니다.\n\n두 번째 문단도 모두 표시합니다."}"""
        )

        assertEquals(expected, banner?.text)
    }

    @Test
    fun ignoresDisabledOrEmptyBanner() {
        assertNull(RemoteBannerFetcher.parse("""{"enabled":false,"text":"숨김"}"""))
        assertNull(RemoteBannerFetcher.parse("""{"enabled":true,"text":""}"""))
    }
}
