package com.seedlessds.app.control

import androidx.annotation.Keep
import com.seedlessds.core.SeedlessCore

@Keep
object NativeControl {
    init { check(!SeedlessCore.startupError) }

    @JvmStatic external fun begin(clock: Long, noSkips: Boolean, threads: Int)
    @JvmStatic external fun end()
    @JvmStatic external fun status(): LongArray
    @JvmStatic external fun pause(paused: Boolean)
    @JvmStatic external fun runTo(frame: Long): Boolean
    @JvmStatic external fun step(frames: Long): Boolean
    @JvmStatic external fun capture(fd: Int, first: Long, last: Long, raw: Boolean): Boolean
    @JvmStatic external fun audio(fd: Int, first: Long, last: Long): Boolean
    @JvmStatic external fun cancelCapture()
    @JvmStatic external fun snapshot(fd: Int, raw: Boolean): Boolean
    @JvmStatic external fun state(slot: Int, load: Boolean): Long
}
