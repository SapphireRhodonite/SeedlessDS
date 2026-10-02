package com.seedlessds.app.emu

object ExtraKeyRouter {
    @Volatile var handler: ((fn: Int, pressed: Boolean) -> Unit)? = null

    fun dispatch(fn: Int, pressed: Boolean): Boolean {
        val h = handler ?: return false
        h(fn, pressed)
        return true
    }
}
