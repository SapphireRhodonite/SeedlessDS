package com.seedlessds.app.emu

import com.seedlessds.app.R as AppR
import com.seedlessds.core.SeedlessCore

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.seedlessds.app.MainActivity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import com.seedlessds.app.control.RuntimeControl
import com.seedlessds.app.SeedlessBridge
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.seedlessds.app.filesystem.PathCache

open class EmulatorActivity : ComponentActivity() {
    private val microphone = DeviceMicrophonePermission(this)
    private var phantom: PhantomPlayer? = null
    private var releasingPlayer = false
    private val reconSlot = java.util.concurrent.atomic.AtomicInteger(0)

    private var reconOpenRom: String? = null


    companion object {
        const val EXTRA_ROM_URI = "rom_uri"
        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_LOAD_SLOT = "load_slot"
        const val EXTRA_FIRMWARE_BOOT = "firmware_boot"
        private const val EXTRA_FRONTEND_PARENT = "frontend_parent"
        private const val EXTRA_FRONTEND_NAME = "frontend_name"
        private const val TAG = "SeedlessEmu"
        private var pendingClose: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        const val UP = 0; const val DOWN = 1; const val LEFT = 2; const val RIGHT = 3
        const val A = 4; const val B = 5; const val X = 6; const val Y = 7
        const val L = 8; const val R = 9; const val START = 10; const val SELECT = 11
    }

    private var frontendJob: Job? = null
    private var frontendDialog: android.app.AlertDialog? = null
    private val isFrontendEntry: Boolean get() = javaClass == EmulatorActivity::class.java

    private var inited = false
    private lateinit var glView: DsGlView
    private var emuThread: Thread? = null
    internal var isClosing = false
        private set
    private var closed = false
    @Volatile private var romKey: String = "game.nds"
    private var firmwareBoot = false
    private var extDisplay: ExternalDisplayManager? = null
    private val externalInfo by lazy { ExternalInfoController(this) }
    private var externalInfoActive: Boolean? = null
    private val extTapState = androidx.compose.runtime.mutableIntStateOf(0)
    @Volatile private var menuOpen = false
    @Volatile private var appForeground = false
    private var libraryUri: Uri? = null
    private var sessionStart = 0L
    @Volatile private var bootHold = true
    @Volatile private var booting = true
    private val bootingState = androidx.compose.runtime.mutableStateOf(true)
    private var bootTitle = ""
    private var multiplayerInterruptionShown = false

    private fun refreshExternalMode() {
        if (isClosing) return
        setExternalInfoMode(if (appForeground) (menuOpen && !MultiplayerSession.active) || booting else null)
    }

