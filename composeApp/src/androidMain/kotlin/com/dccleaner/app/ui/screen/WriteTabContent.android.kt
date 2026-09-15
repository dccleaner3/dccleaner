package com.dccleaner.app.ui.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors
import com.dccleaner.app.ui.theme.dccleanerSwitchColors
import com.dccleaner.app.music.createNowPlayingImageBase64
import com.dccleaner.app.music.downloadFallbackImageBase64
import com.dccleaner.app.music.getCurrentPlayingTrack
import com.dccleaner.app.music.getEnabledMusicPlatformIds
import com.dccleaner.app.music.getMusicBackgroundColor
import com.dccleaner.app.music.getMusicBridgeStatus
import com.dccleaner.app.music.getMusicAttachmentEnabled
import com.dccleaner.app.music.getMusicFallbackImageUrl
import com.dccleaner.app.music.musicBridgeDownloadIntent
import com.dccleaner.app.music.musicBridgeLaunchIntent
import com.dccleaner.app.music.musicPlatformOptions
import com.dccleaner.app.music.saveEnabledMusicPlatformIds
import com.dccleaner.app.music.saveMusicAttachmentEnabled
import com.dccleaner.app.music.saveMusicBackgroundColor
import com.dccleaner.app.music.saveMusicFallbackImageUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

private const val LOG_TAG = "DcWriteWebView"
private const val IMAGE_UPLOAD_URL = "https://mupload.dcinside.com/upload_img.php"
private const val WRITE_SUBMIT_URL = "https://mupload.dcinside.com/write_new.php"
private const val MIRROR_STORAGE_KEY = "dccleaner.pendingWriteMirror"
private const val MAX_PARALLEL_WRITE_WEBVIEWS = 10

private data class WriteTarget(val url: String, val galleryId: String)
private data class UserImageAttachment(
    val fileName: String,
    val mimeType: String,
    val base64: String
)
private data class CompletedWriteBatch(
    val mirrorPayload: String?,
    val userImageAttachments: List<UserImageAttachment>
)
private data class ParallelWriteSession(
    val id: Long,
    val mirrorPayload: String,
    val userImageAttachments: List<UserImageAttachment>,
    val workerQueues: List<List<String>>
)

