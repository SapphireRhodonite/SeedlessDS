package com.seedlessds.app.emu

import android.content.Context
import com.seedlessds.core.SeedlessCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.security.SecureRandom

object MultiplayerSession {
    private val operations = Mutex()
    @Volatile internal var group: MultiplayerGroup? = null
        private set

    val active: Boolean get() = !SeedlessCore.startupError && SeedlessCore.multiplayerActive()

    private fun mac(context: Context): ByteArray {
        val preferences = context.getSharedPreferences("seedless_multiplayer", Context.MODE_PRIVATE)
        val stored = preferences.getString("mac", null)
        if (stored != null && stored.matches(Regex("[0-9a-f]{12}")))
            return stored.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val bytes = ByteArray(6).also { SecureRandom().nextBytes(it) }
        bytes[0] = ((bytes[0].toInt() or 2) and 0xfe).toByte()
        check(preferences.edit().putString("mac", bytes.joinToString("") { "%02x".format(it.toInt() and 255) }).commit())
        return bytes
    }

    suspend fun start(context: Context, mode: Int, name: String, address: String = "", direct: Boolean = false,
        maximum: Int = 16, code: String = "", target: MultiplayerRoom.Target? = null, onGroup: () -> Unit = {}): Int =
        withContext(Dispatchers.IO) {
            operations.withLock {
                val player = name.trim().toByteArray(Charsets.UTF_8)
                if (mode !in 1..2 || player.size !in 1..31 || maximum !in 2..16 ||
                    (direct && (mode != 1 || address.isNotEmpty())) ||
                    (target != null && (mode != 2 || address.isNotEmpty() || target.network.isBlank() || target.password.isEmpty()))) return@withLock -1
                MultiplayerDirectNetwork.checkAvailable(context)
                val managed = direct || target != null
                if (managed) {
                    val stopped = SeedlessCore.multiplayerStop()
                    if (stopped != 0) return@withLock stopped
                    group = null
                    closeGroup()
                }
                try {
                    val route = when {
                        direct -> MultiplayerDirectNetwork.create(context, code)
                        target != null -> MultiplayerDirectNetwork.join(context, target.network, target.password)
                        else -> MultiplayerDirectNetwork.existing(context, mode == 1)
                    }
                    if (!route.admits(mode, address)) return@withLock -1
                    onGroup()
                    val result = SeedlessCore.multiplayerStart(mode, player, route.local.address, route.owner.address,
                        route.index, mac(context.applicationContext), maximum)
                    if (result == 0) group = route
                    else if (result != -2) group = null
                    if (managed && result != 0) closeGroup()
                    result
                } catch (error: Throwable) {
                    if (managed) withContext(NonCancellable) { closeGroup() }
                    throw error
                }
            }
        }

    private suspend fun closeGroup() {
        try {
            MultiplayerDirectNetwork.stop()
        } finally {
            MultiplayerDirectNetwork.leave()
        }
    }

    suspend fun stop(): Int = withContext(Dispatchers.IO) {
        operations.withLock {
            val result = SeedlessCore.multiplayerStop()
            if (result == 0) {
                group = null
                closeGroup()
            }
            result
        }
    }

    suspend fun status(): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(String(SeedlessCore.multiplayerStatus(), Charsets.UTF_8)).also { result ->
            if (result.optInt("mode") in 1..2) group?.let {
                result.put("direct_network", JSONObject().put("name", it.name).put("address", it.owner.hostAddress)
                    .put("local", it.local.hostAddress).put("interface", it.interfaceName).put("index", it.index))
            }
        }
    }
}
