package com.kuhoo.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kuhoo.di.appModule
import com.kuhoo.ui.KuhooApp
import org.koin.core.context.startKoin

fun main() = application {
    startKoin {
        modules(appModule)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Kuhoo Music",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        KuhooApp()
    }
}
