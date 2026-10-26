package com.dccleaner.app.ui.screen

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.dccleaner.app.model.UserInfo
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.network.CleanerLogSink
import com.dccleaner.app.storage.DeleteTaskStore
import com.dccleaner.app.util.LogManager
import kotlinx.coroutines.Dispatchers

/** Credentials and the authenticated client survive configuration changes only in memory. */
class DccleanerSession(application: Application) : AndroidViewModel(application) {
    // Preserve the order of blocking credential reads, saves and removals off Main.
    val storageDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val logManager = LogManager(application)
    val cleaner = Cleaner(CleanerLogSink { tag, message -> logManager.addLog(tag, message) })
    val deleteTaskStore = DeleteTaskStore(application)
    val id = mutableStateOf("")
    val password = mutableStateOf("")
    val loginInfo = mutableStateOf<UserInfo?>(null)
    val postingGalleries = mutableStateOf<Map<String, String>>(emptyMap())
    val commentGalleries = mutableStateOf<Map<String, String>>(emptyMap())
    val guestbookUsers = mutableStateOf("")
    val guestbookMessage = mutableStateOf("")
    val selectedGalleries = mutableStateOf<List<String>>(emptyList())
    /** 설정 중이 아닌 다른 종류(글/댓글)의 선택 갤러리. null이면 아직 안 건드려서 그 종류 전체. */
    val otherSelectedGalleries = mutableStateOf<List<String>?>(null)
}
