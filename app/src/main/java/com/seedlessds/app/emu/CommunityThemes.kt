package com.seedlessds.app.emu

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream

data class CommunityTheme(
    val id: String,
    val name: String,
    val background: File?,
    val backgroundPortrait: File? = null,
    val backgroundLandscape: File? = null,
    val button: Color,
    val pressedButton: Color,
    val text: Color,
    val assets: Map<String, File>,
    val pressedAssets: Map<String, File>,
    val drasticPadSheet: File? = null,
    val drasticPressedPadSheet: File? = null,
    val drasticDpadSheets: List<File> = emptyList(),
)

object CommunityThemes {
    private const val THEMES_DIR = "themes"
    private const val MANIFEST = "theme.json"

    fun list(ctx: Context): List<CommunityTheme> = themeDir(ctx).listFiles()
        ?.mapNotNull { readTheme(it) }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()

    fun install(ctx: Context, uri: Uri): CommunityTheme? {
        val temp = File(ctx.cacheDir, "theme-import-${System.nanoTime()}").apply { mkdirs() }
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name.replace('\\', '/')
                        if (!entry.isDirectory && name.substringAfterLast('/').isNotBlank() &&
                            !name.startsWith("/") && name.split('/').none { it == ".." }
                        ) {
                            val out = File(temp, name)
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return null
            val manifest = File(temp, MANIFEST).takeIf { it.isFile }
                ?: createLegacyManifest(temp, uri.lastPathSegment ?: "Community theme")
                ?: return null
            val json = JSONObject(manifest.readText())
            val id = safeId(json.optString("id", json.optString("name", "theme")))
            if (id.isBlank() || json.optString("name").isBlank()) return null
            val destination = File(themeDir(ctx), id)
            destination.deleteRecursively()
            temp.copyRecursively(destination, overwrite = true)
            readTheme(destination)
        } catch (_: Exception) {
            null
        } finally {
            temp.deleteRecursively()
        }
    }

    fun image(theme: CommunityTheme, asset: String = "background", pressed: Boolean = false, dpadState: Int = 0): ImageBitmap? {
        val file = if (asset == "background") theme.background
        else if (asset == "backgroundPortrait") theme.backgroundPortrait ?: theme.backgroundLandscape ?: theme.background
        else if (asset == "backgroundLandscape") theme.backgroundLandscape ?: theme.backgroundPortrait ?: theme.background
        else if (theme.drasticPadSheet != null && asset in DRASTIC_PAD_KEYS) {
            if (pressed) theme.drasticPressedPadSheet ?: theme.drasticPadSheet else theme.drasticPadSheet
        } else if (asset == "dpad" && theme.drasticDpadSheets.isNotEmpty()) {
            theme.drasticDpadSheets[dpadState.coerceIn(0, theme.drasticDpadSheets.lastIndex)]
        } else if (pressed) theme.pressedAssets[asset] ?: theme.assets[asset] else theme.assets[asset]
        return file?.let { f ->
            runCatching {
                // For padbuttons sheets, only return the cropped region; never the full sheet.
                val cropped = if (f.name.startsWith("padbuttons_")) cropDrastic(f, asset) else null
                cropped?.asImageBitmap() ?: if (!f.name.startsWith("padbuttons_")) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
            }.getOrNull()
        }
    }

    fun importBackground(ctx: Context, uri: Uri): String? = runCatching {
        val dir = File(themeDir(ctx), "standalone-backgrounds").apply { mkdirs() }
        val file = File(dir, "background-${System.nanoTime()}.png")
        ctx.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
            ?: return null
        BitmapFactory.decodeFile(file.path) ?: return null
        file.path
    }.getOrNull()

    fun standaloneImage(path: String): ImageBitmap? = runCatching {
        BitmapFactory.decodeFile(path)?.asImageBitmap()
    }.getOrNull()

    fun id(theme: CommunityTheme): String = "community:${theme.id}"

