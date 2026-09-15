package com.dccleaner.app.desktop

import com.dccleaner.app.model.CollectedPost
import com.dccleaner.app.model.DeleteQueueCheckpoint
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskState
import com.dccleaner.app.model.isEligibleForResume
import com.dccleaner.app.runtime.DeleteTaskStorePort
import com.dccleaner.app.runtime.RuntimeLogSink
import com.dccleaner.app.runtime.RuntimeNotifier
import com.dccleaner.app.util.SensitiveLogSanitizer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.prefs.Preferences

object DesktopPaths {
    val appDataDir: File by lazy {
        val osName = System.getProperty("os.name").lowercase()
        val path = when {
            osName.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/DCCleaner")
            osName.contains("win") -> File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "DCCleaner")
            else -> File(System.getProperty("user.home"), ".local/share/dccleaner")
        }
        path.apply { mkdirs() }
    }
}

class DesktopThemePreferenceStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/dccleaner/app/desktop/ui")
) {
    fun getDarkTheme(defaultValue: Boolean): Boolean =
        if (preferences.get(KEY_DARK_THEME, null) == null) {
            defaultValue
        } else {
            preferences.getBoolean(KEY_DARK_THEME, defaultValue)
        }

    fun saveDarkTheme(darkTheme: Boolean) {
        preferences.putBoolean(KEY_DARK_THEME, darkTheme)
        preferences.flush()
    }

    fun getRecordGuestbookLog(): Boolean =
        preferences.getBoolean(KEY_RECORD_GUESTBOOK_LOG, true)

    fun saveRecordGuestbookLog(enabled: Boolean) {
        preferences.putBoolean(KEY_RECORD_GUESTBOOK_LOG, enabled)
        preferences.flush()
    }

    fun getDeveloperMode(): Boolean =
        preferences.getBoolean(KEY_DEVELOPER_MODE, false)

    fun saveDeveloperMode(enabled: Boolean) {
        preferences.putBoolean(KEY_DEVELOPER_MODE, enabled)
        preferences.flush()
    }

    private companion object {
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_RECORD_GUESTBOOK_LOG = "record_guestbook_log"
        const val KEY_DEVELOPER_MODE = "developer_mode"
    }
}

