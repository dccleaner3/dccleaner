package com.dccleaner.app.ui.screen

import java.util.prefs.Preferences

private class JavaPreferencesStore(private val prefs: Preferences) : KeyValueStore {
    override fun getString(key: String, default: String): String = prefs.get(key, default)
    // 입력 중 임시 저장은 키를 누를 때마다 오므로 바로 디스크에 쓰지 않는다. 켜고 끌 때 flush로 남긴다.
    override fun putString(key: String, value: String) = prefs.put(key, value)
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.putInt(key, value)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.putBoolean(key, value)
    override fun flush() = prefs.flush()
}

fun desktopCommentMonitorStorage(): KeyValueStore =
    JavaPreferencesStore(Preferences.userRoot().node("com/dccleaner/app/desktop").node("comment_cleaner"))
