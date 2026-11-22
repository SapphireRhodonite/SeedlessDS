package com.seedlessds.app.emu

import org.json.JSONObject

internal class BoxArtCatalog private constructor(
    val revision: String,
    private val bySha1: Map<String, Cover>,
    private val byCode: Map<String, Cover>,
    private val byCodeAndRevision: Map<String, Cover>,
    private val coveredCodes: Set<String>,
) {
    data class Cover(val gameCode: String, val variant: String, val url: String, val blobSha: String)

    val size: Int get() = bySha1.size

    fun fromHeader(id: CartridgeId): Cover? =
        byCodeAndRevision["${id.gameCode}:${id.revision}"] ?: byCode[id.gameCode]

    fun hasCode(id: CartridgeId): Boolean = id.gameCode in coveredCodes

    fun fromSha1(id: CartridgeId, sha1: String): Cover? =
        bySha1[sha1]?.takeIf { it.gameCode.isEmpty() || it.gameCode == id.gameCode }

    companion object {
        private const val DIRECTORY = "Named_Boxarts"
        private val SHA1 = Regex("[0-9a-f]{40}")
        private val ASSET_NAME = Regex("(?:[a-zA-Z0-9._~-]|%[0-9A-F]{2})+\\.png")

        fun parse(json: JSONObject, repository: String): BoxArtCatalog {
            val source = json.getJSONObject("source")
            require(source.getString("repository") == repository)
            require(source.getString("directory") == DIRECTORY)
            val revision = source.getString("revision")
            require(SHA1.matches(revision))
            val urlPrefix = "https://raw.githubusercontent.com/$repository/$revision/$DIRECTORY/"
            val entries = json.getJSONArray("entries")
            require(entries.length() > 0)
            val bySha1 = HashMap<String, Cover>(entries.length())
            val covers = List(entries.length()) { index ->
                val entry = entries.getJSONObject(index)
                val sha1 = entry.getString("rom_sha1")
                require(SHA1.matches(sha1))
                val blobSha = entry.getString("image_git_blob_sha")
                require(SHA1.matches(blobSha))
                val url = entry.getString("url")
                require(url.startsWith(urlPrefix) && ASSET_NAME.matches(url.substring(urlPrefix.length)))
                val cover = Cover(entry.optString("game_code"), entry.getString("variant"), url, blobSha)
                require(bySha1.put(sha1, cover) == null)
                cover
            }
            val byCode = lookup(json.getJSONObject("code_fallbacks"), covers) { it }
            val byCodeAndRevision = lookup(json.getJSONObject("code_revision_fallbacks"), covers) { it.substringBefore(':') }
            val coveredCodes = covers.mapTo(HashSet()) { it.gameCode }
            return BoxArtCatalog(revision, bySha1, byCode, byCodeAndRevision, coveredCodes)
        }

        private fun lookup(data: JSONObject, covers: List<Cover>, code: (String) -> String): Map<String, Cover> =
            data.keys().asSequence().associateWith { key ->
                val position = data.getInt(key)
                require(position in covers.indices)
                covers[position].also { require(it.gameCode == code(key)) }
            }
    }
}

internal class BoxArtCatalogs private constructor(val nds: BoxArtCatalog, val dsiWare: BoxArtCatalog) {
    fun forUnit(unitCode: Int): BoxArtCatalog? = when (unitCode) {
        0, 2 -> nds
        3 -> dsiWare
        else -> null
    }

    companion object {
        const val ASSET = "ds-boxart-v2.json"

        fun parse(json: JSONObject): BoxArtCatalogs {
            require(json.getInt("schema_version") == 2)
            val platforms = json.getJSONObject("platforms")
            return BoxArtCatalogs(
                BoxArtCatalog.parse(platforms.getJSONObject("nds"), "libretro-thumbnails/Nintendo_-_Nintendo_DS"),
                BoxArtCatalog.parse(platforms.getJSONObject("dsiware"), "libretro-thumbnails/Nintendo_-_Nintendo_DSi"),
            )
        }
    }
}
