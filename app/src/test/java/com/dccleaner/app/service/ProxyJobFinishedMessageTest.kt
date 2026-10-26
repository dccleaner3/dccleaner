package com.dccleaner.app.service

import com.dccleaner.app.model.ProxyJob
import com.dccleaner.app.ui.proxy.proxyJobFinishedMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyJobFinishedMessageTest {
    private fun job(status: String, deleted: Int) = ProxyJob(
        id = "job", status = status, deleteType = "posting", reservedCount = 20_000, consumedCount = deleted.toLong(),
        deletedCount = deleted, skippedCount = 0, progress = 1f, galleriesDone = 1, galleriesTotal = 1,
        currentGallery = "", speedPerHour = 0, recentSpeedPerHour = 0, etaSeconds = 0, statusMessage = "",
        logTail = emptyList(), cancelRequested = false, error = null
    )

    @Test fun messageMatchesHowTheJobEnded() {
        assertEquals("초고속 삭제 완료 · 12,345개 삭제했어요", proxyJobFinishedMessage(job("completed", 12_345)))
        assertEquals("초고속 삭제를 중단했어요 · 10개 삭제했어요", proxyJobFinishedMessage(job("cancelled", 10)))
        assertEquals("초고속 삭제가 멈췄어요 · 0개 삭제했어요. 앱에서 확인해 주세요.", proxyJobFinishedMessage(job("failed", 0)))
    }
}
