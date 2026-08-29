package com.dccleaner.app.network

import com.dccleaner.app.model.CollectedPost
import com.dccleaner.app.model.DaewangconProgress
import com.dccleaner.app.model.DeleteResult
import com.dccleaner.app.model.PostDetails
import com.dccleaner.app.model.PostListResult
import com.dccleaner.app.model.WriteResult

interface CleanerPort {
    fun getPostListSize(): Int
    fun getFirstPost(): String?
    fun removeFirstPost()
    fun clearPostData()
    fun exportCollectedPosts(postNumbers: List<String>): List<CollectedPost>
    fun importCollectedPosts(posts: List<CollectedPost>)
    fun getPostUrl(postNo: String): String?
    fun isPostDccon(postNo: String): Boolean
    fun getPostText(postNo: String): String
    fun getUserId(): String
    fun getPostAgeDays(postNo: String): Long?
    fun has2CaptchaKey(): Boolean
    fun restore2CaptchaKey(key: String)
    fun resetCaptchaState()
    fun getPostList(): List<String>

    suspend fun deletePost(postNo: String, postType: String, solveCaptcha: Boolean = false): DeleteResult
    suspend fun getPageCount(gno: String, postType: String): Int
    suspend fun getPostList(gno: String, postType: String, page: Int): PostListResult
    suspend fun getPostDetails(postUrl: String): PostDetails?
    suspend fun getPostWriterUid(postUrl: String): String?
    suspend fun writePost(galleryId: String, subject: String, content: String): WriteResult
    suspend fun writeComment(galleryId: String, postNo: String, content: String): WriteResult
    suspend fun getDaewangconProgress(): DaewangconProgress?
    suspend fun recordCleanerRunGuestbookLog(
        deletedPosts: Int,
        deletedComments: Int,
        onProgress: (String) -> Unit = {}
    ): Boolean
    suspend fun setBigcon(): WriteResult
}
