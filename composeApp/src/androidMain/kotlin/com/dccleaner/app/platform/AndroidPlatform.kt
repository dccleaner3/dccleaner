package com.dccleaner.app.platform

import com.dccleaner.app.model.RemoteBannerConfig
import com.dccleaner.app.remote.RemoteBannerFetcher
import com.dccleaner.app.model.ProxyCleanerPricing
import com.dccleaner.app.remote.ProxyCleanerPricingFetcher
import java.util.UUID

actual val currentPlatform: RuntimePlatform = RuntimePlatform(
    family = PlatformFamily.Android,
    name = "Android",
    appDataDescription = "Android app-private storage and encrypted SharedPreferences"
)

actual fun generateDeleteTaskId(): String = UUID.randomUUID().toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual suspend fun fetchRemoteBannerConfig(localJson: String?): RemoteBannerConfig? =
    if (localJson != null) {
        RemoteBannerFetcher.parse(localJson)
    } else {
        RemoteBannerFetcher.fetchRemote()
    }

actual suspend fun fetchProxyCleanerPricing(): ProxyCleanerPricing? =
    ProxyCleanerPricingFetcher.fetchRemote()
