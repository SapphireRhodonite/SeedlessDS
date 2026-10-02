package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyMapperScreen(onBack: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf<KeyMap.Btn?>(null) }
    var nameDialog by remember { mutableStateOf(false) }
    var capturingFn by remember { mutableStateOf<KeyMap.Special?>(null) }
    val profile = KeyMap.activeProfile(ctx).let { refresh; it }
    androidx.activity.compose.BackHandler { onBack() }

    if (nameDialog) KeyNamerDialog(profile, onDismiss = { nameDialog = false }, onNamed = { refresh++ })

    SeedlessScreenScaffold(
        title = uiContext.getString(R.string.keymap_map_controller),
        subtitle = uiContext.getString(R.string.keymap_physical_gamepad),
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    ) { pad ->
        val total = KeyMap.buttons.size + KeyMap.specials.size
        val cursor = rememberMenuCursor(
            count = if (capturing == null && capturingFn == null) total else 0,
            onAccept = { i ->
                if (i < KeyMap.buttons.size) capturing = KeyMap.buttons[i]
                else capturingFn = KeyMap.specials.getOrNull(i - KeyMap.buttons.size)
            },
            onCancel = {
                when {
                    capturing != null -> capturing = null
                    capturingFn != null -> capturingFn = null
                    else -> onBack()
                }
            },
        )
        val listState = rememberLazyListState()
        androidx.compose.runtime.LaunchedEffect(cursor.index) {
            val extraHeaders = if (cursor.index >= KeyMap.buttons.size) 1 else 0
            runCatching { listState.animateScrollToItem(cursor.index + 1 + extraHeaders) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(pad)
                .verticalScrollbar(listState, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        ) {
            item {
                @Suppress("UNUSED_EXPRESSION") refresh

                SectionHeader(uiContext.getString(R.string.keymap_mapping_profile))
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (0..2).forEach { pr ->
                        FilterChip(
                            selected = pr == profile,
                            onClick = { KeyMap.setActiveProfile(ctx, pr); refresh++ },
                            label = { Text(uiContext.getString(R.string.keymap_profile_2, pr + 1)) }
                        )
                    }
                }

                SectionHeader(uiContext.getString(R.string.keymap_device))
                IconCard(
                    icon = Icons.Rounded.SportsEsports,
                    title = uiContext.getString(R.string.keymap_profile_device),
                    subtitle = KeyMap.deviceName(ctx, profile).ifBlank { uiContext.getString(R.string.keymap_unnamed) }
                ) {
                    TextButton(onClick = { nameDialog = true }) { Text(uiContext.getString(R.string.common_name)) }
                }

                SectionHeader(uiContext.getString(R.string.keymap_ds_buttons))
            }

            itemsIndexed(KeyMap.buttons) { i, b ->
                @Suppress("UNUSED_EXPRESSION") refresh
                val kc = KeyMap.keyCodeOf(ctx, b.index)
                ButtonMapCard(
                    label = uiContext.getString(R.string.keymap_ds_button, uiContext.getString(b.label)),
                    keyName = KeyMap.keyName(uiContext, kc),
                    selected = i == cursor.index,
                    onMap = { cursor.select(i); capturing = b },
                    onClear = { KeyMap.clearMapping(ctx, b.index); refresh++ }
                )
            }

            item { SectionHeader(uiContext.getString(R.string.keymap_emulator_functions)) }

            itemsIndexed(KeyMap.specials) { i, sp ->
                @Suppress("UNUSED_EXPRESSION") refresh
                val kc = KeyMap.specialKeyCodeOf(ctx, sp.fn)
                ButtonMapCard(
                    label = uiContext.getString(sp.label),
                    keyName = if (kc >= 0) KeyMap.keyName(uiContext, kc) else uiContext.getString(R.string.keymap_unassigned_2),
                    selected = KeyMap.buttons.size + i == cursor.index,
                    onMap = { cursor.select(KeyMap.buttons.size + i); capturingFn = sp },
                    onClear = { KeyMap.clearSpecialMapping(ctx, sp.fn); refresh++ }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    capturing?.let { b ->
        CaptureOverlay(uiContext.getString(b.label), onCancel = { capturing = null }) { kc ->
            KeyMap.setMapping(ctx, b.index, kc); capturing = null; refresh++
        }
    }

    capturingFn?.let { sp ->
        CaptureOverlay(uiContext.getString(sp.label), onCancel = { capturingFn = null }) { kc ->
            KeyMap.setSpecialMapping(ctx, sp.fn, kc); capturingFn = null; refresh++
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 2.dp)
    )
}

@Composable
private fun IconCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, fontSize = 15.5.sp)
                Text(subtitle, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp, modifier = Modifier.padding(top = 1.dp))
            }
            trailing()
        }
    }
}

@Composable
private fun ButtonMapCard(
    label: String,
    keyName: String,
    selected: Boolean,
    onMap: () -> Unit,
    onClear: () -> Unit
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.surface2 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, colors.red) else null,
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.VideogameAsset, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 6.dp)) {
                Text(label, fontSize = 15.5.sp)
                Text(keyName, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp, modifier = Modifier.padding(top = 1.dp))
            }
            TextButton(onClick = onMap) { Text(uiContext.getString(R.string.keymap_map)) }
            TextButton(
                onClick = onClear,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text(uiContext.getString(R.string.common_clear)) }
        }
    }
}

@Composable
private fun KeyNamerDialog(profile: Int, onDismiss: () -> Unit, onNamed: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(KeyMap.deviceName(ctx, profile)) }
    var bonded by remember { mutableStateOf(KeyMap.bondedDeviceNames(ctx)) }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) bonded = KeyMap.bondedDeviceNames(ctx) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 31 && bonded.isEmpty())
            permLauncher.launch("android.permission.BLUETOOTH_CONNECT")
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiContext.getString(R.string.keymap_name_device)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(name, { name = it }, singleLine = true,
                    label = { Text(uiContext.getString(R.string.common_name)) }, modifier = Modifier.fillMaxWidth())
                if (bonded.isNotEmpty()) {
                    Text(uiContext.getString(R.string.keymap_paired_bluetooth_devices), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp))
                    bonded.forEach { dev ->
                        Text(dev, Modifier.fillMaxWidth().clickable { name = dev }.padding(vertical = 8.dp),
                            fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { KeyMap.setDeviceName(ctx, profile, name); onNamed(); onDismiss() }) { Text(uiContext.getString(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiContext.getString(R.string.common_cancel)) } }
    )
}

@Composable
private fun CaptureOverlay(label: String, onCancel: () -> Unit, onKey: (Int) -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val fr = remember { FocusRequester() }
    LaunchedEffect(label) { fr.requestFocus() }
    Box(
        Modifier.fillMaxSize()
            .focusRequester(fr)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) onKey(ev.nativeKeyEvent.keyCode)
                true
            }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(340.dp).padding(24.dp)
        ) {
            Column(
                Modifier.padding(28.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Keyboard, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp))
                }
                Text(uiContext.getString(R.string.keymap_press_a_key), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp))
                Text(uiContext.getString(R.string.keymap_press_the_physical_button_for, label),
                    fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                TextButton(onClick = onCancel) { Text(uiContext.getString(R.string.common_cancel)) }
            }
        }
    }
}
