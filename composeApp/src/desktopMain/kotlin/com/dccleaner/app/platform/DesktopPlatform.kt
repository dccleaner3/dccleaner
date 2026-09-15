package com.dccleaner.app.platform

import com.dccleaner.app.model.RemoteBannerConfig
import com.dccleaner.app.model.ProxyCleanerPricing
import com.dccleaner.app.remote.RemoteBannerFetcher
import com.dccleaner.app.remote.ProxyCleanerPricingFetcher
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.UUID

actual val currentPlatform: RuntimePlatform = RuntimePlatform(
    family = PlatformFamily.Desktop,
    name = "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
    appDataDescription = desktopAppDataDescription()
)

actual fun generateDeleteTaskId(): String = UUID.randomUUID().toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual suspend fun fetchRemoteBannerConfig(): RemoteBannerConfig? {
    val localConfig = File("docs/ad.json").takeIf(File::isFile)?.readText()
    return if (localConfig != null) {
        RemoteBannerFetcher.parse(localConfig)
    } else {
        RemoteBannerFetcher.fetchRemote()
    }
}

actual suspend fun fetchProxyCleanerPricing(): ProxyCleanerPricing =
    ProxyCleanerPricingFetcher.fetchRemote()

object DesktopExternalNavigator : ExternalNavigator {
    override fun openUrl(url: String): Boolean = runCatching {
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
        desktop.browse(URI(url))
        true
    }.getOrDefault(false)
}

private fun desktopAppDataDescription(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("mac") -> "~/Library/Application Support/DCCleaner"
        osName.contains("win") -> "%APPDATA%\\DCCleaner"
        else -> "~/.local/share/dccleaner"
    }
}
