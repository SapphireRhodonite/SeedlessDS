package com.seedlessds.core

import androidx.annotation.Keep

@Keep
object SeedlessCore {

    @JvmField var startupError = false

    init {
        startupError = try {
            System.loadLibrary("recon_fn")
            System.loadLibrary("recon")
            false
        } catch (e: UnsatisfiedLinkError) {
            true
        }
    }

    @JvmStatic external fun attachSurface(surface: Any, versionCode: Int, apiLevel: Int)
    @JvmStatic external fun loadRom(path: String, stateSlot: Int, configBits: Long,
                                    benchmarkFrames: Int, inCacheDir: Boolean,
                                    runLimitUs: Long): Boolean
    @JvmStatic external fun insertGba(path: String, stateSlot: Int, inCacheDir: Boolean,
                                      runLimitUs: Long): Boolean
    @JvmStatic external fun reset()
    @JvmStatic external fun pause(mode: Int)
    @JvmStatic external fun quit()
    @JvmStatic external fun release()

    @JvmStatic external fun romIsNds(path: String): Boolean
    @JvmStatic external fun romType(path: String): Int
    @JvmStatic external fun romSize(path: String): Long
    @JvmStatic external fun romIcon(path: String, palette: IntArray, pixels: ByteArray,
                                    title: ByteArray): Boolean

    @JvmStatic external fun input(packedPad: Int, packedTouch: Int, mask: Int)
    @JvmStatic external fun uploadScreens(first: Int, second: Int, skipUpload: Boolean)
    @JvmStatic external fun drawScreen(texture: Int, index: Int)
    @JvmStatic external fun drawScreenExt(texture: Int, index: Int)
    @JvmStatic external fun frameWord(): Int
    @JvmStatic external fun hudWord(): Int
    @JvmStatic external fun screensRgba(top: IntArray, bottom: IntArray)
    @JvmStatic external fun screenshot(out: IntArray)
    @JvmStatic external fun clearScreens(textureTop: Int, textureBottom: Int)
    @JvmStatic external fun signalScreen()
    @JvmStatic external fun waitScreen()

    @JvmStatic external fun snapshotSlot(slot: Int, top: IntArray, bottom: IntArray)
    @JvmStatic external fun snapshotFile(path: String, top: IntArray, bottom: IntArray)
    @JvmStatic external fun snapshotTopEmboss(path: String, out: IntArray)

    @JvmStatic external fun stateSave(slot: Int, withScreens: Boolean): Boolean
    @JvmStatic external fun stateLoad(slot: Int): Boolean
    @JvmStatic external fun stateSaving(): Boolean
    @JvmStatic external fun stateSlot(): Int
    @JvmStatic external fun autosaveInterval(seconds: Int)

    @JvmStatic external fun accelerometer(x: Float, y: Float, z: Float)
    @JvmStatic external fun gyroscope(rotation: Float)
    @JvmStatic external fun rumble(): Boolean
    @JvmStatic external fun whitenoise(on: Boolean)
    @JvmStatic external fun hinge(closed: Boolean)

    @JvmStatic external fun scriptActive(): Boolean
    @JvmStatic external fun scriptOverrides(): Int
    @JvmStatic external fun scriptAxes(leftX: Float, leftY: Float, rightX: Float,
                                       rightY: Float)
    @JvmStatic external fun scriptRotation(rotation: Int)

    @JvmStatic external fun cheatAddCustom(name: String, codes: IntArray, words: Int,
                                           enabled: Boolean): Int
    @JvmStatic external fun cheatFindCustom(codes: IntArray, words: Int): Int
    @JvmStatic external fun cheatRemoveCustom(index: Int)
    @JvmStatic external fun cheatCustomCount(): Int
    @JvmStatic external fun cheatCustomData(index: Int): IntArray
    @JvmStatic external fun cheatCustomName(index: Int): ByteArray
    @JvmStatic external fun cheatCustomEnabled(index: Int): Boolean
    @JvmStatic external fun cheatSetCustomEnabled(index: Int, enabled: Boolean)
    @JvmStatic external fun cheatUpdate(enabled: Boolean)
    @JvmStatic external fun cheatCount(): Int
    @JvmStatic external fun cheatEnabled(index: Int): Boolean
    @JvmStatic external fun cheatSetEnabled(index: Int, enabled: Boolean)
    @JvmStatic external fun cheatName(index: Int): ByteArray
    @JvmStatic external fun cheatNote(index: Int): ByteArray
    @JvmStatic external fun cheatFolderCount(): Int
    @JvmStatic external fun cheatFolderId(index: Int): Int
    @JvmStatic external fun cheatFolderName(index: Int): ByteArray
    @JvmStatic external fun cheatFolderNote(index: Int): ByteArray
    @JvmStatic external fun cheatFolderExpanded(index: Int): Boolean
    @JvmStatic external fun cheatSetFolderExpanded(index: Int, expanded: Boolean)
    @JvmStatic external fun cheatFolderMultiSelect(index: Int): Boolean

    @JvmStatic external fun applyConfig(bits: Long)
    @JvmStatic external fun audioVolume(volume: Int)
    @JvmStatic external fun firmwareUser(nickname: String, packed: Int)

    @JvmStatic external fun fxLoad(recipePath: String, vboVertexOffset: Int,
                                   vboTexcoordOffset: Int): Int
    @JvmStatic external fun fxSetup(sourceWidth: Int, sourceHeight: Int, x: Int, y: Int,
                                    viewWidth: Int, viewHeight: Int)
    @JvmStatic external fun fxRender(textureTop: Int, textureBottom: Int, vertexTop: Int,
                                     vertexBottom: Int, vertexIntermediate: Int,
                                     topWidth: Int, topHeight: Int, bottomWidth: Int,
                                     bottomHeight: Int, flag: Boolean)
    @JvmStatic external fun extfxLoad(recipePath: String, vboVertexOffset: Int,
                                      vboTexcoordOffset: Int): Int
    @JvmStatic external fun extfxSetup(sourceWidth: Int, sourceHeight: Int, x: Int, y: Int,
                                       viewWidth: Int, viewHeight: Int)
    @JvmStatic external fun extfxRender(texture: Int, index: Int, a: Int, b: Int, c: Int,
                                        d: Int)

    @JvmStatic external fun infoString(): String
    @JvmStatic external fun versionString(kind: Int): String

    @JvmStatic external fun logFd(fd: Int)
    @JvmStatic external fun crashLogFd(fd: Int)
    @JvmStatic external fun presentFlips(): Int
    @JvmStatic external fun setInternalResolution(scale: Int, native: Boolean)
    @JvmStatic external fun pageScale(): Int
    @JvmStatic external fun setDynamicResolution(enabled: Boolean, floor: Int)
    @JvmStatic external fun logRomEvent(event: String, name: String)
}
