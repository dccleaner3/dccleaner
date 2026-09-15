package com.dccleaner.app.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.jsoup.Jsoup

class GallogTotalCountTest {
    @Test
    fun parsesSelectedGalleryCount() {
        val doc = Jsoup.parse(
            """
            <div class="option_sort gallog">
              <div class="select_box"></div>
              <span class="num">(62)</span>
              <span class="greybox">비공개</span>
            </div>
            """.trimIndent()
        )

        assertEquals(62, gallogTotalCount(doc))
    }

    @Test
    fun parsesCommaSeparatedCount() {
        val doc = Jsoup.parse(
            "<div class='option_sort gallog'><span class='num'>(1,234)</span></div>"
        )

        assertEquals(1234, gallogTotalCount(doc))
    }

    @Test
    fun returnsNullWhenCountIsMissing() {
        assertNull(gallogTotalCount(Jsoup.parse("<div class='option_sort gallog'></div>")))
    }

}
