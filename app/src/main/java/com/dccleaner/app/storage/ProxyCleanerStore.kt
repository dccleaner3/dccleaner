package com.dccleaner.app.storage

import android.content.Context
import com.dccleaner.app.platform.ProxyCleanerStorage

private const val ENCRYPTED_PREFERENCES_NAME = "proxy_cleaner_encrypted"
private const val PREFERENCES_NAME = "proxy_cleaner"
private const val REFRESH_TOKEN = "refresh_token"
private const val CONSENT = "consent_accepted"

/**
 * Losing the refresh token hands the user a new code and strands their credits, so read
 * failures propagate instead of looking like "no token yet".
 */
class AndroidProxyCleanerStorage(context: Context) : ProxyCleanerStorage {
    private val appContext = context.applicationContext

    override fun readRefreshToken(): String? =
        encryptedPreferences(appContext, ENCRYPTED_PREFERENCES_NAME).getString(REFRESH_TOKEN, null)

    override fun writeRefreshToken(token: String) {
        check(encryptedPreferences(appContext, ENCRYPTED_PREFERENCES_NAME).edit().putString(REFRESH_TOKEN, token).commit())
    }

    override fun hasConsent(): Boolean =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).getBoolean(CONSENT, false)

    override fun saveConsent() {
        check(appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().putBoolean(CONSENT, true).commit())
    }

}
