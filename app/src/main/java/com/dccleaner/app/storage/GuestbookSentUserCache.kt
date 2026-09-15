package com.dccleaner.app.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

class GuestbookSentUserCache(context: Context) {
    companion object {
        const val WRITE_BATCH_SIZE = 50

        private const val CACHE_DIRECTORY_NAME = "guestbook-sent-users"
        private val fileMutex = Mutex()
    }

    private val cacheDirectory = File(
        context.applicationContext.noBackupFilesDir,
        CACHE_DIRECTORY_NAME
    )

    suspend fun loadUserIds(senderId: String): Set<String> = withContext(Dispatchers.IO) {
        if (senderId.isBlank()) return@withContext emptySet()
        fileMutex.withLock {
            val file = cacheFile(senderId)
            if (!file.isFile) {
                emptySet()
            } else {
                completeCacheLines(file.readText(Charsets.UTF_8))
            }
        }
    }

    suspend fun count(senderId: String): Int = loadUserIds(senderId).size

    suspend fun append(senderId: String, userIds: Collection<String>): Boolean =
        withContext(Dispatchers.IO) {
            if (senderId.isBlank() || userIds.isEmpty()) return@withContext true
            fileMutex.withLock {
                try {
                    if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
                        error("방명록 캐시 디렉터리를 만들지 못했습니다.")
                    }
                    val file = cacheFile(senderId)
                    truncatePartialLastLine(file)
                    file.appendText(
                        cacheBatchText(userIds),
                        Charsets.UTF_8
                    )
                    true
                } catch (error: Exception) {
                    Log.e("GuestbookSentCache", "방명록 전송 캐시 저장 실패", error)
                    false
                }
            }
        }

    suspend fun clear(senderId: String): Boolean = withContext(Dispatchers.IO) {
        if (senderId.isBlank()) return@withContext true
        fileMutex.withLock {
            val file = cacheFile(senderId)
            !file.exists() || file.delete()
        }
    }

    private fun cacheFile(senderId: String): File =
        File(cacheDirectory, "${senderId.sha256()}.txt")
}

internal fun completeCacheLines(content: String): Set<String> {
    val completeContent = if (content.endsWith('\n')) {
        content
    } else {
        content.substringBeforeLast('\n', missingDelimiterValue = "")
    }
    return completeContent.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toHashSet()
}

internal fun cacheBatchText(userIds: Collection<String>): String =
    userIds.asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n", postfix = "\n")

internal fun truncatePartialLastLine(file: File) {
    if (!file.isFile || file.length() == 0L) return
    RandomAccessFile(file, "rw").use { randomAccessFile ->
        val lastIndex = randomAccessFile.length() - 1
        randomAccessFile.seek(lastIndex)
        if (randomAccessFile.readByte().toInt() == '\n'.code) return

        var index = lastIndex - 1
        while (index >= 0) {
            randomAccessFile.seek(index)
            if (randomAccessFile.readByte().toInt() == '\n'.code) {
                randomAccessFile.setLength(index + 1)
                return
            }
            index--
        }
        randomAccessFile.setLength(0)
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
