package com.dccleaner.app.ui.userposts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ranking.GalleryRankingCrawler
import com.dccleaner.app.ranking.KST
import com.dccleaner.app.ranking.RankingGallery
import com.dccleaner.app.ui.theme.BusyBar
import com.dccleaner.app.ui.theme.BusyIndicator
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors
import com.dccleaner.app.userposts.FoundPost
import com.dccleaner.app.userposts.PostKind
import com.dccleaner.app.userposts.UserPostSearchRequest
import com.dccleaner.app.userposts.UserPostSearchRunner
import com.dccleaner.app.userposts.UserPostSearchState
import com.dccleaner.app.userposts.UserPostTargetKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 글을 연다. 안드로이드는 디시 앱(없으면 인앱 브라우저), PC는 기본 브라우저. */
@Composable
internal expect fun rememberPostOpener(): (RankingGallery, Long) -> Unit

private const val DEFAULT_NICKNAME = "ㅇㅇ"
/** 찾은 글을 한 번에 그리는 개수. */
private const val POSTS_PER_SCREEN = 200

actual fun clearUserPostSearch() = UserPostSearchRunner.clear()

@Composable
actual fun userPostSearchBadge(): String? {
    val state by UserPostSearchRunner.state.collectAsState()
    val searching = when (val s = state) {
        is UserPostSearchState.Starting -> true
        is UserPostSearchState.Active -> s.loading
        else -> false
    }
    return if (searching) "찾는 중" else null
}

private enum class InputField { GALLERY, VALUE, NICKNAME }

