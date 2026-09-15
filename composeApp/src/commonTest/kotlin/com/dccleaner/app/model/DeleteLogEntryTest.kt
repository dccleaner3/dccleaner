package com.dccleaner.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteLogEntryTest {
    @Test
    fun guestbookRunCompletionRecognizesSuccessAndFinalFailure() {
        assertTrue("[12:34:56] ✅ 방명록 가동 기록 작성 완료".isGuestbookRunLogCompletion())
        assertTrue("[12:34:56] ⚠️ 방명록 가동 기록 작성 실패".isGuestbookRunLogCompletion())
    }

    @Test
    fun guestbookRunCompletionIgnoresFailureDetailLog() {
        assertFalse(
            "[12:34:56] ⚠️ 방명록 가동 기록 작성 실패: 네트워크 오류"
                .isGuestbookRunLogCompletion()
        )
    }

    @Test
    fun transientProgressStatusesReplaceTheSingleActiveLine() {
        val initial = listOf(
            "[12:00:00] 🚀 테스트 갤러리 글 삭제 시작",
            "[12:00:01] $DELETE_PROGRESS_LOG_MARKER🗑️ 3/10 (30%)"
        )

        val solving = initial.replaceDeleteProgressLog(
            "[12:00:02] $DELETE_PROGRESS_LOG_MARKER⚠️ 캡챠 감지됨 - 자동 해결 시도 (1/3)"
        )
        val retrying = solving.replaceDeleteProgressLog(
            "[12:00:03] $DELETE_PROGRESS_LOG_MARKER⚠️ 2captcha 실패 - 재시도 중... (1/3)"
        )
        val resumed = retrying.replaceDeleteProgressLog(
            "[12:00:04] $DELETE_PROGRESS_LOG_MARKER🗑️ 4/10 (40%)"
        )

        assertEquals(2, resumed.size)
        assertEquals(1, resumed.count(String::isDeleteProgressLog))
        assertTrue(resumed.none { "자동 해결 시도" in it || "재시도 중" in it })
    }

    @Test
    fun manualCaptchaStatusesAreReplacedAfterResume() {
        val waiting = listOf(
            "[12:00:00] $DELETE_PROGRESS_LOG_MARKER⚠️ 캡챠 감지됨 - 수동 해결 필요"
        )
        val resolving = waiting.replaceDeleteProgressLog(
            "[12:00:01] $DELETE_PROGRESS_LOG_MARKER✅ 캡챠 해결 완료 - 삭제 재개"
        )
        val resumed = resolving.replaceDeleteProgressLog(
            "[12:00:02] $DELETE_PROGRESS_LOG_MARKER🗑️ 5/10 (50%)"
        )

        assertEquals(1, resumed.size)
        assertTrue("수동 해결 필요" !in resumed.single())
        assertTrue("해결 완료" !in resumed.single())
    }

    @Test
    fun completedStatusReplacesAndFinalizesAnActiveStatus() {
        val running = listOf(
            "[12:00:00] $DELETE_PROGRESS_LOG_MARKER📝 방명록 가동 기록 작성 중"
        )
        val completed = running.replaceDeleteProgressLog(
            "[12:00:01] ✅ 방명록 가동 기록 작성 완료"
        )

        assertEquals(1, completed.size)
        assertFalse(completed.single().isDeleteProgressLog())
        assertTrue("작성 중" !in completed.single())
    }
}
