package com.dccleaner.app.ui.userposts

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.dccleaner.app.ranking.RankingGallery
import com.dccleaner.app.userposts.pcPostUrl

/** PC 글 주소를 기본 브라우저로 연다(도움말 링크와 같은 방법). */
@Composable
internal actual fun rememberPostOpener(): (RankingGallery, Long) -> Unit {
    val uriHandler = LocalUriHandler.current
    return { gallery, no -> runCatching { uriHandler.openUri(gallery.pcPostUrl(no)) } }
}
