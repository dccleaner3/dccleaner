package com.dccleaner.app.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.dccleaner.app.model.ProxyJob
import com.dccleaner.app.proxy.SupabaseProxyCleanerBackend
import com.dccleaner.app.storage.AndroidProxyCleanerStorage
import com.dccleaner.app.ui.proxy.proxyJobFinishedMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * 초고속 삭제는 서버에서 돌아 앱을 꺼도 계속된다. 끝났다는 알림을 주려고 작업이 도는 동안
 * JobScheduler 최소 주기(약 15분)마다 서버 상태를 한 번 확인한다. 앱이 켜져 있으면 화면이 먼저 알아채고 [finished]를 부른다.
 */
object ProxyJobWatcher {
    private const val JOB_ID = 7301
    private const val PREFS = "proxy_job_watch"
    private const val KEY_JOB = "job_id"
    private const val KEY_ACCOUNT = "account_id"
    private const val PERIOD_MILLIS = 15 * 60 * 1000L

    fun watch(context: Context, jobId: String, accountId: String?) {
        val prefs = prefs(context)
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (prefs.getString(KEY_JOB, null) == jobId && scheduler.getPendingJob(JOB_ID) != null) return
        prefs.edit().putString(KEY_JOB, jobId).putString(KEY_ACCOUNT, accountId).apply()
        scheduler.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, ProxyJobCheckService::class.java))
                .setPeriodic(PERIOD_MILLIS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
        )
    }

    /** 감시하던 작업이 끝났으면 알림을 띄우고(앱이 화면에 없을 때만) 감시를 멈춘다. 다른 작업이면 무시한다. */
    fun finished(context: Context, job: ProxyJob, notify: Boolean) {
        if (prefs(context).getString(KEY_JOB, null) != job.id) return
        if (notify) DcCleanerNotifier(context).showCompletedNotification(proxyJobFinishedMessage(job))
        stop(context)
    }

    fun stop(context: Context) {
        prefs(context).edit().clear().apply()
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }

    internal fun watchedJob(context: Context): Pair<String, String?>? =
        prefs(context).getString(KEY_JOB, null)?.let { it to prefs(context).getString(KEY_ACCOUNT, null) }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class ProxyJobCheckService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        val (jobId, accountId) = ProxyJobWatcher.watchedJob(this) ?: run {
            ProxyJobWatcher.stop(this)
            return false
        }
        scope.launch {
            try {
                val backend = SupabaseProxyCleanerBackend(AndroidProxyCleanerStorage(applicationContext))
                val job = try {
                    // 서버 작업이 멈춰 정산이 밀린 작업은 잔고 조회가 서버에서 정산한다.
                    backend.latestJob(accountId)?.let {
                        if (it.needsSettlement) { backend.balance(); backend.latestJob(accountId) } else it
                    }
                } finally {
                    backend.close()
                }
                when {
                    job == null -> Unit
                    job.id != jobId -> ProxyJobWatcher.stop(applicationContext)
                    !job.isActive && !job.needsSettlement -> ProxyJobWatcher.finished(applicationContext, job, notify = true)
                }
            } catch (_: Exception) {
                // 네트워크·세션 문제는 다음 주기에 다시 확인한다.
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.coroutineContext.cancelChildren()
        return true
    }
}
