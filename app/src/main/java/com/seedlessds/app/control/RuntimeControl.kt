package com.seedlessds.app.control

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Xml
import com.seedlessds.app.emu.DsSettings
import com.seedlessds.app.emu.EmuAssets
import com.seedlessds.app.emu.EmulatorActivity
import com.seedlessds.app.emu.SettingsRepo
import com.seedlessds.app.filesystem.PathCache
import com.seedlessds.core.SeedlessCore
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object RuntimeControl {
    @Volatile var settings: DsSettings? = null
        private set
    @Volatile var threads3d: Int? = null
        private set
    @Volatile var nativeDelivery: Boolean = true
        private set
    @Volatile var lifecyclePaused = true
    @Volatile var running = false
        private set
    @Volatile private var activity = WeakReference<EmulatorActivity>(null)
    @Volatile private var session: String? = null
    @Volatile private var ended = false
    private var directory: File? = null
    private var captureName: String? = null
    private var audioName: String? = null
    private var configBeforeLoad = false
    private val main = Handler(Looper.getMainLooper())
    val active: Boolean get() = session != null && !ended

    fun attach(value: EmulatorActivity) {
        activity = WeakReference(value)
        running = true
    }

    fun detach(value: EmulatorActivity) {
        if (activity.get() !== value) return
        if (active) {
            NativeControl.end()
            ended = true
            settings = null
            threads3d = null
            nativeDelivery = true
            PathCache.setDataRoot(null)
            SettingsRepo.load(value)
        }
        activity.clear()
        running = false
        lifecyclePaused = true
    }

    fun beforeLoad() {
        if (active && configBeforeLoad) SeedlessCore.applyConfig(SettingsRepo.current.pack())
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) { action(); return }
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        main.post {
            try { action() } catch (error: Throwable) { failure = error }
            finally { done.countDown() }
        }
        check(done.await(5, TimeUnit.SECONDS)) { "Main thread did not acknowledge command" }
        failure?.let { throw it }
    }

    private fun externalFile(ctx: Context, relative: String): File {
        require(relative.isNotBlank() && !File(relative).isAbsolute) { "Expected a relative app-storage path" }
        val root = requireNotNull(ctx.getExternalFilesDir(null)).canonicalFile
        val target = File(root, relative).canonicalFile
        require(target.path.startsWith(root.path + File.separator)) { "Path outside app storage" }
        return target
    }

    private fun identifier(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))) { "Invalid identifier" }
        return value
    }

    private fun readSettings(ctx: Context, file: File, id: String): DsSettings {
        require(file.isFile && file.length() <= 65536) { "Settings file missing or too large" }
        val prefs = ctx.getSharedPreferences("runtime_$id", Context.MODE_PRIVATE)
        val edit = prefs.edit().clear()
        file.inputStream().use { stream ->
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name == "map") continue
                val key = requireNotNull(parser.getAttributeValue(null, "name"))
                require(key.startsWith("_") && key.length <= 100)
                val value = parser.getAttributeValue(null, "value")
                when (parser.name) {
                    "int" -> edit.putInt(key, requireNotNull(value).toInt())
                    "long" -> edit.putLong(key, requireNotNull(value).toLong())
                    "float" -> edit.putFloat(key, requireNotNull(value).toFloat())
                    "boolean" -> edit.putBoolean(key, requireNotNull(value).toBooleanStrict())
                    "string" -> edit.putString(key, parser.nextText())
                    else -> error("Unsupported settings value")
                }
            }
        }
        check(edit.commit()) { "Settings import failed" }
        return SettingsRepo.readFrom(prefs)
    }

    private fun begin(ctx: Context, args: JSONObject) {
        check(session == null && !running) { "Start a fresh process for a new session" }
        validateConfiguration(args)
        val id = identifier(args.getString("session"))
        val output = externalFile(ctx, "control/captures/$id")
        require(!output.exists()) { "Session output already exists" }
        val imported = if (args.has("settings")) readSettings(ctx, externalFile(ctx, args.getString("settings")), id)
            else SettingsRepo.current
        val root = if (args.has("data_root")) externalFile(ctx, args.getString("data_root")) else null
        if (root != null) require(root.isDirectory) { "Data root does not exist" }
        val clock = args.optLong("clock", -1)
        require(clock >= -1)
        val composeThreads = args.optInt("threads_2d", -1)
        require(composeThreads in -1..16 || composeThreads == 255)
        check(output.mkdirs()) { "Cannot create session output" }
        settings = imported
        session = id
        directory = output
        configBeforeLoad = args.optBoolean("config_before_load", false)
        nativeDelivery = args.optBoolean("native_delivery", true)
        PathCache.setDataRoot(root)
        if (root != null) EmuAssets.ensureInstalled(ctx, root)
        NativeControl.begin(clock, args.optBoolean("no_frameskip", false), composeThreads)
        configure(ctx, args)
        File(output, "session.json").writeText(JSONObject(args.toString())
            .put("package", ctx.packageName).put("protocol", 1).toString(2))
    }

    private fun configure(ctx: Context, args: JSONObject) {
        check(active)
        check(!running || NativeControl.status()[13] == 1L) { "Pause before changing configuration" }
        validateConfiguration(args)
        var value = requireNotNull(settings)
        if (args.has("scale")) {
            val scale = args.getInt("scale")
            val index = SettingsRepo.irScales.indexOf(scale)
            require(index >= 0) { "Unsupported scale" }
            check(!running || SettingsRepo.coreScale == scale) { "Scale change requires a fresh process" }
            value = value.copy(internalRes = index, hires3D = scale != 1)
        }
        if (args.has("threads_3d")) {
            check(!running) { "Thread count applies before ROM load" }
            val count = args.getInt("threads_3d")
            require(count in 0..15)
            threads3d = count.takeIf { it > 0 }
        }
        if (args.has("frameskip")) {
            val mode = args.getInt("frameskip")
            require(mode in 0..2)
            value = value.withFrameskip(mode)
        }
        if (args.has("volume")) {
            val volume = args.getInt("volume")
            require(volume in 0..10)
            value = value.copy(volume = volume)
        }
        if (args.has("sound")) value = value.copy(soundEnabled = args.getBoolean("sound"))
        settings = value
        onMain {
            SettingsRepo.runtimeUpdate(value)
            SettingsRepo.writeScale(ctx, value)
            if (running) SettingsRepo.applyToCore()
        }
    }

    private fun validateConfiguration(args: JSONObject) {
        if (args.has("scale")) require(args.getInt("scale") in SettingsRepo.irScales) { "Unsupported scale" }
        if (args.has("threads_3d")) require(args.getInt("threads_3d") in 0..15)
        if (args.has("frameskip")) require(args.getInt("frameskip") in 0..2)
        if (args.has("volume")) require(args.getInt("volume") in 0..10)
        if (args.has("sound")) args.getBoolean("sound")
    }

    private fun capture(args: JSONObject, audio: Boolean) {
        check(active)
        val id = identifier(args.getString("capture"))
        val first = args.getLong("first")
        val last = args.getLong("last")
        require(first >= 0 && last >= first)
        val file = File(requireNotNull(directory), "$id.${if (audio) "pcm" else "bin"}")
        check(file.createNewFile()) { "Capture output already exists" }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY).use { fd ->
            val accepted = if (audio) NativeControl.audio(fd.fd, first, last)
                else NativeControl.capture(fd.fd, first, last, args.optBoolean("raw", false))
            check(accepted) { "Capture was not accepted" }
        }
        if (audio) audioName = file.name else captureName = file.name
        File(requireNotNull(directory), "$id.json").writeText(JSONObject(args.toString())
            .put("session", session).put("counter", if (audio) "audio_blocks" else "output_frames")
            .put("status_at_arm", status()).toString(2))
    }

    fun status(): JSONObject {
        val result = JSONObject().put("session", session ?: JSONObject.NULL)
            .put("running", running).put("lifecycle_paused", lifecyclePaused)
            .put("capture", captureName ?: JSONObject.NULL).put("audio", audioName ?: JSONObject.NULL)
            .put("scale", SettingsRepo.coreScale).put("requested_scale", SettingsRepo.scaleRequested())
            .put("page_scale", runCatching { SeedlessCore.pageScale() }.getOrDefault(0))
            .put("threads_3d", SettingsRepo.threadCount).put("pid", android.os.Process.myPid())
            .put("dispatch_probe_available", SettingsRepo.coreScale > 1)
            .put("volume", SettingsRepo.current.volume).put("sound", SettingsRepo.current.soundEnabled)
            .put("frameskip", SettingsRepo.current.frameskipType)
        val keys = arrayOf("frames", "output_frames", "output_reads", "target", "capture_records", "capture_bytes",
            "audio_blocks", "audio_bytes", "first_3d", "first_geometry", "dispatches", "max_polygons", "active",
            "paused", "capture_complete", "audio_complete", "error", "state_request", "state_completed", "epoch",
            "state_result", "state_busy", "audio_position")
        val values = NativeControl.status()
        check(values.size == keys.size)
        keys.forEachIndexed { index, key -> result.put(key, values[index]) }
        return result
    }

    fun command(ctx: Context, command: String, args: JSONObject): JSONObject {
        if (command == "SESSION") begin(ctx, args)
        else {
            check(session != null && (command == "STATUS" || active)) { "No active control session" }
            when (command) {
                "STATUS" -> Unit
                "CONFIGURE" -> configure(ctx, args)
                "LAUNCH_ROM" -> {
                    check(!running) { "An emulation session is already running" }
                    val rom = externalFile(ctx, args.getString("rom"))
                    require(rom.isFile) { "ROM does not exist" }
                    onMain { ctx.startActivity(Intent(ctx, EmulatorActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EmulatorActivity.EXTRA_ROM_PATH, rom.absolutePath)) }
                }
                "PAUSE" -> { check(running); NativeControl.pause(args.optBoolean("paused", true)) }
                "RUN_TO_FRAME" -> {
                    check(!running || !lifecyclePaused) { "Emulator is not in foreground playback" }
                    check(NativeControl.runTo(args.getLong("frame"))) { "Target must be after the current frame" }
                }
                "STEP_FRAMES" -> {
                    check(running && !lifecyclePaused) { "Emulator is not in foreground playback" }
                    check(NativeControl.step(args.optLong("frames", 1))) { "Step requires confirmed pause and 1..10000 frames" }
                }
                "CAPTURE" -> capture(args, false)
                "CAPTURE_AUDIO" -> capture(args, true)
                "DUMP_FRAME" -> {
                    check(running && NativeControl.status()[13] == 1L) { "Pause before taking a snapshot" }
                    val id = identifier(args.getString("capture"))
                    val file = File(requireNotNull(directory), "$id.bin")
                    check(file.createNewFile()) { "Capture output already exists" }
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY).use { fd ->
                        check(NativeControl.snapshot(fd.fd, args.optBoolean("raw", false))) { "Snapshot failed" }
                    }
                    captureName = file.name
                    File(requireNotNull(directory), "$id.json").writeText(status().toString(2))
                }
                "CANCEL_CAPTURE" -> NativeControl.cancelCapture()
                "SAVE_STATE", "LOAD_STATE" -> {
                    check(running && NativeControl.status()[13] == 1L) { "Pause before using a state" }
                    check(!com.seedlessds.app.ra.RetroAchievements.isEnabled()) { "State commands require RetroAchievements disabled" }
                    val slot = args.getInt("slot")
                    require(slot in 0..9)
                    check(NativeControl.state(slot, command == "LOAD_STATE") != 0L) { "State operation is busy" }
                }
                "INPUT" -> {
                    check(running)
                    val buttons = args.optInt("buttons", 0)
                    require(buttons in 0..4095)
                    val touch = args.optBoolean("touch", false)
                    val x = args.optInt("x", 0); val y = args.optInt("y", 0)
                    require(x in 0..255 && y in 0..191)
                    onMain { SeedlessCore.input(buttons or (if (touch) Int.MIN_VALUE else 0), (x shl 16) or y, 0) }
                }
                "STOP" -> onMain { requireNotNull(activity.get()).finish() }
                else -> error("Unknown command")
            }
        }
        return status()
    }
}
