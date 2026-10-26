package com.dccleaner.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.network.COMMENT_CLEANER_REGEX_PREFIX
import com.dccleaner.app.network.commentCleanerPatterns
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors

private const val COMMENT_CLEANER_KEYWORDS = "keywords"
private const val COMMENT_CLEANER_INTERVAL = "interval_seconds"
private const val COMMENT_CLEANER_DURATION = "monitor_minutes"
private const val COMMENT_CLEANER_ENABLED = "enabled"

/** 저장된 댓글 자동 정리 설정. 꺼져 있으면 차단 단어가 비어 있다. */
data class SavedCommentCleanerSettings(
    val keywords: List<String>,
    val intervalSeconds: Int,
    val monitorMinutes: Int
)

private fun parseKeywords(text: String) =
    text.lineSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()

/** 로그인 직후 자동으로 감시를 다시 켤 때 쓴다. */
fun loadSavedCommentMonitor(prefs: KeyValueStore) = SavedCommentCleanerSettings(
    keywords = if (!prefs.getBoolean(COMMENT_CLEANER_ENABLED, false)) emptyList()
        else parseKeywords(prefs.getString(COMMENT_CLEANER_KEYWORDS, "")),
    intervalSeconds = prefs.getInt(COMMENT_CLEANER_INTERVAL, 60).coerceAtLeast(5),
    monitorMinutes = prefs.getInt(COMMENT_CLEANER_DURATION, 10).coerceAtLeast(1)
)

@Stable
class CommentMonitorState(private val prefs: KeyValueStore) {
    var keywords by mutableStateOf(prefs.getString(COMMENT_CLEANER_KEYWORDS, ""))
    var intervalSeconds by mutableStateOf(prefs.getInt(COMMENT_CLEANER_INTERVAL, 60).toString())
    var monitorMinutes by mutableStateOf(prefs.getInt(COMMENT_CLEANER_DURATION, 10).toString())
    var enabled by mutableStateOf(prefs.getBoolean(COMMENT_CLEANER_ENABLED, false))
        private set
    var error by mutableStateOf<String?>(null)

    // 켜기 전에 고친 값도 바로 저장해 다른 탭에 다녀와도 지워지지 않게 한다. 켜 있는 동안은 입력이 잠겨 있고,
    // 다시 로그인할 때는 켜져 있을 때만 이 값을 읽으므로 고치는 중인 값으로 시작하지 않는다.
    fun editKeywords(value: String) {
        keywords = value
        prefs.putString(COMMENT_CLEANER_KEYWORDS, value)
    }

    fun editInterval(value: String) {
        intervalSeconds = value.filter(Char::isDigit)
        intervalSeconds.toIntOrNull()?.let { prefs.putInt(COMMENT_CLEANER_INTERVAL, it) }
    }

    fun editDuration(value: String) {
        monitorMinutes = value.filter(Char::isDigit)
        monitorMinutes.toIntOrNull()?.let { prefs.putInt(COMMENT_CLEANER_DURATION, it) }
    }

    /** 켤 때는 설정을 확인해 저장한 뒤 감시를 시작하고, 문제가 있으면 오류 문구를 돌려준다. */
    private fun apply(on: Boolean, onStart: (List<String>, Int, Int) -> Unit, onStop: () -> Unit): String? {
        if (!on) {
            onStop()
            return null
        }
        val keywordList = parseKeywords(keywords)
        val interval = intervalSeconds.toIntOrNull()
        val duration = monitorMinutes.toIntOrNull()
        val error = when {
            keywordList.isEmpty() -> "차단할 단어를 하나 이상 넣어 주세요"
            runCatching { commentCleanerPatterns(keywordList) }.isFailure -> "‘${COMMENT_CLEANER_REGEX_PREFIX}’ 줄의 패턴이 올바르지 않아요"
            interval == null || interval < 5 -> "확인 간격은 5초 이상으로 해 주세요"
            duration == null || duration < 1 -> "감시 시간은 1분 이상으로 해 주세요"
            else -> null
        }
        if (error != null) return error
        prefs.putString(COMMENT_CLEANER_KEYWORDS, keywords)
        prefs.putInt(COMMENT_CLEANER_INTERVAL, interval!!)
        prefs.putInt(COMMENT_CLEANER_DURATION, duration!!)
        onStart(keywordList, interval, duration)
        return null
    }

    fun setEnabled(on: Boolean, onStart: (List<String>, Int, Int) -> Unit, onStop: () -> Unit) {
        error = apply(on, onStart, onStop)
        if (error == null) {
            enabled = on
            prefs.putBoolean(COMMENT_CLEANER_ENABLED, on)
        }
        prefs.flush()
    }
}

