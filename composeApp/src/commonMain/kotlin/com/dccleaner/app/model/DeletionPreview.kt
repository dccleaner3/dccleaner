package com.dccleaner.app.model

data class DeletionPreviewItem(
    val galleryName: String,
    val postNo: String,
    val text: String
)

data class DeletionPreview(
    val inspectedCount: Int,
    val deleteCount: Int,
    val excludedCount: Int,
    val items: List<DeletionPreviewItem>
)
