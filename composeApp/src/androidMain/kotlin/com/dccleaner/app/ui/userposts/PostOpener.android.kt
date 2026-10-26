package com.dccleaner.app.ui.userposts

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.dccleaner.app.ranking.RankingGallery
import com.dccleaner.app.userposts.dcAppUri
import com.dccleaner.app.userposts.mobilePostUrl

private const val DC_APP_PACKAGE = "com.dcinside.app.android"

/** 디시 앱이 있으면 앱으로 열고, 없거나 미니 갤러리면 모바일 글 주소를 연다(앱의 링크 처리기가 인앱 브라우저로 띄운다). */
@Composable
internal actual fun rememberPostOpener(): (RankingGallery, Long) -> Unit {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    return remember(context, uriHandler) {
        { gallery, no ->
            val opened = gallery.dcAppUri(no)?.let { uri ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(DC_APP_PACKAGE)
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 앱이 없으면 ActivityNotFoundException, 디시 앱이 이 화면을 열어 주지 않으면 SecurityException. 어느 쪽이든 웹으로 연다.
                runCatching { context.startActivity(intent) }.isSuccess
            } ?: false
            if (!opened) runCatching { uriHandler.openUri(gallery.mobilePostUrl(no)) }
        }
    }
}