    internal fun refreshPauseState() {
        if (isClosing || !inited) return
        val menuPaused = menuOpen && !MultiplayerSession.active
        val session = phantom
        val pause = menuPaused || (!appForeground && session?.running != true) || bootHold
        RuntimeControl.lifecyclePaused = pause
        try { SeedlessCore.pause(if (pause) 1 else 0) } catch (_: Throwable) {}
        if (session != null) return
        val interrupted = SeedlessCore.multiplayerPause(!appForeground)
        if (interrupted && appForeground && !menuOpen && !multiplayerInterruptionShown) {
            multiplayerInterruptionShown = true
            android.app.AlertDialog.Builder(this)
                .setTitle(AppR.string.multiplayer_interrupted_title)
                .setMessage(AppR.string.multiplayer_interrupted_message)
                .setPositiveButton(AppR.string.common_ok, null)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFrontendEntry) {
            openFrontend(intent)
            return
        }
        if (this is GameActivity && intent.hasExtra(EXTRA_FRONTEND_PARENT)) {
            lifecycleScope.launch {
                pendingClose?.await()
                val profile = if (RuntimeControl.active) "" else Profiles.userSub(this@EmulatorActivity)
                val parent = intent.getStringExtra(EXTRA_FRONTEND_PARENT) ?: return@launch finish()
                val uri = intent.getStringExtra(EXTRA_ROM_URI)?.let(Uri::parse) ?: return@launch finish()
                val saves = withContext(Dispatchers.IO) {
                    runCatching { SaveLocation.frontendSaves(applicationContext, uri, parent, profile) }.getOrNull()
                }
                if (!isFinishing && !isDestroyed) {
                    if (saves == null) showFrontendError(AppR.string.frontend_save_unavailable)
                    else createEmulation(saves, profile)
                }
            }
            return
        }
        val player = when (this) {
            is PhantomPrimaryActivity -> PhantomPlayer(this, true)
            is PhantomPeerActivity -> PhantomPlayer(this, false)
            else -> null
        }
        phantom = player
        val previous = pendingClose
        if (player == null && previous == null) createEmulation()
        else lifecycleScope.launch {
            previous?.await()
            if (player != null) {
                try {
                    player.attach()
                    player.connect()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { player.notify(PhantomPlayer.ERROR, e.localizedMessage); return@launch }
            }
            if (!isFinishing && !isDestroyed && player?.closing != true) {
                createEmulation()
                player?.notify(PhantomPlayer.READY)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isFrontendEntry) {
            setIntent(intent)
            openFrontend(intent)
        }
    }

    private fun openFrontend(request: Intent) {
        frontendJob?.cancel()
        frontendDialog?.setOnDismissListener(null)
        frontendDialog?.dismiss()
        frontendJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { FrontendLaunch.resolve(applicationContext, request) }
                    .getOrDefault(FrontendLaunch.Result.Unverified)
            }
            if (isFinishing || isDestroyed) return@launch
            when (result) {
                is FrontendLaunch.Result.Game -> {
                    startActivity(Intent(this@EmulatorActivity, GameActivity::class.java)
                        .putExtra(EXTRA_ROM_URI, result.uri.toString())
                        .putExtra(EXTRA_FRONTEND_PARENT, result.parentId)
                        .putExtra(EXTRA_FRONTEND_NAME, result.name)
                        .putExtra(EXTRA_LOAD_SLOT, request.getIntExtra(EXTRA_LOAD_SLOT, -1)))
                    finish()
                }
                FrontendLaunch.Result.Unverified -> showFrontendError(AppR.string.frontend_origin_unverified)
                FrontendLaunch.Result.NameNotFound -> showFrontendError(AppR.string.frontend_name_not_found)
                FrontendLaunch.Result.AmbiguousName -> showFrontendError(AppR.string.frontend_name_ambiguous)
            }
        }
    }

