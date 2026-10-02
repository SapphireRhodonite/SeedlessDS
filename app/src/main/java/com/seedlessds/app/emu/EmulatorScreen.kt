package com.seedlessds.app.emu

import com.seedlessds.app.R
import com.seedlessds.core.SeedlessCore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.DragIndicator
import android.content.res.Configuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private enum class Overlay { NONE, MENU, SETTINGS, SETTINGS_GAME, STATE_SAVE, STATE_LOAD, CHEATS, KEYMAP, MAPPING_INFO, SYSTEM_TOOLS, HELP, ACHIEVEMENTS, LEADERBOARDS, PRESETS }

@Composable
fun EmulatorScreen(
    glView: DsGlView,
    initialSettings: DsSettings,
    initialPerGame: DsSettings,
    gameTitle: String,
    stateInfo: SaveStateInfo,
    onSaveState: (Int) -> Boolean,
    onLoadState: (Int) -> Boolean,
    onReset: () -> Unit,
    onExit: () -> Unit,
    onApplySettings: (DsSettings) -> Unit,
    onApplyPerGame: (DsSettings) -> Unit,
    onClearPerGame: () -> Unit,
    onMenuVisibilityChange: (Boolean) -> Unit = {},
    gameIcon: androidx.compose.ui.graphics.ImageBitmap? = null,
    gameCoverUrl: String? = null,
    gamePlatformLabel: String = androidx.compose.ui.res.stringResource(R.string.platform_nds),
    onLoadingChange: (Boolean) -> Unit = {},
    hasExternal: () -> Boolean = { false },
    onApplyExternal: () -> Unit = {},
    extTapCount: Int = 0,
    onExtEditState: (Boolean, Boolean) -> Unit = { _, _ -> },
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    val menuSelected = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var coreReady by remember { mutableStateOf(false) }
    var heldLongEnough by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(glView) {
        glView.onFirstFrame = { coreReady = true; com.seedlessds.app.AppLog.i("game", "first frame") }
        onDispose { glView.onFirstFrame = null }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        heldLongEnough = true
    }
    androidx.compose.runtime.LaunchedEffect(loading) { onLoadingChange(loading) }
    var settings by remember { mutableStateOf(initialSettings) }
    var perGame by remember { mutableStateOf(initialPerGame) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val emulationBlocked = overlay != Overlay.NONE
    androidx.compose.runtime.LaunchedEffect(emulationBlocked) { onMenuVisibilityChange(emulationBlocked) }
    var editingControls by remember { mutableStateOf(false) }
    val offsets = remember {
        androidx.compose.runtime.mutableStateMapOf<String, Pair<Float, Float>>().apply {
            ControlLayout.ids.forEach { put(it, ControlLayout.offsetOf(it)) }
        }
    }
    val scales = remember {
        mutableStateMapOf<String, Float>().apply {
            ControlLayout.ids.forEach { put(it, ControlLayout.scaleOf(it)) }
        }
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var rectTick by remember { mutableStateOf(0) }
    var canvasPx by remember { mutableStateOf(IntSize.Zero) }
    var toolbarOffset by remember { mutableStateOf(androidx.compose.ui.unit.IntOffset.Zero) }
    val hiddenSet = remember {
        mutableStateMapOf<String, Boolean>().apply { ControlLayout.ids.forEach { put(it, ControlLayout.isHidden(it)) } }
    }
    androidx.compose.runtime.LaunchedEffect(extTapCount) { if (extTapCount > 0 && editingControls) selectedId = "extScreen" }
    androidx.compose.runtime.LaunchedEffect(editingControls, selectedId) { onExtEditState(editingControls, selectedId == "extScreen") }
    var lidClosed by remember { mutableStateOf(false) }

    var gamepadConnected by remember { mutableStateOf(KeyMap.hasGamepad()) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val im = ctx.getSystemService(android.content.Context.INPUT_SERVICE) as android.hardware.input.InputManager
        val l = object : android.hardware.input.InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(id: Int) { gamepadConnected = KeyMap.hasGamepad() }
            override fun onInputDeviceRemoved(id: Int) { gamepadConnected = KeyMap.hasGamepad() }
            override fun onInputDeviceChanged(id: Int) { gamepadConnected = KeyMap.hasGamepad() }
        }
        im.registerInputDeviceListener(l, null)
        onDispose { im.unregisterInputDeviceListener(l) }
    }
    var manualControls by remember { mutableStateOf<Boolean?>(null) }
    val hideControls = manualControls ?: (settings.hideControlsWithGamepad && gamepadConnected)
    fun toggleControls() { manualControls = !(manualControls ?: (settings.hideControlsWithGamepad && gamepadConnected)) }

    fun applyDisplayPersist(mut: (DsSettings) -> DsSettings) {
        val next = mut(SettingsRepo.current)
        if (SettingsRepo.hasPerGame(ctx, gameTitle)) { perGame = next; onApplyPerGame(next) }
        else { settings = next; onApplySettings(next) }
    }
    fun nextLayout(l: Int) = (l + 1) % 5

    fun dispatchExtra(fn: Int, pressed: Boolean) {
        if (fn == 0) return
        if (pressed) { if (fn == 7) SeedlessCore.whitenoise(true); return }
        when (fn) {
            1 -> { SettingsRepo.fastForward = !SettingsRepo.fastForward; glView.applyFastForward() }
            2 -> onSaveState(8)
            3 -> onLoadState(8)
            4 -> applyDisplayPersist { it.copy(swapScreens = !it.swapScreens) }
            5 -> applyDisplayPersist { it.copy(screenLayout = it.screenLayout2, screenLayout2 = it.screenLayout) }
            6 -> toggleControls()
            7 -> SeedlessCore.whitenoise(false)
            8 -> applyDisplayPersist { it.copy(screenLayout = nextLayout(it.screenLayout)) }
            9 -> glView.toggleRapidFire()
        }
    }

    val extras = androidx.compose.runtime.rememberUpdatedState<(Int, Boolean) -> Unit> { fn, pressed ->
        dispatchExtra(fn, pressed)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        ExtraKeyRouter.handler = { fn, pressed -> extras.value(fn, pressed) }
        onDispose { ExtraKeyRouter.handler = null }
    }

    BackHandler(enabled = editingControls) {
        ControlLayout.load(ctx)
        ControlLayout.ids.forEach { offsets[it] = ControlLayout.offsetOf(it); scales[it] = ControlLayout.scaleOf(it); hiddenSet[it] = ControlLayout.isHidden(it) }
        selectedId = null; glView.applyDisplaySettings(); rectTick++; editingControls = false
    }
    BackHandler(enabled = overlay == Overlay.NONE && !editingControls) { if (!settings.disableBackButton) overlay = Overlay.MENU }
    BackHandler(enabled = overlay == Overlay.MENU) { overlay = Overlay.NONE }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { canvasPx = it }) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { glView }, modifier = Modifier.fillMaxSize()
        )

        if (overlay == Overlay.NONE) {
            if (editingControls) {
                Box(Modifier.fillMaxSize()
                    .background(Color(0x66000000))
                    .pointerInput(Unit) { detectTapGestures { selectedId = null } })
                val topRect = remember(rectTick) { glView.topScreenRect }
                val botRect = remember(rectTick) { glView.bottomScreenRect }
                ScreenEditHandle("screenTop", uiContext.getString(R.string.common_top_2), topRect, selectedId == "screenTop", canvasPx,
                    onSelect = { selectedId = "screenTop" }) { dx, dy ->
                    ControlLayout.topOffX += dx; ControlLayout.topOffY += dy
                    glView.applyDisplaySettings(); rectTick++
                }
                ScreenEditHandle("screenBot", uiContext.getString(R.string.common_bottom_2), botRect, selectedId == "screenBot", canvasPx,
                    onSelect = { selectedId = "screenBot" }) { dx, dy ->
                    ControlLayout.botOffX += dx; ControlLayout.botOffY += dy
                    glView.applyDisplaySettings(); rectTick++
                }
            }
            ControlsOverlay(glView, settings, offsets, scales, hiddenSet, selectedId,
                { selectedId = it }, editingControls, hideControls, ::dispatchExtra) {
                if (!editingControls) overlay = Overlay.MENU
            }
            if (settings.showFps && !editingControls)
                PerfOverlay(Modifier.align(Alignment.TopStart).padding(start = 90.dp, top = 16.dp))
            if (settings.showScale && !editingControls)
                ScaleOverlay(Modifier.align(Alignment.TopEnd).padding(end = 90.dp, top = 16.dp))
            if (editingControls) {
                if (hasExternal()) ExtScreenHandle(selectedId == "extScreen") { selectedId = "extScreen" }

                Column(
                    Modifier.align(Alignment.TopCenter).offset { toolbarOffset }.padding(top = 68.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EditActionBar(
                        onDrag = { dx, dy -> toolbarOffset = androidx.compose.ui.unit.IntOffset(toolbarOffset.x + dx, toolbarOffset.y + dy) },
                        onSave = {
                            ControlLayout.save(ctx, offsets.toMap(), scales.toMap())
                            selectedId = null; editingControls = false
                        },
                        onReset = {
                            ControlLayout.reset(ctx)
                            ControlLayout.ids.forEach { offsets[it] = 0f to 0f; scales[it] = 1f; hiddenSet[it] = false }
                            selectedId = null; toolbarOffset = androidx.compose.ui.unit.IntOffset.Zero
                            glView.applyDisplaySettings(); onApplyExternal(); rectTick++
                        },
                        onCancel = {
                            ControlLayout.load(ctx)
                            ControlLayout.ids.forEach { offsets[it] = ControlLayout.offsetOf(it); scales[it] = ControlLayout.scaleOf(it); hiddenSet[it] = ControlLayout.isHidden(it) }
                            selectedId = null; glView.applyDisplaySettings(); onApplyExternal(); rectTick++; editingControls = false
                        }
                    )
                    val sel = selectedId
                    if (sel != null) {
                        val isScreen = sel == "screenTop" || sel == "screenBot" || sel == "extScreen"
                        val range = if (isScreen) 0.25f..1f else 0.5f..2f
                        val curValue = when (sel) {
                            "screenTop" -> ControlLayout.topScale
                            "screenBot" -> ControlLayout.botScale
                            "extScreen" -> ControlLayout.extScale
                            else -> scales[sel] ?: 1f
                        }
                        val isBtn = sel in ControlLayout.ids
                        EditSizeBar(
                            modifier = Modifier.padding(top = 6.dp),
                            label = when (sel) {
                                "screenTop" -> uiContext.getString(R.string.common_top_screen); "screenBot" -> uiContext.getString(R.string.common_bottom_screen)
                                "extScreen" -> uiContext.getString(R.string.menu_external_screen); else -> sel
                            },
                            value = curValue.coerceIn(range.start, range.endInclusive),
                            range = range,
                            hidden = if (isBtn) hiddenSet[sel] == true else null,
                            onToggleHide = if (isBtn) {
                                { val h = hiddenSet[sel] != true; hiddenSet[sel] = h; ControlLayout.setHidden(sel, h) }
                            } else null,
                            onValue = { v ->
                                when (sel) {
                                    "screenTop" -> { ControlLayout.topScale = v; glView.applyDisplaySettings(); rectTick++ }
                                    "screenBot" -> { ControlLayout.botScale = v; glView.applyDisplaySettings(); rectTick++ }
                                    "extScreen" -> { ControlLayout.extScale = v; onApplyExternal(); rectTick++ }
                                    else -> scales[sel] = v
                                }
                            }
                        )
                    }
                }
            }
        }

        when (overlay) {
            Overlay.MENU -> GameMenu(
                gameTitle = gameTitle,
                onResume = { overlay = Overlay.NONE },
                onSaveState = { overlay = Overlay.STATE_SAVE },
                onLoadState = { overlay = Overlay.STATE_LOAD },
                onReset = { onReset(); overlay = Overlay.NONE },
                onCheats = { overlay = Overlay.CHEATS },
                onSettings = { overlay = Overlay.SETTINGS },
                onGameSettings = { overlay = Overlay.SETTINGS_GAME },
                onEditControls = { editingControls = true; overlay = Overlay.NONE },
                onToggleControls = { toggleControls(); overlay = Overlay.NONE },
                controlsHidden = hideControls,
                onHelp = { overlay = Overlay.HELP },
                onAchievements = { overlay = Overlay.ACHIEVEMENTS },
                onLeaderboards = { overlay = Overlay.LEADERBOARDS },
                achievementsEnabled = com.seedlessds.app.ra.RetroAchievements.gameTitle.value != null,
                leaderboardsEnabled = com.seedlessds.app.ra.RetroAchievements.gameTitle.value != null &&
                    com.seedlessds.app.ra.RetroAchievements.hasLeaderboards.value,
                perGameActive = SettingsRepo.hasPerGame(ctx, gameTitle),
                onSwapScreens = { applyDisplayPersist { it.copy(swapScreens = !it.swapScreens) }; overlay = Overlay.NONE },
                onCycleLayout = { applyDisplayPersist { it.copy(screenLayout = nextLayout(it.screenLayout)) }; overlay = Overlay.NONE },
                onPresets = { overlay = Overlay.PRESETS },
                selectedState = menuSelected,
                layoutMatters = !(DualScreenPresets.configuredSingle(SettingsRepo.current) && SettingsRepo.current.extDisplayType != 0),
                onToggleLid = { lidClosed = !lidClosed; SeedlessCore.hinge(lidClosed); overlay = Overlay.NONE },
                lidClosed = lidClosed,
                onExit = onExit,
            )
            Overlay.ACHIEVEMENTS -> Surface(Modifier.fillMaxSize()) {
                AchievementsScreen { overlay = Overlay.MENU }
            }
            Overlay.PRESETS -> Surface(Modifier.fillMaxSize()) {
                PresetsScreen(
                    settings = SettingsRepo.current,
                    onChange = { next -> applyDisplayPersist { next } },
                    onBack = { overlay = Overlay.MENU },
                )
            }
            Overlay.LEADERBOARDS -> Surface(Modifier.fillMaxSize()) {
                LeaderboardsScreen { overlay = Overlay.MENU }
            }
            Overlay.SETTINGS -> Surface(Modifier.fillMaxSize()) {
                SettingsScreen(
                    initial = settings,
                    onSave = { settings = it; onApplySettings(it) },
                    onOpenKeyMapper = { overlay = Overlay.KEYMAP },
                    onOpenMappingInfo = { overlay = Overlay.MAPPING_INFO },
                    onOpenSystemTools = { overlay = Overlay.SYSTEM_TOOLS },
                    onBack = { overlay = Overlay.MENU },
                    gameRunning = true,
                )
            }
            Overlay.SETTINGS_GAME -> Surface(Modifier.fillMaxSize()) {
                SettingsScreen(
                    initial = perGame,
                    onSave = { perGame = it; onApplyPerGame(it) },
                    onOpenKeyMapper = { overlay = Overlay.KEYMAP },
                    onOpenMappingInfo = { overlay = Overlay.MAPPING_INFO },
                    onOpenSystemTools = { overlay = Overlay.SYSTEM_TOOLS },
                    onBack = { overlay = Overlay.MENU },
                    gameRunning = true,
                    subtitle = uiContext.getString(R.string.common_only_for, gameTitle, if (SettingsRepo.hasPerGame(ctx, gameTitle)) uiContext.getString(R.string.common_active_2) else uiContext.getString(R.string.menu_global)),
                    onReset = { onClearPerGame() },
                )
            }
            Overlay.KEYMAP -> Surface(Modifier.fillMaxSize()) {
                KeyMapperScreen { overlay = Overlay.SETTINGS }
            }
            Overlay.MAPPING_INFO -> Surface(Modifier.fillMaxSize()) {
                MappingInfoScreen { overlay = Overlay.SETTINGS }
            }
            Overlay.SYSTEM_TOOLS -> Surface(Modifier.fillMaxSize()) {
                SystemToolsScreen { overlay = Overlay.SETTINGS }
            }
            Overlay.HELP -> Surface(Modifier.fillMaxSize()) {
                HelpScreen { overlay = Overlay.MENU }
            }
            Overlay.STATE_SAVE -> Surface(Modifier.fillMaxSize()) {
                StateMenu(StateMode.SAVE, stateInfo, onSaveState, onLoadState) { overlay = Overlay.MENU }
            }
            Overlay.STATE_LOAD -> Surface(Modifier.fillMaxSize()) {
                StateMenu(
                    StateMode.LOAD, stateInfo, onSaveState,
                    onLoad = { slot -> onLoadState(slot).also { if (it) overlay = Overlay.NONE } },
                ) { overlay = Overlay.MENU }
            }
            Overlay.CHEATS -> Surface(Modifier.fillMaxSize()) {
                CheatsScreen { overlay = Overlay.MENU }
            }
            Overlay.NONE -> {}
        }

        if (loading) {
            BootInfoOverlay(
                title = gameTitle,
                icon = gameIcon,
                coverUrl = gameCoverUrl,
                platformLabel = gamePlatformLabel,
                statusText = uiContext.getString(R.string.common_loading),
                romReady = coreReady && heldLongEnough,
                onFinished = { loading = false },
            )
        }

        if (overlay == Overlay.NONE && !loading) {
            if (com.seedlessds.app.ra.RetroAchievements.settings.showRichPresence)
                RaRichPresenceBar(Modifier.align(Alignment.TopCenter).padding(top = 60.dp))
            RaChallengeIndicators(Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 8.dp))
            RaLeaderboardTrackers(Modifier.align(Alignment.TopEnd).padding(top = 116.dp, end = 8.dp))
            RaProgressPopup(Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
            RaLbToast(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
        }
        RaUnlockOverlay(Modifier.align(Alignment.TopCenter))
        RaWelcomeOverlay(Modifier.align(Alignment.TopCenter))
        RaScoreboardPopup(Modifier.align(Alignment.BottomCenter))
        RaMasteryOverlay(Modifier.align(Alignment.Center))

        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                if (com.seedlessds.app.ra.RetroAchievements.settings.showRichPresence)
                    com.seedlessds.app.ra.RetroAchievements.refreshRichPresence()
                else com.seedlessds.app.ra.RetroAchievements.richPresence.value = null
                kotlinx.coroutines.delay(3000)
            }
        }
    }
}

