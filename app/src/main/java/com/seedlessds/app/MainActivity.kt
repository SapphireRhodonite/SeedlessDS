package com.seedlessds.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.seedlessds.app.emu.ExternalIdleInfo
import com.seedlessds.app.emu.LibraryKeyRouter
import com.seedlessds.app.emu.MenuKeyRouter
import com.seedlessds.app.emu.ExternalInfoBus
import com.seedlessds.app.emu.ExternalInfoController
import com.seedlessds.app.emu.SeedlessTheme
import com.seedlessds.app.emu.EmulatorActivity
import com.seedlessds.app.emu.LibraryScreen
import com.seedlessds.app.emu.RomLibrary
import com.seedlessds.app.emu.SettingsHost

class MainActivity : ComponentActivity() {

    private val refresh = mutableIntStateOf(0)
    private val externalInfo by lazy { ExternalInfoController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val dark = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        setContent {
            SeedlessTheme {
                val folderPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree()
                ) { uri -> if (uri != null) { RomLibrary.addFolder(this, uri); refresh.intValue++ } }

                val singlePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> if (uri != null) launchRom(uri, uri.lastPathSegment ?: "game.nds", takePerm = true, loadSlot = -1) }

                var inSettings by remember { mutableStateOf(false) }
                var settingsGameKey by remember { mutableStateOf<String?>(null) }

                if (inSettings) {
                    SettingsHost(gameKey = settingsGameKey) { inSettings = false }
                } else {
                    LibraryScreen(
                        refreshKey = refresh.intValue,
                        onPlayNew = { entry ->
                            RomLibrary.markPlayed(this, entry)
                            launchRom(entry.uri, entry.name, takePerm = false, loadSlot = -1)
                        },
                        onGameSettings = { entry -> settingsGameKey = entry.title; inSettings = true },
                        onGlobalSettings = { settingsGameKey = null; inSettings = true },
                        onAddFolder = { folderPicker.launch(null) },
                        onOpenSingle = { singlePicker.launch(arrayOf("*/*")) },
                    )
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        com.seedlessds.app.emu.KeyCapture.onKey?.let { capture ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) capture(event.keyCode)
            return true
        }
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when {
                MenuKeyRouter.isPrevTab(event.keyCode) -> {
                    if (LibraryKeyRouter.cycleFilter?.invoke(false) == true) return true
                }
                MenuKeyRouter.isNextTab(event.keyCode) -> {
                    if (LibraryKeyRouter.cycleFilter?.invoke(true) == true) return true
                }
                event.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_X -> {
                    if (LibraryKeyRouter.openOptions?.invoke() == true) return true
                }
                event.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_Y -> {
                    if (LibraryKeyRouter.toggleFavorite?.invoke() == true) return true
                }
            }
            if (MenuKeyRouter.dispatch(event.keyCode)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        externalInfo.detach()
    }

    override fun onResume() {
        super.onResume()
        externalInfo.attach()
        externalInfo.setContent {
            val block = ExternalInfoBus.content.value
            if (block != null) block() else ExternalIdleInfo()
        }
        refresh.intValue++
    }

    private fun launchRom(uri: Uri, name: String, takePerm: Boolean, loadSlot: Int) {
        if (takePerm) runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent(this, EmulatorActivity::class.java).apply {
            putExtra(EmulatorActivity.EXTRA_ROM_URI, uri.toString())
            putExtra(EmulatorActivity.EXTRA_LOAD_SLOT, loadSlot)
        })
    }
}
