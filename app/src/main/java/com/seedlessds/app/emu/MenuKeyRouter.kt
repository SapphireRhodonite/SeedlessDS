package com.seedlessds.app.emu

import android.view.KeyEvent

object MenuKeyRouter {

    @Volatile
    var handler: ((keyCode: Int) -> Boolean)? = null

    fun dispatch(keyCode: Int): Boolean = handler?.invoke(keyCode) == true

    fun isUp(k: Int) = k == KeyEvent.KEYCODE_DPAD_UP
    fun isDown(k: Int) = k == KeyEvent.KEYCODE_DPAD_DOWN
    fun isLeft(k: Int) = k == KeyEvent.KEYCODE_DPAD_LEFT
    fun isRight(k: Int) = k == KeyEvent.KEYCODE_DPAD_RIGHT

    fun isAccept(k: Int) = k == KeyEvent.KEYCODE_BUTTON_A ||
        k == KeyEvent.KEYCODE_DPAD_CENTER ||
        k == KeyEvent.KEYCODE_ENTER ||
        k == KeyEvent.KEYCODE_NUMPAD_ENTER

    fun isCancel(k: Int) = k == KeyEvent.KEYCODE_BUTTON_B || k == KeyEvent.KEYCODE_BACK

    fun isPrevTab(k: Int) = k == KeyEvent.KEYCODE_BUTTON_L1 || k == KeyEvent.KEYCODE_PAGE_UP
    fun isNextTab(k: Int) = k == KeyEvent.KEYCODE_BUTTON_R1 || k == KeyEvent.KEYCODE_PAGE_DOWN

    private var lastX = 0
    private var lastY = 0

    fun dispatchAxes(x: Float, y: Float): Boolean {
        val dz = 0.5f
        val nx = if (x < -dz) -1 else if (x > dz) 1 else 0
        val ny = if (y < -dz) -1 else if (y > dz) 1 else 0
        var handled = false
        if (nx != lastX) {
            lastX = nx
            when (nx) {
                -1 -> handled = dispatch(KeyEvent.KEYCODE_DPAD_LEFT)
                1 -> handled = dispatch(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
        }
        if (ny != lastY) {
            lastY = ny
            when (ny) {
                -1 -> handled = dispatch(KeyEvent.KEYCODE_DPAD_UP) || handled
                1 -> handled = dispatch(KeyEvent.KEYCODE_DPAD_DOWN) || handled
            }
        }
        return handled
    }

    fun resetAxes() {
        lastX = 0
        lastY = 0
    }
}