class DesktopDeleteTaskStore(
    private val root: File = DesktopPaths.appDataDir,
    private val gson: Gson = Gson()
) : DeleteTaskStorePort {
    private val tasksFile = File(root, "delete_tasks.json")
    private val queueDirectory = File(root, "delete_task_queues")
    private val taskListType = object : TypeToken<List<DeleteTaskProgress>>() {}.type
    private val postListType = object : TypeToken<List<CollectedPost>>() {}.type

    @Synchronized
    override fun getAll(): List<DeleteTaskProgress> = readTasks().sortedByDescending { it.updatedAt }

    @Synchronized
    override fun getForLogin(loginId: String): List<DeleteTaskProgress> =
        readTasks()
            .filter { it.loginId == loginId && it.state.isEligibleForResume() }
            .sortedByDescending { it.updatedAt }

    @Synchronized
    override fun get(taskId: String): DeleteTaskProgress? = readTasks().firstOrNull { it.id == taskId }

    @Synchronized
    override fun save(task: DeleteTaskProgress): Boolean = runCatching {
        val tasks = readTasks().toMutableList()
        val updated = task.copy(
            twoCaptchaApiKey = "",
            updatedAt = System.currentTimeMillis()
        )
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) tasks[index] = updated else tasks.add(updated)
        writeTasks(tasks)
    }.getOrDefault(false)

    @Synchronized
    override fun updateState(
        taskId: String,
        state: DeleteTaskState,
        message: String,
        captchaRequired: Boolean
    ): DeleteTaskProgress? {
        val tasks = readTasks().toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return null
        val updated = tasks[index].copy(
            state = state,
            statusMessage = message,
            captchaRequired = captchaRequired,
            updatedAt = System.currentTimeMillis()
        )
        tasks[index] = updated
        return if (writeTasks(tasks)) updated else null
    }

    @Synchronized
    override fun remove(taskId: String): Boolean {
        val tasks = readTasks().filterNot { it.id == taskId }
        val saved = writeTasks(tasks)
        if (saved) {
            deleteQueue(taskId)
            deleteCollectedPages(taskId)
        }
        return saved
    }

    @Synchronized
    override fun saveQueue(taskId: String, posts: List<CollectedPost>): Boolean =
        writePostList(queueFile(taskId), posts)

    @Synchronized
    override fun loadRemainingQueue(taskId: String, cursor: Int): List<CollectedPost>? = runCatching {
        val posts = readPostList(queueFile(taskId)) ?: return@runCatching null
        posts.filterIndexed { index, _ ->
            !DeleteQueueCheckpoint.shouldSkipQueueItem(index, cursor.coerceAtLeast(0))
        }
    }.getOrNull()

    @Synchronized
    override fun hasQueue(taskId: String): Boolean = queueFile(taskId).exists()

    @Synchronized
    override fun deleteQueue(taskId: String): Boolean {
        val file = queueFile(taskId)
        file.delete()
        return !file.exists()
    }

    @Synchronized
    override fun saveCollectedPage(taskId: String, page: Int, posts: List<CollectedPost>): Boolean =
        writePostList(collectedPageFile(taskId, page), posts)

    @Synchronized
    override fun findNextMissingCollectedPage(taskId: String, totalPages: Int): Int =
        DeleteQueueCheckpoint.findNextMissingPage(totalPages) { page ->
            collectedPageFile(taskId, page).exists()
        }

    @Synchronized
    override fun loadCollectedPages(taskId: String, totalPages: Int): List<CollectedPost>? = runCatching {
        val posts = mutableListOf<CollectedPost>()
        for (page in DeleteQueueCheckpoint.pagesInQueueOrder(totalPages)) {
            posts.addAll(readPostList(collectedPageFile(taskId, page)) ?: return@runCatching null)
        }
        posts
    }.getOrNull()

    @Synchronized
    override fun deleteCollectedPages(taskId: String): Boolean {
        val directory = collectedPagesDirectory(taskId)
        directory.listFiles()?.forEach { it.delete() }
        return !directory.exists() || directory.delete()
    }

    private fun readTasks(): List<DeleteTaskProgress> = runCatching {
        if (!tasksFile.exists()) return@runCatching emptyList()
        tasksFile.reader(Charsets.UTF_8).use { reader ->
            val tasks = gson.fromJson<List<DeleteTaskProgress>>(reader, taskListType) ?: emptyList()
            tasks.map { task -> task.copy(twoCaptchaApiKey = "") }
        }
    }.getOrDefault(emptyList())

    private fun writeTasks(tasks: List<DeleteTaskProgress>): Boolean = runCatching {
        atomicWriteText(tasksFile, gson.toJson(tasks))
        true
    }.getOrDefault(false)

    private fun queueFile(taskId: String): File = File(queueDirectory, "$taskId.json")
    private fun collectedPagesDirectory(taskId: String): File = File(queueDirectory, "${taskId}_pages")
    private fun collectedPageFile(taskId: String, page: Int): File =
        File(collectedPagesDirectory(taskId), "$page.json")

    private fun writePostList(file: File, posts: List<CollectedPost>): Boolean = runCatching {
        file.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) return@runCatching false
        }
        atomicWriteText(file, gson.toJson(posts))
        true
    }.getOrDefault(false)

    private fun readPostList(file: File): List<CollectedPost>? = runCatching {
        if (!file.exists()) return@runCatching null
        InputStreamReader(file.inputStream(), Charsets.UTF_8).use { reader ->
            gson.fromJson<List<CollectedPost>>(reader, postListType) ?: emptyList()
        }
    }.getOrNull()
}

class DesktopFileLogSink(
    private val file: File = File(DesktopPaths.appDataDir, "dccleaner.log")
) : RuntimeLogSink {
    override suspend fun addLog(tag: String, message: String) {
        file.parentFile?.mkdirs()
        file.appendText("[${System.currentTimeMillis()}][$tag] ${SensitiveLogSanitizer.sanitize(message)}\n", Charsets.UTF_8)
    }
}

class DesktopTrayNotifier : RuntimeNotifier {
    private val trayIcon: TrayIcon? by lazy {
        if (!SystemTray.isSupported()) return@lazy null
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        TrayIcon(image, "디시클리너 모바일 & PC").apply {
            isImageAutoSize = true
            runCatching { SystemTray.getSystemTray().add(this) }
        }
    }

    override fun notify(title: String, message: String) {
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
    }
}

private fun atomicWriteText(file: File, text: String) {
    file.parentFile?.let { parent ->
        if (!parent.exists() && !parent.mkdirs()) error("Could not create ${parent.absolutePath}")
    }
    val temp = File(file.parentFile, "${file.name}.tmp")
    OutputStreamWriter(temp.outputStream(), Charsets.UTF_8).use { writer ->
        writer.write(text)
        writer.flush()
    }
    if (file.exists() && !file.delete()) error("Could not replace ${file.absolutePath}")
    if (!temp.renameTo(file)) error("Could not write ${file.absolutePath}")
}
