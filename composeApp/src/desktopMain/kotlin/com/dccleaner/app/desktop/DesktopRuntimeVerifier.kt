package com.dccleaner.app.desktop

import com.dccleaner.app.model.DeleteTaskProgress
import java.nio.file.Files
import java.util.UUID

internal fun verifyPackagedStorageRuntime() {
    if (credentialStorageSupported()) DesktopCredentialStore()
    if (System.getProperty("os.name").lowercase().contains("win")) {
        val backend = WindowsCredentialBackend()
        val name = "runtime-verifier-${UUID.randomUUID()}"
        try {
            backend.write(name, "한글-test")
            check(backend.read(name) == "한글-test") { "Windows credential round trip failed" }
        } finally {
            backend.delete(name)
        }
        check(backend.read(name) == null) { "Windows credential deletion failed" }
    }
    val root = Files.createTempDirectory("dccleaner-packaged-runtime").toFile()
    try {
        val task = DeleteTaskProgress(
            loginId = "runtime-verifier",
            deleteType = "posting",
            selectedGalleries = listOf("gallery"),
            galleryMap = mapOf("gallery" to "Gallery")
        )
        check(DesktopDeleteTaskStore(root).save(task)) {
            "Packaged runtime could not save a deletion checkpoint"
        }
        val restored = DesktopDeleteTaskStore(root).get(task.id)
        check(restored?.id == task.id && restored.loginId == task.loginId) {
            "Packaged runtime could not restore a deletion checkpoint"
        }
        println("DESKTOP_RUNTIME_RESTORE_OK")
    } finally {
        root.deleteRecursively()
    }
}
