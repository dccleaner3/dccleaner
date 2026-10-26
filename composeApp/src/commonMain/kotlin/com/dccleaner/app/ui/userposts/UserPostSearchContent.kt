package com.dccleaner.app.ui.userposts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dccleaner.app.model.UiColors

/** 더보기의 반고닉 글 찾기 화면. 검색·글 열기가 JVM·플랫폼 기능이라 jvmMain에서 구현한다. */
@Composable
expect fun UserPostSearchContent(uiColors: UiColors, modifier: Modifier = Modifier)

/** 로그아웃하면 찾던 것과 찾은 글을 모두 지운다. 화면을 떠났다 오는 것만으로는 지우지 않는다. */
expect fun clearUserPostSearch()

/** 더보기 줄에 띄울 진행 상태. 찾는 중이 아니면 null. */
@Composable
expect fun userPostSearchBadge(): String?
