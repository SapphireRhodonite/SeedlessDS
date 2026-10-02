package com.seedlessds.app.emu

import com.seedlessds.core.SeedlessCore

import android.content.Context

data class DsSettings(
    val frameskipType: Int = 0,
    val frameskipValue: Int = 4,
    val frameskipSafe: Boolean = false,
    val ffwdSpeed: Int = 2,
    val internalRes: Int = 1,
    val hires3D: Boolean = true,
    val dynamicRes: Boolean = false,
    val gl16bit: Boolean = false,
    val disableEdgeMarking: Boolean = false,
    val fixMainScreen: Boolean = false,
    val threaded3D: Boolean = false,
    val blend: Boolean = false,
    val soundEnabled: Boolean = true,
    val audioLatency: Int = 3,
    val volume: Int = 10,
    val micEnabled: Boolean = true,
    val micLevel: Int = 1,
    val firmwareNick: String = "Dra Sapph",
    val firmwareLanguage: Int = 1,
    val firmwareColor: Int = 0,
    val bdayMonth: Int = 6,
    val bdayDay: Int = 6,
    val slot2Type: Int = 1,
    val rtcSystemTime: Boolean = false,
    val showFps: Boolean = false,
    val showScale: Boolean = false,
    val autosaveMode: Int = 0,
    val autofireSpeed: Int = 2,
    val cheatsEnabled: Boolean = true,
    val luaEnabled: Boolean = true,
    val rawSavFormat: Boolean = false,
    val backupInSavestates: Boolean = true,
    val ignoreGamecardLimit: Boolean = false,
    val autoTrim: Boolean = false,
    val preloadRoms: Boolean = false,
    val filter: String = "None",
    val controllerAlpha: Float = 0.45f,
    val hapticFeedback: Boolean = false,
    val showStartSelect: Boolean = true,
    val hideControlsWithGamepad: Boolean = true,
    val swapScreens: Boolean = false,
    val singleScreen: Boolean = false,
    val singleBottom: Boolean = false,
    val padStyle: Int = 0,
    val screenLayout: Int = 0,
    val screenLayout2: Int = 2,
    val rotationMode: Int = 0,
    val extraFunc1: Int = 0,
    val extraFunc2: Int = 0,
    val extraFunc3: Int = 0,
    val touchThrough: Boolean = false,
    val noDiagonals: Boolean = false,
    val appLogFile: Boolean = false,
    val menuButtonPos: Int = 0,
    val dpadModifier: Float = 1.6f,
    val buttonModifier: Float = 1.6f,
    val fastForwardToggle: Boolean = true,
    val showFfwdIndicator: Boolean = true,
    val rumbleDev: Int = 2,
    val analogStickMode: Int = -1,
    val analogTriggers: Boolean = true,
    val analogTouch: Boolean = false,
    val analogDeadzone: Float = 0.05f,
    val disableMapped: Boolean = false,
    val disableBackButton: Boolean = false,
    val loadNeedsConfirm: Boolean = false,
    val overwriteNeedsConfirm: Boolean = true,
    val fpsTransparent: Boolean = true,
    val extDisplayType: Int = 2,
    val extDisplayScreen: Int = 1,
    val extDisplayBorder: Int = 0,

    val extDisplayDelay: Int = 1,
    val integerScale: Boolean = false,
    val dualScreenPreset: Int = 1,
    val dsKeepRatio: Boolean = false,
    val dsIntFillW: Boolean = false,
    val dsIntFillH: Boolean = false,
    val dsExtFillW: Boolean = false,
    val dsExtFillH: Boolean = false,
    val dsIntAlign: Int = 2,
    val dsExtAlign: Int = 1,

    val dsSameSize: Boolean = true,
    val saveLocation: Int = 0,
    val saveFolder: String = "",
    val cpuLoad: Int = 0,
    val glThreadSleep: Boolean = true,
    val lowResTextures: Boolean = false,
    val zipCaching: Boolean = true,
    val customClockEnable: Boolean = false,
    val customClock: Long = 0L,
    val autoload: Boolean = false,
    val smartEdit: Boolean = true,
) {
    fun withFrameskip(type: Int): DsSettings = copy(frameskipType = type, dynamicRes = dynamicRes && type == 0)
    fun withDynamicRes(on: Boolean): DsSettings = copy(dynamicRes = on, frameskipType = if (on) 0 else frameskipType)

    fun pack(): Long {
        var v = 0L
        v = v or (frameskipValue.toLong() and 15L)
        v = v or (when (frameskipType) { 0 -> 1L; 1 -> 2L; else -> 0L } shl 5)
        v = v or ((audioLatency.toLong() and 3L) shl 8)
        v = v or ((ffwdSpeed.toLong() and 15L) shl 12)
        v = v or ((SettingsRepo.threadCount.toLong() and 15L) shl 16)
        v = v or ((autofireSpeed.toLong() and 7L) shl 32)
        v = v or ((micLevel.toLong() and 3L) shl 37)
        v = v or ((slot2Type.toLong() and 15L) shl 43)
        if (soundEnabled)        v = v or 0x80000000L
        if (showFps)             v = v or 0x40000000L
        if (SettingsRepo.fastForward) v = v or 0x20000000L
        if (threaded3D)          v = v or 0x10000000L
        if (cheatsEnabled)       v = v or 0x8000000L
        if (micEnabled)          v = v or 0x4000000L
        if (backupInSavestates)  v = v or 0x2000000L
        if (ignoreGamecardLimit) v = v or 0x1000000L
        if (gl16bit)             v = v or 0x800000L
        if (autoTrim)            v = v or 0x1000000000L
        if (fixMainScreen)       v = v or 0x800000000L
        if (rtcSystemTime)       v = v or 0x8000000000L
        if (disableEdgeMarking)  v = v or 0x10000000000L
        if (hires3D)             v = v or 0x20000000000L
        if (luaEnabled)          v = v or 0x40000000000L
        if (frameskipSafe)       v = v or 0x800000000000L
        if (preloadRoms)         v = v or 0x1000000000000L
        if (blend)               v = v or 0x2000000000000L
        if (rawSavFormat)        v = v or 0x4000000000000L
        return v
    }

    fun firmwarePacked(): Int =
        (firmwareLanguage and 0xFF) or ((firmwareColor and 0xFF) shl 8) or
        ((bdayMonth and 0xFF) shl 16) or ((bdayDay and 0xFF) shl 24)

    fun autosaveInterval(): Int = when (autosaveMode) {
        2 -> 300; 3 -> 900; 4 -> 1800; else -> 0
    }
}

