package com.dccleaner.app.model

data class RemoteBannerConfig(
    val id: String,
    val type: String,
    val text: String,
    val actionText: String?,
    val actionUrl: String?,
    val backgroundColor: String?,
    val textColor: String?,
    val dismissible: Boolean
)
