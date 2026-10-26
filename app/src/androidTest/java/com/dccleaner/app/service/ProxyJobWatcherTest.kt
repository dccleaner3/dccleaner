package com.dccleaner.app.service

import android.app.job.JobScheduler
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dccleaner.app.model.ProxyJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProxyJobWatcherTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scheduler = context.getSystemService(JobScheduler::class.java)

    private fun job(id: String, status: String) = ProxyJob(
        id = id, status = status, deleteType = "posting", reservedCount = 1_000, consumedCount = null,
        deletedCount = 3, skippedCount = 0, progress = 0f, galleriesDone = 0, galleriesTotal = 1,
        currentGallery = "", speedPerHour = 0, recentSpeedPerHour = 0, etaSeconds = 0, statusMessage = "",
        logTail = emptyList(), cancelRequested = false, error = null
    )

    @After fun tearDown() = ProxyJobWatcher.stop(context)

    @Test fun watchSchedulesPeriodicCheckUntilThatJobFinishes() {
        ProxyJobWatcher.watch(context, "job-a", "tester")
        val pending = scheduler.allPendingJobs.singleOrNull { it.service.className == ProxyJobCheckService::class.java.name }
        assertNotNull(pending)
        assertEquals(true, pending!!.isPeriodic)
        assertEquals("job-a" to "tester", ProxyJobWatcher.watchedJob(context))

        // 다른 작업의 종료는 감시를 끊지 않는다.
        ProxyJobWatcher.finished(context, job("job-b", "completed"), notify = false)
        assertNotNull(ProxyJobWatcher.watchedJob(context))

        ProxyJobWatcher.finished(context, job("job-a", "completed"), notify = false)
        assertNull(ProxyJobWatcher.watchedJob(context))
        assertNull(scheduler.allPendingJobs.firstOrNull { it.service.className == ProxyJobCheckService::class.java.name })
    }
}
