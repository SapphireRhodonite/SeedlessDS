package com.seedlessds.app.emu

import com.seedlessds.app.R
import android.content.Context
import android.os.Build
import android.view.KeyEvent

object KeyMap {
    private const val PREFS = "keymap"
    private const val SCHEMA_VER = 2

    data class Btn(val index: Int, @androidx.annotation.StringRes val label: Int)

    val buttons = listOf(
        Btn(4, R.string.button_a), Btn(5, R.string.button_b), Btn(6, R.string.button_x), Btn(7, R.string.button_y),
        Btn(8, R.string.button_l), Btn(9, R.string.button_r), Btn(10, R.string.common_start_2), Btn(11, R.string.common_select_2),
        Btn(0, R.string.keymap_up), Btn(1, R.string.keymap_down), Btn(2, R.string.common_left), Btn(3, R.string.common_right)
    )

    data class Special(val fn: Int, @androidx.annotation.StringRes val label: Int)

    val specials = listOf(
        Special(1, R.string.common_fast_forward),
        Special(2, R.string.common_quick_save),
        Special(3, R.string.common_quick_load),
        Special(4, R.string.common_swap_screens),
        Special(5, R.string.common_swap_layouts_1_2),
        Special(6, R.string.keymap_show_hide_buttons),
        Special(7, R.string.common_microphone_noise),
        Special(8, R.string.common_cycle_layouts),
        Special(9, R.string.common_turbo_rapid_fire),
    )

    private val defaults = mapOf(
        4 to KeyEvent.KEYCODE_BUTTON_A, 5 to KeyEvent.KEYCODE_BUTTON_B,
        6 to KeyEvent.KEYCODE_BUTTON_X, 7 to KeyEvent.KEYCODE_BUTTON_Y,
        8 to KeyEvent.KEYCODE_BUTTON_L1, 9 to KeyEvent.KEYCODE_BUTTON_R1,
        10 to KeyEvent.KEYCODE_BUTTON_START, 11 to KeyEvent.KEYCODE_BUTTON_SELECT,
        0 to KeyEvent.KEYCODE_DPAD_UP, 1 to KeyEvent.KEYCODE_DPAD_DOWN,
        2 to KeyEvent.KEYCODE_DPAD_LEFT, 3 to KeyEvent.KEYCODE_DPAD_RIGHT
    )

    @Volatile private var keyToDs: Map<Int, Int> = emptyMap()
    @Volatile private var keyToSpecial: Map<Int, Int> = emptyMap()

    fun activeProfile(ctx: Context): Int = ctx.getSharedPreferences(PREFS, 0).getInt("_KeyMapId", 0).coerceIn(0, 2)
    fun setActiveProfile(ctx: Context, prof: Int) {
        ctx.getSharedPreferences(PREFS, 0).edit().putInt("_KeyMapId", prof.coerceIn(0, 2)).apply(); load(ctx)
    }
    private fun key(prof: Int, dsIndex: Int) = if (prof == 0) "k_$dsIndex" else "p${prof}_k_$dsIndex"
    private fun sKey(prof: Int, fn: Int) = if (prof == 0) "s_$fn" else "p${prof}_s_$fn"