@Composable
actual fun UserPostSearchContent(uiColors: UiColors, modifier: Modifier) {
    val state by UserPostSearchRunner.state.collectAsState()
    val lastRequest = when (val s = state) {
        is UserPostSearchState.Starting -> s.request
        is UserPostSearchState.Failed -> s.request
        is UserPostSearchState.Active -> s.request
        UserPostSearchState.Idle -> null
    }
    // 백그라운드에서 앱이 멈춰 검색이 실패했다면, 돌아왔을 때 같은 자리부터 한 번 다시 찾는다(처음 화면에 들어올 때는 하지 않는다).
    // 내려갈 때 찾는 중이었던 경우만 본다. PC는 창 포커스만 옮겨도 멈춤으로 오고, 이미 실패해 있던 상태는 사용자가 정하게 둔다.
    var searchingAtPause by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (searchingAtPause) UserPostSearchRunner.retryAfterInterruption()
        searchingAtPause = false
        onPauseOrDispose { searchingAtPause = (UserPostSearchRunner.state.value as? UserPostSearchState.Active)?.loading == true }
    }
    // 결과를 보는 동안에도 입력은 남겨 둬서 새로 찾기를 누르면 그대로 고쳐 쓸 수 있다.
    var gallery by rememberSaveable { mutableStateOf(lastRequest?.galleryInput.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(lastRequest?.kind ?: UserPostTargetKind.UID) }
    // 식별코드와 IP는 따로 둬서 방식을 바꿔 봐도 넣어 둔 값이 지워지지 않는다.
    var uid by rememberSaveable { mutableStateOf(lastRequest?.takeIf { it.kind == UserPostTargetKind.UID }?.value.orEmpty()) }
    var ip by rememberSaveable { mutableStateOf(lastRequest?.takeIf { it.kind == UserPostTargetKind.IP }?.value.orEmpty()) }
    var nickname by rememberSaveable { mutableStateOf(lastRequest?.nickname?.ifBlank { null } ?: DEFAULT_NICKNAME) }
    var inputError by remember { mutableStateOf<Pair<InputField, String>?>(null) }
    val openPost = rememberPostOpener()

    fun start() {
        val request = UserPostSearchRequest(gallery.trim(), kind,
            if (kind == UserPostTargetKind.UID) uid.trim() else ip.trim(),
            if (kind == UserPostTargetKind.IP) nickname.trim() else "")
        val error = UserPostSearchRunner.validate(request)
        // validate는 갤러리 → 식별코드·IP → 닉네임 순으로 보므로, 앞이 맞는지로 어느 칸의 문제인지 가린다.
        inputError = error?.let {
            when {
                GalleryRankingCrawler.parseGalleryInput(request.galleryInput) == null -> InputField.GALLERY
                UserPostSearchRunner.validate(request.copy(nickname = DEFAULT_NICKNAME)) == null -> InputField.NICKNAME
                else -> InputField.VALUE
            } to it
        }
        if (error == null) UserPostSearchRunner.start(request)
    }
    fun errorOf(field: InputField) = inputError?.takeIf { it.first == field }?.second

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val s = state) {
            is UserPostSearchState.Active -> {
                ActiveContent(uiColors, s, openPost)
                // 하나도 못 찾고 끝났으면 결과 칸이 같은 안내를 이미 한다.
                if (!(s.exhausted && s.posts.isEmpty() && s.error == null)) SearchNotes(uiColors)
                return@Column
            }
            is UserPostSearchState.Starting -> {
                StartingCard(uiColors, s.request)
                SearchNotes(uiColors)
                return@Column
            }
            is UserPostSearchState.Failed -> FailedCard(uiColors, s.message, onRetry = { UserPostSearchRunner.start(s.request) })
            UserPostSearchState.Idle -> Unit
        }

        SectionLabel("갤러리", uiColors)
        SearchCard(uiColors) {
            OutlinedTextField(
                value = gallery,
                onValueChange = { gallery = it; inputError = null },
                label = { Text("갤러리 주소 또는 ID") },
                placeholder = { Text("gall.dcinside.com/board/lists/?id=…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingText = { Text(errorOf(InputField.GALLERY) ?: "PC·모바일 주소, 갤러리 ID 모두 돼요") },
                isError = errorOf(InputField.GALLERY) != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                colors = dccleanerOutlinedTextFieldColors(uiColors),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.size(4.dp))
        SectionLabel("찾을 사람", uiColors)
        SearchCard(uiColors) {
            ChoiceRow(UserPostTargetKind.entries, kind, { if (it == UserPostTargetKind.UID) "식별코드" else "유동 IP" }, uiColors) {
                kind = it
                inputError = null
            }
            Spacer(Modifier.size(12.dp))
            when (kind) {
                UserPostTargetKind.UID -> SearchField(
                    value = uid, onValueChange = { uid = it; inputError = null }, label = "식별코드", placeholder = "예: cling2258",
                    hint = "갤로그 주소를 그대로 붙여 넣어도 돼요", error = errorOf(InputField.VALUE), uiColors = uiColors,
                    keyboardType = KeyboardType.Ascii, onDone = ::start
                )
                UserPostTargetKind.IP -> {
                    SearchField(
                        value = ip, onValueChange = { ip = it; inputError = null }, label = "IP 앞 두 자리", placeholder = "118.235",
                        hint = "글쓴이 닉네임 옆 괄호 안의 숫자예요", error = errorOf(InputField.VALUE), uiColors = uiColors,
                        keyboardType = KeyboardType.Decimal, onDone = null
                    )
                    Spacer(Modifier.size(4.dp))
                    SearchField(
                        value = nickname, onValueChange = { nickname = it; inputError = null }, label = "글쓴이 닉네임", placeholder = DEFAULT_NICKNAME,
                        hint = "디시 검색에 필요해요 · 유동 기본은 ㅇㅇ", error = errorOf(InputField.NICKNAME), uiColors = uiColors,
                        keyboardType = KeyboardType.Text, onDone = ::start
                    )
                    Spacer(Modifier.size(4.dp))
                    IpWarning(uiColors, null)
                }
            }
        }

        Spacer(Modifier.size(8.dp))
        Button(
            onClick = ::start,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary),
            shape = RoundedCornerShape(12.dp)
        ) { Text("글 찾기", fontWeight = FontWeight.Bold) }
        SearchNotes(uiColors)
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    hint: String,
    error: String?,
    uiColors: UiColors,
    keyboardType: KeyboardType,
    onDone: (() -> Unit)?
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, maxLines = 1) },
        supportingText = { Text(error ?: hint) },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = false,
            imeAction = if (onDone != null) ImeAction.Search else ImeAction.Next),
        keyboardActions = KeyboardActions(onSearch = { onDone?.invoke() }),
        colors = dccleanerOutlinedTextFieldColors(uiColors),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

/** 결과를 믿기 전에 알아야 할 한계 하나만 늘 보인다. */
@Composable
private fun SearchNotes(uiColors: UiColors) {
    Text("닉네임을 바꾸기 전에 쓴 글은 찾지 못해요", Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
}

/** 유동 IP는 앞 두 자리만 보여서 같은 대역의 다른 사람 글도 걸린다. 결과를 믿기 전에 꼭 보게 한다. */
@Composable
private fun IpWarning(uiColors: UiColors, ip: String?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(uiColors.warning.copy(alpha = 0.14f)).padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = uiColors.warningText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "같은 IP 대역${ip?.let { "($it)" }.orEmpty()}을 쓰는 다른 사람의 글이 섞일 수 있어요. 통신사 IP는 특히 많이 겹쳐요",
            style = MaterialTheme.typography.bodySmall, color = uiColors.warningText
        )
    }
}

