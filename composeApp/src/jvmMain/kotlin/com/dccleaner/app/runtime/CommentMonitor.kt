package com.dccleaner.app.runtime

import com.dccleaner.app.network.COMMENT_CLEANER_REGEX_PREFIX
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.network.MonitoredWrittenPost
import com.dccleaner.app.network.commentCleanerPatterns
import com.dccleaner.app.network.commentContainsBlockedKeyword
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 댓글 자동 정리 설정. 내 새 글에 달린 댓글 중 차단 단어가 든 것을 지운다. */
data class CommentMonitorConfig(
    val cleaner: Cleaner,
    val keywords: List<String>,
    val intervalSeconds: Int,
    val monitorMinutes: Int
) {
    val hasWork: Boolean get() = keywords.isNotEmpty()

    companion object {
        fun of(
            cleaner: Cleaner,
            keywords: List<String>,
            intervalSeconds: Int,
            monitorMinutes: Int
        ) = CommentMonitorConfig(
            cleaner,
            keywords.filter(String::isNotBlank).distinct(),
            intervalSeconds.coerceAtLeast(5),
            monitorMinutes.coerceAtLeast(1)
        )
    }
}

private class WatchedPost(
    val post: MonitoredWrittenPost,
    val detectedAt: Long,
    val deletedCommentIds: MutableSet<String> = mutableSetOf()
)

/**
 * 취소될 때까지 내 새 글을 감시한다. 켠 시점 이후에 쓴 글만 [CommentMonitorConfig.monitorMinutes] 동안 본다.
 * 글마다 차단 단어가 든 댓글을 지운다.
 * [nowMillis]는 단조 시계여야 한다 (Android는 잠자는 동안에도 흐르는 elapsedRealtime).
 */
suspend fun runCommentMonitor(config: CommentMonitorConfig, nowMillis: () -> Long) {
    val literalKeywords = config.keywords.filterNot { it.startsWith(COMMENT_CLEANER_REGEX_PREFIX) }
    val patterns = runCatching { commentCleanerPatterns(config.keywords) }.getOrDefault(emptyList())
    val watchedPosts = linkedMapOf<String, WatchedPost>()
    var latestGallogNo: Long? = null
    while (currentCoroutineContext().isActive) {
        val firstPage = config.cleaner.getRecentWrittenPosts()
        if (firstPage == null) {
            delay(config.intervalSeconds * 1_000L)
            continue
        }
        if (latestGallogNo == null) {
            latestGallogNo = firstPage.maxOfOrNull(MonitoredWrittenPost::gallogNo) ?: 0L
        } else {
            val previousGallogNo = latestGallogNo
            var page = 1
            var posts: List<MonitoredWrittenPost> = firstPage
            while (posts.isNotEmpty()) {
                val reachedKnownPost = posts.any { it.gallogNo <= previousGallogNo }
                posts.filter { it.gallogNo > previousGallogNo }.forEach { post ->
                    watchedPosts[post.key] = WatchedPost(post, nowMillis())
                }
                if (reachedKnownPost) break
                delay(Cleaner.POST_REQUEST_DELAY)
                posts = config.cleaner.getRecentWrittenPosts(++page) ?: break
            }
            latestGallogNo = maxOf(
                previousGallogNo,
                firstPage.maxOfOrNull(MonitoredWrittenPost::gallogNo) ?: previousGallogNo
            )
        }

        val expiresAfter = config.monitorMinutes * 60_000L
        val now = nowMillis()
        watchedPosts.entries.removeAll { now - it.value.detectedAt >= expiresAfter }
        // 새 글을 몰아 썼을 때 디시에 요청이 몰리지 않게 글 사이를 띄운다.
        watchedPosts.values.forEachIndexed { index, watched ->
            if (index > 0) delay(Cleaner.POST_REQUEST_DELAY)
            val comments = config.cleaner.getMonitoredPostComments(watched.post) ?: return@forEachIndexed
            comments.filter { comment ->
                comment.id !in watched.deletedCommentIds &&
                    commentContainsBlockedKeyword(comment.content, literalKeywords, patterns)
            }.forEach { comment ->
                if (config.cleaner.deleteMonitoredPostComment(watched.post, comment.id)) {
                    watched.deletedCommentIds += comment.id
                }
            }
        }
        delay(config.intervalSeconds * 1_000L)
    }
}
