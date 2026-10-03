package com.seedlessds.app.ra

import android.content.Context
import android.content.SharedPreferences
import java.net.URI

object RaHostOverride {
    private const val PREFS = "ra_host_override"
    private const val KEY_HOST = "client_host"

    fun validate(rawHost: String?): Result<String> = runCatching {
        val raw = rawHost?.trim().orEmpty()
        require(raw.isNotEmpty()) { "Missing host" }

        val uri = URI(raw)
        require(uri.scheme == "http") { "Only HTTP loopback is supported" }
        require(uri.rawUserInfo == null) { "User info is not allowed" }
        require(uri.rawQuery == null) { "Query is not allowed" }
        require(uri.rawFragment == null) { "Fragment is not allowed" }
        require(uri.host == "127.0.0.1" || uri.host.equals("localhost", ignoreCase = true)) {
            "Only localhost or 127.0.0.1 is allowed"
        }
        require(uri.port in 1..65535) { "A valid explicit port is required" }
        require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/" || uri.rawPath == "/dorequest.php") {
            "Only /dorequest.php is allowed"
        }
        "http://${uri.host.lowercase()}:${uri.port}"
    }

    fun load(context: Context): String? =
        prefs(context).getString(KEY_HOST, null)?.let { validate(it).getOrNull() }

    fun store(context: Context, clientHost: String?): Boolean {
        val editor = prefs(context).edit()
        if (clientHost == null) editor.remove(KEY_HOST) else editor.putString(KEY_HOST, clientHost)
        return editor.commit()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
