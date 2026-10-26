package com.dccleaner.app.util

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals

class RotatingLogFileTest {
    @Test fun oldWorldReadableLogBecomesPrivateAndRotatesBeforeWrite() {
        val dir = Files.createTempDirectory("rotating-log").toFile()
        val file = dir.resolve("dccleaner.log").apply { writeText("x".repeat(100)) }
        // Windows에는 POSIX 권한이 없다(앱은 사용자 프로필 폴더 권한을 따른다). 권한 확인은 지원하는 곳에서만 한다.
        val posix = "posix" in FileSystems.getDefault().supportedFileAttributeViews()
        if (posix) Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
        val log = RotatingLogFile(file, maxBytes = 50, keepDays = 14, maxArchives = 5)
        if (posix) assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))
        log.append("새 줄")
        assertEquals("새 줄\n", file.readText())
        log.archive()
        assertEquals(1, dir.listFiles { f -> f.name.endsWith(".gz") }!!.size)
        dir.deleteRecursively()
    }
}