@Composable
private fun PerfOverlay(modifier: Modifier) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            val pc = SeedlessCore.hudWord()
            val a = (pc ushr 16) * 0.0625f
            val b = (pc and 0xFFFF) * 0.0625f
            text = if (pc == -1) "" else String.format(uiContext.getString(R.string.performance_percentage_format), a, b)
            if (pc != -1 && ticks++ % 40 == 0) com.seedlessds.app.AppLog.i("reconDS", String.format("HUD %.1f %.1f", a, b))
            kotlinx.coroutines.delay(250)
        }
    }
    if (text.isNotEmpty()) Text(
        text = text,
        color = Color.White,
        fontFamily = SeedlessMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color(0x8C000000), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun ScaleOverlay(modifier: Modifier) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            val configured = SettingsRepo.coreScale
            val current = runCatching { SeedlessCore.pageScale() }.getOrDefault(0)
            text = if (configured <= 1) uiContext.getString(R.string.menu_1x) else if (current in 1..8) uiContext.getString(R.string.menu_x_x, current, configured) else uiContext.getString(R.string.menu_x, configured)
            kotlinx.coroutines.delay(250)
        }
    }
    if (text.isNotEmpty()) Text(
        text = text,
        color = Color.White,
        fontFamily = SeedlessMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color(0x8C000000), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun GameMenu(
    gameTitle: String,
    onResume: () -> Unit,
    onSaveState: () -> Unit,
    onLoadState: () -> Unit,
    onReset: () -> Unit,
    onCheats: () -> Unit,
    onSettings: () -> Unit,
    onGameSettings: () -> Unit,
    onEditControls: () -> Unit,
    onToggleControls: () -> Unit,
    controlsHidden: Boolean,
    onHelp: () -> Unit,
    onAchievements: () -> Unit,
    onLeaderboards: () -> Unit,
    achievementsEnabled: Boolean,
    leaderboardsEnabled: Boolean,
    perGameActive: Boolean,
    onSwapScreens: () -> Unit,
    onCycleLayout: () -> Unit,
    onPresets: () -> Unit,
    selectedState: androidx.compose.runtime.MutableIntState,
    layoutMatters: Boolean,
    onToggleLid: () -> Unit,
    lidClosed: Boolean,
    onExit: () -> Unit,
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val colors = seedless
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val ra = com.seedlessds.app.ra.RetroAchievements
    var selected by selectedState

    val options = buildList {
        add(MenuOption(Icons.Rounded.PlayArrow, uiContext.getString(R.string.menu_resume), onResume, highlighted = true))
        add(MenuOption(Icons.Rounded.Settings, uiContext.getString(R.string.common_settings), onSettings))
        add(MenuOption(Icons.Rounded.Tune, uiContext.getString(R.string.common_game_settings), onGameSettings,
            trailing = if (perGameActive) uiContext.getString(R.string.common_active_3) else uiContext.getString(R.string.menu_global_2)))
        add(MenuOption(Icons.Rounded.Save, uiContext.getString(R.string.common_save_state), onSaveState))
        add(MenuOption(Icons.Rounded.FolderOpen, uiContext.getString(R.string.common_load_state), onLoadState))
        add(MenuOption(Icons.Rounded.Bolt, uiContext.getString(R.string.common_cheats), onCheats))
        if (achievementsEnabled) add(MenuOption(Icons.Rounded.EmojiEvents, uiContext.getString(R.string.common_achievements), onAchievements))
        if (leaderboardsEnabled) add(MenuOption(Icons.Rounded.Leaderboard, uiContext.getString(R.string.common_leaderboards), onLeaderboards))
        add(MenuOption(Icons.Rounded.Edit, uiContext.getString(R.string.menu_edit_controls), onEditControls))
        add(MenuOption(
            if (controlsHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            if (controlsHidden) uiContext.getString(R.string.menu_show_controls) else uiContext.getString(R.string.menu_hide_controls), onToggleControls))
        add(MenuOption(Icons.Rounded.SwapVert, uiContext.getString(R.string.common_swap_screens), onSwapScreens))
        add(MenuOption(Icons.Rounded.Dashboard, uiContext.getString(R.string.common_dual_screen_presets), onPresets))
        if (layoutMatters) add(MenuOption(Icons.Rounded.Dashboard, uiContext.getString(R.string.menu_change_layout), onCycleLayout))
        add(MenuOption(Icons.Rounded.Bedtime, if (lidClosed) uiContext.getString(R.string.menu_open_lid) else uiContext.getString(R.string.menu_close_lid), onToggleLid))
        add(MenuOption(Icons.AutoMirrored.Rounded.HelpOutline, uiContext.getString(R.string.common_help), onHelp))
        add(MenuOption(Icons.Rounded.RestartAlt, uiContext.getString(R.string.common_reset), { confirmReset = true }, destructive = true))
        add(MenuOption(Icons.AutoMirrored.Rounded.ExitToApp, uiContext.getString(R.string.menu_exit), { confirmExit = true }, destructive = true))
    }

    val columns = if (landscape) 3 else 1
    val menuGrid = rememberLazyGridState()

    androidx.compose.runtime.DisposableEffect(options.size, columns) {
        MenuKeyRouter.handler = handler@{ key ->
            val last = options.lastIndex
            when {
                MenuKeyRouter.isUp(key) -> selected = (selected - columns).coerceAtLeast(0)
                MenuKeyRouter.isDown(key) -> selected = (selected + columns).coerceAtMost(last)
                MenuKeyRouter.isLeft(key) -> selected = (selected - 1).coerceAtLeast(0)
                MenuKeyRouter.isRight(key) -> selected = (selected + 1).coerceAtMost(last)
                MenuKeyRouter.isAccept(key) -> options.getOrNull(selected)?.onClick?.invoke()
                MenuKeyRouter.isCancel(key) -> onResume()
                else -> return@handler false
            }
            true
        }
        onDispose { MenuKeyRouter.handler = null }
    }
    androidx.compose.runtime.LaunchedEffect(selected) {
        runCatching { menuGrid.animateScrollToItem(selected) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF008070A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onResume,
            )
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))) {
                    GameArt(gameTitle, null, Modifier.fillMaxSize(), raIconUrl = ra.gameIconUrl.value)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = gameTitle,
                        color = colors.text,
                        fontFamily = SpaceGrotesk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiContext.getString(R.string.menu_paused),
                            color = colors.red,
                            fontFamily = SeedlessMono,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (perGameActive) uiContext.getString(R.string.menu_per_game_settings) else uiContext.getString(R.string.menu_global_settings),
                            color = colors.text3,
                            fontFamily = SeedlessMono,
                            fontSize = 9.sp,
                        )
                    }
                }
                if (achievementsEnabled && ra.achTotal.value > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.EmojiEvents, null,
                            tint = SeedlessColors.gold, modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = uiContext.getString(R.string.achievement_menu_progress, ra.achUnlocked.value, ra.achTotal.value),
                            color = colors.text2,
                            fontFamily = SeedlessMono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = menuGrid,
                modifier = Modifier.weight(1f).verticalScrollbar(menuGrid, colors.green.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(options) { index, option ->
                    MenuTile(option, selected = index == selected, onFocus = { selected = index })
                }
            }

            GamepadHintsFooter(
                hints = listOf(
                    GamepadHint(null, uiContext.getString(R.string.common_navigate)),
                    GamepadHint(uiContext.getString(R.string.button_a), uiContext.getString(R.string.common_accept)),
                    GamepadHint(uiContext.getString(R.string.button_b), uiContext.getString(R.string.menu_resume)),
                ),
            )
        }
    }

    if (confirmReset) SeedlessConfirmDialog(
        title = uiContext.getString(R.string.menu_reset_the_game),
        message = uiContext.getString(R.string.menu_unsaved_progress_will_be_lost),
        confirmLabel = uiContext.getString(R.string.common_reset),
        onConfirm = { confirmReset = false; onReset() },
        onDismiss = { confirmReset = false },
    )
    if (confirmExit) SeedlessConfirmDialog(
        title = uiContext.getString(R.string.menu_exit_the_game),
        message = uiContext.getString(R.string.menu_the_emulator_will_close_and_unsaved_progress),
        confirmLabel = uiContext.getString(R.string.menu_exit),
        onConfirm = { confirmExit = false; onExit() },
        onDismiss = { confirmExit = false },
    )
}

