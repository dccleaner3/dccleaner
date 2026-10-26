package com.dccleaner.app.ui.ranking

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.dccleaner.app.ranking.clearEdgeBackground
import com.dccleaner.app.ranking.rankingFontBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

@Composable
internal actual fun rememberRankingPlatform(): RankingPlatform = DesktopRankingPlatform

private object DesktopRankingPlatform : RankingPlatform {
    override val keepsRunningInBackground = "다른 화면으로 가도 계속 만들어요 · 앱을 닫으면 멈춰요"

    override fun onStarted() = Unit

    override fun encodePng(image: ImageBitmap): ByteArray =
        requireNotNull(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)).bytes

    override val rankingFont: FontFamily by lazy {
        FontFamily(Font("DCCleanerRanking-Medium", rankingFontBytes(), FontWeight.Medium))
    }

    override fun decodeIcon(bytes: ByteArray): ImageBitmap? {
        val source = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
        val (w, h) = source.width to source.height
        val pixels = source.getRGB(0, 0, w, h, null, 0, w)
        clearEdgeBackground(pixels, w, h)
        return BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, w, h, pixels, 0, w) }.toComposeImageBitmap()
    }

    override suspend fun saveImages(images: Sequence<Pair<String, ByteArray>>): String = withContext(Dispatchers.IO) {
        val home = File(System.getProperty("user.home"))
        val downloads = File(home, "Downloads").takeIf(File::isDirectory) ?: home
        val dir = File(downloads, "DCCleaner 갤창랭킹").apply { mkdirs() }
        // 수정한 날짜로 늘어놓아도 같은 초에 묶여 섞이지 않게 1초씩 벌린다.
        val start = System.currentTimeMillis()
        val count = images.onEachIndexed { i, (name, bytes) ->
            File(dir, name).apply { writeBytes(bytes) }.setLastModified(start + i * 1_000L)
        }.count()
        "${dir.absolutePath}에 ${count}장 저장했어요"
    }
}
