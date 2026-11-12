package com.seedlessds.app.emu

internal class StylusInput(private val send: (Boolean, Int) -> Unit) {
    private var pointerId: Long? = null
    private val pendingPointers = HashSet<Long>()

    fun down(id: Long, x: Float, y: Float, rect: FloatArray): Boolean {
        if (pointerId != null) return false
        if (!contains(x, y, rect)) {
            pendingPointers.add(id)
            return false
        }
        pointerId = id
        pendingPointers.clear()
        move(id, x, y, rect)
        return true
    }

    fun move(id: Long, x: Float, y: Float, rect: FloatArray): Boolean {
        if (!contains(x, y, rect)) return false
        val acquired = pointerId == null && id in pendingPointers
        if (acquired) {
            pointerId = id
            pendingPointers.clear()
        }
        if (pointerId != id) return false
        val dx = ((x - rect[0]) / rect[2] * 255f).toInt().coerceIn(0, 255)
        val dy = ((y - rect[1]) / rect[3] * 191f).toInt().coerceIn(0, 191)
        send(true, (dx shl 16) or dy)
        return acquired
    }

    fun up(id: Long) {
        pendingPointers.remove(id)
        if (pointerId == id) cancel()
    }

    fun cancel() {
        pendingPointers.clear()
        if (pointerId == null) return
        pointerId = null
        send(false, 0)
    }

    private fun contains(x: Float, y: Float, rect: FloatArray): Boolean =
        rect[2] > 0f && rect[3] > 0f &&
            x >= rect[0] && x <= rect[0] + rect[2] &&
            y >= rect[1] && y <= rect[1] + rect[3]
}
