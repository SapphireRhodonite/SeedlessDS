package com.seedlessds.app.emu

import com.seedlessds.app.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsHost(gameKey: String?, onExit: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val ctx = LocalContext.current
    var sub by remember { mutableIntStateOf(0) }
    var s by remember {
        mutableStateOf(run {
            SettingsRepo.load(ctx)
            if (gameKey == null) SettingsRepo.global else SettingsRepo.perGame(ctx, gameKey)
        })
    }
    var hasPg by remember { mutableStateOf(gameKey != null && SettingsRepo.hasPerGame(ctx, gameKey)) }

    when (sub) {
        1 -> KeyMapperScreen { sub = 0 }
        2 -> MappingInfoScreen { sub = 0 }
        3 -> SystemToolsScreen { sub = 0 }
        else -> SettingsScreen(
            initial = s,
            onSave = { new ->
                s = new
                if (gameKey == null) SettingsRepo.saveGlobal(ctx, new, false)
                else { SettingsRepo.savePerGame(ctx, gameKey, new); hasPg = true }
            },
            onOpenKeyMapper = { sub = 1 },
            onOpenMappingInfo = { sub = 2 },
            onOpenSystemTools = { sub = 3 },
            onBack = onExit,
            subtitle = if (gameKey == null) null
            else uiContext.getString(R.string.common_only_for, gameKey, if (hasPg) uiContext.getString(R.string.common_active_2) else uiContext.getString(R.string.menu_global)),
            onReset = if (gameKey == null) null
            else ({ SettingsRepo.clearPerGame(ctx, gameKey); hasPg = false }),
        )
    }
}
