package com.dccleaner.musicbridge

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.dccleaner.musiccontract.supportedMusicPlatforms

class MusicBridgeActivity : Activity() {
    private lateinit var statusCard: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusDescription: TextView
    private lateinit var settingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_bridge)

        statusCard = findViewById(R.id.status_card)
        statusTitle = findViewById(R.id.status_title)
        statusDescription = findViewById(R.id.status_description)
        settingsButton = findViewById(R.id.notification_settings_button)
        findViewById<TextView>(R.id.supported_platforms).text =
            supportedMusicPlatforms.joinToString { it.displayName }
        settingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val expected = ComponentName(this, MusicNotificationListenerService::class.java)
        val granted = enabledListeners.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }

        statusCard.setBackgroundResource(
            if (granted) R.drawable.bg_status_ready else R.drawable.bg_status_required
        )
        statusTitle.setText(if (granted) R.string.status_ready else R.string.status_permission_required)
        statusDescription.text = if (granted) {
            getString(R.string.status_ready_description)
        } else {
            getString(R.string.status_permission_description)
        }
        settingsButton.setText(
            if (granted) R.string.open_notification_settings else R.string.configure_permission
        )
    }
}
