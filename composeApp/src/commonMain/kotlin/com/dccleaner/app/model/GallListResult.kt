package com.dccleaner.app.model

sealed class GallListResult {
    object Blocked : GallListResult()
    object SessionExpired : GallListResult()
    data class Success(val galleries: Map<String, String>) : GallListResult()
}
