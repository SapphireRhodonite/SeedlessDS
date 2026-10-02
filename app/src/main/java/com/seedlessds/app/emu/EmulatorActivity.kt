package com.seedlessds.app.emu

import com.seedlessds.app.R as AppR
import com.seedlessds.core.SeedlessCore

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import com.seedlessds.app.control.RuntimeControl
import com.seedlessds.app.SeedlessBridge
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.seedlessds.app.filesystem.PathCache

class EmulatorActivity : ComponentActivity() {
    private val reconSlot = java.util.concurrent.atomic.AtomicInteger(0)

    private var reconOpenRom: String? = null


    companion object {
        const val EXTRA_ROM_URI = "rom_uri"
        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_LOAD_SLOT = "load_slot"
        private const val TAG = "SeedlessEmu"
        @Volatile private var inited = false
        const val UP = 0; const val DOWN = 1; const val LEFT = 2; const val RIGHT = 3
        const val A = 4; const val B = 5; const val X = 6; const val Y = 7
        const val L = 8; const val R = 9; const val START = 10; const val SELECT = 11
    }

    private lateinit var glView: DsGlView
    private var emuThread: Thread? = null
    @Volatile private var romKey: String = "game.nds"
    private var extDisplay: ExternalDisplayManager? = null
    private val externalInfo by lazy { ExternalInfoController(this) }
    private var externalInfoActive = false
    private val extTapState = androidx.compose.runtime.mutableIntStateOf(0)
    @Volatile private var menuOpen = false
    @Volatile private var appForeground = true
    private var libraryUri: Uri? = null
    private var sessionStart = 0L
    @Volatile private var bootHold = true
    @Volatile private var booting = true
    private val bootingState = androidx.compose.runtime.mutableStateOf(true)
    private var bootTitle = ""

    private fun refreshExternalMode() {
        setExternalInfoMode(menuOpen || booting)
    }