    private fun showFrontendError(message: Int) {
        frontendDialog = android.app.AlertDialog.Builder(this)
            .setTitle(AppR.string.frontend_launch_unavailable)
            .setMessage(message)
            .setPositiveButton(AppR.string.frontend_open_library) { _, _ ->
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
            .setNegativeButton(AppR.string.common_cancel, null)
            .setOnDismissListener { finish() }
            .show()
    }

    private fun createEmulation(frontendSaves: SaveLocation.FrontendSaves? = null, frontendProfile: String? = null) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersive()
        if (phantom == null) EmuAssets.ensureInstalled(this)

        firmwareBoot = intent.getBooleanExtra(EXTRA_FIRMWARE_BOOT, false)
        val data = intent.data
        val romPath = intent.getStringExtra(EXTRA_ROM_PATH)
            ?: data?.takeIf { it.scheme == "file" }?.path
        val romUri = intent.getStringExtra(EXTRA_ROM_URI)?.let { Uri.parse(it) }
            ?: data?.takeIf { it.scheme == "content" }
        val displayName: String = when {
            firmwareBoot -> {
                romKey = "firmware"
                val name = getString(AppR.string.firmware_title)
                PathCache.changeRom(romKey, null, null, name, null)
                name
            }
            romPath != null -> { romKey = romPath; val n = romPath.substringAfterLast('/'); PathCache.changeRom(romKey, null, romPath, n, null); n }
            romUri != null -> {
                val name = intent.getStringExtra(EXTRA_FRONTEND_NAME) ?: queryName(romUri) ?: "game.nds"
                romKey = name
                PathCache.changeRom(romKey, romUri, null, name, frontendSaves)
                libraryUri = romUri
                name
            }
            else -> { finish(); return }
        }

        val gameBase = if (firmwareBoot) getString(AppR.string.multiplayer_download_play)
            else displayName.substringBeforeLast('.')
        val userSub = frontendProfile ?: if (RuntimeControl.active || phantom != null) "" else Profiles.userSub(this)
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
        val stateInfo = SaveStateInfo(this, userSub, gameBase, romUri, romPath, frontendSaves)
        reconSlot.set(0)
        if (com.seedlessds.app.Recon.available) {
            com.seedlessds.app.Recon.rom("open", displayName)
            reconOpenRom = displayName
        }
        val gameIcon = if (firmwareBoot) null else runCatching {
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

        glView = DsGlView(this, SettingsRepo.current.lowLatency && !MultiplayerSession.active)

        microphone.attachGame()
        RuntimeControl.attach(this)


        setContent {
            SeedlessTheme(isDarkTheme = true) {
                EmulatorScreen(
                    glView = glView,
                    onEnableMicrophone = microphone::requestFromSettings,
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
                        if (MultiplayerSession.active) android.widget.Toast.makeText(this, AppR.string.multiplayer_operation_unavailable, android.widget.Toast.LENGTH_SHORT).show()
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
                        if (MultiplayerSession.active) android.widget.Toast.makeText(this, AppR.string.multiplayer_operation_unavailable, android.widget.Toast.LENGTH_SHORT).show()
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
                    playerControls = { phantom?.let { PhantomControls(it) } },
                )
            }
        }
        applyOrientation()
        startEmulation()
        if (phantom == null && !firmwareBoot && com.seedlessds.app.ra.RetroAchievements.isEnabled()) {
            val bootSlot = if (MultiplayerSession.active) -1 else intent.getIntExtra(EXTRA_LOAD_SLOT, -1)
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
            extDisplay = ExternalDisplayManager(this, glView.lowLatency, phantom?.primary == false).also {
                it.touchSink = { active, packed -> glView.pushTouch(active, packed) }
                it.onTap = { runOnUiThread { extTapState.intValue++ } }
                if (externalInfoActive == false) it.start()
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
        if (hasFocus && !isFrontendEntry) enterImmersive()
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

    private fun acquireMultiplayerWifiLock(): WifiManager.WifiLock? {
        if (Build.VERSION.SDK_INT < 29 || phantom != null || !MultiplayerSession.active) return null
        return try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "seedless-multiplayer")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (error: RuntimeException) {
            com.seedlessds.app.AppLog.e(TAG, "Wi-Fi low latency unavailable", error)
            null
        }
    }

    private fun startEmulation() {
        val cfg = SettingsRepo.current
        val loadSlot = if (firmwareBoot || MultiplayerSession.active) -1 else intent.getIntExtra(EXTRA_LOAD_SLOT, -1)
        val t = Thread {
            val wifiLock = acquireMultiplayerWifiLock()
            try {
                SeedlessCore.firmwareUser(cfg.firmwareNick, cfg.firmwarePacked())
                SeedlessCore.autosaveInterval(if (firmwareBoot) 0 else cfg.autosaveInterval())
                SeedlessCore.audioVolume(if (phantom != null && !appForeground) 0 else cfg.volume * 10)
                com.seedlessds.app.SeedlessBridge.resetMemory()
                SeedlessCore.setEarlyInputPacing(glView.lowLatency)
                RuntimeControl.beforeLoad()
                val clock = if (cfg.customClockEnable) cfg.customClock else -1L
                val ok = if (firmwareBoot) SeedlessCore.loadFirmware(cfg.pack(), clock)
                    else SeedlessCore.loadRom(romKey, loadSlot, cfg.pack(), 0, false, clock)
                com.seedlessds.app.AppLog.i(TAG, "startGame returned ok=$ok")
                if (!ok) runOnUiThread { showLoadError() }
            } catch (e: Throwable) { com.seedlessds.app.AppLog.e(TAG, "startGame failed", e); runOnUiThread { showLoadError() } }
            finally {
                try { if (wifiLock?.isHeld == true) wifiLock.release() }
                catch (error: RuntimeException) { com.seedlessds.app.AppLog.e(TAG, "Wi-Fi low latency release failed", error) }
            }
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
        if (errorShown || isFinishing || isClosing) return
        errorShown = true
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(if (firmwareBoot) AppR.string.multiplayer_firmware_load_failed else AppR.string.emu_failed_to_load_rom))
            .setMessage(getString(if (firmwareBoot) AppR.string.multiplayer_firmware_load_error else AppR.string.emu_load_error_description))
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
        if (isFrontendEntry) return super.dispatchKeyEvent(event)
        if (isClosing || !::glView.isInitialized) return true
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
        if (isClosing || !::glView.isInitialized) return true
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

    private fun setExternalInfoMode(active: Boolean?) {
        if (externalInfoActive == active) return
        externalInfoActive = active
        extDisplay?.stop()
        externalInfo.detach()
        if (active == true) {
            externalInfo.attach()
            externalInfo.setContent {
                if (bootingState.value) {
                    ExternalLoadingInfo(bootTitle)
                } else {
                    val block = ExternalInfoBus.content.value
                    if (block != null) block() else ExternalIdleInfo()
                }
            }
        } else if (active == false) {
            ExternalInfoBus.clear()
            extDisplay?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isFrontendEntry) return
        com.seedlessds.app.AppLog.i(TAG, "onPause")
        if (microphone.pending && MultiplayerSession.active) return
        pauseEmulation()
    }

    override fun onStart() {
        super.onStart()
        if (this is GameActivity) {
            ExternalInfoController.reservePanel(this, androidx.core.content.ContextCompat.getDisplayOrDefault(this).displayId)
        }
    }

    override fun onStop() {
        super.onStop()
        if (this is GameActivity) ExternalInfoController.reservePanel(this, null)
        if (appForeground) pauseEmulation()
    }

    private fun pauseEmulation() {
        commitPlayTime()
        stopCpuLoad()
        appForeground = false
        phantom?.let {
            it.visibility(false)
            if (::glView.isInitialized) {
                glView.clearInput()
                keyL = false
                keyR = false
            }
            if (inited) SeedlessCore.audioVolume(0)
        }
        refreshPauseState()
        if (::glView.isInitialized) glView.onPause()
        refreshExternalMode()
        if (inited && phantom == null && !isFinishing && !isClosing) showResumeNotification()
    }

    override fun onResume() {
        super.onResume()
        if (isFrontendEntry || isClosing) return
        sessionStart = System.currentTimeMillis()
        if (::glView.isInitialized) glView.onResume()
        cancelResumeNotification()
        startCpuLoad()
        appForeground = true
        microphone.resumeGame()
        phantom?.let {
            it.visibility(true)
            if (inited) SeedlessCore.audioVolume(SettingsRepo.current.volume * 10)
        }
        refreshPauseState()
        refreshExternalMode()
    }

    private fun showResumeNotification() = runCatching {
        val ch = "seedless_resume"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(ch, getString(AppR.string.emu_emulation), NotificationManager.IMPORTANCE_LOW))
        val pi = android.app.PendingIntent.getActivity(this, 0,
            Intent(this, GameActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
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
        if (phantom != null) return
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



    override fun finish() {
        val player = phantom
        if (player != null && !player.closing) {
            player.notify(PhantomPlayer.CLOSE)
            return
        }
        if (closed || !inited) {
            super.finish()
            return
        }
        closeEmulation()
    }

    private fun closeEmulation() {
        if (isClosing || !inited) return
        isClosing = true
        microphone.detachGame()
        val completion = kotlinx.coroutines.CompletableDeferred<Unit>()
        pendingClose = completion
        setExternalInfoMode(null)
        ExternalInfoBus.clear()
        commitPlayTime()
        val rumble = rumbleThread
        stopRumblePoll()
        stopCpuLoad()
        if (phantom == null) com.seedlessds.app.ra.RetroAchievements.unloadGame()
        cancelResumeNotification()
        extDisplay?.stop()
        if (::glView.isInitialized) glView.stopRendering()
        if (!isDestroyed) setContent {}
        Thread({
            try {
                if (::glView.isInitialized) glView.awaitRenderingStopped()
                rumble?.join()
                SeedlessCore.quit()
                emuThread?.join()
                SeedlessCore.release()
                runOnUiThread {
                    reconOpenRom?.let {
                        if (com.seedlessds.app.Recon.available) com.seedlessds.app.Recon.rom("close", it)
                        reconOpenRom = null
                    }
                    RuntimeControl.detach(this)
                    inited = false
                    closed = true
                    pendingClose = null
                    completion.complete(Unit)
                    com.seedlessds.app.AppLog.i(TAG, "emulation closed")
                    if (!isDestroyed) super.finish() else releasePlayer()
                }
            } catch (error: Throwable) {
                com.seedlessds.app.AppLog.e(TAG, "emulation close failed", error)
            }
        }, "seedless-close").start()
    }

    override fun onDestroy() {
        com.seedlessds.app.AppLog.i(TAG, "onDestroy")
        frontendDialog?.setOnDismissListener(null)
        frontendDialog?.dismiss()
        super.onDestroy()
        if (!closed && inited) closeEmulation() else releasePlayer()
    }

    private fun releasePlayer() {
        val player = phantom ?: return
        if (releasingPlayer) return
        releasingPlayer = true
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { player.close() }
    }
}

class GameActivity : EmulatorActivity()
