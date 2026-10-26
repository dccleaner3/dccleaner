package com.dccleaner.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DeleteTaskStartValidatorTest {
    @Test
    fun selectedGalleryMapSkipsGalleriesMissingFromTheList() {
        // 새로고침으로 목록에서 빠진 갤러리가 선택에 남아 있어도 화면 그리기가 죽지 않아야 한다.
        val list = mapOf("a" to "A갤", "b" to "B갤")
        assertEquals(mapOf("a" to "A갤"), DeleteTaskStartValidator.selectedGalleryMap(listOf("a", "gone"), list))
        // 시작은 hasCompleteGalleryMap이 막는다.
        assertFalse(DeleteTaskStartValidator.hasCompleteGalleryMap(listOf("a", "gone"), list))
    }
}
