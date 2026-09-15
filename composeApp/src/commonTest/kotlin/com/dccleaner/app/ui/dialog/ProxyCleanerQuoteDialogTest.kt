package com.dccleaner.app.ui.dialog

import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyCleanerQuoteDialogTest {
    @Test
    fun applicationMessageIncludesDeletionScope() {
        assertEquals(
            """대리 클리너 신청
게시글 수: 0
댓글 수: 2400
작업 범위: 전체 갤러리 중 1개 갤러리 제외하고 삭제
제외할 갤러리: 책 갤러리
총 삭제 대상: 2400개
예상 소요 시간: 40분
총 견적 금액: 1700원
결제 방식: 카카오톡 송금하기""",
            buildProxyCleanerApplicationMessage(
                postCount = 0,
                commentCount = 2400,
                gallList = linkedMapOf(
                    "book" to "책 갤러리",
                    "baseball_new13" to "국내야구 갤러리",
                    "test1" to "테스트 1",
                    "test2" to "테스트 2"
                ),
                selectedGalleries = listOf("baseball_new13", "test1", "test2"),
                totalCount = 2400,
                estimatedTime = "40분",
                finalPrice = 1700
            )
        )
        assertEquals(
            "작업 범위: 전체 갤러리 중 1개 갤러리만 삭제\n삭제할 갤러리: 책 갤러리",
            buildGalleryScopeText(
                linkedMapOf("book" to "책 갤러리", "baseball_new13" to "국내야구 갤러리", "test" to "테스트"),
                listOf("book")
            )
        )
    }
}
