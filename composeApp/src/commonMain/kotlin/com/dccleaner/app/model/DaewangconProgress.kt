package com.dccleaner.app.model

data class DaewangconProgress(
    val postCount: Int,
    val commentCount: Int,
    val requiredPostCount: Int = DaewangconDefaults.DEFAULT_REQUIRED_POST_COUNT,
    val requiredCommentCount: Int = DaewangconDefaults.DEFAULT_REQUIRED_COMMENT_COUNT,
    val durationHours: Int = 0,
    val status: String = ""
) {
    val remainingPostCount: Int
        get() = (requiredPostCount - postCount).coerceAtLeast(0)

    val remainingCommentCount: Int
        get() = (requiredCommentCount - commentCount).coerceAtLeast(0)

    val requirementsMet: Boolean
        get() = remainingPostCount == 0 && remainingCommentCount == 0
}