private data class MenuOption(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val highlighted: Boolean = false,
    val destructive: Boolean = false,
    val trailing: String? = null,
)

@Composable
private fun MenuTile(option: MenuOption, selected: Boolean, onFocus: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(
                when {
                    selected -> Color.White.copy(alpha = 0.18f)
                    option.highlighted -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.045f)
                }
            )
            .let { if (selected) it.border(2.dp, colors.red, shape) else it }
            .clickable { onFocus(); option.onClick() },
    ) {
        if (option.highlighted) {
            Box(Modifier.width(3.dp).height(24.dp).background(colors.red))
        }
        Spacer(Modifier.width(if (option.highlighted) 11.dp else 14.dp))
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            tint = if (option.destructive) colors.red else colors.text2,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = option.label,
            color = if (option.destructive) colors.red else colors.text,
            fontFamily = Manrope,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        if (option.trailing != null) {
            Text(
                text = option.trailing,
                color = if (option.trailing == uiContext.getString(R.string.common_active_3)) colors.green else colors.text3,
                fontFamily = SeedlessMono,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

private val EXTRA_LABELS = mapOf(
    1 to R.string.menu_ff, 2 to R.string.menu_qs, 3 to R.string.menu_ql, 4 to R.string.swap_screens_symbol, 5 to R.string.menu_1_2, 6 to R.string.toggle_controls_symbol, 7 to R.string.menu_mic, 8 to R.string.cycle_layouts_symbol, 9 to R.string.menu_tb
)

@Composable
private fun ControlsOverlay(
    glView: DsGlView, settings: DsSettings,
    offsets: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Pair<Float, Float>>,
    scales: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Float>,
    hiddenSet: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    selectedId: String?, onSelect: (String) -> Unit,
    editMode: Boolean, hideControls: Boolean, dispatchExtra: (Int, Boolean) -> Unit,
    onMenu: () -> Unit
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val C = EmulatorActivity
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator)
    }
    val haptic: () -> Unit = {
        if (settings.hapticFeedback) {
            if (android.os.Build.VERSION.SDK_INT >= 26)
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(18, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator.vibrate(18)
        }
    }
    val alpha = settings.controllerAlpha.coerceIn(0f, 1f)
    val style = settings.padStyle

    fun isVisible(id: String): Boolean = editMode || hiddenSet[id] != true

    fun Modifier.ctrl(id: String): Modifier {
        val o = offsets[id] ?: (0f to 0f)
        val sc = scales[id] ?: 1f
        var m = this
            .offset { IntOffset(o.first.dp.roundToPx(), o.second.dp.roundToPx()) }
            .graphicsLayer { scaleX = sc; scaleY = sc; if (editMode && hiddenSet[id] == true) this.alpha = 0.35f }
        if (editMode) m = m
            .dashedOutline(selectedId == id)
            .pointerInput(id) {
                detectDragGestures(onDragStart = { onSelect(id) }) { change, drag ->
                    change.consume()
                    val cur = offsets[id] ?: (0f to 0f)
                    offsets[id] = (cur.first + drag.x / density / sc) to (cur.second + drag.y / density / sc)
                }
            }
            .pointerInput(id) { detectTapGestures { onSelect(id) } }
        return m
    }

    val showMain = !hideControls || editMode

    Box(Modifier.fillMaxSize()) {
        if (showMain) {
            if (isVisible("lbtn")) PadButton(uiContext.getString(R.string.button_l), Modifier.align(Alignment.TopStart).ctrl("lbtn").padding(12.dp).size(64.dp, 44.dp),
                RoundedCornerShape(10.dp), alpha, haptic, editMode, style) { glView.setButton(C.L, it) }
            if (isVisible("rbtn")) PadButton(uiContext.getString(R.string.button_r), Modifier.align(Alignment.TopEnd).ctrl("rbtn").padding(12.dp).size(64.dp, 44.dp),
                RoundedCornerShape(10.dp), alpha, haptic, editMode, style) { glView.setButton(C.R, it) }

            if (isVisible("dpad")) Dpad(Modifier.align(Alignment.BottomStart).ctrl("dpad").padding(16.dp), glView, alpha, haptic, editMode, style)
            if (isVisible("face")) FaceButtons(Modifier.align(Alignment.BottomEnd).ctrl("face").padding(16.dp), glView, alpha, haptic, editMode, style)

            if (settings.showStartSelect && isVisible("startselect")) Row(Modifier.align(Alignment.BottomCenter).ctrl("startselect").padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PadButton(uiContext.getString(R.string.common_select), Modifier.size(96.dp, 34.dp), RoundedCornerShape(16.dp), alpha, haptic, editMode, style) { glView.setButton(C.SELECT, it) }
                PadButton(uiContext.getString(R.string.common_start), Modifier.size(96.dp, 34.dp), RoundedCornerShape(16.dp), alpha, haptic, editMode, style) { glView.setButton(C.START, it) }
            }
        }

        val extras = listOf(settings.extraFunc1, settings.extraFunc2, settings.extraFunc3)
        if (isVisible("extra")) Column(Modifier.align(Alignment.CenterEnd).ctrl("extra").padding(end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            extras.forEachIndexed { i, fn ->
                if (fn != 0) PadButton(uiContext.getString(EXTRA_LABELS[fn] ?: R.string.unknown_symbol), Modifier.size(50.dp),
                    RoundedCornerShape(8.dp), alpha, haptic, editMode, style) { pressed -> dispatchExtra(fn, pressed) }
            }
        }

        val menuAlign = when (settings.menuButtonPos) {
            1 -> Alignment.BottomCenter; 2 -> Alignment.CenterStart; 3 -> Alignment.CenterEnd
            4 -> null; else -> Alignment.TopCenter
        }
        if (menuAlign != null && isVisible("menu")) Box(
            Modifier.align(menuAlign).ctrl("menu").padding(8.dp).size(48.dp)
                .background(Color(0x55000000), CircleShape)
                .then(if (editMode) Modifier else Modifier.pointerInput(Unit) { detectTapReleased(onMenu) }),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Menu, "menu", tint = Color.White.copy(alpha = 0.9f)) }
    }
}

@Composable
private fun Dpad(modifier: Modifier, glView: DsGlView, alpha: Float, haptic: () -> Unit, editMode: Boolean, style: Int) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val C = EmulatorActivity
    val s = 52.dp
    val noDiagonals = SettingsRepo.current.noDiagonals
    var mask by remember { mutableIntStateOf(0) }
    val (bg, pressedBg, fg) = padColors(style)
    Box(
        modifier.size(s * 3).androidx_alpha(alpha)
            .then(if (editMode) Modifier else Modifier.pointerInput(noDiagonals) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        var m = 0
                        for (ch in ev.changes) if (ch.pressed) m = m or dpadMask(ch.position, size, noDiagonals)
                        if (m != mask) {
                            if (mask == 0 && m != 0) haptic()
                            mask = m
                            glView.setButton(C.UP, m and 1 != 0); glView.setButton(C.DOWN, m and 2 != 0)
                            glView.setButton(C.LEFT, m and 4 != 0); glView.setButton(C.RIGHT, m and 8 != 0)
                        }
                    }
                }
            })
    ) {
        DpadCap(uiContext.getString(R.string.direction_up_symbol), mask and 1 != 0, Modifier.align(Alignment.TopCenter).size(s), bg, pressedBg, fg)
        DpadCap(uiContext.getString(R.string.direction_down_symbol), mask and 2 != 0, Modifier.align(Alignment.BottomCenter).size(s), bg, pressedBg, fg)
        DpadCap(uiContext.getString(R.string.direction_left_symbol), mask and 4 != 0, Modifier.align(Alignment.CenterStart).size(s), bg, pressedBg, fg)
        DpadCap(uiContext.getString(R.string.direction_right_symbol), mask and 8 != 0, Modifier.align(Alignment.CenterEnd).size(s), bg, pressedBg, fg)
    }
}

