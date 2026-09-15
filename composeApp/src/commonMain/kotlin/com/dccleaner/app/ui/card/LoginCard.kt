package com.dccleaner.app.ui.card

import com.dccleaner.app.model.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors

@Composable
fun LoginCard(
    uiColors: UiColors,
    savedAccounts: List<SavedAccount>,
    id: String,
    onIdChange: (String) -> Unit,
    pw: String,
    onPwChange: (String) -> Unit,
    saveLogin: Boolean,
    credentialStorageSupported: Boolean = true,
    onSaveLoginChange: (Boolean) -> Unit,
    isLoggingIn: Boolean,
    sessionExpired: Boolean,
    onSavedAccountClick: (SavedAccount) -> Unit,
    onDeleteSavedAccountClick: (SavedAccount) -> Unit,
    onLoginClick: () -> Unit
) {
    val primaryColor = uiColors.primary
    val backgroundColor = uiColors.surfaceVariant
    val cardColor = uiColors.card
    val passwordFocusRequester = remember { FocusRequester() }
    val loginEnabled = !isLoggingIn && id.isNotBlank() && pw.isNotBlank()
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, uiColors.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AccountBox,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "로그인",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor
                )
            }

            Spacer(Modifier.height(20.dp))

            if (sessionExpired) {
                Text(
                    "로그인 세션이 만료되었습니다. 다시 로그인해주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = uiColors.danger
                )
                Spacer(Modifier.height(16.dp))
            }

            // 저장된 계정 목록
            if (savedAccounts.isNotEmpty()) {
                Text(
                    "저장된 계정",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor
                )
                Spacer(Modifier.height(12.dp))

                savedAccounts.forEach { account ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSavedAccountClick(account)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = backgroundColor
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    account.id,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (account.nickname.isNotEmpty()) {
                                    Text(
                                        account.nickname,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    onDeleteSavedAccountClick(account)
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "계정 삭제",
                                    tint = uiColors.danger,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = id,
                onValueChange = onIdChange,
                label = { Text("아이디") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocusRequester.requestFocus() }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = dccleanerOutlinedTextFieldColors(uiColors)
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = pw,
                onValueChange = onPwChange,
                label = { Text("비밀번호") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        passwordVisible = true
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            passwordVisible = false
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = "누르는 동안 비밀번호 표시"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester),
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (loginEnabled) onLoginClick() }
                ),
                shape = RoundedCornerShape(12.dp),
                colors = dccleanerOutlinedTextFieldColors(uiColors)
            )

            Spacer(Modifier.height(16.dp))

            if (credentialStorageSupported) {
                // 로그인 정보 저장 체크박스
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onSaveLoginChange(!saveLogin) }
                ) {
                    Checkbox(
                        checked = saveLogin,
                        onCheckedChange = onSaveLoginChange,
                        colors = CheckboxDefaults.colors(checkedColor = primaryColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "로그인 정보 저장",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(24.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                onClick = onLoginClick,
                enabled = loginEnabled, // 로그인 중이거나 입력값이 없으면 비활성화
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (sessionExpired) "재로그인 중..." else "로그인 중...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        if (sessionExpired) "재로그인" else "로그인",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
