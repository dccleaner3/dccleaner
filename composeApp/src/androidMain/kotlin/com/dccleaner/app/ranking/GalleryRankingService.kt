package com.dccleaner.app.ranking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 갤창랭킹을 만드는 동안만 앱 프로세스를 붙잡는다. 작업은 [GalleryRankingRunner]가 하고,
 * 이 서비스는 진행 알림을 보여 주다가 끝나면 결과를 알리고 스스로 멈춘다.
 */
class GalleryRankingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "갤창랭킹", NotificationManager.IMPORTANCE_LOW)
            )
        }
        lastStartId = startId
        // 안드로이드 15부터 dataSync는 하루 시간 한도가 있다. 다 쓰면 시작이 막히는데, 그래도 앱이 켜져 있는 동안은 계속 만든다.
        val started = runCatching {
            ServiceCompat.startForeground(
                this, PROGRESS_ID, notification("갤창랭킹 만드는 중", "갤러리를 확인하고 있어요", ongoing = true),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        }.isSuccess
        if (!started) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (watch == null) watch = scope.launch {
            GalleryRankingRunner.state.collect { state ->
                when (state) {
                    is GalleryRankingState.Running -> manager.notify(PROGRESS_ID, notification(
                        "${state.galleryName ?: "갤창랭킹"} 만드는 중", progressText(state.progress), ongoing = true))
                    else -> {
                        when (state) {
                            is GalleryRankingState.Done -> manager.notify(RESULT_ID, notification(
                                "갤창랭킹이 완성됐어요", "${state.result.gallery.name} · 눌러서 결과 보기", ongoing = false))
                            is GalleryRankingState.Failed -> manager.notify(RESULT_ID, notification(
                                "갤창랭킹을 만들지 못했어요", state.message, ongoing = false))
                            else -> Unit
                        }
                        ServiceCompat.stopForeground(this@GalleryRankingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        // 끝난 뒤 곧바로 다시 시작했다면 새 시작은 멈추지 않는다.
                        stopSelf(lastStartId)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** 안드로이드 15+: dataSync 시간 한도를 다 쓰면 불린다. 몇 초 안에 멈추지 않으면 앱이 죽으니 바로 내려간다. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun progressText(progress: CrawlProgress) = when (progress.stage) {
        CrawlProgress.Stage.RESOLVING -> "갤러리를 확인하고 있어요"
        CrawlProgress.Stage.POSTS -> "글 ${"%,d".format(progress.posts)}개 모으는 중"
        CrawlProgress.Stage.COMMENTS -> "댓글 ${"%,d".format(progress.commentRequestsDone)}/${"%,d".format(progress.commentRequestsTotal)}쪽 모으는 중"
    }

    private fun notification(title: String, text: String, ongoing: Boolean): Notification {
        // 알림을 누르면 앱 첫 화면이 아니라 갤창랭킹 화면(진행 상황·결과)을 연다. 이미 떠 있으면 그 화면이 새 요청을 받는다.
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let {
            // 런처 인텐트에는 패키지가 없다. 같게 맞춰야 나중에 런처로 열 때 화면이 하나 더 쌓이지 않는다.
            it.setPackage(null).putExtra(EXTRA_OPEN_GALLERY_RANKING, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setAutoCancel(!ongoing)
            .setContentIntent(launch)
            .build()
    }

    private companion object {
        const val CHANNEL = "gallery_ranking"
        const val PROGRESS_ID = 4_301
        const val RESULT_ID = 4_302
    }
}

/** 갤창랭킹 알림으로 앱을 열 때 붙이는 표시. 앱이 받으면 갤창랭킹 화면으로 간다. */
const val EXTRA_OPEN_GALLERY_RANKING = "open_gallery_ranking"
