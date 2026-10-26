package com.dccleaner.app.ui.screen

import android.content.Context
import android.content.SharedPreferences

private class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, default: String) = prefs.getString(key, default) ?: default
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
}

/** 기존 설치의 설정이 그대로 이어지도록 예전 SharedPreferences 이름을 쓴다. */
fun androidCommentMonitorStorage(context: Context): KeyValueStore =
    SharedPreferencesStore(context.applicationContext.getSharedPreferences("comment_cleaner_settings", Context.MODE_PRIVATE))
