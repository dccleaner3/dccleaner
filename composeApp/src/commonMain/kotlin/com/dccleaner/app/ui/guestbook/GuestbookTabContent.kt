package com.dccleaner.app.ui.guestbook

import com.dccleaner.app.ui.dialog.GuestbookConfirmDialog
import com.dccleaner.app.ui.dialog.GuestbookResultDialog

import com.dccleaner.app.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dccleaner.app.ui.theme.BusyIndicator

/** 이보다 많으면 받는 사람 칸 대신 개수만 보여 준다. 수만 줄을 입력칸에 그리면 글자를 칠 때마다 멈춘다. */
internal const val LARGE_USER_LIST_LINES = 1_000

data class GuestbookCacheFilterResult(
    val userIdsToSend: List<String>,
    val skippedCount: Int
)

@Composable
fun GuestbookTabContent(
    uiColors: UiColors,
    coroutine: CoroutineScope,
    userListText: String,
    onUserListTextChange: (String) -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    showConfirmDialog: Boolean,
    onShowConfirmDialogChange: (Boolean) -> Unit,
    showResultDialog: Boolean,
    onShowResultDialogChange: (Boolean) -> Unit,
    isSending: Boolean,
    onIsSendingChange: (Boolean) -> Unit,
    progressDone: Int,
    onProgressDoneChange: (Int) -> Unit,
    progressTotal: Int,
    onProgressTotalChange: (Int) -> Unit,
    successCount: Int,
    onSuccessCountChange: (Int) -> Unit,
    failCount: Int,
    onFailCountChange: (Int) -> Unit,
    cacheEnabled: Boolean,
    cachedUserCount: Int,
    onFilterCachedUsers: suspend (List<String>) -> GuestbookCacheFilterResult,
    onClearCachedUsers: suspend () -> Boolean,
    onResolveUserList: suspend (String) -> String,
    onStartGuestbookSend: (List<String>, String) -> Unit,
    onOpenRanking: () -> Unit,
) {
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card
    var isLoadingRawList by remember { mutableStateOf(false) }
    var rawListError by remember { mutableStateOf<String?>(null) }
    var resolvedUserIds by remember { mutableStateOf<List<String>?>(null) }
    var cachedSkippedCount by remember { mutableStateOf(0) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var isClearingCache by remember { mutableStateOf(false) }
    var isApplyingCache by remember { mutableStateOf(false) }

    val userIds = remember(userListText) {
        userListText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val isRawLinkInput = remember(userListText) {
        userListText.trimStart().let { it.startsWith("https://") || it.startsWith("http://") }
    }
    val isLargeList = !isRawLinkInput && userIds.size > LARGE_USER_LIST_LINES
    val rawLinkCount = if (isRawLinkInput) userIds.size else 0
    val latestOnUserListTextChange by rememberUpdatedState(onUserListTextChange)
    val latestOnMessageTextChange by rememberUpdatedState(onMessageTextChange)
    val handleUserListTextChange: (String) -> Unit = remember {
        { value ->
            rawListError = null
            resolvedUserIds = null
            cachedSkippedCount = 0
            latestOnUserListTextChange(value)
        }
    }
    val handleMessageTextChange: (String) -> Unit = remember {
        { value -> latestOnMessageTextChange(value) }
    }

    val distinctIdCount = remember(userIds) { userIds.distinct().size }
    val isBusy = isSending || isLoadingRawList || isApplyingCache
    val hasRecipients = isRawLinkInput || userIds.isNotEmpty()
    val canSend = !isBusy && hasRecipients && messageText.isNotBlank()
    var showLinkHelp by remember { mutableStateOf(false) }
    val allCachedMessage = "모두 이미 보낸 사람이에요. 다시 보내려면 보낸 기록을 초기화해 주세요."
    val placeholderColor = uiColors.textSecondary

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (userListText.isBlank() && messageText.isBlank()) {
            Text(
                "여러 사람의 방명록에 같은 글을 한 번에 남겨요",
                style = MaterialTheme.typography.bodyMedium,
                color = uiColors.textSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }

        GuestbookSectionLabel("받는 사람", uiColors)
        GuestbookCard(uiColors) {
            if (isLargeList) Row(
                Modifier
                    .fillMaxWidth()
                    .background(uiColors.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "식별코드 ${"%,d".format(userIds.size)}개",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${userIds.take(3).joinToString(", ")} 외",
                        style = MaterialTheme.typography.bodySmall,
                        color = uiColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = { handleUserListTextChange("") },
                    // 보낸 기록을 확인하는 중에 비우면 끝난 뒤 예전 명단으로 확인 창이 뜬다.
                    enabled = !isBusy
                ) {
                    Text("비우기", color = if (isBusy) uiColors.textSecondary else uiColors.danger)
                }
            } else OutlinedTextField(
                value = userListText,
                onValueChange = handleUserListTextChange,
                placeholder = { Text("식별코드를 한 줄에 하나씩 써 주세요", color = placeholderColor) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                maxLines = Int.MAX_VALUE,
                shape = RoundedCornerShape(12.dp),
                colors = dccleanerOutlinedTextFieldColors(uiColors),
                enabled = !isSending && !isLoadingRawList
            )
            rawListError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = uiColors.danger,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            val resolved = resolvedUserIds
            val statusText = when {
                isLoadingRawList -> "명단 불러오는 중"
                resolved != null -> buildString {
                    append("${"%,d".format(resolved.size)}명에게 보내요")
                    if (cachedSkippedCount > 0) append(" · 이미 보낸 ${cachedSkippedCount}명은 빼요")
                }
                isRawLinkInput -> "링크 ${rawLinkCount}개 · 보낼 때 명단을 불러와요"
                else -> null
            }
            val countPill = when {
                isLoadingRawList -> null
                !isRawLinkInput && distinctIdCount > 0 -> "${"%,d".format(distinctIdCount)}명"
                else -> null
            }
            // 상태 문구와 링크 도움말을 한 줄에 두고, 오른쪽에 인원 수를 붙인다.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (statusText != null) Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = uiColors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    // 좁은 폭에서 두 버튼이 한 줄에 안 들어가면 글자를 쪼개지 않고 다음 줄로 넘긴다.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(
                            onClick = { showLinkHelp = !showLinkHelp },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) {
                            Text(if (showLinkHelp) "링크 안내 닫기" else "목록 링크로 넣기",
                                style = MaterialTheme.typography.bodySmall, color = uiColors.primary)
                        }
                        // 갤창랭킹 결과 화면의 '방명록 보내기'가 받는 사람을 채우고 여기로 돌아온다.
                        // 명단 확인 중에 탭을 떠나면 확인 결과는 버려지고 확인 창만 남으니 그동안은 막는다.
                        TextButton(
                            onClick = onOpenRanking,
                            enabled = !isBusy,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) {
                            Text("갤창랭킹으로 식별코드 뽑기", style = MaterialTheme.typography.bodySmall,
                                color = if (isBusy) uiColors.textSecondary else uiColors.primary)
                        }
                    }
                }
                if (countPill != null) {
                    Text(
                        countPill,
                        style = MaterialTheme.typography.labelMedium,
                        color = uiColors.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier
                            .background(uiColors.primary.copy(alpha = 0.1f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            if (showLinkHelp) {
                Text(
                    "식별코드만 줄마다 적힌 웹페이지 주소를 넣으면 명단을 불러와요. 식별코드와 주소는 함께 쓸 수 없어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = uiColors.textSecondary
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        GuestbookSectionLabel("메시지", uiColors)
        GuestbookCard(uiColors) {
            OutlinedTextField(
                value = messageText,
                onValueChange = handleMessageTextChange,
                placeholder = { Text("남길 말을 써 주세요", color = placeholderColor) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = Int.MAX_VALUE,
                shape = RoundedCornerShape(12.dp),
                colors = dccleanerOutlinedTextFieldColors(uiColors),
                enabled = !isSending
            )
            Text(
                "${messageText.length}자",
                style = MaterialTheme.typography.bodySmall,
                color = uiColors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }

        if (cacheEnabled) {
            Spacer(Modifier.height(4.dp))
            GuestbookSectionLabel("보낸 기록", uiColors)
            GuestbookCard(uiColors) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "이미 보낸 사람 빼기",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (cachedUserCount > 0) "보낸 적 있는 ${cachedUserCount}명은 자동으로 빼요"
                            else "아직 보낸 기록이 없어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = uiColors.textSecondary
                        )
                    }
                    if (cachedUserCount > 0) {
                        TextButton(
                            onClick = { showClearCacheDialog = true },
                            enabled = !isBusy && !isClearingCache
                        ) {
                            Text("초기화", color = uiColors.danger)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        if (!isBusy) {
            val hint = when {
                !hasRecipients -> "받는 사람을 넣어 주세요"
                messageText.isBlank() -> "메시지를 써 주세요"
                else -> null
            }
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = uiColors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            onClick = {
                rawListError = null
                if (resolvedUserIds != null) {
                    if (resolvedUserIds?.isEmpty() == true) {
                        rawListError = allCachedMessage
                    } else {
                        onShowConfirmDialogChange(true)
                    }
                    return@Button
                }
                if (!isRawLinkInput) {
                    coroutine.launch {
                        isApplyingCache = true
                        try {
                            val distinctUserIds = withContext(Dispatchers.Default) {
                                userIds.distinct()
                            }
                            val cacheResult = onFilterCachedUsers(distinctUserIds)
                            resolvedUserIds = cacheResult.userIdsToSend
                            cachedSkippedCount = cacheResult.skippedCount
                            if (cacheResult.userIdsToSend.isEmpty()) {
                                rawListError = allCachedMessage
                                return@launch
                            }
                            onShowConfirmDialogChange(true)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            rawListError = e.message ?: "보낸 기록을 확인하지 못했어요"
                        } finally {
                            isApplyingCache = false
                        }
                    }
                    return@Button
                }
                coroutine.launch {
                    isLoadingRawList = true
                    try {
                        val rawLinks = withContext(Dispatchers.Default) {
                            parseRawLinks(userListText)
                        }
                        val resolvedTexts = rawLinks.mapIndexed { index, url ->
                            try {
                                onResolveUserList(url)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                val detail = e.message?.takeIf { it.isNotBlank() }
                                    ?: "명단을 불러오지 못했어요"
                                error("${index + 1}번째 링크를 불러오지 못했어요: $detail")
                            }
                        }
                        val resolvedIds = withContext(Dispatchers.Default) {
                            mergeUserListTexts(resolvedTexts)
                        }
                        if (resolvedIds.isEmpty()) {
                            rawListError = "보낼 식별코드가 없어요"
                            return@launch
                        }
                        val cacheResult = onFilterCachedUsers(resolvedIds)
                        resolvedUserIds = cacheResult.userIdsToSend
                        cachedSkippedCount = cacheResult.skippedCount
                        if (cacheResult.userIdsToSend.isEmpty()) {
                            rawListError = allCachedMessage
                            return@launch
                        }
                        onShowConfirmDialogChange(true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        rawListError = e.message ?: "링크에서 명단을 불러오지 못했어요"
                    } finally {
                        isLoadingRawList = false
                    }
                }
            },
            enabled = canSend,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
        ) {
            if (isLoadingRawList || isApplyingCache) {
                BusyIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current
                )
                Spacer(Modifier.width(8.dp))
            }
            val sendCount = resolvedUserIds?.size ?: distinctIdCount
            Text(
                when {
                    isSending -> "보내는 중 $progressDone/$progressTotal"
                    isLoadingRawList -> "명단 불러오는 중"
                    isApplyingCache -> "보낸 기록 확인 중"
                    isRawLinkInput && resolvedUserIds == null -> "명단 불러오기"
                    sendCount > 0 -> "${"%,d".format(sendCount)}명에게 보내기"
                    else -> "보내기"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    // 보내기 확인 다이얼로그
    if (showConfirmDialog) {
        GuestbookConfirmDialog(
            uiColors = uiColors,
            userCount = resolvedUserIds?.size ?: userIds.size,
            messagePreview = messageText,
            onConfirm = {
                onShowConfirmDialogChange(false)
                val message = messageText
                val ids = resolvedUserIds ?: userIds
                onIsSendingChange(true)
                onProgressDoneChange(0)
                onProgressTotalChange(ids.size)
                onSuccessCountChange(0)
                onFailCountChange(0)
                if (ids.isEmpty()) {
                    rawListError = "보낼 식별코드가 없어요"
                    onIsSendingChange(false)
                    return@GuestbookConfirmDialog
                }
                onStartGuestbookSend(ids, message)
            },
            onDismiss = { onShowConfirmDialogChange(false) }
        )
    }

    if (cacheEnabled && showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = uiColors.card,
            shape = RoundedCornerShape(16.dp),
            title = { Text("보낸 기록을 지울까요?") },
            text = {
                Text("이 계정에서 보낸 ${cachedUserCount}명에게 다시 보낼 수 있게 돼요.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        coroutine.launch {
                            isClearingCache = true
                            try {
                                if (onClearCachedUsers()) {
                                    resolvedUserIds = null
                                    cachedSkippedCount = 0
                                    rawListError = null
                                } else {
                                    rawListError = "기록을 초기화하지 못했어요"
                                }
                            } finally {
                                isClearingCache = false
                            }
                        }
                    }
                ) {
                    Text("지우기", color = uiColors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("취소", color = uiColors.textSecondary)
                }
            }
        )
    }

    // 전송 완료 다이얼로그
    if (showResultDialog) {
        GuestbookResultDialog(
            uiColors = uiColors,
            successCount = successCount,
            failCount = failCount,
            onDismiss = { onShowResultDialogChange(false) }
        )
    }
}

@Composable
private fun GuestbookSectionLabel(text: String, uiColors: UiColors) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = uiColors.textSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun GuestbookCard(uiColors: UiColors, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

internal fun parseRawLinks(input: String): List<String> {
    val links = input.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    require(links.isNotEmpty()) { "링크를 넣어 주세요" }
    require(links.all { it.startsWith("https://") || it.startsWith("http://") }) {
        "링크는 줄마다 http로 시작해야 해요"
    }
    return links
}

internal fun mergeUserListTexts(texts: List<String>): List<String> =
    texts.asSequence()
        .flatMap { it.lineSequence() }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