    fun find(ctx: Context, storedId: String): CommunityTheme? {
        if (!storedId.startsWith("community:")) return null
        return list(ctx).firstOrNull { id(it) == storedId }
    }

    private fun readTheme(dir: File): CommunityTheme? = runCatching {
        val json = JSONObject(File(dir, MANIFEST).readText())
        val name = json.getString("name")
        val discoveredBackgrounds = findLegacyBackgrounds(dir.walkTopDown().filter { it.isFile }.toList())
        val assets = mutableMapOf<String, File>()
        val pressedAssets = mutableMapOf<String, File>()
        val assetJson = json.optJSONObject("assets")
        val pressedJson = json.optJSONObject("pressedAssets")
        listOf("a", "b", "x", "y", "l", "r", "start", "select", "dpad", "menu").forEach { key ->
            val path = assetJson?.optString(key).orEmpty()
                .ifBlank { json.optString("${key}Image").orEmpty() }
            if (path.isSafeRelativePath()) File(dir, path).takeIf { it.isFile }?.let { assets[key] = it }
            val pressedPath = pressedJson?.optString(key).orEmpty()
                .ifBlank { json.optString("${key}PressedImage").orEmpty() }
            if (pressedPath.isSafeRelativePath()) File(dir, pressedPath).takeIf { it.isFile }?.let { pressedAssets[key] = it }
        }
        CommunityTheme(
            id = dir.name,
            name = name,
            background = json.optString("background", "background.png")
                .takeIf { it.isSafeRelativePath() }?.let { File(dir, it) }?.takeIf { it.isFile }
                ?: discoveredBackgrounds.single,
            backgroundPortrait = json.optString("backgroundPortrait").takeIf { it.isSafeRelativePath() }
                ?.let { File(dir, it) }?.takeIf { it.isFile }
                ?: discoveredBackgrounds.portrait,
            backgroundLandscape = json.optString("backgroundLandscape").takeIf { it.isSafeRelativePath() }
                ?.let { File(dir, it) }?.takeIf { it.isFile }
                ?: discoveredBackgrounds.landscape,
            button = color(json.optString("buttonColor", "#222233"), Color(0xFF222233)),
            pressedButton = color(json.optString("pressedColor", "#DDDDEE"), Color(0xFFDDDDEE)),
            text = color(json.optString("textColor", "#FFFFFF"), Color.White),
            assets = assets,
            pressedAssets = pressedAssets,
            drasticPadSheet = File(dir, "padbuttons_0.png").takeIf { it.isFile },
            drasticPressedPadSheet = File(dir, "padbuttons_1.png").takeIf { it.isFile },
            drasticDpadSheets = (0..8).mapNotNull { n -> File(dir, "dpad_$n.png").takeIf { it.isFile } },
        )
    }.getOrNull()

    private fun color(value: String, fallback: Color): Color = runCatching {
        Color(android.graphics.Color.parseColor(value))
    }.getOrDefault(fallback)

    private fun safeId(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9._-]"), "-")
        .trim('-').take(48)

    private fun createLegacyManifest(dir: File, fallbackName: String): File? {
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        val backgrounds = findLegacyBackgrounds(files)
        val skinIni = files.firstOrNull { it.name.equals("skin.ini", true) }
        val iniName = skinIni?.runCatching {
            readLines().firstOrNull { line -> line.trim().startsWith("name", true) }
                ?.substringAfter('=').orEmpty().trim().takeIf { it.isNotBlank() }
        }?.getOrNull()
        val name = iniName ?: fallbackName.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Community theme" }
        val assetJson = JSONObject()
        val pressedJson = JSONObject()
        files.filter { it.extension.lowercase(Locale.ROOT) in setOf("png", "jpg", "jpeg", "webp") }.forEach { file ->
            val pressed = legacyPressedKey(file.name)
            val key = legacyAssetKey(file.name)
            val relative = file.relativeTo(dir).path.replace(File.separatorChar, '/')
            if (pressed != null) pressedJson.put(pressed, relative)
            else if (key != null) assetJson.put(key, relative)
        }
        val hasDrasticSprites = files.any { file ->
            file.name.matches(Regex("(?:padbuttons_[01]|dpad_[0-8])(?:_low)?\\.(png|jpg|jpeg|webp)", RegexOption.IGNORE_CASE))
        }
        if (backgrounds.single == null && backgrounds.portrait == null && backgrounds.landscape == null &&
            assetJson.length() == 0 && pressedJson.length() == 0 && !hasDrasticSprites
        ) return null
        val manifest = File(dir, MANIFEST)
        val json = JSONObject().put("id", name).put("name", name)
            .put("assets", assetJson).put("pressedAssets", pressedJson)
        backgrounds.single?.let { json.put("background", it.relativeTo(dir).path.replace(File.separatorChar, '/')) }
        backgrounds.portrait?.let { json.put("backgroundPortrait", it.relativeTo(dir).path.replace(File.separatorChar, '/')) }
        backgrounds.landscape?.let { json.put("backgroundLandscape", it.relativeTo(dir).path.replace(File.separatorChar, '/')) }
        manifest.writeText(json.toString())
        return manifest
    }