    private fun refreshPauseState() {
        val pause = menuOpen || !appForeground || bootHold
        RuntimeControl.lifecyclePaused = pause
        try { SeedlessCore.pause(if (pause) 1 else 0) } catch (_: Throwable) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersive()
        EmuAssets.ensureInstalled(this)

        val data = intent.data
        val romPath = intent.getStringExtra(EXTRA_ROM_PATH)
            ?: data?.takeIf { it.scheme == "file" }?.path
        val romUri = intent.getStringExtra(EXTRA_ROM_URI)?.let { Uri.parse(it) }
            ?: data?.takeIf { it.scheme == "content" }
        val displayName: String = when {
            romPath != null -> { romKey = romPath; val n = romPath.substringAfterLast('/'); PathCache.changeRom(romKey, null, romPath, n); n }
            romUri != null -> { val n = queryName(romUri) ?: "game.nds"; romKey = n; PathCache.changeRom(romKey, romUri, null, n); libraryUri = romUri; n }
            else -> { finish(); return }
        }

        val gameBase = displayName.substringBeforeLast('.')
        val userSub = if (RuntimeControl.active) "" else Profiles.userSub(this)
        PathCache.activeUserSub = userSub
        SettingsRepo.load(this)
        SettingsRepo.activateFor(this, gameBase)
        SettingsRepo.current.let { c ->
            com.seedlessds.app.AppLog.i(TAG, "game: $displayName uri=$romUri path=$romPath")
            com.seedlessds.app.AppLog.i(TAG, "settings: scale=${SettingsRepo.scaleRequested()} preset=${c.dualScreenPreset} sameSize=${c.dsSameSize} extType=${c.extDisplayType} extScreen=${c.extDisplayScreen} layout=${c.screenLayout} rotation=${c.rotationMode} saves=${runCatching { SaveLocation.folderLabel(this) }.getOrDefault("?")}")
        }

        if (SettingsRepo.scaleNeedsRestart()) {
            val other = Intent(intent)
            other.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            SettingsRepo.flush(this)
            com.seedlessds.app.AppLog.i(TAG, "restarting the process for the new 3D scale")
            startActivity(other)
            finish()
            Runtime.getRuntime().exit(0)
            return
        }
        if (!inited) {
            SettingsRepo.markCoreScale()
            SeedlessCore.attachSurface(this, com.seedlessds.app.BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT); inited = true
        }


        KeyMap.load(this)
        ControlLayout.load(this)
        val stateInfo = SaveStateInfo(this, userSub, gameBase, romUri, romPath)
        reconSlot.set(0)
        if (com.seedlessds.app.Recon.available) {
            com.seedlessds.app.Recon.rom("open", displayName)
            reconOpenRom = displayName
        }
        val gameIcon = runCatching {
            val stream = if (romPath != null) java.io.FileInputStream(romPath)
                else contentResolver.openInputStream(romUri!!)
            stream?.let { NdsRom.read(it).icon?.asImageBitmap() }
        }.getOrNull()
        val coverUrl = androidx.compose.runtime.mutableStateOf<String?>(null)
        if (libraryUri != null) {
            lifecycleScope.launch {
                val entry = RomLibrary.RomEntry(libraryUri!!, displayName)
                coverUrl.value = runCatching { BoxArt.resolveUrl(this@EmulatorActivity, entry) }.getOrNull()
            }
        }

        refreshPauseState()
        window.decorView.postDelayed({ bootHold = false; refreshPauseState() }, 1000)

        glView = DsGlView(this)

        RuntimeControl.attach(this)


        setContent {
            SeedlessTheme(isDarkTheme = true) {
                EmulatorScreen(
                    glView = glView,
                    initialSettings = SettingsRepo.global,
                    initialPerGame = SettingsRepo.current,
                    gameTitle = gameBase,
                    gameIcon = gameIcon,
                    gameCoverUrl = coverUrl.value,
                    gamePlatformLabel = if (displayName.endsWith(".dsi", true)) getString(AppR.string.common_dsiware) else getString(AppR.string.boot_nintendo_ds_2),
                    onLoadingChange = { busy ->
                        booting = busy
                        bootingState.value = busy
                        bootTitle = gameBase
                        refreshExternalMode()
                    },
                    stateInfo = stateInfo,
                    onSaveState = { slot ->
                        val ra = com.seedlessds.app.ra.RetroAchievements
                        reconSlot.set(slot)
                        val ok = SeedlessCore.stateSave(slot, false)
                        if (ok) {
                            val blob = ra.serializeProgress()
                            if (blob != null)
                                if (!stateInfo.writeSidecar(slot, blob)) stateInfo.deleteSidecar(slot)
                            else stateInfo.deleteSidecar(slot)
                        }
                        ok
                    },
                    onLoadState = { slot ->
                        val ra = com.seedlessds.app.ra.RetroAchievements
                        ra.beginStateLoad()
                        reconSlot.set(slot)
                        val ok = try { SeedlessCore.stateLoad(slot) } catch (t: Throwable) { ra.endStateLoad(); throw t }
                        if (ok) {
                            val bytes = stateInfo.readSidecar(slot)
                            ra.deserializeProgress(bytes)
                        } else {
                            ra.endStateLoad()
                        }
                        ok
                    },
                    onReset = {
                        val ra = com.seedlessds.app.ra.RetroAchievements
                        ra.beginStateLoad()
                        SeedlessCore.reset()
                        ra.resetRuntime()
                    },
                    onExit = { finish() },
                    onApplySettings = { s ->
                        SettingsRepo.saveGlobal(this, s, SettingsRepo.hasPerGame(this, gameBase))
                        SettingsRepo.applyToCore(); glView.applyDisplaySettings(); extDisplay?.applyDisplaySettings()
                        applyOrientation(); startCpuLoad(); startRumblePoll()
                    },
                    onApplyPerGame = { s ->
                        SettingsRepo.savePerGame(this, gameBase, s)
                        SettingsRepo.applyToCore(); glView.applyDisplaySettings(); extDisplay?.applyDisplaySettings()
                        applyOrientation(); startCpuLoad(); startRumblePoll()
                    },
                    onClearPerGame = {
                        SettingsRepo.clearPerGame(this, gameBase)
                        SettingsRepo.applyToCore(); glView.applyDisplaySettings(); extDisplay?.applyDisplaySettings()
                        applyOrientation(); startCpuLoad(); startRumblePoll()
                    },
                    onMenuVisibilityChange = { open ->
                        menuOpen = open
                        if (open) runCatching { for (b in 0 until 16) glView.setButton(b, false) }
                        MenuKeyRouter.resetAxes()
                        refreshExternalMode()
                        refreshPauseState()
                    },
                    hasExternal = { extDisplay?.isConnected == true },
                    onApplyExternal = { extDisplay?.applyDisplaySettings() },
                    extTapCount = extTapState.intValue,
                    onExtEditState = { editing, selected -> extDisplay?.setEdit(editing, selected) },
                )
            }
        }
        applyOrientation()
        startEmulation()
        if (com.seedlessds.app.ra.RetroAchievements.isEnabled()) {
            val bootSlot = intent.getIntExtra(EXTRA_LOAD_SLOT, -1)
            val bootBytes = if (bootSlot in 0..9) {
                stateInfo.readSidecar(bootSlot)
            } else null
            when {
                romPath != null -> com.seedlessds.app.ra.RetroAchievements.loadGame(romPath)
                romUri != null -> com.seedlessds.app.ra.RetroAchievements.loadGameUri(romUri)
            }
            com.seedlessds.app.ra.RetroAchievements.setBootStateSidecar(bootBytes)
        }
        if (SettingsRepo.current.extDisplayType != 0) {
            extDisplay = ExternalDisplayManager(this).also {
                it.touchSink = { active, packed -> glView.pushTouch(active, packed) }
                it.onTap = { runOnUiThread { extTapState.intValue++ } }
                if (!externalInfoActive) it.start()
            }
        }
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun applyOrientation() {
        val c = SettingsRepo.current
        requestedOrientation = if (c.screenLayout == 2)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else when (c.rotationMode) {
            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            3 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            4 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    private fun startEmulation() {
        val cfg = SettingsRepo.current
        val loadSlot = intent.getIntExtra(EXTRA_LOAD_SLOT, -1)
        val t = Thread {
            try {
                SeedlessCore.firmwareUser(cfg.firmwareNick, cfg.firmwarePacked())
                SeedlessCore.autosaveInterval(cfg.autosaveInterval())
                SeedlessCore.audioVolume(cfg.volume * 10)
                com.seedlessds.app.SeedlessBridge.resetMemory()
                RuntimeControl.beforeLoad()
                val clock = if (cfg.customClockEnable) cfg.customClock else -1L
                val ok = SeedlessCore.loadRom(romKey, loadSlot, cfg.pack(), 0, false, clock)
                com.seedlessds.app.AppLog.i(TAG, "startGame returned ok=$ok")
                if (!ok) runOnUiThread { showLoadError() }
            } catch (e: Throwable) { com.seedlessds.app.AppLog.e(TAG, "startGame failed", e); runOnUiThread { showLoadError() } }
        }
        t.name = "seedless-emu"; t.priority = Thread.MAX_PRIORITY; emuThread = t; t.start()
        startRumblePoll()
    }

    @Volatile private var rumbleRunning = false
    private var rumbleThread: Thread? = null

    private fun selectVibrator(): android.os.Vibrator? {
        val sys: android.os.Vibrator? =
            if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator?)
        val sysOk = sys?.takeIf { it.hasVibrator() }
        val ctrl = KeyMap.activeDeviceVibrator()
        return when (SettingsRepo.current.rumbleDev) {
            0 -> null
            1 -> ctrl ?: sysOk
            else -> sysOk ?: ctrl
        }
    }

    private fun stopRumblePoll() {
        rumbleRunning = false
        rumbleThread?.interrupt()
        rumbleThread = null
    }

    private fun startRumblePoll() {
        stopRumblePoll()
        val vib = selectVibrator() ?: return
        rumbleRunning = true
        val t = Thread {
            var buzzing = false
            while (rumbleRunning) {
                try {
                    val on = SeedlessCore.rumble()
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 26)
                            vib.vibrate(android.os.VibrationEffect.createOneShot(120, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        else @Suppress("DEPRECATION") vib.vibrate(120)
                    } else if (buzzing) vib.cancel()
                    buzzing = on
                } catch (_: Throwable) {}
                try { Thread.sleep(50) } catch (_: InterruptedException) { break }
            }
            try { if (buzzing) vib.cancel() } catch (_: Throwable) {}
        }
        t.isDaemon = true; t.name = "seedless-rumble"
        rumbleThread = t; t.start()
    }

    @Volatile private var cpuLoadRunning = false
    private fun startCpuLoad() {
        if (cpuLoadRunning) return
        cpuLoadRunning = true
        Thread {
            var r = 0.123456789
            while (cpuLoadRunning) {
                val load = SettingsRepo.current.cpuLoad
                if (load > 0) {
                    val iters = (Math.pow(10.0, load / 100.0) * 5000.0).toLong()
                    var i = 0L
                    while (i < iters && cpuLoadRunning) { r = Math.sqrt((r + 1.0) * 1.0000001); i++ }
                }
                try { Thread.sleep(10) } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; name = "seedless-cpuload"; start() }
    }
    private fun stopCpuLoad() { cpuLoadRunning = false }

    private var errorShown = false
    private fun showLoadError() {
        if (errorShown || isFinishing) return
        errorShown = true
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(AppR.string.emu_failed_to_load_rom))
            .setMessage(getString(AppR.string.emu_load_error_description))
            .setCancelable(false)
            .setPositiveButton(getString(AppR.string.common_ok)) { _, _ -> finish() }
            .show()
    }

    private fun queryName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    } catch (e: Exception) { null }

    private var keyL = false
    private var keyR = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        KeyCapture.onKey?.let { capture ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) capture(event.keyCode)
            return true
        }
        if (!menuOpen && (KeyMap.dsForKey(event.keyCode) >= 0 || KeyMap.specialForKey(event.keyCode) != 0)) {
            return when (event.action) {
                KeyEvent.ACTION_DOWN -> onKeyDown(event.keyCode, event)
                KeyEvent.ACTION_UP -> onKeyUp(event.keyCode, event)
                else -> true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        KeyCapture.onKey?.let { it(keyCode); return true }
        if (menuOpen && MenuKeyRouter.dispatch(keyCode)) return true
        if (menuOpen) return super.onKeyDown(keyCode, event)
        val i = KeyMap.dsForKey(keyCode)
        if (i >= 0) { if (i == L) keyL = true; if (i == R) keyR = true; glView.setButton(i, true); return true }
        val fn = KeyMap.specialForKey(keyCode)
        if (fn != 0 && event.repeatCount == 0 && ExtraKeyRouter.dispatch(fn, true)) return true
        if (fn != 0) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (KeyCapture.onKey != null) return true
        if (menuOpen) return true
        val i = KeyMap.dsForKey(keyCode)
        if (i >= 0) { if (i == L) keyL = false; if (i == R) keyR = false; glView.setButton(i, false); return true }
        val fn = KeyMap.specialForKey(keyCode)
        if (fn != 0) { ExtraKeyRouter.dispatch(fn, false); return true }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (event.source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK &&
            event.action == android.view.MotionEvent.ACTION_MOVE) {
            val dz = 0.5f
            var x = event.getAxisValue(android.view.MotionEvent.AXIS_X)
            var y = event.getAxisValue(android.view.MotionEvent.AXIS_Y)
            val hx = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X)
            val hy = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y)
            if (hx != 0f || hy != 0f) { x = hx; y = hy }
            if (menuOpen) {
                MenuKeyRouter.dispatchAxes(x, y)
                return true
            }
            glView.setButton(LEFT, x < -dz); glView.setButton(RIGHT, x > dz)
            glView.setButton(UP, y < -dz); glView.setButton(DOWN, y > dz)
            if (SettingsRepo.current.analogTriggers) {
                val ax = android.view.MotionEvent.AXIS_LTRIGGER; val bx = android.view.MotionEvent.AXIS_BRAKE
                val cx = android.view.MotionEvent.AXIS_RTRIGGER; val gx = android.view.MotionEvent.AXIS_GAS
                val lt = maxOf(event.getAxisValue(ax), event.getAxisValue(bx))
                val rt = maxOf(event.getAxisValue(cx), event.getAxisValue(gx))
                glView.setButton(L, keyL || lt > 0.5f); glView.setButton(R, keyR || rt > 0.5f)
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun setExternalInfoMode(active: Boolean) {
        if (externalInfoActive == active) return
        externalInfoActive = active
        if (active) {
            extDisplay?.stop()
            externalInfo.attach()
            externalInfo.setContent {
                if (bootingState.value) {
                    ExternalLoadingInfo(bootTitle)
                } else {
                    val block = ExternalInfoBus.content.value
                    if (block != null) block() else ExternalIdleInfo()
                }
            }
        } else {
            ExternalInfoBus.clear()
            externalInfo.detach()
            extDisplay?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        com.seedlessds.app.AppLog.i(TAG, "onPause")
        commitPlayTime()
        stopCpuLoad()
        appForeground = false
        refreshPauseState()
        if (::glView.isInitialized) glView.onPause()
        extDisplay?.pause()
        if (!isFinishing) showResumeNotification()
    }

    override fun onResume() {
        super.onResume()
        sessionStart = System.currentTimeMillis()
        if (::glView.isInitialized) glView.onResume()
        extDisplay?.resume()
        cancelResumeNotification()
        startCpuLoad()
        appForeground = true
        refreshPauseState()
        if (booting) {
            window.decorView.post {
                if (booting) {
                    externalInfoActive = false
                    refreshExternalMode()
                }
            }
        }
    }

    private fun showResumeNotification() = runCatching {
        val ch = "seedless_resume"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(ch, getString(AppR.string.emu_emulation), NotificationManager.IMPORTANCE_LOW))
        val pi = android.app.PendingIntent.getActivity(this, 0,
            Intent(this, EmulatorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val n = androidx.core.app.NotificationCompat.Builder(this, ch)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(AppR.string.app_name))
            .setContentText(getString(AppR.string.emu_game_paused_tap_to_resume))
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
        androidx.core.app.NotificationManagerCompat.from(this).notify(45054, n)
    }
    private fun cancelResumeNotification() = runCatching {
        androidx.core.app.NotificationManagerCompat.from(this).cancel(45054)
    }

    private fun commitPlayTime() {
        val uri = libraryUri ?: return
        if (sessionStart > 0L) {
            RomLibrary.addPlayTime(this, uri, System.currentTimeMillis() - sessionStart)
            sessionStart = 0L
        }
        val ra = com.seedlessds.app.ra.RetroAchievements
        if (ra.gameTitle.value != null && ra.achTotal.value > 0) {
            RomLibrary.markHasAchievements(this, uri, ra.gameIconUrl.value)
        }
    }



    override fun onDestroy() {
        com.seedlessds.app.AppLog.i(TAG, "onDestroy")
        reconOpenRom?.let {
            if (com.seedlessds.app.Recon.available) {
                com.seedlessds.app.Recon.rom("close", it)
            }
            reconOpenRom = null
        }
        super.onDestroy()
        setExternalInfoMode(false)
        commitPlayTime()
        stopRumblePoll()
        stopCpuLoad()
        com.seedlessds.app.ra.RetroAchievements.unloadGame()
        cancelResumeNotification()
        extDisplay?.stop()
        try { SeedlessCore.quit(); emuThread?.join(2000); SeedlessCore.release() } catch (_: Throwable) {}
        RuntimeControl.detach(this)
        inited = false
    }
}
