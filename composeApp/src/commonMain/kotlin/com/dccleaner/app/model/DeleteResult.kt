package com.dccleaner.app.model

sealed class DeleteResult {
    data class Success(val data: Map<String, Any?>) : DeleteResult()
    data class Excluded(val message: String) : DeleteResult()
    object Failed : DeleteResult()
    object Blocked : DeleteResult()
    data class Error(val message: String) : DeleteResult()
}
