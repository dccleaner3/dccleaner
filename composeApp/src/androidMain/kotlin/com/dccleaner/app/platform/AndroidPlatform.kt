package com.dccleaner.app.platform

import com.dccleaner.composeapp.BuildConfig
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

actual suspend fun fetchRemoteBannerConfig(): RemoteBannerConfig? =
    if (BuildConfig.DEBUG) {
        RemoteBannerFetcher.parse(BuildConfig.LOCAL_AD_JSON)
    } else {
        RemoteBannerFetcher.fetchRemote()
    }

actual suspend fun fetchProxyCleanerPricing(): ProxyCleanerPricing =
    ProxyCleanerPricingFetcher.fetchRemote()
