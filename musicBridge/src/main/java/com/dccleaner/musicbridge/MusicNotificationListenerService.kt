package com.dccleaner.musicbridge

import android.service.notification.NotificationListenerService
import android.util.Log

class MusicNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "음악 알림 접근 서비스가 연결되었습니다.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "음악 알림 접근 서비스 연결이 해제되었습니다.")
    }

    private companion object {
        const val TAG = "MusicBridge"
    }
}