private const val COMMENT_CLEANER_PREFS = "comment_cleaner_settings"
private const val COMMENT_CLEANER_KEYWORDS = "keywords"
private const val COMMENT_CLEANER_INTERVAL = "interval_seconds"
private const val COMMENT_CLEANER_DURATION = "monitor_minutes"
private const val COMMENT_CLEANER_ENABLED = "enabled"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentCleanerSettings(
    uiColors: UiColors,
    running: Boolean,
    keywords: String,
    intervalSeconds: String,
    monitorMinutes: String,
    error: String?,
    onKeywordsChange: (String) -> Unit,
    onIntervalChange: (String) -> Unit,
    onDurationChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val keywordCount = keywords.lineSequence().count { it.isNotBlank() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        border = BorderStroke(1.dp, uiColors.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "댓글 자동 정리",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = uiColors.primary
                )
                Text(
                    if (running) "실행 중 · 키워드 ${keywordCount}개" else "사용 안 함 · 키워드 ${keywordCount}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { showSheet = true }) {
                Text("설정", color = uiColors.primary)
            }
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden }
        )
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "댓글 자동 정리 설정",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "새로 작성한 글만 설정 시간 동안 감시합니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }
                Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (running) "댓글 자동 정리 실행 중" else "댓글 자동 정리 사용",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = uiColors.primary
                    )
                    Text(
                        if (running) "설정을 변경하려면 먼저 꺼주세요" else "켜는 시점의 기존 글은 처리하지 않습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = running,
                    onCheckedChange = onEnabledChange,
                    colors = dccleanerSwitchColors(uiColors)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedTextField(
                    value = intervalSeconds,
                    onValueChange = onIntervalChange,
                    enabled = !running,
                    label = { Text("확인 간격(초)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = dccleanerOutlinedTextFieldColors(uiColors)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = monitorMinutes,
                    onValueChange = onDurationChange,
                    enabled = !running,
                    label = { Text("감시 시간(분)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = dccleanerOutlinedTextFieldColors(uiColors)
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = keywords,
                onValueChange = onKeywordsChange,
                enabled = !running,
                label = { Text("차단 키워드 (줄바꿈으로 구분)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                colors = dccleanerOutlinedTextFieldColors(uiColors)
            )
            if (error != null) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    border = BorderStroke(1.dp, uiColors.primary)
                ) {
                    Text("완료", color = uiColors.primary)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun commentCleanerPreferences(context: Context) =
    context.getSharedPreferences(COMMENT_CLEANER_PREFS, Context.MODE_PRIVATE)

private fun getCommentCleanerKeywords(context: Context): String =
    commentCleanerPreferences(context).getString(COMMENT_CLEANER_KEYWORDS, "").orEmpty()

private fun getCommentCleanerInterval(context: Context): Int =
    commentCleanerPreferences(context).getInt(COMMENT_CLEANER_INTERVAL, 60)

private fun getCommentCleanerDuration(context: Context): Int =
    commentCleanerPreferences(context).getInt(COMMENT_CLEANER_DURATION, 10)

private fun getCommentCleanerEnabled(context: Context): Boolean =
    commentCleanerPreferences(context).getBoolean(COMMENT_CLEANER_ENABLED, false)

private fun saveCommentCleanerEnabled(context: Context, enabled: Boolean) {
    commentCleanerPreferences(context).edit().putBoolean(COMMENT_CLEANER_ENABLED, enabled).apply()
}

data class SavedCommentCleanerSettings(
    val enabled: Boolean,
    val keywords: List<String>,
    val intervalSeconds: Int,
    val monitorMinutes: Int
)

fun getSavedCommentCleanerSettings(context: Context) = SavedCommentCleanerSettings(
    enabled = getCommentCleanerEnabled(context),
    keywords = getCommentCleanerKeywords(context).lineSequence().map(String::trim)
        .filter(String::isNotEmpty).distinct().toList(),
    intervalSeconds = getCommentCleanerInterval(context).coerceAtLeast(5),
    monitorMinutes = getCommentCleanerDuration(context).coerceAtLeast(1)
)

private fun saveCommentCleanerSettings(
    context: Context,
    keywords: String,
    intervalSeconds: Int,
    monitorMinutes: Int
) {
    commentCleanerPreferences(context).edit()
        .putString(COMMENT_CLEANER_KEYWORDS, keywords)
        .putInt(COMMENT_CLEANER_INTERVAL, intervalSeconds)
        .putInt(COMMENT_CLEANER_DURATION, monitorMinutes)
        .apply()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WriteTabContent(
    onRequestCookies: () -> List<String>,
    commentCleanerRunning: Boolean,
    onStartCommentCleaner: (List<String>, Int, Int) -> Unit,
    onStopCommentCleaner: () -> Unit,
    uiColors: UiColors,
    modifier: Modifier
) {
    val context = LocalContext.current
    var writeUrls by remember { mutableStateOf(getSavedWriteGalleryUrls(context)) }
    var selectedWriteUrls by rememberSaveable { mutableStateOf(writeUrls) }
    val activeWriteUrls = remember(writeUrls, selectedWriteUrls) {
        writeUrls.filter(selectedWriteUrls::contains)
    }
    var musicAttachmentEnabled by remember { mutableStateOf(getMusicAttachmentEnabled(context)) }
    var enabledMusicPlatformIds by remember {
        mutableStateOf(getEnabledMusicPlatformIds(context))
    }
    var musicBackgroundColor by remember { mutableStateOf(getMusicBackgroundColor(context)) }
    var musicFallbackImageUrl by remember { mutableStateOf(getMusicFallbackImageUrl(context)) }
    var musicBridgeStatus by remember { mutableStateOf(getMusicBridgeStatus(context)) }
    var showFullScreenWriter by remember { mutableStateOf(false) }
    var preparingWriter by remember { mutableStateOf(false) }
    var pendingAttachmentImageBase64 by remember { mutableStateOf<String?>(null) }
    var parallelWriteSession by remember { mutableStateOf<ParallelWriteSession?>(null) }
    var completedParallelWorkers by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var visibleWriterWebView by remember { mutableStateOf<WebView?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val musicBridgeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        musicBridgeStatus = getMusicBridgeStatus(context)
    }

    var blockedKeywords by remember { mutableStateOf(getCommentCleanerKeywords(context)) }
    var commentCheckInterval by remember { mutableStateOf(getCommentCleanerInterval(context).toString()) }
    var commentMonitorMinutes by remember { mutableStateOf(getCommentCleanerDuration(context).toString()) }
    var commentCleanerEnabled by rememberSaveable { mutableStateOf(getCommentCleanerEnabled(context)) }
    var commentCleanerError by remember { mutableStateOf<String?>(null) }

    val finishWriting: () -> Unit = {
        parallelWriteSession = null
        completedParallelWorkers = emptySet()
        showFullScreenWriter = false
    }

    Column(modifier = modifier) {
        CommentCleanerSettings(
            uiColors = uiColors,
            running = commentCleanerEnabled,
            keywords = blockedKeywords,
            intervalSeconds = commentCheckInterval,
            monitorMinutes = commentMonitorMinutes,
            error = commentCleanerError,
            onKeywordsChange = { blockedKeywords = it },
            onIntervalChange = { commentCheckInterval = it.filter(Char::isDigit) },
            onDurationChange = { commentMonitorMinutes = it.filter(Char::isDigit) },
            onEnabledChange = { enabled ->
                if (!enabled) {
                    commentCleanerError = null
                    commentCleanerEnabled = false
                    saveCommentCleanerEnabled(context, false)
                    onStopCommentCleaner()
                } else {
                    val keywords = blockedKeywords.lineSequence().map(String::trim)
                        .filter(String::isNotEmpty).distinct().toList()
                    val interval = commentCheckInterval.toIntOrNull()
                    val duration = commentMonitorMinutes.toIntOrNull()
                    commentCleanerError = when {
                        keywords.isEmpty() -> "차단 키워드를 하나 이상 입력해 주세요"
                        interval == null || interval < 5 -> "확인 간격은 5초 이상이어야 합니다"
                        duration == null || duration < 1 -> "감시 시간은 1분 이상이어야 합니다"
                        else -> null
                    }
                    if (commentCleanerError == null) {
                        saveCommentCleanerSettings(context, blockedKeywords, interval!!, duration!!)
                        commentCleanerEnabled = true
                        saveCommentCleanerEnabled(context, true)
                        onStartCommentCleaner(keywords, interval, duration)
                    }
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        WriteGalleryManager(
            uiColors = uiColors,
            writeUrls = writeUrls,
            selectedWriteUrls = selectedWriteUrls.toSet(),
            onSelectedWriteUrlsChange = { selectedWriteUrls = it.toList() },
            onAddUrl = { input ->
                val normalized = normalizeWriteUrl(input)
                    ?: return@WriteGalleryManager "m.dcinside.com의 갤러리 또는 write 링크를 입력해 주세요"
                if (normalized in writeUrls) {
                    return@WriteGalleryManager "이미 등록된 링크입니다"
                }
                val galleryId = writeGalleryIdFromUrl(normalized)
                if (writeUrls.any { writeGalleryIdFromUrl(it) == galleryId }) {
                    return@WriteGalleryManager "같은 갤러리가 이미 등록되어 있습니다"
                }
                val updated = writeUrls + normalized
                if (saveWriteGalleryUrls(context, updated)) {
                    writeUrls = updated
                    selectedWriteUrls = selectedWriteUrls + normalized
                    null
                } else {
                    "링크를 저장하지 못했습니다"
                }
            },
            onDeleteUrls = { selected ->
                val updated = writeUrls.filterNot(selected::contains)
                if (saveWriteGalleryUrls(context, updated)) {
                    writeUrls = updated
                    selectedWriteUrls = selectedWriteUrls.filterNot(selected::contains)
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        WriteMusicAttachmentManager(
            uiColors = uiColors,
            enabled = musicAttachmentEnabled,
            platforms = musicPlatformOptions(),
            enabledPlatformIds = enabledMusicPlatformIds,
            backgroundColorArgb = musicBackgroundColor,
            fallbackImageUrl = musicFallbackImageUrl,
            musicBridgeInstalled = musicBridgeStatus.installed,
            musicBridgeReady = musicBridgeStatus.notificationAccessGranted &&
                musicBridgeStatus.compatible,
            musicBridgeCompatible = musicBridgeStatus.compatible,
            onEnabledChange = { enabled ->
                if (saveMusicAttachmentEnabled(context, enabled)) {
                    musicAttachmentEnabled = enabled
                }
                musicBridgeStatus = getMusicBridgeStatus(context)
            },
            onPlatformEnabledChange = { platformId, enabled ->
                val updated = if (enabled) {
                    enabledMusicPlatformIds + platformId
                } else {
                    enabledMusicPlatformIds - platformId
                }
                if (saveEnabledMusicPlatformIds(context, updated)) {
                    enabledMusicPlatformIds = updated
                }
            },
            onBackgroundColorChange = { color ->
                if (saveMusicBackgroundColor(context, color)) {
                    musicBackgroundColor = color
                }
            },
            onFallbackImageUrlChange = { url ->
                if (saveMusicFallbackImageUrl(context, url)) {
                    musicFallbackImageUrl = url
                }
            },
            onInstallMusicBridge = {
                runCatching { musicBridgeLauncher.launch(musicBridgeDownloadIntent()) }
                    .onFailure { error ->
                        Log.e(LOG_TAG, "음악 연동 앱 다운로드 페이지를 열지 못했습니다.", error)
                    }
            },
            onOpenMusicBridge = {
                val launchIntent = musicBridgeLaunchIntent(context)
                if (launchIntent != null) {
                    musicBridgeLauncher.launch(launchIntent)
                } else {
                    musicBridgeStatus = getMusicBridgeStatus(context)
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (preparingWriter) return@Button
                parallelWriteSession = null
                completedParallelWorkers = emptySet()
                preparingWriter = true
                val appContext = context.applicationContext
                val attachMusic = musicAttachmentEnabled
                val selectedPlatformIds = enabledMusicPlatformIds
                val selectedBackgroundColor = musicBackgroundColor
                val selectedFallbackImageUrl = musicFallbackImageUrl
                coroutineScope.launch {
                    try {
                        val preparation = withContext(Dispatchers.IO) {
                            val bridgeStatus = getMusicBridgeStatus(appContext)
                            val track = if (
                                attachMusic &&
                                bridgeStatus.notificationAccessGranted &&
                                bridgeStatus.compatible
                            ) {
                                getCurrentPlayingTrack(appContext, selectedPlatformIds)
                            } else {
                                null
                            }
                            val attachmentImage = when {
                                !attachMusic -> null
                                track != null -> createNowPlayingImageBase64(track, selectedBackgroundColor)
                                selectedFallbackImageUrl.isNotBlank() ->
                                    downloadFallbackImageBase64(selectedFallbackImageUrl)
                                else -> null
                            }
                            Triple(
                                bridgeStatus,
                                track?.title,
                                attachmentImage
                            )
                        }
                        musicBridgeStatus = preparation.first
                        pendingAttachmentImageBase64 = preparation.third
                        Log.d(
                            LOG_TAG,
                            "Now-playing attachment prepared: enabled=$attachMusic, " +
                                "platformIds=$selectedPlatformIds, bridge=${preparation.first}, " +
                                "track=${preparation.second}, " +
                                "imagePrepared=${preparation.third != null}"
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        pendingAttachmentImageBase64 = null
                        Log.e(LOG_TAG, "Failed to prepare now-playing attachment", error)
                    } finally {
                        preparingWriter = false
                    }
                    showFullScreenWriter = true
                }
            },
            enabled = activeWriteUrls.isNotEmpty() && !preparingWriter,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary)
        ) {
            Text(if (preparingWriter) "준비 중" else "글쓰기")
        }

        if (activeWriteUrls.isEmpty()) {
            Text(
                if (writeUrls.isEmpty()) {
                    "글쓰기 갤러리 write 링크를 먼저 등록해 주세요"
                } else {
                    "글을 작성할 갤러리를 하나 이상 선택해 주세요"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        }
    }

    if (showFullScreenWriter && activeWriteUrls.isNotEmpty()) {
        Dialog(
            onDismissRequest = {
                parallelWriteSession = null
                completedParallelWorkers = emptySet()
                showFullScreenWriter = false
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false
            )
        ) {
            BackHandler {
                val webView = visibleWriterWebView
                if (
                    parallelWriteSession == null &&
                    webView?.canGoBack() == true
                ) {
                    Log.d(LOG_TAG, "System back handled by writer WebView: url=${webView.url}")
                    webView.goBack()
                } else {
                    parallelWriteSession = null
                    completedParallelWorkers = emptySet()
                    showFullScreenWriter = false
                }
            }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = uiColors.background
            ) {
                Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (parallelWriteSession == null) "글쓰기" else "글쓰기 · 병렬 처리 중",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                parallelWriteSession = null
                                completedParallelWorkers = emptySet()
                                showFullScreenWriter = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "글쓰기 닫기")
                            }
                        }

                        val activeParallelSession = parallelWriteSession
                        if (activeParallelSession == null) {
                            key(activeWriteUrls) {
                                WriteWebView(
                                    writeUrls = activeWriteUrls.take(1),
                                    attachmentImageBase64 = pendingAttachmentImageBase64,
                                    onRequestCookies = onRequestCookies,
                                    onWebViewReferenceChanged = { visibleWriterWebView = it },
                                    onAllPostsCompleted = { completedBatch ->
                                        val remainingUrls = activeWriteUrls.drop(1)
                                        val mirrorPayload = completedBatch.mirrorPayload
                                        if (remainingUrls.isEmpty() || mirrorPayload.isNullOrBlank()) {
                                            if (remainingUrls.isNotEmpty()) {
                                                Log.e(
                                                    LOG_TAG,
                                                    "First post completed without mirror payload; " +
                                                        "parallel writes were skipped"
                                                )
                                            }
                                            finishWriting()
                                        } else {
                                            val workerQueues = parallelWriteQueues(
                                                remainingUrls,
                                                MAX_PARALLEL_WRITE_WEBVIEWS
                                            )
                                            completedParallelWorkers = emptySet()
                                            parallelWriteSession = ParallelWriteSession(
                                                id = System.nanoTime(),
                                                mirrorPayload = mirrorPayload,
                                                userImageAttachments = completedBatch.userImageAttachments,
                                                workerQueues = workerQueues
                                            )
                                            Log.d(
                                                LOG_TAG,
                                                "Starting parallel mirror workers: count=${workerQueues.size}, " +
                                                    "queues=${workerQueues.map { it.size }}"
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                key(activeParallelSession.id) {
                                    activeParallelSession.workerQueues.forEachIndexed { workerIndex, queue ->
                                        WriteWebView(
                                            writeUrls = queue,
                                            attachmentImageBase64 = pendingAttachmentImageBase64,
                                            onRequestCookies = onRequestCookies,
                                            initialMirrorPayload = activeParallelSession.mirrorPayload,
                                            userImageAttachments = activeParallelSession.userImageAttachments,
                                            mirrorWorker = true,
                                            onAllPostsCompleted = {
                                                val updatedWorkers = completedParallelWorkers + workerIndex
                                                completedParallelWorkers = updatedWorkers
                                                Log.d(
                                                    LOG_TAG,
                                                    "Parallel worker completed: index=$workerIndex, " +
                                                        "completed=${updatedWorkers.size}/" +
                                                        activeParallelSession.workerQueues.size
                                                )
                                                if (updatedWorkers.size == activeParallelSession.workerQueues.size) {
                                                    finishWriting()
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(onClick = {}),
                                    color = uiColors.background
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            "최대 ${activeParallelSession.workerQueues.size}개씩 작성 중",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "완료된 작업 ${completedParallelWorkers.size} / " +
                                                activeParallelSession.workerQueues.size,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WriteWebView(
    writeUrls: List<String>,
    attachmentImageBase64: String?,
    onRequestCookies: () -> List<String>,
    initialMirrorPayload: String? = null,
    userImageAttachments: List<UserImageAttachment> = emptyList(),
    mirrorWorker: Boolean = false,
    onWebViewReferenceChanged: (WebView?) -> Unit = {},
    onAllPostsCompleted: (CompletedWriteBatch) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val nestedScrollConnection = rememberNestedScrollInteropConnection()
    val selectedUserImageAttachments = remember { mutableListOf<UserImageAttachment>() }
    val targets = remember(writeUrls) {
        writeUrls.mapNotNull { url ->
            writeGalleryIdFromUrl(url)?.let { galleryId -> WriteTarget(url, galleryId) }
        }
    }
    if (targets.isEmpty()) return

    Log.d(
        LOG_TAG,
        "Writer attachment image: prepared=${attachmentImageBase64 != null}, " +
            "base64Length=${attachmentImageBase64?.length ?: 0}"
    )
    var pendingFileCallback by remember {
        mutableStateOf<ValueCallback<Array<Uri>>?>(null)
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        val resultIntent = result.data
        val directlySelectedUris = if (result.resultCode == Activity.RESULT_OK) {
            buildList {
                resultIntent?.clipData?.let { clipData ->
                    repeat(clipData.itemCount) { index ->
                        clipData.getItemAt(index).uri?.let(::add)
                    }
                }
                resultIntent?.data?.let(::add)
            }.distinct()
        } else {
            emptyList()
        }
        val parsedUris = if (directlySelectedUris.isEmpty()) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, resultIntent)
                .orEmpty()
                .toList()
        } else {
            emptyList()
        }
        val uris = (directlySelectedUris + parsedUris).distinct().toTypedArray()

        if (result.resultCode == Activity.RESULT_OK) {
            val resultFlags = resultIntent?.flags ?: 0
            val canPersistReadPermission =
                resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0 &&
                    resultFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
            if (canPersistReadPermission) {
                uris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (error: SecurityException) {
                        Log.w(LOG_TAG, "Could not persist read permission for $uri", error)
                    }
                }
            }
        }
        Log.d(
            LOG_TAG,
            "File chooser result: resultCode=${result.resultCode}, uriCount=${uris.size}, " +
                "directUriCount=${directlySelectedUris.size}, parsedUriCount=${parsedUris.size}, " +
                "dataUri=${resultIntent?.data}, clipItemCount=${resultIntent?.clipData?.itemCount ?: 0}, " +
                "intentFlags=${resultIntent?.flags ?: 0}, callbackPresent=${callback != null}"
        )
        uris.forEachIndexed { index, uri ->
            Log.d(LOG_TAG, "File chooser uri[$index]: ${describeUri(context, uri)}")
        }
        coroutineScope.launch {
            if (!mirrorWorker && uris.isNotEmpty()) {
                val capturedAttachments = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> readUserImageAttachment(context, uri) }
                }
                selectedUserImageAttachments += capturedAttachments
                Log.d(
                    LOG_TAG,
                    "User images retained for mirror uploads: selected=${uris.size}, " +
                        "captured=${capturedAttachments.size}, " +
                        "total=${selectedUserImageAttachments.size}"
                )
            }
            callback?.onReceiveValue(uris.takeIf { it.isNotEmpty() })
        }
    }

    AndroidView(
        modifier = modifier.nestedScroll(nestedScrollConnection),
        factory = { viewContext ->
            Log.d(LOG_TAG, "Creating WebView; targets=${targets.map(WriteTarget::url)}")
            WebView(viewContext).apply {
                ViewCompat.setNestedScrollingEnabled(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = true
                isVerticalScrollBarEnabled = true

                val injectedWriteTargets = mutableSetOf<Int>()
                var activeTargetIndex: Int? = null
                var submittedTargetIndex: Int? = null

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        Log.d(LOG_TAG, "Page started: $url")
                        if (url.startsWith(WRITE_SUBMIT_URL)) {
                            submittedTargetIndex = activeTargetIndex
                            Log.d(
                                LOG_TAG,
                                "Final write submission observed: targetIndex=$submittedTargetIndex"
                            )
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        Log.d(
                            LOG_TAG,
                            "Page finished: $url; writeTarget=${writeTargetIndex(url, targets)}; " +
                                "injectedTargets=$injectedWriteTargets"
                        )
                        val targetIndex = writeTargetIndex(url, targets)
                        if (targetIndex != null) {
                            if (submittedTargetIndex == targetIndex) {
                                Log.w(
                                    LOG_TAG,
                                    "Write returned to its form; submission was not treated as successful: " +
                                        "targetIndex=$targetIndex"
                                )
                                submittedTargetIndex = null
                            }
                            activeTargetIndex = targetIndex
                            if (!injectedWriteTargets.add(targetIndex)) return
                            view.evaluateJavascript(
                                writeAutomationScript(
                                    imageBase64 = attachmentImageBase64,
                                    galleryId = targets[targetIndex].galleryId,
                                    isFirstTarget = !mirrorWorker && targetIndex == 0,
                                    initialMirrorPayload = initialMirrorPayload,
                                    userImageAttachments = userImageAttachments
                                )
                            ) { result ->
                                Log.d(LOG_TAG, "Write automation evaluation result: $result")
                            }
                        } else {
                            val completedTargetIndex = submittedTargetIndex?.takeIf { submittedIndex ->
                                isCompletedPostDestination(url, targets[submittedIndex].galleryId)
                            }
                            if (completedTargetIndex != null) {
                                submittedTargetIndex = null
                                handleCompletedPostPage(
                                    view,
                                    targets,
                                    completedTargetIndex,
                                    userImageAttachments = if (mirrorWorker) {
                                        userImageAttachments
                                    } else {
                                        selectedUserImageAttachments.toList()
                                    },
                                    onAllPostsCompleted = onAllPostsCompleted
                                )
                            }
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        if (request.url.toString().startsWith(IMAGE_UPLOAD_URL)) {
                            Log.d(
                                LOG_TAG,
                                "Upload request observed: method=${request.method}, url=${request.url}, " +
                                    "headerNames=${request.requestHeaders.keys.sorted()}"
                            )
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request.url.toString().startsWith(IMAGE_UPLOAD_URL)) {
                            Log.e(
                                LOG_TAG,
                                "Upload HTTP error: status=${errorResponse.statusCode}, " +
                                    "reason=${errorResponse.reasonPhrase}"
                            )
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame || request.url.toString().startsWith(IMAGE_UPLOAD_URL)) {
                            Log.e(
                                LOG_TAG,
                                "Web resource error: mainFrame=${request.isForMainFrame}, " +
                                    "url=${request.url}, code=${error.errorCode}, description=${error.description}"
                            )
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d(
                            LOG_TAG,
                            "JS ${consoleMessage.messageLevel()} " +
                                "${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} " +
                                consoleMessage.message()
                        )
                        return true
                    }

                    override fun onShowFileChooser(
                        webView: WebView,
                        filePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams
                    ): Boolean {
                        Log.d(
                            LOG_TAG,
                            "File chooser requested: page=${webView.url}, mode=${fileChooserParams.mode}, " +
                                "acceptTypes=${fileChooserParams.acceptTypes.contentToString()}, " +
                                "capture=${fileChooserParams.isCaptureEnabled}"
                        )
                        pendingFileCallback?.onReceiveValue(null)
                        pendingFileCallback = null

                        return try {
                            pendingFileCallback = filePathCallback
                            val chooserIntent = fileChooserParams.createIntent().apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            fileChooserLauncher.launch(chooserIntent)
                            Log.d(LOG_TAG, "System file chooser launched")
                            true
                        } catch (error: ActivityNotFoundException) {
                            Log.e(LOG_TAG, "No activity can handle the file chooser", error)
                            pendingFileCallback = null
                            filePathCallback.onReceiveValue(null)
                            true
                        }
                    }
                }

                installCookiesThenLoad(this, onRequestCookies(), targets.first().url)
                if (!mirrorWorker) onWebViewReferenceChanged(this)
            }
        },
        onRelease = { webView ->
            Log.d(LOG_TAG, "Releasing WebView")
            if (!mirrorWorker) onWebViewReferenceChanged(null)
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    )
}

private fun installCookiesThenLoad(webView: WebView, cookies: List<String>, initialUrl: String) {
    val cookieManager = CookieManager.getInstance().apply {
        setAcceptCookie(true)
    }

    Log.d(
        LOG_TAG,
        "Cookie install starting: count=${cookies.size}, names=${cookieNames(cookies)}"
    )
    // CookieManager is process-global. Clearing it here races parallel WebViews and can
    // remove another worker's authenticated session while it is submitting a post.
    setCookiesAndLoad(webView, cookieManager, cookies, initialUrl)
}

private fun setCookiesAndLoad(
    webView: WebView,
    cookieManager: CookieManager,
    cookies: List<String>,
    initialUrl: String
) {
    if (cookies.isEmpty()) {
        Log.w(LOG_TAG, "No session cookies available; loading target without authentication")
        cookieManager.flush()
        webView.loadUrl(initialUrl)
        return
    }

    val remaining = AtomicInteger(cookies.size)
    cookies.forEach { cookie ->
        val cookieName = cookie.substringBefore('=').trim()
        cookieManager.setCookie(initialUrl, cookie) { accepted ->
            Log.d(LOG_TAG, "Cookie set result: name=$cookieName, accepted=$accepted")
            if (remaining.decrementAndGet() == 0) {
                cookieManager.flush()
                val installedNames = CookieManager.getInstance()
                    .getCookie(initialUrl)
                    ?.split(';')
                    ?.map { it.substringBefore('=').trim() }
                    ?.filter(String::isNotBlank)
                    .orEmpty()
                Log.d(LOG_TAG, "Cookie install complete: installedNames=$installedNames; loading page")
                webView.post { webView.loadUrl(initialUrl) }
            }
        }
    }
}

private fun cookieNames(cookies: List<String>): List<String> =
    cookies.map { it.substringBefore('=').trim() }.filter(String::isNotBlank)

private fun normalizeWriteUrl(input: String): String? {
    val uri = runCatching { Uri.parse(input.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "https" || !uri.host.equals("m.dcinside.com", ignoreCase = true)) {
        return null
    }
    val segments = uri.pathSegments
    if (segments.size != 2 || segments.firstOrNull() !in setOf("write", "board")) return null
    val galleryId = segments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
    if (!galleryId.matches(Regex("[A-Za-z0-9_\\-$]+"))) return null
    return "https://m.dcinside.com/write/$galleryId"
}

private fun writeGalleryIdFromUrl(url: String?): String? {
    val normalized = url?.let(::normalizeWriteUrl) ?: return null
    return Uri.parse(normalized).pathSegments.lastOrNull()
}

private fun writeTargetIndex(url: String?, targets: List<WriteTarget>): Int? {
    val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
    if (uri.scheme != "https" || !uri.host.equals("m.dcinside.com", ignoreCase = true)) {
        return null
    }
    val segments = uri.pathSegments
    if (segments.size != 2 || segments.firstOrNull() != "write") return null
    val galleryId = segments.getOrNull(1) ?: return null
    return targets.indexOfFirst { it.galleryId == galleryId }.takeIf { it >= 0 }
}

private fun isCompletedPostDestination(url: String?, galleryId: String): Boolean {
    val uri = url?.let(Uri::parse) ?: return false
    if (uri.scheme != "https" || !uri.host.equals("m.dcinside.com", ignoreCase = true)) return false
    val segments = uri.pathSegments
    if (uri.getQueryParameter("id") == galleryId) return true
    val galleryPosition = segments.indexOf(galleryId)
    if (galleryPosition < 0 || "board" !in segments.take(galleryPosition)) return false
    val possiblePostNumber = segments.getOrNull(galleryPosition + 1)
    return possiblePostNumber == null || possiblePostNumber.toLongOrNull() != null
}

private fun handleCompletedPostPage(
    webView: WebView,
    targets: List<WriteTarget>,
    completedTargetIndex: Int,
    userImageAttachments: List<UserImageAttachment>,
    onAllPostsCompleted: (CompletedWriteBatch) -> Unit
) {
    val completedTarget = targets[completedTargetIndex]
    val nextTarget = targets.getOrNull(completedTargetIndex + 1)
    Log.d(
        LOG_TAG,
        "Completed post destination observed: index=$completedTargetIndex, " +
            "gallery=${completedTarget.galleryId}, next=${nextTarget?.url}"
    )
    if (nextTarget == null) {
        webView.evaluateJavascript(
            "sessionStorage.getItem('$MIRROR_STORAGE_KEY')"
        ) { storedMirror ->
            val mirrorPayload = mirrorPayloadFromJavascriptResult(storedMirror)
            webView.evaluateJavascript(
                "sessionStorage.removeItem('$MIRROR_STORAGE_KEY'); true"
            ) {}
            Log.d(LOG_TAG, "All configured posts completed; pending mirror content cleared")
            onAllPostsCompleted(
                CompletedWriteBatch(
                    mirrorPayload = mirrorPayload,
                    userImageAttachments = userImageAttachments
                )
            )
        }
        return
    }

    webView.evaluateJavascript(
        "sessionStorage.getItem('$MIRROR_STORAGE_KEY') !== null"
    ) { hasPendingMirror ->
        if (hasPendingMirror == "true") {
            Log.d(LOG_TAG, "Opening next configured write page: ${nextTarget.url}")
            webView.loadUrl(nextTarget.url)
        } else {
            Log.w(LOG_TAG, "Post completed without captured mirror content; sequence stopped")
        }
    }
}

private fun mirrorPayloadFromJavascriptResult(result: String?): String? = runCatching {
    (JSONTokener(result.orEmpty()).nextValue() as? String)?.takeIf(String::isNotBlank)
}.getOrNull()

private fun parallelWriteQueues(writeUrls: List<String>, maxWorkers: Int): List<List<String>> {
    val workerCount = minOf(writeUrls.size, maxWorkers.coerceAtLeast(1))
    if (workerCount == 0) return emptyList()
    val queues = List(workerCount) { mutableListOf<String>() }
    writeUrls.forEachIndexed { index, url -> queues[index % workerCount].add(url) }
    return queues
}

private fun describeUri(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
    val reportedSize = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()
    val readable = runCatching {
        resolver.openInputStream(uri)?.use { input -> input.read() >= -1 } == true
    }.getOrDefault(false)

    return "scheme=${uri.scheme}, authority=${uri.authority}, mimeType=$mimeType, " +
        "reportedSize=$reportedSize, readable=$readable"
}

private fun readUserImageAttachment(context: Context, uri: Uri): UserImageAttachment? =
    runCatching {
        val resolver = context.contentResolver
        val fileName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }?.takeIf(String::isNotBlank) ?: "dc-user-image.jpg"
        val mimeType = resolver.getType(uri)
            ?.takeIf { it.startsWith("image/") }
            ?: "image/jpeg"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        UserImageAttachment(
            fileName = fileName,
            mimeType = mimeType,
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        )
    }.onFailure { error ->
        Log.e(LOG_TAG, "Failed to retain user-selected image for mirror upload", error)
    }.getOrNull()

private fun userImageAttachmentsJson(attachments: List<UserImageAttachment>): String =
    JSONArray().apply {
        attachments.forEach { attachment ->
            put(
                JSONObject().apply {
                    put("fileName", attachment.fileName)
                    put("mimeType", attachment.mimeType)
                    put("base64", attachment.base64)
                }
            )
        }
    }.toString()

private fun writeAutomationScript(
    imageBase64: String?,
    galleryId: String,
    isFirstTarget: Boolean,
    initialMirrorPayload: String?,
    userImageAttachments: List<UserImageAttachment>
): String =
    """
    (() => {
      const prefix = '[DCCleaner] ';
      const log = message => console.log(prefix + message);
      const logError = message => console.error(prefix + message);
      const galleryId = '$galleryId';
      const mirrorStorageKey = '$MIRROR_STORAGE_KEY';
      const isFirstGallery = $isFirstTarget;
      const isMirrorGallery = !isFirstGallery;
      const suppliedMirrorPayload = ${JSONObject.quote(initialMirrorPayload.orEmpty())};
      const appImageBase64 = '${imageBase64.orEmpty()}';
      const mirrorUserAttachments = ${userImageAttachmentsJson(userImageAttachments)};
      const appUploadedThumbs = new Set();
      const userUploadedThumbs = [];
      const appUploadRequests = new WeakSet();
      const mirrorUserUploadRequests = new WeakMap();
      let appFileChangePending = false;
      let mirrorUserFileChangePendingIndex = null;

      if (isMirrorGallery && suppliedMirrorPayload) {
        sessionStorage.setItem(mirrorStorageKey, suppliedMirrorPayload);
        log('Native mirror payload installed: length=' + suppliedMirrorPayload.length);
      }

      log('Diagnostics script started; readyState=' + document.readyState +
          ', url=' + location.href + ', galleryId=' + galleryId +
          ', jQuery=' + (typeof window.jQuery));

      const hasSummernoteEditor = () => Boolean(
        window.jQuery &&
        window.jQuery.fn &&
        typeof window.jQuery.fn.summernote === 'function' &&
        document.querySelector('#textbox') &&
        document.querySelector('.note-editable')
      );

      const readEditorHtml = () => {
        if (hasSummernoteEditor()) {
          return String(window.jQuery('#textbox').summernote('code') || '');
        }
        const visibleEditor = document.querySelector('.note-editable');
        return visibleEditor?.innerHTML || document.querySelector('#textbox')?.innerHTML || '';
      };

      const writeEditorHtml = html => {
        if (hasSummernoteEditor()) {
          window.jQuery('#textbox').summernote('code', html || '');
          return true;
        }
        const visibleEditor = document.querySelector('.note-editable') ||
            document.querySelector('#textbox');
        if (!visibleEditor) return false;
        visibleEditor.innerHTML = html || '';
        return true;
      };

      const removeGeneratedImages = html => {
        const template = document.createElement('template');
        template.innerHTML = html || '';
        let removedAutoZzal = 0;
        let removedAppAttachment = 0;
        let replacedUserAttachment = 0;
        template.content.querySelectorAll('img').forEach(image => {
          if (!template.content.contains(image)) return;
          const autoZzalContainer = image.closest('[class*="autozzal_"]');
          const isAutoZzal = Boolean(autoZzalContainer);
          const src = image.getAttribute('src') || '';
          const isAppAttachment = Array.from(appUploadedThumbs).some(thumb => src.includes(thumb));
          const userAttachmentIndex = userUploadedThumbs.findIndex(thumb => src.includes(thumb));
          if (!isAutoZzal && !isAppAttachment && userAttachmentIndex < 0) return;

          if (isAutoZzal) removedAutoZzal++;
          if (isAppAttachment) removedAppAttachment++;
          if (autoZzalContainer) {
            autoZzalContainer.remove();
          } else if (userAttachmentIndex >= 0) {
            const imageBlock = image.closest('.block') || image;
            const marker = document.createElement('p');
            marker.setAttribute('data-dccleaner-user-image-index', String(userAttachmentIndex));
            marker.innerHTML = '<br>';
            imageBlock.replaceWith(marker);
            replacedUserAttachment++;
          } else {
            const imageBlock = image.closest('.block');
            if (imageBlock) imageBlock.remove(); else image.remove();
          }
        });
        log('Copied content sanitized: removedAutoZzal=' + removedAutoZzal +
            ', removedAppAttachment=' + removedAppAttachment +
            ', replacedUserAttachment=' + replacedUserAttachment);
        return template.innerHTML;
      };

      const removeEditorGuides = html => {
        const template = document.createElement('template');
        template.innerHTML = html || '';
        template.content.querySelectorAll('.wrt-guide-preview').forEach(guide => guide.remove());
        return template.innerHTML;
      };

      const placeEditorCursorAtStart = () => {
        const editor = document.querySelector('.note-editable') ||
            document.querySelector('#textbox');
        if (!editor) return false;
        editor.focus();
        const range = document.createRange();
        range.selectNodeContents(editor);
        range.collapse(true);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        if (hasSummernoteEditor()) {
          window.jQuery('#textbox').summernote('saveRange');
        }
        log('Summernote cursor placed at editor start');
        return true;
      };

      const captureFirstPostContent = source => {
        if (!isFirstGallery) return;
        try {
          const payload = {
            subject: document.querySelector('#subject')?.value || '',
            memo: removeGeneratedImages(readEditorHtml()),
            capturedAt: Date.now()
          };
          sessionStorage.setItem(mirrorStorageKey, JSON.stringify(payload));
          log('First post content captured: source=' + source +
              ', subjectLength=' + payload.subject.length +
              ', memoLength=' + payload.memo.length);
        } catch (error) {
          logError('Failed to capture first post content: source=' + source +
              ', error=' + error.message);
        }
      };

      if (isFirstGallery && !window.__dcCleanerMirrorCaptureInstalled) {
        window.__dcCleanerMirrorCaptureInstalled = true;

        if (typeof window.write_submit === 'function') {
          const originalWriteSubmit = window.write_submit;
          window.write_submit = function(...args) {
            captureFirstPostContent('write_submit');
            return originalWriteSubmit.apply(this, args);
          };
          log('First post write_submit capture installed');
        } else {
          logError('write_submit is unavailable; using submit-event capture only');
        }

        document.addEventListener('submit', event => {
          if (event.target?.id === 'writeForm') {
            captureFirstPostContent('submit-event');
          }
        }, true);
        log('First post submit-event fallback capture installed');
      }

      let mirrorPayload = null;
      let mirrorEditorPrepared = !isMirrorGallery;
      const mirrorPreparationStartedAt = Date.now();
      if (isMirrorGallery) {
        try {
          const stored = sessionStorage.getItem(mirrorStorageKey);
          mirrorPayload = stored ? JSON.parse(stored) : null;
          if (mirrorPayload) {
            mirrorPayload.memo = removeGeneratedImages(mirrorPayload.memo);
            const subjectInput = document.querySelector('#subject');
            if (subjectInput) {
              subjectInput.value = mirrorPayload.subject || '';
              subjectInput.dispatchEvent(new Event('input', { bubbles: true }));
              subjectInput.dispatchEvent(new Event('change', { bubbles: true }));
            }
            log('Mirror payload loaded: subjectLength=' +
                String(mirrorPayload.subject || '').length + ', memoLength=' +
                String(mirrorPayload.memo || '').length);
          } else {
            mirrorEditorPrepared = true;
            log('No pending mirror content; automatic mirror submit is disabled');
          }
        } catch (error) {
          mirrorPayload = null;
          mirrorEditorPrepared = true;
          logError('Failed to restore mirror content: ' + error.message);
        }
      }

      const prepareMirrorEditor = () => {
        if (!isMirrorGallery || mirrorEditorPrepared) return true;
        if (!mirrorPayload) {
          mirrorEditorPrepared = true;
          return true;
        }

        const editorRoot = document.querySelector('.note-editable') ||
            document.querySelector('#textbox');
        const currentAutoZzalContainers = editorRoot ? Array.from(
          editorRoot.querySelectorAll('[class*="autozzal_"]')
        ).filter(container => container.matches('img') || container.querySelector('img')) : [];
        const autoZzalPresent = currentAutoZzalContainers.length > 0;
        const waitedMs = Date.now() - mirrorPreparationStartedAt;
        if (!autoZzalPresent && waitedMs < 3000) return false;

        currentAutoZzalContainers.slice(1).forEach(container => container.remove());
        if (currentAutoZzalContainers.length > 1) {
          log('Duplicate current-page auto zzal removed before body append: count=' +
              (currentAutoZzalContainers.length - 1));
        }

        const existingHtml = removeEditorGuides(readEditorHtml());
        const copiedHtml = mirrorPayload.memo || '';
        const editorWritten = writeEditorHtml(existingHtml + copiedHtml);
        const memo = document.querySelector('#memo');
        if (memo) memo.value = existingHtml + copiedHtml;
        mirrorEditorPrepared = true;
        log('Mirror content appended after existing editor content: autoZzalPresent=' +
            autoZzalPresent + ', waitedMs=' + waitedMs + ', editorWritten=' + editorWritten +
            ', existingLength=' + existingHtml.length + ', copiedLength=' + copiedHtml.length);
        return true;
      };

      let mirrorSubmitRequested = false;
      let appAttachmentComplete = !appImageBase64;
      let mirrorUserUploadsComplete = !isMirrorGallery;
      let nextMirrorUserAttachmentIndex = 0;
      let mirrorImageInput = null;
      let startNextMirrorUserUpload = () => {};
      const submitMirror = source => {
        if (!isMirrorGallery || !mirrorPayload || mirrorSubmitRequested) return;
        mirrorSubmitRequested = true;
        setTimeout(() => {
          if (typeof window.write_submit === 'function') {
            log('Automatic mirror submit requested: source=' + source);
            window.write_submit();
          } else {
            mirrorSubmitRequested = false;
            logError('write_submit is unavailable; mirror post was not submitted');
          }
        }, 250);
      };

      const submitMirrorWhenImagesReady = source => {
        if (!appAttachmentComplete || !mirrorUserUploadsComplete) {
          log('Mirror submit waiting for images: source=' + source +
              ', appComplete=' + appAttachmentComplete +
              ', userComplete=' + mirrorUserUploadsComplete);
          return;
        }
        submitMirror(source);
      };

      const rememberUploadedAppImage = response => {
        const thumbs = Array.isArray(response?.thumb) ? response.thumb : [];
        thumbs.forEach(thumb => appUploadedThumbs.add(thumb));
        log('App attachment remembered without DOM mutation: thumbCount=' + thumbs.length);
      };

      const rememberUploadedUserImages = response => {
        const thumbs = Array.isArray(response?.thumb) ? response.thumb : [];
        thumbs.forEach(thumb => {
          if (!userUploadedThumbs.includes(thumb)) userUploadedThumbs.push(thumb);
        });
        log('User attachments remembered for mirror placeholders: added=' + thumbs.length +
            ', total=' + userUploadedThumbs.length);
      };

      const directEditorChild = (node, editor) => {
        let current = node;
        while (current.parentElement && current.parentElement !== editor) {
          current = current.parentElement;
        }
        return current;
      };

      const moveAppAttachmentAfterAutoZzal = response => {
        const thumbs = Array.isArray(response?.thumb) ? response.thumb : [];
        if (thumbs.length === 0) return false;

        const editor = document.querySelector('.note-editable') ||
            document.querySelector('#textbox');
        if (!editor) return false;
        const autoZzal = Array.from(editor.querySelectorAll('[class*="autozzal_"]'))
          .find(container => container.matches('img') || container.querySelector('img'));
        const appImage = Array.from(editor.querySelectorAll('img')).find(image => {
          const src = image.getAttribute('src') || '';
          return thumbs.some(thumb => src.includes(thumb));
        });
        if (!autoZzal || !appImage) return false;

        const autoZzalBlock = directEditorChild(autoZzal, editor);
        const appImageBlock = directEditorChild(appImage, editor);
        if (autoZzalBlock === appImageBlock || autoZzalBlock.parentElement !== editor ||
            appImageBlock.parentElement !== editor) return false;

        autoZzalBlock.insertAdjacentElement('afterend', appImageBlock);
        if (hasSummernoteEditor()) {
          window.jQuery('#textbox').summernote('code', editor.innerHTML);
        }
        log('App attachment block moved directly after auto zzal');
        return true;
      };

      const moveMirrorUserAttachmentToMarker = (response, attachmentIndex, attempt = 0) => {
        const editor = document.querySelector('.note-editable') ||
            document.querySelector('#textbox');
        const marker = editor?.querySelector(
          '[data-dccleaner-user-image-index="' + attachmentIndex + '"]'
        );
        const thumbs = Array.isArray(response?.thumb) ? response.thumb : [];
        const uploadedImage = editor ? Array.from(editor.querySelectorAll('img')).find(image => {
          const src = image.getAttribute('src') || '';
          return thumbs.some(thumb => src.includes(thumb));
        }) : null;

        if (editor && marker && uploadedImage) {
          const imageBlock = directEditorChild(uploadedImage, editor);
          if (imageBlock === marker) {
            marker.removeAttribute('data-dccleaner-user-image-index');
          } else {
            marker.replaceWith(imageBlock);
          }
          if (hasSummernoteEditor()) {
            window.jQuery('#textbox').summernote('code', editor.innerHTML);
          }
          log('Mirror user attachment placed at original position: index=' + attachmentIndex);
          nextMirrorUserAttachmentIndex = attachmentIndex + 1;
          startNextMirrorUserUpload();
          return;
        }

        if (attempt < 10) {
          setTimeout(() => {
            moveMirrorUserAttachmentToMarker(response, attachmentIndex, attempt + 1);
          }, 50);
          return;
        }

        marker?.remove();
        logError('Could not place mirror user attachment at marker; keeping uploaded image: index=' +
            attachmentIndex);
        nextMirrorUserAttachmentIndex = attachmentIndex + 1;
        startNextMirrorUserUpload();
      };

      if (!window.__dcCleanerDiagnosticsInstalled) {
        window.__dcCleanerDiagnosticsInstalled = true;

        window.addEventListener('error', event => {
          logError('window.error: message=' + event.message + ', source=' + event.filename +
              ':' + event.lineno + ':' + event.colno);
        });
        window.addEventListener('unhandledrejection', event => {
          logError('unhandledrejection: ' + String(event.reason));
        });

        if (window.jQuery) {
          window.jQuery(document).on('ajaxSend.dccleanerDiagnostics', (_event, xhr, settings) => {
            if ((settings.url || '').includes('upload_img.php')) {
              const isAppUpload = appFileChangePending;
              const mirrorUserIndex = isAppUpload ? null : mirrorUserFileChangePendingIndex;
              if (isAppUpload) {
                appUploadRequests.add(xhr);
                appFileChangePending = false;
              } else if (Number.isInteger(mirrorUserIndex)) {
                mirrorUserUploadRequests.set(xhr, mirrorUserIndex);
                mirrorUserFileChangePendingIndex = null;
              }
              log('AJAX send: type=' + settings.type + ', url=' + settings.url +
                  ', processData=' + settings.processData + ', contentType=' + settings.contentType +
                  ', appUpload=' + isAppUpload + ', mirrorUserIndex=' + mirrorUserIndex);
            }
          });
          window.jQuery(document).on('ajaxSuccess.dccleanerDiagnostics', (_event, xhr, settings) => {
            if ((settings.url || '').includes('upload_img.php')) {
              const isAppUpload = appUploadRequests.has(xhr);
              const mirrorUserIndex = mirrorUserUploadRequests.get(xhr);
              log('AJAX success: status=' + xhr.status + ', response=' +
                  String(xhr.responseText || '').slice(0, 2000) + ', appUpload=' + isAppUpload +
                  ', mirrorUserIndex=' + mirrorUserIndex);
              let response = xhr.responseJSON;
              if (!response && xhr.responseText) {
                try { response = JSON.parse(xhr.responseText); } catch (_error) {}
              }
              if (isAppUpload && response && response.result === true) {
                rememberUploadedAppImage(response);
                const moved = moveAppAttachmentAfterAutoZzal(response);
                log('App attachment ordering completed: moved=' + moved);
                appAttachmentComplete = true;
                startNextMirrorUserUpload();
                submitMirrorWhenImagesReady('app-image-upload-success');
              } else if (Number.isInteger(mirrorUserIndex) && response?.result === true) {
                moveMirrorUserAttachmentToMarker(response, mirrorUserIndex);
              } else if (!Number.isInteger(mirrorUserIndex) && response?.result === true) {
                rememberUploadedUserImages(response);
              }
            }
          });
          window.jQuery(document).on('ajaxError.dccleanerDiagnostics', (_event, xhr, settings, error) => {
            if ((settings.url || '').includes('upload_img.php')) {
              logError('AJAX error: status=' + xhr.status + ', statusText=' + xhr.statusText +
                  ', error=' + String(error) + ', response=' +
                  String(xhr.responseText || '').slice(0, 2000));
            }
          });
          window.jQuery(document).on('ajaxComplete.dccleanerDiagnostics', (_event, xhr, settings) => {
            if ((settings.url || '').includes('upload_img.php')) {
              log('AJAX complete: status=' + xhr.status + ', readyState=' + xhr.readyState);
            }
          });
          log('jQuery AJAX diagnostics installed; version=' + window.jQuery.fn.jquery);
        } else {
          logError('jQuery is unavailable; upload event diagnostics could not be installed');
        }
      }

      const findImageInput = () => {
        const inputs = Array.from(document.querySelectorAll('input[type="file"]'));
        log('File inputs found: count=' + inputs.length + ', details=' + JSON.stringify(
          inputs.map(input => ({ id: input.id, name: input.name, accept: input.accept,
              inlineChange: input.getAttribute('onchange') || '' }))
        ));
        return inputs.find(input => input.id === 'upload') || inputs.find(input =>
          (input.getAttribute('onchange') || '').includes('handleImgFileSelect')
        ) || inputs.find(input => {
          const hint = [input.id, input.name, input.accept].join(' ').toLowerCase();
          return /image|img|upload/.test(hint);
        }) || inputs[0];
      };

      const fileFromBase64 = attachment => {
        const binary = atob(attachment.base64 || '');
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
          bytes[i] = binary.charCodeAt(i);
        }
        return new File(
          [bytes],
          attachment.fileName || 'dc-user-image.jpg',
          { type: attachment.mimeType || 'image/jpeg' }
        );
      };

      const placeEditorCursorAtUserMarker = attachmentIndex => {
        const editor = document.querySelector('.note-editable') ||
            document.querySelector('#textbox');
        const marker = editor?.querySelector(
          '[data-dccleaner-user-image-index="' + attachmentIndex + '"]'
        );
        if (!editor || !marker) return false;
        editor.focus();
        const range = document.createRange();
        range.setStartBefore(marker);
        range.collapse(true);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        if (hasSummernoteEditor()) {
          window.jQuery('#textbox').summernote('saveRange');
        }
        return true;
      };

      const dispatchSyntheticImage = (input, attachment, kind, attachmentIndex = null) => {
        const file = fileFromBase64(attachment);
        const transfer = new DataTransfer();
        transfer.items.add(file);
        input.files = transfer.files;

        const cursorPlaced = kind === 'app'
          ? placeEditorCursorAtStart()
          : placeEditorCursorAtUserMarker(attachmentIndex);
        if (kind === 'app') {
          appFileChangePending = true;
        } else {
          mirrorUserFileChangePendingIndex = attachmentIndex;
        }

        const dispatchResult = input.dispatchEvent(new Event('change', { bubbles: true }));
        const requestStarted = kind === 'app'
          ? !appFileChangePending
          : mirrorUserFileChangePendingIndex === null;
        if (!requestStarted) {
          if (kind === 'app') appFileChangePending = false;
          else mirrorUserFileChangePendingIndex = null;
          logError('Synthetic image change did not start an upload: kind=' + kind +
              ', index=' + attachmentIndex);
          return false;
        }

        log('Synthetic image upload dispatched: kind=' + kind + ', index=' + attachmentIndex +
            ', fileName=' + file.name + ', fileType=' + file.type +
            ', fileSize=' + file.size + ', dispatchResult=' + dispatchResult +
            ', cursorPlaced=' + cursorPlaced);
        return true;
      };

      startNextMirrorUserUpload = () => {
        if (!isMirrorGallery || mirrorUserUploadsComplete) return;
        const editor = document.querySelector('.note-editable') ||
            document.querySelector('#textbox');
        while (nextMirrorUserAttachmentIndex < mirrorUserAttachments.length) {
          const marker = editor?.querySelector(
            '[data-dccleaner-user-image-index="' + nextMirrorUserAttachmentIndex + '"]'
          );
          if (marker) break;
          log('Mirror user attachment skipped because its marker is absent: index=' +
              nextMirrorUserAttachmentIndex);
          nextMirrorUserAttachmentIndex++;
        }

        if (nextMirrorUserAttachmentIndex >= mirrorUserAttachments.length) {
          editor?.querySelectorAll('[data-dccleaner-user-image-index]').forEach(marker => {
            logError('Removing unmatched user image marker: index=' +
                marker.getAttribute('data-dccleaner-user-image-index'));
            marker.remove();
          });
          mirrorUserUploadsComplete = true;
          log('All mirror user attachment uploads completed');
          submitMirrorWhenImagesReady('all-user-images-uploaded');
          return;
        }

        if (!mirrorImageInput) {
          logError('Mirror user image input is unavailable');
          return;
        }
        const attachmentIndex = nextMirrorUserAttachmentIndex;
        dispatchSyntheticImage(
          mirrorImageInput,
          mirrorUserAttachments[attachmentIndex],
          'user',
          attachmentIndex
        );
      };

      let attempts = 0;
      const timer = setInterval(() => {
        try {
          if (!prepareMirrorEditor()) {
            attempts++;
            if (attempts === 1 || attempts === 5 || attempts === 10) {
              log('Waiting for gallery auto zzal before appending copied content: attempt=' +
                  attempts);
            }
            return;
          }
          const hasMirrorUserMarkers = isMirrorGallery && Boolean(
            document.querySelector('[data-dccleaner-user-image-index]')
          );
          if (!appImageBase64 && !hasMirrorUserMarkers) {
            clearInterval(timer);
            log('No now-playing attachment image; automatic file attachment skipped');
            mirrorUserUploadsComplete = true;
            submitMirrorWhenImagesReady('no-images-to-upload');
            return;
          }
          const input = findImageInput();
          if (input) {
            clearInterval(timer);
            const changeHandlers = window.jQuery && window.jQuery._data
              ? ((window.jQuery._data(input, 'events') || {}).change || []).length
              : -1;
            log('Selected file input: id=' + input.id + ', name=' + input.name +
                ', accept=' + input.accept + ', disabled=' + input.disabled +
                ', connected=' + input.isConnected + ', jQueryChangeHandlers=' + changeHandlers);
            mirrorImageInput = input;
            if (appImageBase64) {
              dispatchSyntheticImage(
                input,
                {
                  base64: appImageBase64,
                  fileName: 'dc-now-playing.png',
                  mimeType: 'image/png'
                },
                'app'
              );
            } else {
              log('No now-playing attachment image; starting user image mirror uploads');
              appAttachmentComplete = true;
              startNextMirrorUserUpload();
            }

            setTimeout(() => {
              const loading = document.querySelector('.loading-box');
              const editorImages = document.querySelectorAll('#textbox img, .note-editable img');
              log('Post-dispatch state: loadingExists=' + Boolean(loading) +
                  ', loadingDisplay=' + (loading ? getComputedStyle(loading).display : '<none>') +
                  ', editorImageCount=' + editorImages.length);
            }, 1000);
          } else {
            attempts++;
            if (attempts === 1 || attempts === 5 || attempts === 10) {
              log('Waiting for image input: attempt=' + attempts);
            }
            if (attempts >= 20) {
              clearInterval(timer);
              logError('Image input was not found after ' + attempts + ' attempts');
            }
          }
        } catch (error) {
          clearInterval(timer);
          logError('Auto-attach exception: name=' + error.name + ', message=' + error.message +
              ', stack=' + String(error.stack || ''));
        }
      }, 250);

      return 'write-automation-installed-and-auto-attach-scheduled:' + galleryId;
    })();
    """.trimIndent()
