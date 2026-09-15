package com.dccleaner.app.runtime

import com.dccleaner.app.model.DeletionPreview
import com.dccleaner.app.model.DeletionPreviewItem
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.CollectedPost
import com.dccleaner.app.model.PostListResult
import com.dccleaner.app.network.CleanerPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

suspend fun previewDeletion(
    cleaner: CleanerPort,
    task: DeleteTaskProgress,
    sampleLimit: Int = 5,
    onUpdate: (DeletionPreview) -> Unit = {}
): DeletionPreview {
    val normalized = task.normalizedForExecution()
    val postRegex = normalized.postContentRegex.toDeletionRegex(
        enabled = normalized.deleteType == "posting" && normalized.postContentFilterEnabled,
        label = "글 제목"
    )
    val commentRegex = normalized.commentRegexFilter.toDeletionRegex(
        enabled = normalized.deleteType == "comment" && normalized.commentContentFilterEnabled,
        label = "댓글 내용"
    )
    var inspected = 0
    var deleteCount = 0
    val samples = mutableListOf<DeletionPreviewItem>()

    fun currentPreview() = DeletionPreview(
        inspectedCount = inspected,
        deleteCount = deleteCount,
        excludedCount = inspected - deleteCount,
        items = samples.toList()
    )

    val galleryId = normalized.selectedGalleries.firstOrNull() ?: return currentPreview()
    val galleryName = normalized.galleryMap[galleryId] ?: "첫 번째 갤러리"
    val collected = try {
        when (val result = cleaner.getPostList(galleryId, normalized.deleteType, 1, cachePostData = false)) {
            is PostListResult.Success -> result.collectedPosts
            is PostListResult.Blocked, PostListResult.Failed -> error("$galleryName 미리보기를 불러오지 못했습니다.")
        }
    } catch (e: CancellationException) {
        throw e
    }
    for (post in collected.asReversed()) {
        inspected++
        if (matchesDeletionFilters(cleaner, normalized, post, postRegex, commentRegex)) {
            deleteCount++
            samples += DeletionPreviewItem(
                galleryName = galleryName,
                postNo = post.postNo,
                text = post.text.ifBlank { "번호 ${post.postNo}" }
            )
            onUpdate(currentPreview())
            if (samples.size >= sampleLimit) break
        } else {
            onUpdate(currentPreview())
        }
    }

    return currentPreview()
}

private suspend fun matchesDeletionFilters(
    cleaner: CleanerPort,
    task: DeleteTaskProgress,
    post: CollectedPost,
    postRegex: Regex?,
    commentRegex: Regex?
): Boolean {
    if (task.minPostAgeDaysToDelete >= 0) {
        val ageDays = post.date
            ?.let { date -> runCatching { LocalDate.parse(date) }.getOrNull() }
            ?.let { ChronoUnit.DAYS.between(it, LocalDate.now(ZoneId.of("Asia/Seoul"))) }
        if (ageDays == null || ageDays < task.minPostAgeDaysToDelete) return false
    }

    val regex = if (task.deleteType == "posting") postRegex else commentRegex
    if (regex != null && !regex.containsMatchIn(post.text)) return false

    if (task.deleteType == "posting" &&
        (task.minRecommendToKeep >= 0 || task.minCommentToKeep >= 0 || task.minViewToKeep >= 0)
    ) {
        val postUrl = post.postUrl ?: return false
        delay(com.dccleaner.app.network.Cleaner.POST_REQUEST_DELAY)
        val details = cleaner.getPostDetails(postUrl) ?: return false
        if (!details.hasCountsRequiredBy(
                task.recommendFilterEnabled,
                task.commentFilterEnabled,
                task.viewFilterEnabled
            )
        ) return false
        if (task.minRecommendToKeep >= 0 && details.recommendCount?.let { it >= task.minRecommendToKeep } == true) return false
        if (task.minCommentToKeep >= 0 && details.commentCount?.let { it >= task.minCommentToKeep } == true) return false
        if (task.minViewToKeep >= 0 && details.viewCount?.let { it >= task.minViewToKeep } == true) return false
    }

    if (task.deleteType == "comment" && (task.myPostFilterEnabled || task.dcconOnlyFilterEnabled)) {
        if (task.dcconOnlyFilterEnabled && post.isDccon) return true
        if (!task.myPostFilterEnabled) return false
        val postUrl = post.postUrl ?: return false
        delay(com.dccleaner.app.network.Cleaner.POST_REQUEST_DELAY)
        return cleaner.getPostWriterUid(postUrl) == cleaner.getUserId()
    }

    return true
}

internal fun String.toDeletionRegex(enabled: Boolean, label: String): Regex? {
    if (!enabled) return null
    if (isBlank()) throw IllegalArgumentException("$label 정규식을 입력해 주세요.")
    return runCatching {
        val match = Regex("^/(.+)/([a-zA-Z]*)$").find(this)
        if (match == null) return@runCatching Regex(this)
        val (pattern, flags) = match.destructured
        val options = buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        }
        Regex(pattern, options)
    }.getOrElse { throw IllegalArgumentException("$label 정규식이 올바르지 않습니다.") }
}
