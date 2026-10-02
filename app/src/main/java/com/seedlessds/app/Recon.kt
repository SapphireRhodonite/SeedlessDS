package com.seedlessds.app

import com.seedlessds.core.SeedlessCore

import androidx.annotation.Keep

@Keep
object Recon {


    @JvmField var available = !SeedlessCore.startupError

    @JvmStatic fun setScale(n: Int, nativeScale: Boolean) = SeedlessCore.setInternalResolution(n, nativeScale)
    @JvmStatic fun setScaleSafe(n: Int, nativeScale: Boolean) {
        if (!available) return
        try { setScale(n, nativeScale) } catch (e: UnsatisfiedLinkError) { }
    }
    @JvmStatic fun rom(event: String, name: String) = SeedlessCore.logRomEvent(event, name)

}