    private data class Backgrounds(val single: File?, val portrait: File?, val landscape: File?)

    private fun findLegacyBackgrounds(files: List<File>): Backgrounds {
        val images = files.filter { it.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS }
        fun find(vararg names: String): File? = names.asSequence()
            .flatMap { base -> images.filter { it.name.substringBeforeLast('.').equals(base, true) }.asSequence() }
            .firstOrNull()
        val portrait = find("portrait", "background_portrait", "background-portrait", "bg_portrait", "bg-portrait")
            ?: images.firstOrNull { file ->
                val base = file.name.substringBeforeLast('.').lowercase(Locale.ROOT)
                "portrait" in base && legacyAssetKey(file.name) == null && legacyPressedKey(file.name) == null
            }
        val landscape = find("landscape", "background_landscape", "background-landscape", "bg_landscape", "bg-landscape")
            ?: images.firstOrNull { file ->
                val base = file.name.substringBeforeLast('.').lowercase(Locale.ROOT)
                "landscape" in base && legacyAssetKey(file.name) == null && legacyPressedKey(file.name) == null
            }
        val single = listOf("background", "bg", "backdrop", "skin", "layout", "overlay")
            .asSequence()
            .flatMap { base -> images.filter { it.name.substringBeforeLast('.').equals(base, true) }.asSequence() }
            .firstOrNull()
            ?: images.firstOrNull { file ->
                val base = file.name.substringBeforeLast('.').lowercase(Locale.ROOT)
                ("background" in base || "backdrop" in base || base.startsWith("bg_")) &&
                    legacyAssetKey(file.name) == null && legacyPressedKey(file.name) == null
            }
            ?: images.filterNot(::isControlImage).maxByOrNull { it.length() }
                return Backgrounds(single, portrait, landscape)
    }

    private fun isControlImage(file: File): Boolean {
        val name = file.name.lowercase(Locale.ROOT)
        return legacyAssetKey(file.name) != null || legacyPressedKey(file.name) != null ||
            name.startsWith("padbuttons_") || name.startsWith("dpad_") || name.endsWith("_low.png")
    }

    private fun legacyAssetKey(fileName: String): String? {
        val normalized = legacyButtonName(fileName) ?: return null
        return normalized.second.takeUnless { normalized.first }
    }

    private fun legacyPressedKey(fileName: String): String? {
        val base = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
        val isPressed = listOf("_pressed", "_down", "_active", "_selected", "_pressed_one", "_pressed_two").any { base.endsWith(it) } ||
            listOf("pressed_", "down_", "active_").any { base.startsWith(it) }
        return if (isPressed) legacyButtonName(fileName)?.second else null
    }

