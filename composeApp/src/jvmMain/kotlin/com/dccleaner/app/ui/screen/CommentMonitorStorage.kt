package com.dccleaner.app.ui.screen

/** 댓글 자동 정리 설정 저장소 (Android SharedPreferences, PC java.util.prefs). */
interface KeyValueStore {
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    /** 미뤄 둔 쓰기를 디스크에 남긴다. 저장소가 알아서 쓰면 할 일이 없다. */
    fun flush() = Unit
}
