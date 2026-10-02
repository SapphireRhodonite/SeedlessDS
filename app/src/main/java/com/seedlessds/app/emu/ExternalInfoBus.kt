package com.seedlessds.app.emu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

object ExternalInfoBus {
    val content = mutableStateOf<(@Composable () -> Unit)?>(null)

    fun show(block: (@Composable () -> Unit)?) {
        content.value = block
    }

    fun clear() {
        content.value = null
    }
}
