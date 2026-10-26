package com.dccleaner.app.ui.ranking

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dccleaner.app.model.UiColors

/**
 * 더보기의 갤창랭킹 화면. 수집·이미지 저장이 JVM·플랫폼 기능이라 jvmMain에서 구현한다.
 * [onSendGuestbook]은 결과에 잡힌 고닉·반고닉 식별코드를 방명록 받는 사람으로 넘긴다.
 * [guestbookSending]이면 보내는 중인 명단과 헷갈리지 않게 그 버튼을 끈다.
 */
@Composable
expect fun GalleryRankingContent(uiColors: UiColors, onSendGuestbook: (List<String>) -> Unit, guestbookSending: Boolean,
                                 modifier: Modifier = Modifier)

/** 로그아웃하면 만들던 것과 결과를 모두 지운다. 화면을 떠났다 오는 것만으로는 결과를 지우지 않는다. */
expect fun clearGalleryRanking()

/** 더보기 줄과 아래 탭에 띄울 진행 상태. 만드는 중이 아니면 null. */
@Composable
expect fun galleryRankingBadge(): String?
