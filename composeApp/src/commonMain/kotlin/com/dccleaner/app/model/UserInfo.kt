package com.dccleaner.app.model

data class UserInfo(
    val nickname: String,
    val article_num: String,
    val comment_num: String,
    val userId: String = ""
)
