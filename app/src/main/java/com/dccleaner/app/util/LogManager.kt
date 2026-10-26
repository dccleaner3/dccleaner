package com.dccleaner.app.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LogManager internal constructor(private val logFileProvider: () -> File) {
    constructor(context: Context) : this({
        File(context.applicationContext.getExternalFilesDir(null), LOG_FILE_NAME)
    })
    companion object {
        // All instances write the same file, so append, rotation, read and clear share one lock.
        private val fileMutex = Mutex()
        private const val LOG_FILE_NAME = "dccleaner_log.txt"
        private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB
        // 삭제마다 반복되는 시작·성공 줄은 5분 요약으로 묶는다. 같은 파일을 쓰므로 인스턴스끼리 공유한다.
        private val volume = LogVolumeFilter()
    }

    private val logFile: File
        get() = logFileProvider()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * 로그 메시지 추가
     */
    suspend fun addLog(tag: String, message: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            try {
                val lines = volume.filter(tag, message)
                if (lines.isEmpty()) return@withLock
                val timestamp = dateFormat.format(Date())
                val logEntry = lines.joinToString("") { (t, m) -> "[$timestamp] [$t] ${SensitiveLogSanitizer.sanitize(m)}\n" }

                // 파일 크기 체크 및 로테이션
                if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                    rotateLog()
                }

                logFile.appendText(logEntry)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 로그 파일 전체 내용 읽기
     */
    suspend fun readLogs(): String = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            try {
                if (logFile.exists()) {
                    logFile.readText()
                } else {
                    "로그가 없습니다."
                }
            } catch (e: Exception) {
                "로그 읽기 실패: ${e.message}"
            }
        }
    }

    /**
     * 로그 파일 삭제
     */
    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            try {
                if (logFile.exists()) {
                    logFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 로그 파일 로테이션 (크기가 커지면 백업하고 새로 시작)
     */
    private fun rotateLog() {
        try {
            val backupFile = File(logFile.parentFile, "${logFile.name}.old")
            if (backupFile.exists()) {
                backupFile.delete()
            }
            logFile.renameTo(backupFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
