package com.dccleaner.app.ui.cleaner

import com.dccleaner.app.model.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.dccleaner.app.ui.theme.BusyIndicator

@Composable
fun CaptchaAutomationCard(uiColors: UiColors, isTwocaptchaValid: Boolean?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Key, contentDescription = null, tint = uiColors.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("캡챠 자동 해결", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (isTwocaptchaValid == true) "2Captcha 연결됨" else "캡챠를 자동으로 풀어요",
                    style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "알아보기", tint = uiColors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun TwoCaptchaSetupDialog(
    uiColors: UiColors,
    twocaptchaKey: String,
    onTwocaptchaKeyChange: (String) -> Unit,
    isTwocaptchaValid: Boolean?,
    onTwocaptchaValidChange: (Boolean?) -> Unit,
    isCheckingTwocaptcha: Boolean,
    onIsCheckingTwocaptchaChange: (Boolean) -> Unit,
    coroutine: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onValidateTwocaptchaKey: suspend (String) -> Boolean,
    onOpenAutoCaptchaGuide: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = uiColors.background) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "비교로 돌아가기") }
                    Text("2Captcha 설정", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = uiColors.outline)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = onOpenAutoCaptchaGuide,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("2Captcha 키 얻기")
                    }
                    CaptchaKeyCard(uiColors, twocaptchaKey, onTwocaptchaKeyChange, isTwocaptchaValid,
                        onTwocaptchaValidChange, isCheckingTwocaptcha, onIsCheckingTwocaptchaChange,
                        coroutine, snackbarHostState, onValidateTwocaptchaKey)
                }
            }
        }
    }
}

@Composable
fun CaptchaKeyCard(
    uiColors: UiColors,
    twocaptchaKey: String,
    onTwocaptchaKeyChange: (String) -> Unit,
    isTwocaptchaValid: Boolean?,
    onTwocaptchaValidChange: (Boolean?) -> Unit,
    isCheckingTwocaptcha: Boolean,
    onIsCheckingTwocaptchaChange: (Boolean) -> Unit,
    coroutine: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onValidateTwocaptchaKey: suspend (String) -> Boolean
) {
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),

        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "2Captcha 연동",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    isTwocaptchaValid?.let { isValid ->
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            if (isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isValid) uiColors.success else uiColors.danger,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Text(
                when { isTwocaptchaValid == true -> "연결됨 · 캡챠를 자동으로 풀어요"; twocaptchaKey.isNotBlank() -> "API 키를 확인해 주세요"; else -> "2Captcha 충전 후 API 키를 넣으면 캡챠를 자동으로 풀어요" },
                style = MaterialTheme.typography.bodySmall,
                color = uiColors.textSecondary
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = twocaptchaKey,
                onValueChange = {
                    onTwocaptchaKeyChange(it)
                    onTwocaptchaValidChange(null)
                },
                label = { Text("2Captcha API 키") },
                placeholder = { Text("API 키를 붙여 넣어 주세요") },
                isError = isTwocaptchaValid == false,
                supportingText = if (isTwocaptchaValid == false) {
                    { Text("키가 맞지 않아요. 다시 확인해 주세요") }
                } else null,
                leadingIcon = {
                    Icon(Icons.Default.Key, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = dccleanerOutlinedTextFieldColors(uiColors),
                singleLine = true,
                enabled = !isCheckingTwocaptcha
            )

            Spacer(Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                onClick = {
                    if (twocaptchaKey.isNotBlank()) {
                        coroutine.launch {
                            onIsCheckingTwocaptchaChange(true)
                            val success = onValidateTwocaptchaKey(twocaptchaKey)
                            onTwocaptchaValidChange(success)
                            onIsCheckingTwocaptchaChange(false)

                            if (success) {
                                snackbarHostState.showSnackbar("2Captcha가 연결됐어요")
                            } else {
                                snackbarHostState.showSnackbar("키가 맞지 않아요")
                            }
                        }
                    }
                },
                enabled = twocaptchaKey.isNotBlank() && !isCheckingTwocaptcha,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor
                )
            ) {
                if (isCheckingTwocaptcha) {
                    BusyIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("확인 중…")
                } else {
                    when (isTwocaptchaValid) {
                        true -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("연결됨")
                        }

                        false -> {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("다시 확인")
                        }

                        null -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("키 확인")
                        }
                    }
                }
            }

        }
    }
}
