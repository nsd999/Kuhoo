package com.kuhoo.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.kuhoo.di.appModule
import com.kuhoo.ui.KuhooApp
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(appModule)
    }

    CanvasBasedWindow(canvasElementId = "ComposeTarget", title = "Kuhoo Music") {
        KuhooApp()
    }
}
