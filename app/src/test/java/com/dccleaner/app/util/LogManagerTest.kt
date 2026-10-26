package com.dccleaner.app.util

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class LogManagerTest {
    @Test fun instancesSerializeRotationWritesReadsAndClear() = runBlocking {
        val directory = Files.createTempDirectory("dccleaner-log-test").toFile()
        val file = File(directory, "dccleaner_log.txt")
        val managers = listOf(LogManager { file }, LogManager { file })
        try {
            val original = "x".repeat(5 * 1024 * 1024 + 1)
            file.writeText(original)
            coroutineScope {
                repeat(200) { index ->
                    launch {
                        managers[index % 2].addLog("test", "entry-$index")
                        assertTrue(managers[(index + 1) % 2].readLogs().endsWith("\n"))
                    }
                }
            }
            assertEquals(original, File(directory, "dccleaner_log.txt.old").readText())
            val lines = file.readLines()
            assertEquals(200, lines.size)
            assertTrue(lines.all { it.matches(Regex("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}] \\[test] entry-\\d+")) })
            assertEquals((0 until 200).toSet(), lines.map { it.substringAfterLast('-').toInt() }.toSet())
            managers[0].clearLogs()
            assertEquals("로그가 없습니다.", managers[1].readLogs())
        } finally { directory.deleteRecursively() }
    }
}
