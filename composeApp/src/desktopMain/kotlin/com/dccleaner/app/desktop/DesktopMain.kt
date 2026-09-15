package com.dccleaner.app.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import org.jetbrains.skia.Image

fun main(args: Array<String>) {
    if (args.contentEquals(arrayOf("--verify-storage-runtime"))) {
        verifyPackagedStorageRuntime()
        return
    }

    val instanceLock = DesktopSingleInstanceLock.tryAcquire()
    if (instanceLock == null) {
        System.err.println("DCCleaner Mobile is already running.")
        return
    }
    instanceLock.use {
        application {
            val appIcon = remember {
                Thread.currentThread().contextClassLoader
                    .getResourceAsStream("icons/app.png")
                    ?.use { Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap() }
                    ?.let(::BitmapPainter)
            }
            val windowState = rememberWindowState(
                width = 450.dp,
                height = 800.dp,
                position = WindowPosition(Alignment.Center)
            )
            Window(
                state = windowState,
                onCloseRequest = ::exitApplication,
                title = "디시클리너 모바일 & PC",
                icon = appIcon
            ) {
                LaunchedEffect(Unit) {
                    window.minimumSize = Dimension(380, 640)
                }
                DesktopDccleanerApp()
            }
        }
    }
}
