package com.seedlessds.app.emu

import android.opengl.GLES20

internal class SurfaceOverlayRenderer {
    private var overlayTexture = 0
    private var overlayBitmap: android.graphics.Bitmap? = null
    private var overlayBlitter: DirectBlit? = null

    fun forget() {
        overlayTexture = 0
        overlayBitmap = null
        overlayBlitter = null
    }

    fun release() {
        if (overlayTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(overlayTexture), 0)
        overlayBlitter?.release()
        forget()
    }

    fun draw(overlay: SurfaceOverlayFrame, viewH: Int) {
        val bitmap = overlay.bitmap
        if (overlayBitmap !== bitmap) {
            val previous = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, previous, 0)
            val unit = previous[0]
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, previous, 0)
            if (overlayTexture == 0) {
                val texture = IntArray(1)
                GLES20.glGenTextures(1, texture, 0)
                overlayTexture = texture[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexture)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            } else GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexture)
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previous[0])
            GLES20.glActiveTexture(unit)
            overlayBitmap = bitmap
        }
        val blitter = overlayBlitter ?: DirectBlit(premultipliedAlpha = true).also { overlayBlitter = it }
        blitter.draw(overlayTexture, floatArrayOf(overlay.left.toFloat(), overlay.top.toFloat(),
            bitmap.width.toFloat(), bitmap.height.toFloat()), viewH)
    }
}