object SettingsRepo {
    private const val PREFS = "seedless_config"

    val threadCount: Int
        get() {
            val a = auto3d
            val f = com.seedlessds.app.control.RuntimeControl.threads3d ?: return a
            return if (f in 1..15) f else a
        }

    private val auto3d: Int = run {
        val c = Runtime.getRuntime().availableProcessors()
        if (c >= 6) 8 else if (c >= 5) 4 else if (c >= 4) 3 else if (c >= 2) 2 else 1
    }

    @Volatile var fastForward: Boolean = false

    @Volatile
    var global: DsSettings = DsSettings()
        private set

    @Volatile
    var current: DsSettings = DsSettings()
        private set

    private fun configPrefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS + Profiles.suffix(ctx), Context.MODE_PRIVATE)

    private fun pgPrefs(ctx: Context, gameKey: String) =
        ctx.getSharedPreferences("pg" + Profiles.suffix(ctx) + "_" + gameKey.hashCode(), Context.MODE_PRIVATE)

    fun hasPerGame(ctx: Context, gameKey: String): Boolean =
        pgPrefs(ctx, gameKey).getBoolean("_HasPerGame", false)

    fun activateFor(ctx: Context, gameKey: String) {
        current = com.seedlessds.app.control.RuntimeControl.settings
            ?: if (hasPerGame(ctx, gameKey)) readFrom(pgPrefs(ctx, gameKey), global) else global
        writeScale(ctx, current)
    }

    fun perGame(ctx: Context, gameKey: String): DsSettings =
        if (hasPerGame(ctx, gameKey)) readFrom(pgPrefs(ctx, gameKey), global) else global

    fun savePerGame(ctx: Context, gameKey: String, s: DsSettings) {
        val e = pgPrefs(ctx, gameKey).edit(); e.clear()
        writeDiff(e, s, global); e.putBoolean("_HasPerGame", true).apply()
        current = s
        writeScale(ctx, current)
    }

    fun clearPerGame(ctx: Context, gameKey: String) {
        pgPrefs(ctx, gameKey).edit().clear().apply()
        current = global
        writeScale(ctx, current)
    }

    fun load(ctx: Context): DsSettings {
        val cp = configPrefs(ctx)
        fastForward = cp.getBoolean("_FfwdAtStart", false)
        global = readFrom(cp)
        current = global
        return global
    }

    fun factory(): DsSettings = DsSettings()

    internal fun readFrom(p: android.content.SharedPreferences, base: DsSettings = factory()): DsSettings {
        val d = base
        return DsSettings(
            frameskipType = p.getInt("_FrameskipType", d.frameskipType),
            frameskipValue = p.getInt("_FrameskipValue", d.frameskipValue),
            frameskipSafe = p.getBoolean("_FrameskipSafe", d.frameskipSafe),
            ffwdSpeed = p.getInt("_FfwdSpeed", d.ffwdSpeed),
            internalRes = p.getInt("_InternalRes", d.internalRes),
            hires3D = p.getBoolean("_Hires3D", d.hires3D),
            dynamicRes = p.getBoolean("_DynamicRes", d.dynamicRes),
            showScale = p.getBoolean("_ShowScale", d.showScale),
            gl16bit = p.getBoolean("_GlUse16Bit", d.gl16bit),
            disableEdgeMarking = p.getBoolean("_DisableEdgeMarking", d.disableEdgeMarking),
            fixMainScreen = p.getBoolean("_FixMainEngineScreen", d.fixMainScreen),
            threaded3D = p.getBoolean("_Threaded3D", d.threaded3D),
            blend = p.getBoolean("_Blend", d.blend),
            soundEnabled = p.getBoolean("_SoundEnabled", d.soundEnabled),
            audioLatency = p.getInt("_AudioLatency", d.audioLatency),
            volume = p.getInt("_Volume", d.volume),
            micEnabled = p.getBoolean("_MicEnabled", d.micEnabled),
            micLevel = p.getInt("_MicLevel", d.micLevel),
            firmwareNick = (p.getString("_FirmwareNick", d.firmwareNick) ?: d.firmwareNick).take(10),
            firmwareLanguage = p.getInt("_FirmwareLanguage", d.firmwareLanguage),
            firmwareColor = p.getInt("_FirmwareColor", d.firmwareColor),
            bdayMonth = p.getInt("_FirmwareBdayMonth", d.bdayMonth),
            bdayDay = p.getInt("_FirmwareBdayDay", d.bdayDay),
            slot2Type = p.getInt("_Slot2Type", d.slot2Type),
            rtcSystemTime = p.getBoolean("_RtcSystemTime", d.rtcSystemTime),
            showFps = p.getBoolean("_ShowFPS", d.showFps),
            autosaveMode = p.getInt("_AutosaveMode", d.autosaveMode),
            autofireSpeed = p.getInt("_AutoFireSpeed", d.autofireSpeed),
            cheatsEnabled = p.getBoolean("_CheatsEnabled", d.cheatsEnabled),
            luaEnabled = p.getBoolean("_LuaEnabled", d.luaEnabled),
            rawSavFormat = p.getBoolean("_RawSavFormat", d.rawSavFormat),
            backupInSavestates = p.getBoolean("_BackupInSavestates", d.backupInSavestates),
            ignoreGamecardLimit = p.getBoolean("_IgnoreGamecardLimit", d.ignoreGamecardLimit),
            autoTrim = p.getBoolean("_AutoTrim", d.autoTrim),
            preloadRoms = p.getBoolean("_PreloadRoms", d.preloadRoms),
            filter = p.getString("_CurrentFx", d.filter) ?: d.filter,
            controllerAlpha = p.getFloat("_ControllerAlpha", d.controllerAlpha),
            hapticFeedback = p.getBoolean("_HapticFeedback", d.hapticFeedback),
            showStartSelect = p.getBoolean("_ShowMore", d.showStartSelect),
            hideControlsWithGamepad = p.getBoolean("_HideControlsWithGamepad", d.hideControlsWithGamepad),
            swapScreens = p.getBoolean("_ScreenSwap", d.swapScreens),
            singleScreen = p.getBoolean("_SingleScreen", d.singleScreen),
            singleBottom = p.getBoolean("_SingleScreenBottom", d.singleBottom),
            padStyle = p.getInt("_VirtualPadStyle", d.padStyle),
            screenLayout = p.getInt("_DefaultLayout", d.screenLayout),
            screenLayout2 = p.getInt("_ScreenLayout2", d.screenLayout2),
            rotationMode = p.getInt("_RotationMode", d.rotationMode),
            extraFunc1 = p.getInt("_ExtraFuncs_0", d.extraFunc1),
            extraFunc2 = p.getInt("_ExtraFuncs_1", d.extraFunc2),
            extraFunc3 = p.getInt("_ExtraFuncs_2", d.extraFunc3),
            touchThrough = p.getBoolean("_TouchThrough", d.touchThrough),
            noDiagonals = p.getBoolean("_NoDiagonals", d.noDiagonals),
            appLogFile = p.getBoolean("_AppLogFile", d.appLogFile),
            menuButtonPos = p.getInt("_MenuButtonPos", d.menuButtonPos),
            dpadModifier = p.getFloat("_DpadModifier", d.dpadModifier),
            buttonModifier = p.getFloat("_ButtonModifier", d.buttonModifier),
            fastForwardToggle = p.getBoolean("_FastForwardToggle", d.fastForwardToggle),
            showFfwdIndicator = p.getBoolean("_ShowFfwdIndicator", d.showFfwdIndicator),
            rumbleDev = p.getInt("_RumbleDev", d.rumbleDev),
            analogStickMode = p.getInt("_AnalogStickMode", d.analogStickMode),
            analogTriggers = p.getBoolean("_AnalogTriggers", d.analogTriggers),
            analogTouch = p.getBoolean("_AnalogTouch", d.analogTouch),
            analogDeadzone = p.getFloat("_AnalogDeadzone", d.analogDeadzone),
            disableMapped = p.getBoolean("_DisableMapped", d.disableMapped),
            disableBackButton = p.getBoolean("_DisableBackButton", d.disableBackButton),
            loadNeedsConfirm = p.getBoolean("_LoadNeedsConfirm", d.loadNeedsConfirm),
            overwriteNeedsConfirm = p.getBoolean("_OverwriteNeedsConfirm", d.overwriteNeedsConfirm),
            fpsTransparent = p.getBoolean("_FpsTransparent", d.fpsTransparent),
            extDisplayType = p.getInt("_ExtDisplayType", d.extDisplayType),
            extDisplayScreen = p.getInt("_ExtDisplayScreen", d.extDisplayScreen),
            integerScale = p.getBoolean("_IntegerScale", d.integerScale),
            dualScreenPreset = p.getInt("_DualScreenPreset", d.dualScreenPreset),
            dsKeepRatio = p.getBoolean("_DsKeepRatio", d.dsKeepRatio),
            dsIntFillW = p.getBoolean("_DsIntFillW", d.dsIntFillW),
            dsIntFillH = p.getBoolean("_DsIntFillH", d.dsIntFillH),
            dsExtFillW = p.getBoolean("_DsExtFillW", d.dsExtFillW),
            dsExtFillH = p.getBoolean("_DsExtFillH", d.dsExtFillH),
            dsIntAlign = p.getInt("_DsIntAlign", d.dsIntAlign),
            dsExtAlign = p.getInt("_DsExtAlign", d.dsExtAlign),
            dsSameSize = p.getBoolean("_DsSameSize", d.dsSameSize),
            saveLocation = p.getInt("_SaveLocation", d.saveLocation),
            saveFolder = p.getString("_SaveFolder", d.saveFolder) ?: d.saveFolder,
            extDisplayBorder = p.getInt("_ExtDisplayBorder", d.extDisplayBorder),
            extDisplayDelay = p.getInt("_ExtDisplayDelay", d.extDisplayDelay),
            cpuLoad = p.getInt("_CpuLoad", d.cpuLoad),
            glThreadSleep = p.getBoolean("_GlThreadSleep", d.glThreadSleep),
            lowResTextures = p.getBoolean("_UseLowResTextures", d.lowResTextures),
            zipCaching = p.getBoolean("_ZipCaching", d.zipCaching),
            customClockEnable = p.getBoolean("_CustomClockEnable", d.customClockEnable),
            customClock = p.getLong("_CustomClock", d.customClock),
            autoload = p.getBoolean("_ShortcutAutoResume", d.autoload),
            smartEdit = p.getBoolean("_SmartEdit", d.smartEdit),
        ).let { it.withFrameskip(it.frameskipType) }
    }

    private fun writeTo(e: android.content.SharedPreferences.Editor, s: DsSettings) {
        e.apply {
            putInt("_FrameskipType", s.frameskipType)
            putInt("_FrameskipValue", s.frameskipValue)
            putBoolean("_FrameskipSafe", s.frameskipSafe)
            putInt("_FfwdSpeed", s.ffwdSpeed)
            putInt("_InternalRes", s.internalRes)
            putBoolean("_Hires3D", s.hires3D)
            putBoolean("_DynamicRes", s.dynamicRes)
            putBoolean("_ShowScale", s.showScale)
            putBoolean("_GlUse16Bit", s.gl16bit)
            putBoolean("_DisableEdgeMarking", s.disableEdgeMarking)
            putBoolean("_FixMainEngineScreen", s.fixMainScreen)
            putBoolean("_Threaded3D", s.threaded3D)
            putBoolean("_Blend", s.blend)
            putBoolean("_SoundEnabled", s.soundEnabled)
            putInt("_AudioLatency", s.audioLatency)
            putInt("_Volume", s.volume)
            putBoolean("_MicEnabled", s.micEnabled)
            putInt("_MicLevel", s.micLevel)
            putString("_FirmwareNick", s.firmwareNick)
            putInt("_FirmwareLanguage", s.firmwareLanguage)
            putInt("_FirmwareColor", s.firmwareColor)
            putInt("_FirmwareBdayMonth", s.bdayMonth)
            putInt("_FirmwareBdayDay", s.bdayDay)
            putInt("_Slot2Type", s.slot2Type)
            putBoolean("_RtcSystemTime", s.rtcSystemTime)
            putBoolean("_ShowFPS", s.showFps)
            putInt("_AutosaveMode", s.autosaveMode)
            putInt("_AutoFireSpeed", s.autofireSpeed)
            putBoolean("_CheatsEnabled", s.cheatsEnabled)
            putBoolean("_LuaEnabled", s.luaEnabled)
            putBoolean("_RawSavFormat", s.rawSavFormat)
            putBoolean("_BackupInSavestates", s.backupInSavestates)
            putBoolean("_IgnoreGamecardLimit", s.ignoreGamecardLimit)
            putBoolean("_AutoTrim", s.autoTrim)
            putBoolean("_PreloadRoms", s.preloadRoms)
            putString("_CurrentFx", s.filter)
            putFloat("_ControllerAlpha", s.controllerAlpha)
            putBoolean("_HapticFeedback", s.hapticFeedback)
            putBoolean("_ShowMore", s.showStartSelect)
            putBoolean("_HideControlsWithGamepad", s.hideControlsWithGamepad)
            putBoolean("_ScreenSwap", s.swapScreens)
            putBoolean("_SingleScreen", s.singleScreen)
            putBoolean("_SingleScreenBottom", s.singleBottom)
            putInt("_VirtualPadStyle", s.padStyle)
            putInt("_DefaultLayout", s.screenLayout)
            putInt("_ScreenLayout2", s.screenLayout2)
            putInt("_RotationMode", s.rotationMode)
            putInt("_ExtraFuncs_0", s.extraFunc1)
            putInt("_ExtraFuncs_1", s.extraFunc2)
            putInt("_ExtraFuncs_2", s.extraFunc3)
            putBoolean("_TouchThrough", s.touchThrough)
            putBoolean("_NoDiagonals", s.noDiagonals)
            putBoolean("_AppLogFile", s.appLogFile)
            putInt("_MenuButtonPos", s.menuButtonPos)
            putFloat("_DpadModifier", s.dpadModifier)
            putFloat("_ButtonModifier", s.buttonModifier)
            putBoolean("_FastForwardToggle", s.fastForwardToggle)
            putBoolean("_ShowFfwdIndicator", s.showFfwdIndicator)
            putInt("_RumbleDev", s.rumbleDev)
            putInt("_AnalogStickMode", s.analogStickMode)
            putBoolean("_AnalogTriggers", s.analogTriggers)
            putBoolean("_AnalogTouch", s.analogTouch)
            putFloat("_AnalogDeadzone", s.analogDeadzone)
            putBoolean("_DisableMapped", s.disableMapped)
            putBoolean("_DisableBackButton", s.disableBackButton)
            putBoolean("_LoadNeedsConfirm", s.loadNeedsConfirm)
            putBoolean("_OverwriteNeedsConfirm", s.overwriteNeedsConfirm)
            putBoolean("_FpsTransparent", s.fpsTransparent)
            putInt("_ExtDisplayType", s.extDisplayType)
            putInt("_ExtDisplayScreen", s.extDisplayScreen)
            putBoolean("_IntegerScale", s.integerScale)
            putInt("_DualScreenPreset", s.dualScreenPreset)
            putBoolean("_DsKeepRatio", s.dsKeepRatio)
            putBoolean("_DsIntFillW", s.dsIntFillW)
            putBoolean("_DsIntFillH", s.dsIntFillH)
            putBoolean("_DsExtFillW", s.dsExtFillW)
            putBoolean("_DsExtFillH", s.dsExtFillH)
            putInt("_DsIntAlign", s.dsIntAlign)
            putInt("_DsExtAlign", s.dsExtAlign)
            putBoolean("_DsSameSize", s.dsSameSize)
            putInt("_SaveLocation", s.saveLocation)
            putString("_SaveFolder", s.saveFolder)
            putInt("_ExtDisplayBorder", s.extDisplayBorder)
            putInt("_ExtDisplayDelay", s.extDisplayDelay)
            putInt("_CpuLoad", s.cpuLoad)
            putBoolean("_GlThreadSleep", s.glThreadSleep)
            putBoolean("_UseLowResTextures", s.lowResTextures)
            putBoolean("_ZipCaching", s.zipCaching)
            putBoolean("_CustomClockEnable", s.customClockEnable)
            putLong("_CustomClock", s.customClock)
            putBoolean("_ShortcutAutoResume", s.autoload)
            putBoolean("_SmartEdit", s.smartEdit)
        }
    }

    private fun writeDiff(e: android.content.SharedPreferences.Editor, s: DsSettings, b: DsSettings) {
        fun di(k: String, v: Int, bv: Int) { if (v != bv) e.putInt(k, v) else e.remove(k) }
        fun db(k: String, v: Boolean, bv: Boolean) { if (v != bv) e.putBoolean(k, v) else e.remove(k) }
        fun df(k: String, v: Float, bv: Float) { if (v != bv) e.putFloat(k, v) else e.remove(k) }
        fun ds(k: String, v: String, bv: String) { if (v != bv) e.putString(k, v) else e.remove(k) }
        fun dl(k: String, v: Long, bv: Long) { if (v != bv) e.putLong(k, v) else e.remove(k) }
        di("_FrameskipType", s.frameskipType, b.frameskipType)
        di("_FrameskipValue", s.frameskipValue, b.frameskipValue)
        db("_FrameskipSafe", s.frameskipSafe, b.frameskipSafe)
        di("_FfwdSpeed", s.ffwdSpeed, b.ffwdSpeed)
        di("_InternalRes", s.internalRes, b.internalRes)
        db("_Hires3D", s.hires3D, b.hires3D)
        db("_DynamicRes", s.dynamicRes, b.dynamicRes)
        db("_ShowScale", s.showScale, b.showScale)
        db("_GlUse16Bit", s.gl16bit, b.gl16bit)
        db("_DisableEdgeMarking", s.disableEdgeMarking, b.disableEdgeMarking)
        db("_FixMainEngineScreen", s.fixMainScreen, b.fixMainScreen)
        db("_Threaded3D", s.threaded3D, b.threaded3D)
        db("_Blend", s.blend, b.blend)
        db("_SoundEnabled", s.soundEnabled, b.soundEnabled)
        di("_AudioLatency", s.audioLatency, b.audioLatency)
        di("_Volume", s.volume, b.volume)
        db("_MicEnabled", s.micEnabled, b.micEnabled)
        di("_MicLevel", s.micLevel, b.micLevel)
        ds("_FirmwareNick", s.firmwareNick, b.firmwareNick)
        di("_FirmwareLanguage", s.firmwareLanguage, b.firmwareLanguage)
        di("_FirmwareColor", s.firmwareColor, b.firmwareColor)
        di("_FirmwareBdayMonth", s.bdayMonth, b.bdayMonth)
        di("_FirmwareBdayDay", s.bdayDay, b.bdayDay)
        di("_Slot2Type", s.slot2Type, b.slot2Type)
        db("_RtcSystemTime", s.rtcSystemTime, b.rtcSystemTime)
        db("_ShowFPS", s.showFps, b.showFps)
        di("_AutosaveMode", s.autosaveMode, b.autosaveMode)
        di("_AutoFireSpeed", s.autofireSpeed, b.autofireSpeed)
        db("_CheatsEnabled", s.cheatsEnabled, b.cheatsEnabled)
        db("_LuaEnabled", s.luaEnabled, b.luaEnabled)
        db("_RawSavFormat", s.rawSavFormat, b.rawSavFormat)
        db("_BackupInSavestates", s.backupInSavestates, b.backupInSavestates)
        db("_IgnoreGamecardLimit", s.ignoreGamecardLimit, b.ignoreGamecardLimit)
        db("_AutoTrim", s.autoTrim, b.autoTrim)
        db("_PreloadRoms", s.preloadRoms, b.preloadRoms)
        ds("_CurrentFx", s.filter, b.filter)
        df("_ControllerAlpha", s.controllerAlpha, b.controllerAlpha)
        db("_HapticFeedback", s.hapticFeedback, b.hapticFeedback)
        db("_ShowMore", s.showStartSelect, b.showStartSelect)
        db("_HideControlsWithGamepad", s.hideControlsWithGamepad, b.hideControlsWithGamepad)
        db("_ScreenSwap", s.swapScreens, b.swapScreens)
        db("_SingleScreen", s.singleScreen, b.singleScreen)
        db("_SingleScreenBottom", s.singleBottom, b.singleBottom)
        di("_VirtualPadStyle", s.padStyle, b.padStyle)
        di("_DefaultLayout", s.screenLayout, b.screenLayout)
        di("_ScreenLayout2", s.screenLayout2, b.screenLayout2)
        di("_RotationMode", s.rotationMode, b.rotationMode)
        di("_ExtraFuncs_0", s.extraFunc1, b.extraFunc1)
        di("_ExtraFuncs_1", s.extraFunc2, b.extraFunc2)
        di("_ExtraFuncs_2", s.extraFunc3, b.extraFunc3)
        db("_TouchThrough", s.touchThrough, b.touchThrough)
        db("_NoDiagonals", s.noDiagonals, b.noDiagonals)
        db("_AppLogFile", s.appLogFile, b.appLogFile)
        di("_MenuButtonPos", s.menuButtonPos, b.menuButtonPos)
        df("_DpadModifier", s.dpadModifier, b.dpadModifier)
        df("_ButtonModifier", s.buttonModifier, b.buttonModifier)
        db("_FastForwardToggle", s.fastForwardToggle, b.fastForwardToggle)
        db("_ShowFfwdIndicator", s.showFfwdIndicator, b.showFfwdIndicator)
        di("_RumbleDev", s.rumbleDev, b.rumbleDev)
        di("_AnalogStickMode", s.analogStickMode, b.analogStickMode)
        db("_AnalogTriggers", s.analogTriggers, b.analogTriggers)
        db("_AnalogTouch", s.analogTouch, b.analogTouch)
        df("_AnalogDeadzone", s.analogDeadzone, b.analogDeadzone)
        db("_DisableMapped", s.disableMapped, b.disableMapped)
        db("_DisableBackButton", s.disableBackButton, b.disableBackButton)
        db("_LoadNeedsConfirm", s.loadNeedsConfirm, b.loadNeedsConfirm)
        db("_OverwriteNeedsConfirm", s.overwriteNeedsConfirm, b.overwriteNeedsConfirm)
        db("_FpsTransparent", s.fpsTransparent, b.fpsTransparent)
        di("_ExtDisplayType", s.extDisplayType, b.extDisplayType)
        di("_ExtDisplayScreen", s.extDisplayScreen, b.extDisplayScreen)
        db("_IntegerScale", s.integerScale, b.integerScale)
        di("_DualScreenPreset", s.dualScreenPreset, b.dualScreenPreset)
        db("_DsKeepRatio", s.dsKeepRatio, b.dsKeepRatio)
        db("_DsIntFillW", s.dsIntFillW, b.dsIntFillW)
        db("_DsIntFillH", s.dsIntFillH, b.dsIntFillH)
        db("_DsExtFillW", s.dsExtFillW, b.dsExtFillW)
        db("_DsExtFillH", s.dsExtFillH, b.dsExtFillH)
        di("_DsIntAlign", s.dsIntAlign, b.dsIntAlign)
        di("_DsExtAlign", s.dsExtAlign, b.dsExtAlign)
        db("_DsSameSize", s.dsSameSize, b.dsSameSize)
        di("_SaveLocation", s.saveLocation, b.saveLocation)
        ds("_SaveFolder", s.saveFolder, b.saveFolder)
        di("_ExtDisplayBorder", s.extDisplayBorder, b.extDisplayBorder)
        di("_ExtDisplayDelay", s.extDisplayDelay, b.extDisplayDelay)
        di("_CpuLoad", s.cpuLoad, b.cpuLoad)
        db("_GlThreadSleep", s.glThreadSleep, b.glThreadSleep)
        db("_UseLowResTextures", s.lowResTextures, b.lowResTextures)
        db("_ZipCaching", s.zipCaching, b.zipCaching)
        db("_CustomClockEnable", s.customClockEnable, b.customClockEnable)
        dl("_CustomClock", s.customClock, b.customClock)
        db("_ShortcutAutoResume", s.autoload, b.autoload)
        db("_SmartEdit", s.smartEdit, b.smartEdit)
    }

    fun runtimeUpdate(s: DsSettings) { current = s }

    val irScales = intArrayOf(1, 2, 4, 8, 3, 5, 6, 7)




    var coreScale: Int = 0
        private set

    fun scaleRequested(): Int =
        if (!current.hires3D) 1 else irScales.getOrElse(current.internalRes) { 2 }

    fun markCoreScale() { coreScale = scaleRequested(); writeDynamicRes(current) }

    fun writeDynamicRes(s: DsSettings) {
        if (!com.seedlessds.app.Recon.available) return
        try { SeedlessCore.setDynamicResolution(s.dynamicRes && s.hires3D, 2) } catch (e: UnsatisfiedLinkError) { }
    }

    fun scaleNeedsRestart(): Boolean =
        coreScale != 0 && coreScale != scaleRequested()

    const val nativeDeliveryDefault = true

    fun writeScale(ctx: Context, s: DsSettings) {
        val n = irScales.getOrElse(s.internalRes) { 2 }
        com.seedlessds.app.Recon.setScaleSafe(n, com.seedlessds.app.control.RuntimeControl.nativeDelivery)
        writeDynamicRes(s)
    }

    fun saveGlobal(ctx: Context, s: DsSettings, activeHasPerGame: Boolean) {
        val e = configPrefs(ctx).edit(); writeTo(e, s); e.apply()
        global = s
        if (!activeHasPerGame) current = s
        writeScale(ctx, current)
    }

    fun save(ctx: Context, s: DsSettings) = saveGlobal(ctx, s, false)

    fun flush(ctx: Context) { configPrefs(ctx).edit().commit() }

    fun applyToCore() {
        SeedlessCore.applyConfig(current.pack())
        SeedlessCore.audioVolume(current.volume * 10)
        SeedlessCore.firmwareUser(current.firmwareNick, current.firmwarePacked())
        SeedlessCore.autosaveInterval(current.autosaveInterval())
    }
}
