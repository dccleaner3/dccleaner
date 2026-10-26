package com.dccleaner.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogVolumeFilterTest {
    @Test fun routineLinesAreSummarizedAndImportantLinesKept() {
        var now = 0L
        val filter = LogVolumeFilter(intervalMillis = 1_000, now = { now })
        assertTrue(filter.filter("Cleaner", "글 삭제 시작 - postNo: 1, type: posting").isEmpty())
        assertTrue(filter.filter("Cleaner", "삭제 응답 파싱 성공").isEmpty())
        assertTrue(filter.filter("Cleaner", "삭제 성공 - postNo: 1 ").isEmpty())
        assertTrue(filter.filter("Cleaner", "삭제 성공 - postNo: 2 ").isEmpty())
        assertTrue(filter.filter("Service", "글 삭제 시도 - postNo: 2").isEmpty())
        // 실패·캡챠·제외는 그대로 남는다.
        assertEquals(1, filter.filter("Cleaner", "캡챠 필요 감지 - postNo: 3").size)
        assertEquals(1, filter.filter("Cleaner", "삭제 실패 응답 - result: fail, msg: x").size)
        assertEquals(1, filter.filter("Cleaner", "삭제 확인이 필요한 항목 제외 - postNo: 4, msg: x").size)
        // 간격이 지나면 다음 줄 앞에 요약이 나온다.
        now = 1_000
        val out = filter.filter("Delete", "작업 종료")
        assertEquals("작업 종료", out.last().second)
        assertTrue(out.contains("Cleaner" to "요약 삭제 성공: 2건"))
        assertTrue(out.contains("Cleaner" to "요약 삭제 시작: 1건"))
        assertTrue(out.contains("Service" to "요약 삭제 시도: 1건"))
        assertTrue(filter.drain().isEmpty())
    }
}
