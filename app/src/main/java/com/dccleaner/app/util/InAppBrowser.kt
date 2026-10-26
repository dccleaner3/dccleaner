package com.dccleaner.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.UriHandler

/**
 * 도움말·처리방침 같은 문서 링크와 디시 글 주소는 앱 위에 뜨는 브라우저 탭(Custom Tabs)으로 연다.
 * 그냥 열면 GitHub 앱이 github.io·github.com 주소를 가로채 문서 대신 앱 화면이 뜬다.
 * 디시 글은 반고닉 글 찾기가 디시 앱을 먼저 시도하고, 앱이 없을 때만 여기로 온다.
 * 다운로드·카카오톡 같은 나머지 링크는 지금처럼 시스템에 맡긴다.
 */
fun openLink(context: Context, url: String, darkTheme: Boolean = false) {
    val uri = Uri.parse(url)
    if (!isDocumentLink(uri)) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        return
    }
    // 패키지를 브라우저로 고정해야 GitHub 앱이 끼어들지 않는다.
    val browser = CustomTabsClient.getPackageName(context, emptyList())
    if (browser != null) {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setColorScheme(if (darkTheme) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
            .build()
            .apply { intent.setPackage(browser) }
            .launchUrl(context, uri)
        return
    }
    // Custom Tabs를 지원하는 브라우저가 없으면 기본 브라우저로 연다.
    val browserIntent = Intent(Intent.ACTION_VIEW, uri)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .apply { selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER) }
    try {
        context.startActivity(browserIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

internal fun isDocumentLink(uri: Uri): Boolean = when (uri.host) {
    "dccleaner3.github.io" -> true
    "github.com" -> uri.path.orEmpty().contains("/blob/")
    "m.dcinside.com", "gall.dcinside.com" -> true
    else -> false
}

/** Compose 화면의 openUri가 [openLink]를 거치게 한다. */
class InAppUriHandler(private val context: Context, private val darkTheme: () -> Boolean) : UriHandler {
    override fun openUri(uri: String) = openLink(context, uri, darkTheme())
}
