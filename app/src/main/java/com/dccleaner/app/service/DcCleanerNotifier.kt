package com.dccleaner.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dccleaner.app.MainActivity
import com.dccleaner.app.R

class DcCleanerNotifier(
    private val context: Context
) {
    companion object {
        const val EXTRA_RESUME_TASK_ID = "resume_task_id"
        private const val COMPLETION_CHANNEL_ID = "DCCLEANER_COMPLETION_CHANNEL"
        private const val INTERRUPTION_CHANNEL_ID = "DCCLEANER_INTERRUPTION_CHANNEL"
        private const val INTERRUPTION_NOTIFICATION_BASE_ID = 10_000
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            DcCleanerService.CHANNEL_ID,
            "백그라운드 작업",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "삭제 및 대왕콘 얻기 작업 진행 알림"
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val completionChannel = NotificationChannel(
            COMPLETION_CHANNEL_ID,
            "작업 완료 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "삭제, 대왕콘, 방명록 작업 완료 및 결과 알림"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(completionChannel)

        val captchaChannel = NotificationChannel(
            DcCleanerService.CAPTCHA_CHANNEL_ID,
            "캡챠 해결 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "삭제 중 캡챠 해결이 필요할 때 알림"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(captchaChannel)

        val interruptionChannel = NotificationChannel(
            INTERRUPTION_CHANNEL_ID,
            "삭제 작업 중단 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "삭제 작업을 이어서 진행해야 할 때 알림"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(interruptionChannel)
    }

    fun showCaptchaNotification(taskId: String? = null) {
        val intent = createResumeIntent(taskId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, DcCleanerService.CAPTCHA_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("캡챠 해결이 필요합니다")
            .setContentText("삭제를 계속하려면 앱을 열어 캡챠를 풀어주세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(DcCleanerService.CAPTCHA_NOTIFICATION_ID, notification)
    }

    fun cancelCaptchaNotification() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(DcCleanerService.CAPTCHA_NOTIFICATION_ID)
    }

    fun showInterruptionNotification(taskId: String, title: String, contentText: String) {
        val stableId = stableNotificationId(taskId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            stableId,
            createResumeIntent(taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, INTERRUPTION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(stableId, notification)
    }

    fun createNotification(contentText: String): Notification =
        createServiceNotification("DC 클리너", contentText, ongoing = true)

    fun createDaewangconNotification(): Notification = createServiceNotification(
        title = "DC 클리너",
        contentText = "대왕콘 얻기 진행중",
        ongoing = true
    )

    fun createGuestbookNotification(contentText: String = "방명록 전송 준비 중..."): Notification =
        createServiceNotification("DC 클리너", contentText, ongoing = true)

    private fun createServiceNotification(
        title: String,
        contentText: String,
        ongoing: Boolean
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, DcCleanerService.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createCompletionNotification(contentText: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DC 클리너")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun updateNotification(contentText: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(DcCleanerService.NOTIFICATION_ID, createNotification(contentText))
    }

    fun updateDaewangconNotification() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            DcCleanerService.NOTIFICATION_ID,
            createDaewangconNotification()
        )
    }

    fun updateGuestbookNotification(contentText: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            DcCleanerService.NOTIFICATION_ID,
            createGuestbookNotification(contentText)
        )
    }

    fun showCompletedNotification(contentText: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            DcCleanerService.NOTIFICATION_ID,
            createCompletionNotification(contentText)
        )
    }

    fun showDaewangconCompletedNotification() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            DcCleanerService.NOTIFICATION_ID,
            createCompletionNotification("대왕콘 얻기 완료")
        )
    }

    fun showDaewangconFailedNotification() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            DcCleanerService.NOTIFICATION_ID,
            createCompletionNotification("대왕콘 얻기 실패")
        )
    }

    fun showGuestbookCompletedNotification(successCount: Int, failCount: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            DcCleanerService.NOTIFICATION_ID,
            createCompletionNotification(
                "방명록 전송 완료: 성공 ${successCount}명, 실패 ${failCount}명"
            )
        )
    }

    fun cancelNotification() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(DcCleanerService.NOTIFICATION_ID)
    }

    private fun createResumeIntent(taskId: String?): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            taskId?.let { putExtra(EXTRA_RESUME_TASK_ID, it) }
        }

    private fun stableNotificationId(taskId: String): Int =
        INTERRUPTION_NOTIFICATION_BASE_ID +
                ((taskId.hashCode() and Int.MAX_VALUE) % 1_000_000_000)
}
