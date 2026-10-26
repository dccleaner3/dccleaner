package com.dccleaner.app.storage

import android.content.Context

private const val THEME_PREFS_NAME = "theme_preferences"
private const val KEY_DARK_THEME = "dark_theme"
private const val KEY_RECORD_GUESTBOOK_LOG = "record_guestbook_log"
private const val KEY_DELETE_TARGETS = "delete_targets"
private const val KEY_DELETE_COMMENT_FIRST = "delete_comment_first"
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

/** 클리너 탭에서 마지막에 고른 지울 대상("posting"·"comment"). 저장한 적이 없으면 아무것도 안 고른 상태다. */
fun getSavedDeleteTargets(context: Context): Set<String> =
    context.applicationContext
        .getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(KEY_DELETE_TARGETS, emptySet())
        ?.filterTo(mutableSetOf()) { it == "posting" || it == "comment" } ?: emptySet()

fun saveDeleteTargets(context: Context, targets: Set<String>) {
    context.applicationContext
        .getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putStringSet(KEY_DELETE_TARGETS, targets.toSet())
        .apply()
}

fun getSavedDeleteCommentFirst(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_DELETE_COMMENT_FIRST, true)

fun saveDeleteCommentFirst(context: Context, commentFirst: Boolean) {
    context.applicationContext
        .getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_DELETE_COMMENT_FIRST, commentFirst)
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