@Composable
fun rememberCommentMonitorState(prefs: KeyValueStore) = remember(prefs) { CommentMonitorState(prefs) }

/** 댓글 자동 정리 화면. 설정을 시트 없이 바로 보여 준다. 안드로이드와 PC가 같이 쓴다. */
@Composable
fun CommentCleanerScreen(
    state: CommentMonitorState,
    running: Boolean,
    platformNote: String,
    uiColors: UiColors,
    onStartCommentCleaner: (List<String>, Int, Int) -> Unit,
    onStopCommentCleaner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keywordCount = parseKeywords(state.keywords).size
    // 켜져 있는 동안은 설정을 잠근다. 바꾸려면 끄고 다시 켠다.
    val locked = state.enabled

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "켠 뒤에 새로 쓴 내 글에 차단할 단어가 든 댓글이 달리면 자동으로 지워요",
            style = MaterialTheme.typography.bodyMedium,
            color = uiColors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        CommentCleanerCard(uiColors) {
            SheetSwitchRow(
                uiColors = uiColors,
                checked = state.enabled,
                title = "댓글 자동 정리 사용",
                subtitle = when {
                    !state.enabled -> "켜기 전에 쓴 글은 건드리지 않아요"
                    running -> "실행 중 · 차단 단어 ${keywordCount}개 · 설정을 바꾸려면 먼저 꺼 주세요"
                    else -> "켜져 있지만 지금은 멈춰 있어요"
                },
                onCheckedChange = { state.setEnabled(it, onStartCommentCleaner, onStopCommentCleaner) },
                // 도는지 멈췄는지가 이 화면에서 가장 중요한 상태라 색으로도 구분한다.
                subtitleColor = when {
                    !state.enabled -> Color.Unspecified
                    running -> uiColors.success
                    else -> uiColors.warningText
                }
            )
            state.error?.let { SheetErrorText(it) }
            // 켜 둔 채 멈췄으면(앱·서비스가 다시 켜진 경우 등) 껐다 켤 필요 없이 바로 다시 시작한다.
            if (state.enabled && !running) TextButton(
                onClick = { state.setEnabled(true, onStartCommentCleaner, onStopCommentCleaner) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("다시 시작", color = uiColors.primary, fontWeight = FontWeight.SemiBold) }
        }

        Spacer(Modifier.height(4.dp))
        SectionLabel("차단할 단어", uiColors)
        CommentCleanerCard(uiColors) {
            OutlinedTextField(
                value = state.keywords,
                onValueChange = state::editKeywords,
                enabled = !locked,
                // 칸 위 제목이 따로 있어 화면 읽기 프로그램에는 칸 이름을 따로 알려 준다.
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).semantics { contentDescription = "차단할 단어" },
                // 예시가 입력한 단어처럼 보이지 않게 흐리게, '예)'를 붙여 보여 준다.
                placeholder = {
                    Text("예) 광고\n홍보\n${COMMENT_CLEANER_REGEX_PREFIX}(광고|홍보)\\d+",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                },
                shape = RoundedCornerShape(12.dp),
                colors = dccleanerOutlinedTextFieldColors(uiColors)
            )
            Text(
                "한 줄에 하나씩 써 주세요 · 고급: ‘${COMMENT_CLEANER_REGEX_PREFIX}’으로 시작하면 패턴으로 찾아요 · ${keywordCount}개",
                style = MaterialTheme.typography.bodySmall,
                color = uiColors.textSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(4.dp))
        SectionLabel("감시 설정", uiColors)
        CommentCleanerCard(uiColors) {
            Row {
                OutlinedTextField(
                    value = state.intervalSeconds,
                    onValueChange = state::editInterval,
                    enabled = !locked,
                    label = { Text("확인 간격(초)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = dccleanerOutlinedTextFieldColors(uiColors)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.monitorMinutes,
                    onValueChange = state::editDuration,
                    enabled = !locked,
                    label = { Text("감시 시간(분)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = dccleanerOutlinedTextFieldColors(uiColors)
                )
            }
            Text(
                "확인 간격마다 갤로그에서 내 새 글을 찾고, 새 글마다 감시 시간 동안 댓글을 확인해요",
                style = MaterialTheme.typography.bodySmall,
                color = uiColors.textSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // 라이트에서는 상자 배경이 화면 배경과 같아 보이지 않으므로 아이콘 줄로 둔다.
        Row(Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = uiColors.textSecondary,
                modifier = Modifier.padding(top = 1.dp).size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(platformNote, style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
        }
    }
}

@Composable
private fun CommentCleanerCard(uiColors: UiColors, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = uiColors.card), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}