    fun deviceName(ctx: Context, prof: Int): String =
        ctx.getSharedPreferences(PREFS, 0).getString("p${prof}_device", "") ?: ""
    fun setDeviceName(ctx: Context, prof: Int, name: String) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString("p${prof}_device", name).apply()
    }

    private fun migrate(p: android.content.SharedPreferences) {
        if (p.getInt("_KeyMapVer", 0) >= SCHEMA_VER) return
        val e = p.edit()
        for (prof in 0..2) for (idx in 0..11) e.remove(key(prof, idx))
        e.putInt("_KeyMapVer", SCHEMA_VER).apply()
    }

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, 0)
        migrate(p)
        val prof = activeProfile(ctx)
        val map = HashMap<Int, Int>()
        for (b in buttons) {
            val kc = p.getInt(key(prof, b.index), defaults[b.index] ?: -1)
            if (kc >= 0 && !map.containsKey(kc)) map[kc] = b.index
        }
        keyToDs = map
        val sMap = HashMap<Int, Int>()
        for (sp in specials) {
            val kc = p.getInt(sKey(prof, sp.fn), -1)
            if (kc >= 0 && !map.containsKey(kc) && !sMap.containsKey(kc)) sMap[kc] = sp.fn
        }
        keyToSpecial = sMap
    }

    fun specialForKey(keyCode: Int): Int = keyToSpecial[keyCode] ?: 0

    fun specialKeyCodeOf(ctx: Context, fn: Int): Int =
        ctx.getSharedPreferences(PREFS, 0).getInt(sKey(activeProfile(ctx), fn), -1)

    fun setSpecialMapping(ctx: Context, fn: Int, keyCode: Int) {
        val p = ctx.getSharedPreferences(PREFS, 0)
        val prof = activeProfile(ctx)
        val e = p.edit()
        for (sp in specials) if (sp.fn != fn && p.getInt(sKey(prof, sp.fn), -1) == keyCode) e.remove(sKey(prof, sp.fn))
        for (b in buttons) if (p.getInt(key(prof, b.index), defaults[b.index] ?: -1) == keyCode) e.remove(key(prof, b.index))
        e.putInt(sKey(prof, fn), keyCode).apply()
        load(ctx)
    }

    fun clearSpecialMapping(ctx: Context, fn: Int) {
        ctx.getSharedPreferences(PREFS, 0).edit().remove(sKey(activeProfile(ctx), fn)).apply()
        load(ctx)
    }

    fun keyCodeOf(ctx: Context, dsIndex: Int): Int =
        ctx.getSharedPreferences(PREFS, 0).getInt(key(activeProfile(ctx), dsIndex), defaults[dsIndex] ?: -1)

    fun setMapping(ctx: Context, dsIndex: Int, keyCode: Int) {
        val p = ctx.getSharedPreferences(PREFS, 0)
        val prof = activeProfile(ctx)
        val e = p.edit()
        for (b in buttons) if (b.index != dsIndex &&
            p.getInt(key(prof, b.index), defaults[b.index] ?: -1) == keyCode) e.remove(key(prof, b.index))
        e.putInt(key(prof, dsIndex), keyCode).apply()
        load(ctx)
    }

    fun clearMapping(ctx: Context, dsIndex: Int) {
        ctx.getSharedPreferences(PREFS, 0).edit().putInt(key(activeProfile(ctx), dsIndex), -1).apply()
        load(ctx)
    }

    fun bondedDeviceNames(ctx: Context): List<String> = runCatching {
        val adapter = if (Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
        else @Suppress("DEPRECATION") android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        @Suppress("MissingPermission") adapter?.bondedDevices?.mapNotNull { it.name } ?: emptyList()
    }.getOrDefault(emptyList())

    fun dsForKey(keyCode: Int): Int = keyToDs[keyCode] ?: -1

    fun activeDeviceVibrator(): android.os.Vibrator? {
        for (id in android.view.InputDevice.getDeviceIds()) {
            val dev = android.view.InputDevice.getDevice(id) ?: continue
            val s = dev.sources
            val isPad = s and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD ||
                s and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK
            if (isPad) { val v = dev.vibrator; if (v != null && v.hasVibrator()) return v }
        }
        return null
    }

    fun hasGamepad(): Boolean {
        for (id in android.view.InputDevice.getDeviceIds()) {
            val dev = android.view.InputDevice.getDevice(id) ?: continue
            val s = dev.sources
            val pad = s and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD ||
                      s and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK
            if (pad) return true
        }
        return false
    }

    fun keyName(uiContext: android.content.Context, keyCode: Int): String = when {
        keyCode < 0 -> uiContext.getString(R.string.keymap_unassigned)
        else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }
}

object KeyCapture {
    @Volatile var onKey: ((Int) -> Unit)? = null
}
