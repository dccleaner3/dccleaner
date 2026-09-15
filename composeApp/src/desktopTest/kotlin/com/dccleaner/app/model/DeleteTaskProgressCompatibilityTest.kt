package com.dccleaner.app.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class DeleteTaskProgressCompatibilityTest {
    @Test
    fun missingViewFieldsFromOldCheckpointDoNotEnableViewFilter() {
        val gson = Gson()
        val task = DeleteTaskProgress(
            loginId = "test",
            deleteType = "posting",
            selectedGalleries = listOf("test"),
            galleryMap = mapOf("test" to "테스트"),
            viewFilterEnabled = true,
            minViewToKeep = 100
        )
        val oldCheckpoint = JsonParser.parseString(gson.toJson(task)).asJsonObject.apply {
            remove("viewFilterEnabled")
            remove("minViewToKeep")
        }

        val normalized = gson.fromJson(oldCheckpoint, DeleteTaskProgress::class.java)
            .copy(twoCaptchaApiKey = "")
            .normalizedForExecution()

        assertFalse(normalized.viewFilterEnabled)
        assertEquals(-1, normalized.minViewToKeep)
    }
}
