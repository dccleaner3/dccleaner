package com.dccleaner.app.util

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread

/**
 * 본인만 읽을 수 있는 로그 파일. [maxBytes]마다 회전하고 지난 파일은 gzip으로 압축해
 * [keepDays]일(최대 [maxArchives]개) 보관한다. 줄마다 파일을 다시 열지 않고 열린 채로 쓴다.
 */
class RotatingLogFile(
    private val file: File,
    private val maxBytes: Long,
    private val keepDays: Long,
    private val maxArchives: Int
) {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    private val archiveLock = Any()
    private var writer: BufferedWriter? = null
    private var size = 0L

    init {
        file.parentFile?.mkdirs()
        if (file.exists()) restrict(file) else createPrivate(file)
        // 이전 실행이 압축 도중 꺼졌거나 구 형식(.1)으로 남긴 파일도 정리한다.
        thread(isDaemon = true, name = "log-archive") { archive() }
    }

    @Synchronized
    fun append(line: String) {
        if (writer == null) size = file.length()
        // 방금 쓴 줄이 바로 보관본으로 넘어가지 않게 쓰기 전에 회전한다.
        if (size >= maxBytes) rotate()
        val out = writer ?: FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).also { writer = it }
        out.write(line); out.write("\n")
        // 강제 종료되어도 직전 줄까지 남도록 줄마다 비운다. 비용은 줄마다 열고 닫기보다 훨씬 작다.
        out.flush()
        size += Utf8.size(line) + 1
    }

    private fun rotate() {
        writer?.close(); writer = null
        val base = "${file.path}.${LocalDateTime.now().format(stamp)}"
        val rotated = generateSequence(0) { it + 1 }.map { if (it == 0) File(base) else File("$base-$it") }
            .first { !it.exists() && !File(it.path + ".gz").exists() }
        Files.move(file.toPath(), rotated.toPath())
        createPrivate(file)
        size = 0
        // 압축은 로그 쓰기를 막지 않게 따로 한다.
        thread(isDaemon = true, name = "log-archive") { archive() }
    }

    /** 회전된 원본을 .gz로 바꾸고 보관 기간·개수를 넘긴 압축본을 지운다. */
    fun archive() = synchronized(archiveLock) {
        val dir = file.absoluteFile.parentFile ?: return
        val prefix = file.name + "."
        dir.listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".gz.tmp") }?.forEach { it.delete() }
        dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) && !f.name.endsWith(".gz") }?.forEach { raw ->
            val gz = File(raw.path + ".gz")
            val tmp = File(gz.path + ".tmp")
            createPrivate(tmp)
            GZIPOutputStream(tmp.outputStream()).use { out -> raw.inputStream().use { it.copyTo(out) } }
            Files.move(tmp.toPath(), gz.toPath(), StandardCopyOption.REPLACE_EXISTING)
            raw.delete()
        }
        val cutoff = System.currentTimeMillis() - keepDays * 24 * 60 * 60 * 1000
        dir.listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".gz") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .forEachIndexed { i, f -> if (i >= maxArchives || f.lastModified() < cutoff) f.delete() }
    }

    private fun createPrivate(target: File) {
        Files.deleteIfExists(target.toPath())
        try {
            Files.createFile(target.toPath(), PosixFilePermissions.asFileAttribute(PRIVATE))
        } catch (_: UnsupportedOperationException) {
            Files.createFile(target.toPath()) // Windows: 사용자 프로필 폴더 권한을 따른다.
        }
    }

    /** 예전 버전이 모두 읽기(644)로 만든 파일도 본인 전용으로 바꾼다. */
    private fun restrict(target: File) {
        runCatching { Files.setPosixFilePermissions(target.toPath(), PRIVATE) }
    }

    private companion object { val PRIVATE = PosixFilePermissions.fromString("rw-------") }

    private object Utf8 {
        fun size(text: String): Int {
            var bytes = 0
            for (c in text) bytes += when { c.code < 0x80 -> 1; c.code < 0x800 -> 2; Character.isSurrogate(c) -> 2; else -> 3 }
            return bytes
        }
    }
}