@Composable
private fun StartingCard(uiColors: UiColors, request: UserPostSearchRequest) {
    SectionLabel("찾는 중", uiColors)
    SearchCard(uiColors) {
        Text(request.galleryInput, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (request.kind == UserPostTargetKind.UID) "식별코드 ${request.value}" else "${request.nickname}(${request.value})",
            style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.size(12.dp))
        // 식별코드는 갤로그에서 지금 닉네임을 받아 와야 검색할 수 있다. IP는 넣은 닉네임을 그대로 쓴다.
        Text(if (request.kind == UserPostTargetKind.UID) "갤러리와 닉네임을 확인하고 있어요" else "갤러리를 확인하고 있어요",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.size(10.dp))
        BusyBar(Modifier.fillMaxWidth(), color = uiColors.primary)
        OutlinedButton(
            onClick = UserPostSearchRunner::stop,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("취소") }
    }
}

@Composable
private fun FailedCard(uiColors: UiColors, message: String, onRetry: () -> Unit) {
    SearchCard(uiColors) {
        Text("글을 찾지 못했어요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = uiColors.danger)
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) { Text("같은 조건으로 다시 시도") }
    }
    Spacer(Modifier.size(8.dp))
}

@Composable
private fun ActiveContent(uiColors: UiColors, state: UserPostSearchState.Active, openPost: (RankingGallery, Long) -> Unit) {
    val target = state.target
    val ipMode = target.kind == UserPostTargetKind.IP
    SearchCard(uiColors) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(target.gallery.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                // 누구의 글인지 확인하는 유일한 표시라 자르지 않는다.
                Text("${target.nickname}(${target.value})", style = MaterialTheme.typography.bodyMedium)
            }
            // 오래 모은 결과를 실수로 지우지 않게, 찾은 글이 있거나 찾는 중이면 한 번 묻는다.
            // 문구는 누른 순간의 상태로 정한다(창이 열린 사이 찾기가 끝나도 맞지 않는 말이 남지 않게).
            var confirmText by remember { mutableStateOf<String?>(null) }
            TextButton(onClick = {
                confirmText = when {
                    state.posts.isEmpty() && !state.loading -> null.also { UserPostSearchRunner.clear() }
                    state.posts.isEmpty() -> "지금 찾던 것을 멈춰요."
                    state.loading -> "찾던 것을 멈추고, 찾은 글 ${"%,d".format(state.posts.size)}개도 사라져요."
                    else -> "찾은 글 ${"%,d".format(state.posts.size)}개가 사라져요."
                }
            }) {
                Text("새로 찾기", maxLines = 1)
            }
            confirmText?.let { text ->
                AlertDialog(
                    onDismissRequest = { confirmText = null },
                    title = { Text("새로 찾을까요?") },
                    text = { Text(text) },
                    confirmButton = {
                        Button(onClick = { confirmText = null; UserPostSearchRunner.clear() }, shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = uiColors.danger)) { Text("새로 찾기") }
                    },
                    dismissButton = { TextButton(onClick = { confirmText = null }) { Text("취소", color = uiColors.primary) } }
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        // 진행률은 큰 갤러리에서 거의 늘지 않아 오해만 줘서 보여 주지 않는다. 찾는 중인지는 목록 아래에서 알린다.
        // 작성자를 숨긴 갤러리라 멈춘 것도 끝(exhausted)으로 오므로, 그때는 다 확인했다고 하지 않는다.
        val done = state.exhausted && state.error == null
        // 비율 대신 최근 글을 얼마나(대략) 확인했는지 보여 준다. 첫 범위(글 약 1만 개)를 다 보기 전에는 붙이지 않는다.
        val checked = when {
            done -> " · 처음 글까지 다 확인했어요"
            state.checkedPosts >= 10_000 -> " · 최근 글 약 ${"%,d".format((state.checkedPosts + 5_000) / 10_000)}만 개 확인"
            else -> ""
        }
        Text("찾은 글 ${"%,d".format(state.posts.size)}개$checked",
            style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
    }
    if (ipMode) IpWarning(uiColors, target.value)

    Card(colors = CardDefaults.cardColors(containerColor = uiColors.card), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        // 매번 새 날짜 객체를 넘기면 글 줄이 모두 다시 그려져, 화면을 여는 동안 한 번만 정한다.
        val today = remember { LocalDate.now(KST) }
        // 바깥 화면이 통째로 스크롤돼 Lazy 목록을 못 쓴다. 수천 개를 한 번에 그리면 화면이 멈춰서 끊어서 그린다.
        // ponytail: 더 보기를 계속 누르면 결국 다 그린다. 그래도 느리면 화면을 Lazy 목록 구조로 바꿔야 한다.
        var shownCount by rememberSaveable(target) { mutableStateOf(POSTS_PER_SCREEN) }
        state.posts.take(shownCount).forEachIndexed { index, post ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = uiColors.outline)
            PostRow(uiColors, post, ipMode, today) { openPost(target.gallery, post.no) }
        }
        val hiddenCount = state.posts.size - shownCount
        if (hiddenCount > 0) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = uiColors.outline)
            // 서버에서 더 찾는 '더 불러오기'와 헷갈리지 않게 이미 찾은 글이라는 걸 밝힌다.
            TextButton(onClick = { shownCount += POSTS_PER_SCREEN }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("찾은 글 더 보기 · ${"%,d".format(hiddenCount)}개 남음", color = uiColors.primary)
            }
        }
        if (state.posts.isNotEmpty()) HorizontalDivider(color = uiColors.outline)
        ResultFooter(uiColors, state)
    }
}

