package com.dccleaner.app.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, Any>()
    override fun getString(key: String, default: String) = values[key] as? String ?: default
    override fun putString(key: String, value: String) { values[key] = value }
    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
}

class CommentMonitorStateTest {
    @Test fun turningOnValidatesSavesAndRestartsOnLogin() {
        val prefs = MemoryStore()
        val state = CommentMonitorState(prefs)
        var started: Triple<List<String>, Int, Int>? = null
        val start: (List<String>, Int, Int) -> Unit = { k, i, m -> started = Triple(k, i, m) }

        state.setEnabled(true, start) {}
        assertNotNull(state.error)
        assertFalse(state.enabled)

        state.keywords = "정규식:["
        state.setEnabled(true, start) {}
        assertNotNull(state.error)
        assertNull(started)

        state.keywords = "광고\n 광고 \n정규식:홍보\\d+\n"
        state.intervalSeconds = "30"
        state.setEnabled(true, start) {}
        assertNull(state.error)
        assertTrue(state.enabled)
        assertEquals(Triple(listOf("광고", "정규식:홍보\\d+"), 30, 10), started)
        assertEquals(SavedCommentCleanerSettings(listOf("광고", "정규식:홍보\\d+"), 30, 10), loadSavedCommentMonitor(prefs))

        var stopped = false
        state.setEnabled(false, start) { stopped = true }
        assertTrue(stopped)
        // 꺼 두면 로그인해도 다시 켜지 않지만 써 둔 단어는 남는다.
        assertEquals(emptyList(), loadSavedCommentMonitor(prefs).keywords)
        assertEquals("광고\n 광고 \n정규식:홍보\\d+\n", CommentMonitorState(prefs).keywords)
    }

    @Test fun draftsSurviveLeavingTheScreenButDoNotStartAnything() {
        val prefs = MemoryStore()
        val state = CommentMonitorState(prefs)
        state.editKeywords("광고\n홍보")
        state.editInterval("3a0")
        state.editDuration("")
        // 다른 탭에 다녀오면 화면 상태는 새로 만들어지지만 고치던 값은 남는다.
        val reopened = CommentMonitorState(prefs)
        assertEquals("광고\n홍보", reopened.keywords)
        assertEquals("30", reopened.intervalSeconds)
        assertEquals("10", reopened.monitorMinutes)
        // 켜지 않았으면 로그인해도 시작하지 않는다.
        assertEquals(emptyList(), loadSavedCommentMonitor(prefs).keywords)
    }
}
