package com.dccleaner.app.model

data class PostDetails(
    val recommendCount: Int?,
    val commentCount: Int?,
    val viewCount: Int?
) {
    fun hasCountsRequiredBy(
        recommendFilterEnabled: Boolean,
        commentFilterEnabled: Boolean,
        viewFilterEnabled: Boolean
    ): Boolean =
        (!recommendFilterEnabled || recommendCount != null) &&
                (!commentFilterEnabled || commentCount != null) &&
                (!viewFilterEnabled || viewCount != null)
}
