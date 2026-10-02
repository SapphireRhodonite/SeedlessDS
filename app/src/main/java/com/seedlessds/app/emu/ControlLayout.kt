package com.seedlessds.app.emu

import android.content.Context

object ControlLayout {
    private const val PREFS = "ctrl_layout"
    val ids = listOf("dpad", "face", "lbtn", "rbtn", "startselect", "menu", "extra")

    private val offsets = HashMap<String, Pair<Float, Float>>()
    private val scales = HashMap<String, Float>()
    private val hidden = HashSet<String>()

    @Volatile var topScale: Float = 1f
    @Volatile var topOffX: Float = 0f
    @Volatile var topOffY: Float = 0f
    @Volatile var botScale: Float = 1f
    @Volatile var botOffX: Float = 0f
    @Volatile var botOffY: Float = 0f
    @Volatile var extScale: Float = 1f
    @Volatile var backgroundUri: String = ""

    fun isHidden(id: String): Boolean = id in hidden
    fun setHidden(id: String, h: Boolean) { if (h) hidden.add(id) else hidden.remove(id) }

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, 0)
        offsets.clear(); scales.clear()
        for (id in ids) {
            offsets[id] = p.getFloat("${id}_x", 0f) to p.getFloat("${id}_y", 0f)
            scales[id] = p.getFloat("${id}_s", 1f)
        }
        val oldScale = p.getFloat("screen_scale", 1f)
        val oldOffX = p.getFloat("screen_offx", 0f)
        val oldOffY = p.getFloat("screen_offy", 0f)
        topScale = p.getFloat("top_scale", oldScale)
        topOffX = p.getFloat("top_offx", oldOffX)
        topOffY = p.getFloat("top_offy", oldOffY)
        botScale = p.getFloat("bot_scale", oldScale)
        botOffX = p.getFloat("bot_offx", oldOffX)
        botOffY = p.getFloat("bot_offy", oldOffY)
        extScale = p.getFloat("ext_scale", 1f)
        hidden.clear(); p.getStringSet("hidden", null)?.let { hidden.addAll(it) }
        backgroundUri = p.getString("background_uri", "") ?: ""
    }

    fun offsetOf(id: String): Pair<Float, Float> = offsets[id] ?: (0f to 0f)
    fun scaleOf(id: String): Float = scales[id] ?: 1f

    fun save(
        ctx: Context,
        offsetValues: Map<String, Pair<Float, Float>>,
        scaleValues: Map<String, Float>,
    ) {
        val e = ctx.getSharedPreferences(PREFS, 0).edit()
        for ((id, off) in offsetValues) { e.putFloat("${id}_x", off.first); e.putFloat("${id}_y", off.second) }
        for ((id, s) in scaleValues) { e.putFloat("${id}_s", s) }
        e.putFloat("top_scale", topScale); e.putFloat("top_offx", topOffX); e.putFloat("top_offy", topOffY)
        e.putFloat("bot_scale", botScale); e.putFloat("bot_offx", botOffX); e.putFloat("bot_offy", botOffY)
        e.putFloat("ext_scale", extScale)
        e.putStringSet("hidden", HashSet(hidden))
        e.putFloat("screen_scale", topScale); e.putFloat("screen_offx", topOffX); e.putFloat("screen_offy", topOffY)
        e.apply()
        offsets.putAll(offsetValues); scales.putAll(scaleValues)
    }

    fun setBackground(ctx: Context, uri: String) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString("background_uri", uri).apply()
        backgroundUri = uri
    }

    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREFS, 0).edit().clear().apply()
        offsets.clear(); scales.clear(); hidden.clear()
        for (id in ids) { offsets[id] = 0f to 0f; scales[id] = 1f }
        topScale = 1f; topOffX = 0f; topOffY = 0f
        botScale = 1f; botOffX = 0f; botOffY = 0f
        extScale = 1f
        backgroundUri = ""
    }
}
