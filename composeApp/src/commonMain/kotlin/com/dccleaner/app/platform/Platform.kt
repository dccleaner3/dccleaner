package com.dccleaner.app.platform

import com.dccleaner.app.model.RemoteBannerConfig
import com.dccleaner.app.model.ProxyCleanerPricing

enum class PlatformFamily {
    Android,
    Desktop
}

data class RuntimePlatform(
    val family: PlatformFamily,
    val name: String,
    val appDataDescription: String
)

interface ExternalNavigator {
    fun openUrl(url: String): Boolean
}

interface SecureAccountRepository {
    fun getSavedAccountIds(): List<String>
}

expect val currentPlatform: RuntimePlatform

expect fun generateDeleteTaskId(): String

expect fun currentTimeMillis(): Long

expect suspend fun fetchRemoteBannerConfig(): RemoteBannerConfig?

expect suspend fun fetchProxyCleanerPricing(): ProxyCleanerPricing
