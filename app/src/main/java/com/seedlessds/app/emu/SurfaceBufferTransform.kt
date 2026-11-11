package com.seedlessds.app.emu

internal class SurfaceBufferTransform(private val width: Int, private val height: Int,
                                      private val hint: Int) {
    init {
        require(width > 0 && height > 0 && hint in 0..7)
    }

    val bufferWidth = if (hint and 4 != 0) height else width
    val bufferHeight = if (hint and 4 != 0) width else height
    val inverse = when (hint) { 4 -> 7; 7 -> 4; else -> hint }

    fun transformVertices(vertices: FloatArray) {
        for (i in vertices.indices step 2) {
            var x = vertices[i]
            var y = vertices[i + 1]
            if (hint and 1 != 0) x = -x
            if (hint and 2 != 0) y = -y
            if (hint and 4 != 0) {
                val previousX = x
                x = y
                y = -previousX
            }
            vertices[i] = x
            vertices[i + 1] = -y
        }
    }

    fun transformViewport(left: Int, bottom: Int, viewW: Int, viewH: Int): IntArray {
        var x = left
        var y = height - bottom - viewH
        var w = viewW
        var h = viewH
        if (hint and 1 != 0) x = width - x - w
        if (hint and 2 != 0) y = height - y - h
        if (hint and 4 != 0) {
            val previousX = x
            val previousW = w
            x = height - y - h
            y = previousX
            w = h
            h = previousW
        }
        return intArrayOf(x, y, w, h)
    }
}