    private fun legacyButtonName(fileName: String): Pair<Boolean, String>? {
        var base = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val pressed = listOf("pressed_one", "pressed_two", "pressed", "down", "active", "selected", "normal").firstOrNull { token ->
            base == token || base.startsWith("${token}_") || base.endsWith("_${token}")
        }
        if (pressed != null) base = base.removePrefix("${pressed}_").removeSuffix("_${pressed}")
        base = base.removePrefix("button").removePrefix("btn").removePrefix("key").removePrefix("touch").trim('_')
        val key = when {
            base in BUTTON_KEYS -> base
            base.startsWith("dpad") && base !in setOf("dpad_up", "dpad_down", "dpad_left", "dpad_right") -> "dpad"
            else -> return null
        }
        return (pressed != null && pressed != "normal") to key
    }

    private val BUTTON_KEYS = setOf("a", "b", "x", "y", "l", "r", "start", "select", "dpad", "menu")
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    private val DRASTIC_PAD_KEYS = setOf("a", "b", "x", "y", "l", "r", "start", "select", "i", "ii", "iii", "menu")

    private fun cropDrastic(file: File, asset: String): android.graphics.Bitmap? {
        if (!file.name.startsWith("padbuttons_")) return null
        val source = BitmapFactory.decodeFile(file.path) ?: return null
        // Extended layout: width == height*2  (e.g. 512×256 or its _low half 256×128).
        // Classic layout:  width == height*4  (e.g. 512×128).
        // Coordinates are expressed as fractions of the reference size so they
        // scale correctly for both full-res and _low variants.
        val extended = source.width == source.height * 2
        val sw = source.width.toFloat()
        val sh = source.height.toFloat()
        fun rel(fx: Float, fy: Float, fw: Float, fh: Float) = intArrayOf(
            (fx * sw).toInt(), (fy * sh).toInt(), (fw * sw).toInt(), (fh * sh).toInt()
        )
        val rect = if (extended) {
            // Reference: 512×256
            when (asset) {
                "a"      -> rel(0f/512,   0f/256,  128f/512, 128f/256)
                "b"      -> rel(128f/512, 0f/256,  128f/512, 128f/256)
                "x"      -> rel(0f/512,   128f/256,128f/512, 128f/256)
                "y"      -> rel(128f/512, 128f/256,128f/512, 128f/256)
                "l"      -> rel(278f/512, 0f/256,  150f/512,  58f/256)
                "r"      -> rel(278f/512,  64f/256, 150f/512,  58f/256)
                "start"  -> rel(288f/512, 128f/256,  64f/512,  64f/256)
                "select" -> rel(288f/512, 192f/256,  64f/512,  64f/256)
                "i"      -> rel(448f/512,   0f/256,   64f/512,  64f/256)
                "ii"     -> rel(448f/512,  64f/256,   64f/512,  64f/256)
                "iii"    -> rel(448f/512, 128f/256,   64f/512,  64f/256)
                "menu"   -> rel(384f/512, 216f/256,   64f/512,  40f/256)
                else     -> return null
            }
        } else {
            // Reference: 512×128
            when (asset) {
                "a"      -> rel(0f/512,   0f/128,  128f/512, 128f/128)
                "b"      -> rel(128f/512, 0f/128,  128f/512, 128f/128)
                "x"      -> rel(0f/512,   0f/128,  128f/512, 128f/128)
                "y"      -> rel(128f/512, 0f/128,  128f/512, 128f/128)
                "l"      -> rel(278f/512, 0f/128,  150f/512,  58f/128)
                "r"      -> rel(278f/512, 64f/128, 150f/512,  58f/128)
                "select" -> rel(448f/512,  0f/128,   64f/512,  58f/128)
                "start"  -> rel(448f/512, 64f/128,   64f/512,  58f/128)
                else     -> return null
            }
        }
        return android.graphics.Bitmap.createBitmap(source, rect[0], rect[1], rect[2], rect[3])
    }

    private fun String.isSafeRelativePath(): Boolean = isNotBlank() &&
        !startsWith('/') && split('/').none { it == ".." || it.isBlank() }

    private fun themeDir(ctx: Context): File = File(ctx.filesDir, "${EmuAssets.DATA_DIR}/$THEMES_DIR").apply { mkdirs() }
}