/** 목록 아래: 지금 찾는 중인지, 더 찾을 수 있는지, 왜 멈췄는지. */
@Composable
private fun ResultFooter(uiColors: UiColors, state: UserPostSearchState.Active) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            state.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                // 매 프레임 움직이는 표시를 따로 그려, 같은 카드의 글 목록 전체가 매 프레임 다시 그려지지 않게 한다.
                BusyIndicator(Modifier.size(22.dp).graphicsLayer(), color = uiColors.primary, strokeWidth = 2.5.dp)
                Spacer(Modifier.width(12.dp))
                Text(if (state.untilEnd) "처음 글까지 찾는 중…" else "찾는 중…", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                OutlinedButton(onClick = UserPostSearchRunner::stop, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 44.dp)) { Text("중지", maxLines = 1) }
            }
            state.error != null -> {
                Text(state.error, style = MaterialTheme.typography.bodyMedium, color = uiColors.danger)
                // 작성자를 숨긴 갤러리처럼 끝까지 간 뒤의 실패는 다시 해도 같아서 버튼을 두지 않는다.
                if (!state.exhausted) FooterButton(uiColors, "다시 시도", primary = true, untilEnd = state.untilEnd)
            }
            state.exhausted -> if (state.posts.isEmpty()) {
                Text("찾은 글이 없어요", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("닉네임을 바꾸기 전에 쓴 글은 지금 닉네임으로 찾지 못해요. 식별코드·IP가 맞는지도 확인해 주세요",
                    style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
            } else {
                Text("더 찾을 글이 없어요", style = MaterialTheme.typography.bodyMedium, color = uiColors.textSecondary)
            }
            state.lastRoundEmpty -> {
                Text(if (state.posts.isEmpty()) "최근 범위에서는 글을 못 찾았어요. 더 예전 글까지 계속 찾을까요?"
                    else "최근 범위에서 더 못 찾았어요. 계속 찾을까요?", style = MaterialTheme.typography.bodyMedium)
                FooterButton(uiColors, "계속 찾기", primary = true)
                UntilEndButton(uiColors)
            }
            // 중지했거나 한 번 더 찾을 수 있을 때. 아무것도 못 찾고 멈췄으면 그 사실부터 알린다.
            state.posts.isEmpty() -> {
                Text("아직 찾은 글이 없어요", style = MaterialTheme.typography.bodyMedium, color = uiColors.textSecondary)
                FooterButton(uiColors, "계속 찾기", primary = true)
                UntilEndButton(uiColors)
            }
            else -> {
                FooterButton(uiColors, "더 불러오기", primary = false)
                UntilEndButton(uiColors)
            }
        }
    }
}

/** 계속 누르지 않아도 처음 글까지 이어서 찾는다. 찾는 동안 중지할 수 있다. */
@Composable
private fun UntilEndButton(uiColors: UiColors) {
    TextButton(onClick = { UserPostSearchRunner.loadMore(untilEnd = true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
        Text("처음 글까지 계속 찾기", color = uiColors.primary)
    }
}

@Composable
private fun FooterButton(uiColors: UiColors, label: String, primary: Boolean, untilEnd: Boolean = false) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    val onClick = { UserPostSearchRunner.loadMore(untilEnd) }
    if (primary) Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary)) { Text(label, fontWeight = FontWeight.Bold) }
    else OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp)) { Text(label) }
}

