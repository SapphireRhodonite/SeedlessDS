package com.seedlessds.app

import androidx.annotation.Keep

@Keep
object SeedlessBridge {
    init { System.loadLibrary("seedless_bridge") }

    @JvmStatic external fun nativeHello(): String

    @JvmStatic external fun readMemory(dsAddress: Long, out: ByteArray, len: Int): Int

    @JvmStatic external fun resetMemory()

    @JvmStatic external fun dumpFramebuffer(): Int


    @JvmStatic external fun presentMode(mode: Int): Boolean

    @JvmStatic external fun hwPages(on: Boolean): Boolean
    @JvmStatic external fun pagesActive(): Boolean
    @JvmStatic external fun niceEmu(nice: Int): Boolean
    @JvmStatic external fun texturePages(): Int
    @JvmStatic external fun pageList(): Int
    @JvmStatic external fun pageBuffer(): java.nio.ByteBuffer?
    @JvmStatic external fun presentInstant(tx: android.view.SurfaceControl.Transaction, ns: Long): Boolean
    @JvmStatic external fun fboDest(fbo: Int)
}
