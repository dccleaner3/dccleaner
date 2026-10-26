package com.dccleaner.app.ui.ranking

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.dccleaner.app.ranking.GalleryRankingService
import com.dccleaner.app.ranking.clearEdgeBackground
import com.dccleaner.app.ranking.rankingFontBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
internal actual fun rememberRankingPlatform(): RankingPlatform {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidRankingPlatform(context) }
}

private class AndroidRankingPlatform(private val context: Context) : RankingPlatform {
    override val keepsRunningInBackground = "다른 화면으로 가거나 앱을 내려도 계속 만들어요"

    override fun onStarted() {
        ContextCompat.startForegroundService(context, Intent(context, GalleryRankingService::class.java))
    }

    override fun encodePng(image: ImageBitmap): ByteArray =
        ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    // 안드로이드 Font는 파일에서만 읽어서 리소스를 캐시 폴더에 한 번 풀어 둔다.
    override val rankingFont: FontFamily by lazy {
        val file = File(context.cacheDir, "DCCleanerRanking-Medium.ttf")
        val bytes = rankingFontBytes()
        // 업데이트로 글꼴이 바뀌었거나 쓰다 죽어 반쪽만 남았으면 다시 쓴다. 임시 파일에 다 쓴 뒤 이름을 바꾼다.
        if (file.length() != bytes.size.toLong()) {
            File(context.cacheDir, "DCCleanerRanking-Medium.ttf.tmp").apply { writeBytes(bytes) }.renameTo(file)
        }
        FontFamily(Font(file, FontWeight.Medium))
    }

    override fun decodeIcon(bytes: ByteArray): ImageBitmap? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        clearEdgeBackground(pixels, bitmap.width, bitmap.height)
        return Bitmap.createBitmap(pixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    override suspend fun saveImages(images: Sequence<Pair<String, ByteArray>>): String = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            // 사진 앱은 찍은 시각으로 늘어놓는데, 같은 초에 저장한 장들은 순서가 뒤섞인다. 1초씩 벌려 저장한 순서를 지킨다.
            val start = System.currentTimeMillis()
            val count = images.onEachIndexed { i, (name, bytes) ->
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                    put(MediaStore.Images.Media.DATE_TAKEN, start + i * 1_000L)
                }
                val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
                // 다 쓰기 전에는 갤러리에 안 보이게 두고, 실패하면 반쯤 쓴 사진을 지운다.
                runCatching { requireNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) } }
                    .onFailure { resolver.delete(uri, null, null); throw it }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }.count()
            "사진 앱의 '$ALBUM' 앨범에 ${count}장 저장했어요"
        } else {
            // Android 9 이하는 권한 없이 쓸 수 있는 앱 전용 사진 폴더에 둔다.
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), ALBUM).apply { mkdirs() }
            val start = System.currentTimeMillis()
            val count = images.onEachIndexed { i, (name, bytes) ->
                File(dir, name).apply { writeBytes(bytes) }.setLastModified(start + i * 1_000L)
            }.count()
            "${dir.absolutePath}에 ${count}장 저장했어요"
        }
    }

    private companion object {
        const val ALBUM = "DCCleaner"
    }
}