@Composable
private fun DpadCap(label: String, pressed: Boolean, modifier: Modifier, bg: Color, pressedBg: Color, fg: Color) {
    Box(modifier.background(if (pressed) pressedBg else bg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun dpadMask(p: Offset, size: IntSize, noDiagonals: Boolean): Int {
    if (size.width == 0 || size.height == 0) return 0
    val dx = (p.x - size.width / 2f) / (size.width / 2f)
    val dy = (p.y - size.height / 2f) / (size.height / 2f)
    val ax = kotlin.math.abs(dx); val ay = kotlin.math.abs(dy)
    if (ax < 0.16f && ay < 0.16f) return 0
    val t = 0.34f
    var m = 0
    val vertical = ay >= t || (noDiagonals && ay >= ax)
    val horizontal = ax >= t || (noDiagonals && ax > ay)
    if (vertical && !(noDiagonals && ax > ay)) m = m or (if (dy < 0f) 1 else 2)
    if (horizontal && !(noDiagonals && ay >= ax)) m = m or (if (dx < 0f) 4 else 8)
    return m
}

@Composable
private fun FaceButtons(modifier: Modifier, glView: DsGlView, alpha: Float, haptic: () -> Unit, editMode: Boolean, style: Int) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val C = EmulatorActivity
    val s = 56.dp
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PadButton(uiContext.getString(R.string.button_x), Modifier.size(s), CircleShape, alpha, haptic, editMode, style) { glView.setButton(C.X, it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PadButton(uiContext.getString(R.string.button_y), Modifier.size(s), CircleShape, alpha, haptic, editMode, style) { glView.setButton(C.Y, it) }
            Spacer(Modifier.width(s))
            PadButton(uiContext.getString(R.string.button_a), Modifier.size(s), CircleShape, alpha, haptic, editMode, style) { glView.setButton(C.A, it) }
        }
        PadButton(uiContext.getString(R.string.button_b), Modifier.size(s), CircleShape, alpha, haptic, editMode, style) { glView.setButton(C.B, it) }
    }
}

private fun padColors(style: Int): Triple<Color, Color, Color> = when (style) {
    1 -> Triple(Color(0xFFE8E8F0), Color(0xFFFFFFFF), Color(0xFF222233))
    2 -> Triple(Color(0x33101018), Color(0x66FFFFFF), Color.White)
    else -> Triple(Color(0xFF222233), Color(0xFFDDDDEE), Color.White)
}

@Composable
private fun PadButton(
    label: String, modifier: Modifier, shape: Shape, alpha: Float, haptic: () -> Unit,
    editMode: Boolean, style: Int = 0, onPress: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val (bg, pressedBg, fg) = padColors(style)
    Box(
        modifier
            .androidx_alpha(alpha)
            .background(if (pressed) pressedBg else bg, shape)
            .then(if (editMode) Modifier else Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        pressed = true; onPress(true); haptic()
                        waitForUpOrCancellation()
                        pressed = false; onPress(false)
                    }
                }
            }),
        contentAlignment = Alignment.Center
    ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

private fun Modifier.androidx_alpha(a: Float): Modifier = this.then(Modifier.graphicsLayer { this.alpha = a })

private val SelYellow = Color(0xFFFFC107)

private fun Modifier.dashedOutline(selected: Boolean): Modifier = this.drawBehind {
    val stroke = (if (selected) 3f else 1.5f) * density
    val color = if (selected) SelYellow else SelYellow.copy(alpha = 0.35f)
    val dash = if (selected) floatArrayOf(16f, 9f) else floatArrayOf(9f, 9f)
    val r = 10f * density
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(dash, 0f))
    )
}

