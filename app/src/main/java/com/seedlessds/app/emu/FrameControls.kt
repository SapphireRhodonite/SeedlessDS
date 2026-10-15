package com.seedlessds.app.emu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.seedlessds.app.control.NativeControl
import com.seedlessds.app.control.RuntimeControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FrameControls(modifier: Modifier) {
    if (!RuntimeControl.running) return
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<LongArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(false) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                status = withContext(Dispatchers.Default) { NativeControl.status() }
                delay(100)
            }
        }
    }
    val current = status
    val paused = current?.get(13) == 1L
    val enabled = current?.get(12) == 1L && current[16] == 0L &&
        RuntimeControl.running && !RuntimeControl.lifecyclePaused && !busy
    Row(
        modifier.background(Color(0xD9000000), RoundedCornerShape(8.dp)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = current?.let { "Frame ${it[0]}${if (paused) " II" else ""}${if (rejected) " !" else ""}" } ?: "Frame …",
            color = Color.White,
            fontSize = 16.sp,
        )
        TextButton(enabled = enabled, onClick = {
            busy = true
            scope.launch {
                try {
                    status = withContext(Dispatchers.Default) {
                        NativeControl.pause(!paused)
                        NativeControl.status()
                    }
                    rejected = false
                } finally { busy = false }
            }
        }) { Text(if (paused) ">" else "II", color = Color.White, fontSize = 18.sp) }
        TextButton(enabled = enabled && paused, onClick = {
            busy = true
            scope.launch {
                try {
                    val accepted = withContext(Dispatchers.Default) { NativeControl.step(1) }
                    status = withContext(Dispatchers.Default) { NativeControl.status() }
                    rejected = !accepted
                } finally { busy = false }
            }
        }) { Text("+1", color = Color.White, fontSize = 18.sp) }
    }
}
