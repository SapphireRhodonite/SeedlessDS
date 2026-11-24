package com.seedlessds.app.emu

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import java.io.File

internal object FrontendLaunch {
    private const val STORAGE_PROVIDER = "com.android.externalstorage.documents"
    private val gameExtensions = setOf("nds", "srl", "dsi", "ids", "zip", "7z", "rar")

    sealed interface Result {
        data class Game(val uri: Uri, val name: String, val parentId: String) : Result
        data object Unverified : Result
        data object NameNotFound : Result
        data object AmbiguousName : Result
    }

    fun resolve(ctx: Context, intent: Intent): Result {
        val resolver = ctx.contentResolver
        val permitted = resolver.persistedUriPermissions.filter { it.isReadPermission }.mapTo(HashSet()) { it.uri }
        val roots = RomLibrary.folders(ctx).filter { it in permitted && readableDirectory(ctx, it) }
        val uri = intent.getStringExtra(EmulatorActivity.EXTRA_ROM_URI)?.let(Uri::parse) ?: intent.data
        val path = intent.getStringExtra(EmulatorActivity.EXTRA_ROM_PATH)
            ?: uri?.takeIf { it.scheme == "file" }?.path

        if (path != null) {
            val file = File(path)
            if (file.isAbsolute) {
                return storageDocumentId(ctx, file)?.let { fromStorageDocument(ctx, it, roots) }
                    ?: Result.Unverified
            }
            if (path.isEmpty() || file.name != path || '\\' in path || path == "." || path == "..") {
                return Result.Unverified
            }
            val matches = RomLibrary.scan(ctx, roots).filter { it.name == path }
                .distinctBy { it.uri.authority to documentId(it.uri) }
                .mapNotNull { entry -> fromLibraryEntry(ctx, entry)?.takeIf { it.name == path } }
            return when (matches.size) {
                0 -> Result.NameNotFound
                1 -> matches.single()
                else -> Result.AmbiguousName
            }
        }

        if (uri?.scheme != "content") return Result.Unverified
        val id = documentId(uri)
        if (uri.authority == STORAGE_PROVIDER && id != null) {
            return fromStorageDocument(ctx, id, roots) ?: Result.Unverified
        }
        if (id != null && roots.any { it.authority == uri.authority }) {
            RomLibrary.scan(ctx, roots.filter { it.authority == uri.authority }).firstOrNull {
                documentId(it.uri) == id
            }?.let { fromLibraryEntry(ctx, it) }?.let { return it }
        }
        val descriptorPath = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { Os.readlink("/proc/self/fd/${it.fd}") }
        }.getOrNull()?.takeIf { it.startsWith("/") && !it.endsWith(" (deleted)") }
        return descriptorPath?.let { storageDocumentId(ctx, File(it)) }
            ?.let { fromStorageDocument(ctx, it, roots) } ?: Result.Unverified
    }

    private fun readableDirectory(ctx: Context, tree: Uri): Boolean = runCatching {
        val dir = DocumentFile.fromTreeUri(ctx, tree)
        dir?.isDirectory == true && dir.canRead()
    }.getOrDefault(false)

    private fun storageDocumentId(ctx: Context, file: File): String? = runCatching {
        val full = file.canonicalPath
        val primary = Environment.getExternalStorageDirectory().canonicalPath
        if (full.startsWith("$primary/")) return@runCatching "primary:${full.removePrefix("$primary/")}"
        if (Build.VERSION.SDK_INT < 24) return@runCatching null
        val storage = ctx.getSystemService(StorageManager::class.java)
        storage?.storageVolumes?.firstNotNullOfOrNull { volume ->
            val uuid = volume.uuid ?: return@firstNotNullOfOrNull null
            val location = if (Build.VERSION.SDK_INT >= 30) volume.directory else File("/storage", uuid)
            val root = location?.canonicalPath ?: return@firstNotNullOfOrNull null
            full.takeIf { it.startsWith("$root/") }?.let { "$uuid:${it.removePrefix("$root/")}" }
        }
    }.getOrNull()

    private fun fromStorageDocument(ctx: Context, id: String, roots: List<Uri>): Result.Game? {
        val volume = id.substringBefore(':', "")
        val namePath = id.substringAfter(':', "")
        if (volume.isEmpty() || '\\' in namePath || namePath.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
            return null
        }
        val parent = "$volume:${namePath.substringBeforeLast('/', "")}"
        return roots.asSequence().filter { it.authority == STORAGE_PROVIDER }.firstNotNullOfOrNull { tree ->
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val prefix = if (rootId.endsWith(':')) rootId else "$rootId/"
            if (!id.startsWith(prefix)) return@firstNotNullOfOrNull null
            readGame(ctx, DocumentsContract.buildDocumentUriUsingTree(tree, id), parent)
        }
    }

    private fun fromLibraryEntry(ctx: Context, entry: RomLibrary.RomEntry): Result.Game? {
        val tree = entry.folder ?: return null
        return readGame(ctx, entry.uri, DocumentsContract.getTreeDocumentId(tree))
    }

    private fun documentId(uri: Uri): String? = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()

    private fun readGame(ctx: Context, uri: Uri, parentId: String): Result.Game? = runCatching {
        val doc = DocumentFile.fromSingleUri(ctx, uri) ?: return@runCatching null
        val name = doc.name?.takeIf {
            '/' !in it && '\\' !in it && !it.endsWith("-crash.zip", true) &&
                it.substringAfterLast('.', "").lowercase() in gameExtensions
        } ?: return@runCatching null
        if (!doc.isFile) return@runCatching null
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { Result.Game(uri, name, parentId) }
    }.getOrNull()
}
