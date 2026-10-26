package com.dccleaner.app.model

object DeleteTaskStartValidator {
    fun hasCompleteGalleryMap(
        selectedGalleries: List<String>,
        galleryMap: Map<String, String>
    ): Boolean = selectedGalleries.isNotEmpty() &&
            selectedGalleries.all { galleryId ->
                !galleryMap[galleryId].isNullOrBlank()
            }

    /**
     * 고른 갤러리의 id → 이름. 목록에서 사라진 갤러리는 뺀다(화면을 그리는 중에도 불려서 예외를 던지면 안 된다).
     * 시작할 때는 [hasCompleteGalleryMap]으로 먼저 막으므로 빠지는 갤러리 없이 지운다.
     */
    fun selectedGalleryMap(
        selectedGalleries: List<String>,
        galleryMap: Map<String, String>
    ): Map<String, String> = selectedGalleries.mapNotNull { galleryId -> galleryMap[galleryId]?.let { galleryId to it } }.toMap()

    fun retainAvailableSelection(
        selectedGalleries: List<String>,
        galleryMap: Map<String, String>
    ): List<String> = selectedGalleries.filter(galleryMap::containsKey)
}
