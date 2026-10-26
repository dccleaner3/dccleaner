package com.dccleaner.app.model

sealed class PostListResult {
    object Blocked : PostListResult()
    object Failed : PostListResult()
    data class Success(
        val posts: List<String>,
        val requestedGalleryMatched: Boolean = true,
        val totalCount: Int? = null,
        val collectedPosts: List<CollectedPost> = emptyList()
    ) : PostListResult() {
        fun requireRequestedGallery(): Success = apply {
            check(requestedGalleryMatched) { "선택한 갤러리의 응답인지 확인하지 못해 삭제를 중단했습니다." }
        }
    }
}
