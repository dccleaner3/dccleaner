package com.dccleaner.app.ui.screen

import android.content.Context
import android.util.Log
import org.json.JSONArray

private const val WRITE_GALLERY_PREFS = "write_gallery_preferences"
private const val KEY_WRITE_URLS = "write_urls"
private const val KEY_LEGACY_DEFAULTS_REMOVED = "legacy_defaults_removed"

private val legacyDefaultWriteUrls = setOf(
    "https://m.dcinside.com/write/baseball_new13",
    "https://m.dcinside.com/write/book"
)

internal fun getSavedWriteGalleryUrls(context: Context): List<String> {
    val prefs = context.applicationContext
        .getSharedPreferences(WRITE_GALLERY_PREFS, Context.MODE_PRIVATE)
    val savedUrls = if (!prefs.contains(KEY_WRITE_URLS)) {
        emptyList()
    } else runCatching {
        val array = JSONArray(prefs.getString(KEY_WRITE_URLS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                add(array.getString(index))
            }
        }
    }.onFailure { error ->
        Log.e("WriteGalleryStore", "저장된 write 링크를 읽지 못했습니다.", error)
    }.getOrDefault(emptyList())

    if (prefs.getBoolean(KEY_LEGACY_DEFAULTS_REMOVED, false)) return savedUrls
    val migratedUrls = savedUrls.filterNot(legacyDefaultWriteUrls::contains)
    prefs.edit()
        .putString(KEY_WRITE_URLS, JSONArray().apply { migratedUrls.forEach(::put) }.toString())
        .putBoolean(KEY_LEGACY_DEFAULTS_REMOVED, true)
        .apply()
    return migratedUrls
}

internal fun saveWriteGalleryUrls(context: Context, urls: List<String>): Boolean {
    val serialized = JSONArray().apply { urls.forEach(::put) }.toString()
    return context.applicationContext
        .getSharedPreferences(WRITE_GALLERY_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_WRITE_URLS, serialized)
        .commit()
}