@Composable
private fun ScreenEditHandle(
    id: String, title: String, rect: FloatArray, selected: Boolean, canvas: IntSize,
    onSelect: () -> Unit, addOff: (Float, Float) -> Unit
) {
    if (rect[2] <= 0f || rect[3] <= 0f || canvas.width <= 0) return
    val d = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(rect[0].roundToInt(), rect[1].roundToInt()) }
            .size(with(d) { rect[2].toDp() }, with(d) { rect[3].toDp() })
            .dashedOutline(selected)
            .pointerInput(id) {
                detectDragGestures(onDragStart = { onSelect() }) { change, drag ->
                    change.consume()
                    addOff(drag.x / canvas.width, drag.y / canvas.height)
                }
            }
            .pointerInput(id) { detectTapGestures { onSelect() } },
        contentAlignment = Alignment.Center
    ) { Text(title, color = SelYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BoxScope.ExtScreenHandle(selected: Boolean, onSelect: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    Box(
        Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(132.dp, 82.dp)
            .dashedOutline(selected)
            .pointerInput(Unit) { detectTapGestures { onSelect() } },
        contentAlignment = Alignment.Center
    ) {
        Text(uiContext.getString(R.string.menu_external_screen_2), color = SelYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun EditActionBar(onDrag: (Int, Int) -> Unit, onSave: () -> Unit, onReset: () -> Unit, onCancel: () -> Unit) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.95f))
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        Box(
            Modifier.padding(horizontal = 4.dp).size(32.dp).pointerInput(Unit) {
                detectDragGestures { change, drag -> change.consume(); onDrag(drag.x.roundToInt(), drag.y.roundToInt()) }
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.DragIndicator, uiContext.getString(R.string.menu_move_toolbar), tint = colors.text3, modifier = Modifier.size(19.dp)) }
        SeedlessButton(uiContext.getString(R.string.common_save), colors.green, onSave, Modifier.padding(horizontal = 3.dp))
        SeedlessButton(uiContext.getString(R.string.common_reset), colors.surface2, onReset, Modifier.padding(horizontal = 3.dp))
        SeedlessButton(uiContext.getString(R.string.common_cancel), colors.surface2, onCancel, Modifier.padding(horizontal = 3.dp))
    }
}

@Composable
private fun EditSizeBar(
    modifier: Modifier, label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    hidden: Boolean?, onToggleHide: (() -> Unit)?, onValue: (Float) -> Unit
) {
    val uiContext = androidx.compose.ui.platform.LocalContext.current
    val colors = seedless
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.95f))
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = SelYellow,
            fontFamily = SeedlessMono,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(92.dp),
        )
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            modifier = Modifier.width(158.dp).padding(horizontal = 8.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = colors.green,
                activeTrackColor = colors.green,
                inactiveTrackColor = colors.switchOff,
            ),
        )
        Text(
            text = String.format(uiContext.getString(R.string.control_scale_format), value),
            color = colors.text,
            fontFamily = SeedlessMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(46.dp),
        )
        if (onToggleHide != null) {
            Box(
                Modifier.padding(start = 4.dp).size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (hidden == true) colors.redGlow else Color.Transparent)
                    .clickable(onClick = onToggleHide),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (hidden == true) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = if (hidden == true) uiContext.getString(R.string.menu_show) else uiContext.getString(R.string.menu_hide),
                    tint = if (hidden == true) colors.red else colors.text2,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private suspend fun PointerInputScope.detectTapReleased(onTap: () -> Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitFirstDown(requireUnconsumed = false)
            if (waitForUpOrCancellation() != null) onTap()
        }
    }
}
