package com.seedlessds.app.emu

import android.content.Context

object Profiles {
    private const val PREFS = "profiles"
    const val DEFAULT = "Default"

    fun current(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, 0).getString("_current", DEFAULT) ?: DEFAULT

    fun list(ctx: Context): List<String> {
        val set = ctx.getSharedPreferences(PREFS, 0).getStringSet("_list", null)?.toMutableSet()
            ?: mutableSetOf()
        set.add(DEFAULT)
        return set.sortedWith(compareBy({ it != DEFAULT }, { it.lowercase() }))
    }

    fun create(ctx: Context, name: String) {
        val n = name.trim().ifBlank { return }
        val p = ctx.getSharedPreferences(PREFS, 0)
        val set = (p.getStringSet("_list", null)?.toMutableSet() ?: mutableSetOf())
        set.add(n)
        p.edit().putStringSet("_list", set).putString("_current", n).apply()
    }

    fun switch(ctx: Context, name: String) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString("_current", name).apply()
    }

    fun delete(ctx: Context, name: String) {
        if (name == DEFAULT) return
        val p = ctx.getSharedPreferences(PREFS, 0)
        val set = (p.getStringSet("_list", null)?.toMutableSet() ?: mutableSetOf())
        set.remove(name)
        val cur = if (current(ctx) == name) DEFAULT else current(ctx)
        p.edit().putStringSet("_list", set).putString("_current", cur).apply()
    }

    fun suffix(ctx: Context): String {
        val c = current(ctx)
        return if (c == DEFAULT) "" else "__" + c.hashCode().toString()
    }

    fun userSub(ctx: Context): String {
        val c = current(ctx)
        return if (c == DEFAULT) "" else "p" + c.hashCode().toString()
    }
}
