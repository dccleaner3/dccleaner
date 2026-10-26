package com.dccleaner.app.service

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dccleaner.app.MainActivity
import com.dccleaner.app.R
import com.dccleaner.app.model.formatWon
import com.dccleaner.app.proxy.SupabaseProxyCleanerBackend
import com.dccleaner.app.storage.AndroidProxyCleanerStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 충전 신청 뒤 사용자는 카카오톡에 가 있어 앱이 백그라운드에서 얼어 버린다. 확인을 기다리는 동안만 포그라운드로 돌며
 * 신청서가 확인되면 송금하라고, 충전되면 끝났다고 알린다. 상시 알림이 지금 단계를 보여 준다.
 * 충전·[MAX_WATCH_MILLIS] 초과·[stop](신청 창 닫기) 중 하나면 끝난다.
 */
class ChargeWatchService : Service() {
    companion object {
        private const val EXTRA_PURCHASE = "purchase_id"
        private const val EXTRA_AMOUNT = "amount_won"
        private const val EXTRA_COUNT = "credited_count"
        private const val NOTIFICATION_ID = 5
        private const val POLL_MILLIS = 3_000L
        private const val MAX_WATCH_MILLIS = 30 * 60_000L

        /** 신청 창이 열릴 때(앱이 화면에 있을 때) 부른다. 같은 신청이면 이어서 본다. */
        fun start(context: Context, purchaseId: String, amountWon: Int, creditedCount: Long) {
            val intent = Intent(context, ChargeWatchService::class.java)
                .putExtra(EXTRA_PURCHASE, purchaseId).putExtra(EXTRA_AMOUNT, amountWon).putExtra(EXTRA_COUNT, creditedCount)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChargeWatchService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watching: Job? = null
    // 끝난 확인(IO 스레드)이 새 신청을 보는 서비스를 멈추지 않게 본다.
    @Volatile private var purchaseId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_PURCHASE)
        val amount = intent?.getIntExtra(EXTRA_AMOUNT, 0) ?: 0
        val count = intent?.getLongExtra(EXTRA_COUNT, 0L) ?: 0L
        DcCleanerNotifier(this).createNotificationChannel()
        // startForegroundService 뒤에는 곧바로 포그라운드로 올라와야 한다.
        // dataSync 하루 실행 한도(삭제 서비스와 같이 씀)를 다 썼으면 거절되므로 앱이 죽지 않게 그냥 끝낸다.
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID,
                ongoing("충전 확인 중", "오픈채팅에 신청서를 보내 주세요. 확인되면 알려 드려요."),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (id == null || amount <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (id != purchaseId || watching?.isActive != true) {
            // 취소된 이전 확인이 finally에서 stopSelf하지 않도록 신청 ID를 먼저 바꾼다.
            purchaseId = id
            watching?.cancel()
            watching = scope.launch { watch(id, amount, count) }
        }
        return START_NOT_STICKY
    }

    private suspend fun watch(id: String, amount: Int, count: Long) {
        val backend = SupabaseProxyCleanerBackend(AndroidProxyCleanerStorage(applicationContext))
        try {
            var seen = false
            val deadline = SystemClock.elapsedRealtime() + MAX_WATCH_MILLIS
            while (SystemClock.elapsedRealtime() < deadline) {
                delay(POLL_MILLIS)
                // 네트워크·세션 문제는 다음 확인 때 다시 본다.
                val (status, requestSeen) = try { backend.purchaseProgress(id) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: continue
                if (status == "credited") {
                    alert("충전됐어요 · ${String.format(Locale.US, "%,d", count)}개 삭제할 수 있어요")
                    return
                }
                if (requestSeen && !seen) {
                    seen = true
                    val message = "신청서를 확인했어요. 오픈채팅에서 ${formatWon(amount.toDouble())}을 송금해 주세요."
                    notify(ongoing("${formatWon(amount.toDouble())}을 송금해 주세요", message))
                    alert(message)
                }
            }
        } finally {
            backend.close()
            if (purchaseId == id) stopSelf()
        }
    }

    /** 앱을 보고 있으면 화면이 단계를 보여 주므로 알리지 않는다. */
    private fun alert(message: String) {
        val state = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState)
        if (state.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            DcCleanerNotifier(this).showChargeNotification(message)
        }
    }

    private fun notify(notification: android.app.Notification) {
        runCatching { androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
    }

    private fun ongoing(title: String, text: String) = NotificationCompat.Builder(this, DcCleanerService.CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()

    /** Android 15+ dataSync 실행 한도에 걸리면 곧바로 멈춰야 한다(안 그러면 앱이 강제 종료된다). */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
