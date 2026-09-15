package com.dccleaner.app.runtime

data class GuestbookExecutionProgress(
    val done: Int,
    val total: Int,
    val successCount: Int,
    val failCount: Int
)

object GuestbookExecutionRunner {
    suspend fun run(
        userIds: List<String>,
        message: String,
        send: suspend (userId: String, message: String) -> Boolean,
        onResult: suspend (userId: String, success: Boolean) -> Unit = { _, _ -> },
        onProgress: (GuestbookExecutionProgress) -> Unit = {}
    ): GuestbookExecutionProgress {
        var progress = GuestbookExecutionProgress(
            done = 0,
            total = userIds.size,
            successCount = 0,
            failCount = 0
        )
        onProgress(progress)

        userIds.forEach { userId ->
            val success = send(userId, message)
            onResult(userId, success)
            progress = progress.copy(
                done = progress.done + 1,
                successCount = progress.successCount + if (success) 1 else 0,
                failCount = progress.failCount + if (success) 0 else 1
            )
            onProgress(progress)
        }

        return progress
    }
}
