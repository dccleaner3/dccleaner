package com.dccleaner.app.storage

import android.content.Context

private const val THEME_PREFS_NAME = "theme_preferences"
private const val KEY_DARK_THEME = "dark_theme"
private const val KEY_RECORD_GUESTBOOK_LOG = "record_guestbook_log"
private const val KEY_DEVELOPER_MODE = "developer_mode"

fun getSavedDarkTheme(context: Context, defaultValue: Boolean): Boolean {
    val prefs = context.applicationContext.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
    return if (prefs.contains(KEY_DARK_THEME)) {
        prefs.getBoolean(KEY_DARK_THEME, defaultValue)
    } else {
        defaultValue
    }
}

fun saveDarkTheme(context: Context, darkTheme: Boolean) {
    context.applicationContext
        .getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_DARK_THEME, darkTheme)
        .apply()
}

fun getSavedRecordGuestbookLog(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_RECORD_GUESTBOOK_LOG, true)

fun saveRecordGuestbookLog(context: Context, enabled: Boolean) {
    context.applicationContext
        .getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_RECORD_GUESTBOOK_LOG, enabled)
        .apply()
}

fun getSavedDeveloperMode(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_DEVELOPER_MODE, false)

fun saveDeveloperMode(context: Context, enabled: Boolean) {
    context.applicationContext
        .getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_DEVELOPER_MODE, enabled)
        .apply()
}