/** 디시 글 목록 한 줄처럼: 종류 아이콘, [말머리] 제목 [댓글 수], 아래에 글쓴이·날짜·조회·추천. 줄 전체를 누르면 글을 연다. */
@Composable
private fun PostRow(uiColors: UiColors, post: FoundPost, showIp: Boolean, today: LocalDate, onClick: () -> Unit) {
    val writer = if (showIp && post.ip.isNotEmpty()) "${post.nick}(${post.ip})" else post.nick
    val kindLabel = when (post.kind) { PostKind.TEXT -> "텍스트"; PostKind.IMAGE -> "이미지"; PostKind.MOVIE -> "동영상" }
    val description = listOfNotNull(
        if (post.recommended) "개념글" else null,
        "$kindLabel 글",
        post.subject.ifEmpty { null }?.let { "말머리 $it" },
        post.title,
        "댓글 ${post.commentCount}개",
        writer,
        spokenDate(post.time, today),
        "조회 ${post.views}",
        "추천 ${post.recommends}"
    ).joinToString(", ")
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "글 열기", onClick = onClick).heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            kindIcon(post.kind, post.recommended), contentDescription = null,
            tint = if (post.recommended) uiColors.primary else uiColors.textSecondary,
            modifier = Modifier.padding(top = 2.dp).size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = description }) {
            // 디시 목록처럼 댓글 수는 제목 바로 뒤에 붙인다. 제목이 두 줄을 넘어 줄여지면 댓글 수가 잘리지 않게
            // 떼어서 줄인 제목 끝(둘째 줄) 옆에 둔다.
            val title = (if (post.subject.isNotEmpty()) "[${post.subject}] " else "") + post.title
            val count = if (post.commentCount > 0) " [${post.commentCount}]" else ""
            var countApart by remember(post.no) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (countApart) AnnotatedString(title) else buildAnnotatedString {
                        append(title)
                        withStyle(SpanStyle(color = uiColors.primary, fontWeight = FontWeight.SemiBold)) { append(count) }
                    },
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!countApart && count.isNotEmpty() && it.hasVisualOverflow) countApart = true },
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (countApart) Text(count, style = MaterialTheme.typography.bodyMedium,
                    color = uiColors.primary, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Spacer(Modifier.size(4.dp))
            // 좁은 화면에서는 항목 단위로 줄을 바꾼다(닉네임이나 숫자 중간에서 끊기지 않게).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (post.recommended) MetaText("개념글", uiColors, accent = true)
                MetaText(writer, uiColors)
                MetaText(listDate(post.time, today), uiColors)
                MetaText("조회 ${"%,d".format(post.views)}", uiColors)
                MetaText("추천 ${"%,d".format(post.recommends)}", uiColors)
            }
        }
    }
}

@Composable
private fun MetaText(text: String, uiColors: UiColors, accent: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (accent) uiColors.primary else uiColors.textSecondary,
        fontWeight = if (accent) FontWeight.SemiBold else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 개념글은 같은 모양을 채운 아이콘으로 구분한다(색만으로 구분하지 않게). */
private fun kindIcon(kind: PostKind, recommended: Boolean): ImageVector = when (kind) {
    PostKind.TEXT -> if (recommended) Icons.AutoMirrored.Filled.Article else Icons.AutoMirrored.Outlined.Article
    PostKind.IMAGE -> if (recommended) Icons.Filled.Image else Icons.Outlined.Image
    PostKind.MOVIE -> if (recommended) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircle
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val monthDayFormat = DateTimeFormatter.ofPattern("MM.dd")
private val fullDateFormat = DateTimeFormatter.ofPattern("yy.MM.dd")

/** 디시 목록과 같게: 오늘은 시각, 올해는 월.일, 그 전은 연.월.일. */
private fun listDate(time: LocalDateTime, today: LocalDate): String = when {
    time.toLocalDate() == today -> time.format(timeFormat)
    time.year == today.year -> time.format(monthDayFormat)
    else -> time.format(fullDateFormat)
}

/** 화면 읽기 프로그램용 날짜. "10.21"은 숫자로 읽혀서 말로 쓴다. */
private fun spokenDate(time: LocalDateTime, today: LocalDate): String = when {
    time.toLocalDate() == today -> "오늘 ${time.hour}시 ${time.minute}분"
    time.year == today.year -> "${time.monthValue}월 ${time.dayOfMonth}일"
    else -> "${time.year}년 ${time.monthValue}월 ${time.dayOfMonth}일"
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, uiColors: UiColors, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = uiColors.primary.copy(alpha = 0.16f),
                    activeContentColor = uiColors.primary)
            ) { Text(label(option)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String, uiColors: UiColors) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = uiColors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
}

@Composable
private fun SearchCard(uiColors: UiColors, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = uiColors.card), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
