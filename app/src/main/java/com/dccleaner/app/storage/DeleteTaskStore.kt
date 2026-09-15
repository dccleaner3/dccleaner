package com.dccleaner.app.storage

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import com.dccleaner.app.model.CollectedPost
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskState
import com.dccleaner.app.model.DeleteQueueCheckpoint
import com.dccleaner.app.model.isEligibleForResume
import com.dccleaner.app.runtime.DeleteTaskStorePort
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class DeleteTaskStore(context: Context) : DeleteTaskStorePort {
    companion object {
        private const val PREFS_NAME = "delete_task_progress"
        private const val KEY_TASKS = "tasks"
        private const val SECRET_PREFS_NAME = "delete_task_secrets_encrypted"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secretPrefs: SharedPreferences? = runCatching {
        encryptedPreferences(context, SECRET_PREFS_NAME)
    }.getOrNull()
    private val queueDirectory = File(context.applicationContext.filesDir, "delete_task_queues")
    private val gson = Gson()
    private val taskListType = object : TypeToken<List<DeleteTaskProgress>>() {}.type

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
    override fun save(task: DeleteTaskProgress): Boolean {
        val tasks = readTasks().toMutableList()
        val updated = task.copy(updatedAt = System.currentTimeMillis())
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) tasks[index] = updated else tasks.add(updated)
        val secretSaved = saveTwoCaptchaKey(task.id, task.twoCaptchaApiKey)
        val progressSaved = writeTasks(tasks)
        return secretSaved && progressSaved
    }

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
        val progressRemoved = writeTasks(tasks)
        if (progressRemoved) {
            secretPrefs?.edit()?.remove(taskId)?.commit()
            deleteQueue(taskId)
            deleteCollectedPages(taskId)
        }
        return progressRemoved
    }

    @Synchronized
    override fun saveQueue(taskId: String, posts: List<CollectedPost>): Boolean = runCatching {
        if (!queueDirectory.exists() && !queueDirectory.mkdirs()) return@runCatching false
        val atomicFile = AtomicFile(queueFile(taskId))
        val output = atomicFile.startWrite()
        try {
            val writer = OutputStreamWriter(output, Charsets.UTF_8)
            gson.toJson(posts, writer)
            writer.flush()
            atomicFile.finishWrite(output)
            true
        } catch (e: Exception) {
            atomicFile.failWrite(output)
            throw e
        }
    }.getOrDefault(false)

    @Synchronized
    override fun loadRemainingQueue(taskId: String, cursor: Int): List<CollectedPost>? = runCatching {
        val file = queueFile(taskId)
        val atomicFile = AtomicFile(file)
        atomicFile.openRead().use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                val remaining = mutableListOf<CollectedPost>()
                var index = 0
                val safeCursor = cursor.coerceAtLeast(0)
                reader.beginArray()
                while (reader.hasNext()) {
                    if (DeleteQueueCheckpoint.shouldSkipQueueItem(index++, safeCursor)) {
                        reader.skipValue()
                    } else {
                        remaining.add(gson.fromJson(reader, CollectedPost::class.java))
                    }
                }
                reader.endArray()
                if (safeCursor > index) null else remaining
            }
        }
    }.getOrNull()

    @Synchronized
    override fun hasQueue(taskId: String): Boolean = runCatching {
        AtomicFile(queueFile(taskId)).openRead().use { }
        true
    }.getOrDefault(false)

    @Synchronized
    override fun deleteQueue(taskId: String): Boolean {
        val file = queueFile(taskId)
        AtomicFile(file).delete()
        return !file.exists()
    }

    @Synchronized
    override fun saveCollectedPage(taskId: String, page: Int, posts: List<CollectedPost>): Boolean =
        writePostList(AtomicFile(collectedPageFile(taskId, page)), posts)

    @Synchronized
    override fun findNextMissingCollectedPage(taskId: String, totalPages: Int): Int {
        return DeleteQueueCheckpoint.findNextMissingPage(totalPages) { page ->
            hasCollectedPage(taskId, page)
        }
    }

    @Synchronized
    override fun loadCollectedPages(taskId: String, totalPages: Int): List<CollectedPost>? = runCatching {
        val posts = mutableListOf<CollectedPost>()
        for (page in DeleteQueueCheckpoint.pagesInQueueOrder(totalPages)) {
            val pagePosts = readPostList(collectedPageFile(taskId, page)) ?: return@runCatching null
            posts.addAll(pagePosts)
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
        val json = prefs.getString(KEY_TASKS, null) ?: return@runCatching emptyList()
        val tasks = gson.fromJson<List<DeleteTaskProgress>>(json, taskListType) ?: emptyList()
        tasks.map { task ->
            task.copy(twoCaptchaApiKey = secretPrefs?.getString(task.id, "").orEmpty())
        }
    }.getOrDefault(emptyList())

    @SuppressLint("UseKtx")
    private fun saveTwoCaptchaKey(taskId: String, key: String): Boolean {
        val secrets = secretPrefs ?: return true
        if (secrets.getString(taskId, "").orEmpty() == key) return true
        return secrets.edit().apply {
            if (key.isBlank()) remove(taskId) else putString(taskId, key)
        }.commit()
    }

    // commit()을 사용해 프로세스가 곧바로 종료되어도 체크포인트가 디스크에 반영되게 한다.
    @SuppressLint("UseKtx") // 프로세스 종료 직전에도 반영되도록 비동기 apply 대신 commit을 사용한다.
    private fun writeTasks(tasks: List<DeleteTaskProgress>): Boolean =
        prefs.edit().putString(KEY_TASKS, gson.toJson(tasks)).commit()

    private fun queueFile(taskId: String): File = File(queueDirectory, "$taskId.json")

    private fun collectedPagesDirectory(taskId: String): File = File(queueDirectory, "${taskId}_pages")

    private fun collectedPageFile(taskId: String, page: Int): File =
        File(collectedPagesDirectory(taskId), "$page.json")

    private fun writePostList(atomicFile: AtomicFile, posts: List<CollectedPost>): Boolean = runCatching {
        atomicFile.baseFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) return@runCatching false
        }
        val output = atomicFile.startWrite()
        try {
            val writer = OutputStreamWriter(output, Charsets.UTF_8)
            gson.toJson(posts, writer)
            writer.flush()
            atomicFile.finishWrite(output)
            true
        } catch (e: Exception) {
            atomicFile.failWrite(output)
            throw e
        }
    }.getOrDefault(false)

    private fun readPostList(file: File): List<CollectedPost>? = runCatching {
        AtomicFile(file).openRead().use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { reader ->
                val type = object : TypeToken<List<CollectedPost>>() {}.type
                gson.fromJson<List<CollectedPost>>(reader, type) ?: emptyList()
            }
        }
    }.getOrNull()

    private fun hasCollectedPage(taskId: String, page: Int): Boolean = runCatching {
        AtomicFile(collectedPageFile(taskId, page)).openRead().use { }
        true
    }.getOrDefault(false)

}
