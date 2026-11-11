package com.seedlessds.app.emu

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object MultiplayerNearby {
    const val OWN_APP = "SeedlessDS"
    const val OTHER_APP = "WatermelonDS"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableRooms = MutableStateFlow<List<MultiplayerViewModel.Nearby>>(emptyList())
    private val leases = mutableSetOf<Any>()
    private var searchJob: Job? = null
    val rooms: StateFlow<List<MultiplayerViewModel.Nearby>> = mutableRooms.asStateFlow()

    fun acquire(context: Context): Any {
        val lease = Any()
        leases.add(lease)
        resume(context)
        return lease
    }

    fun release(lease: Any?) {
        if (!leases.remove(lease) || leases.isNotEmpty()) return
        searchJob?.cancel()
        searchJob = null
        mutableRooms.value = emptyList()
    }

    fun resume(context: Context) {
        if (leases.isEmpty() || searchJob?.isActive == true || !MultiplayerDirectNetwork.supported(context) ||
            context.checkSelfPermission(MultiplayerDirectNetwork.permission()) != PackageManager.PERMISSION_GRANTED) return
        searchJob = scope.launch {
            MultiplayerDirectNetwork.rooms(context.applicationContext).collect { update(it) }
        }
    }

    suspend fun pause() {
        searchJob?.cancelAndJoin()
        searchJob = null
        mutableRooms.value = emptyList()
    }

    private fun update(directRooms: List<MultiplayerDirectNetwork.Room>) {
        val result = LinkedHashMap<String, MultiplayerViewModel.Nearby>()
        for (room in directRooms) {
            val record = room.record
            val code = record["c"].orEmpty()
            val network = record["s"].orEmpty()
            val password = record["p"].orEmpty()
            if (code.length != 4 || code.any { it !in MultiplayerRoom.CODE_ALPHABET } ||
                network.isBlank() || password.isEmpty()) continue
            result[code] = MultiplayerViewModel.Nearby(
                key = code,
                code = code,
                host = MultiplayerProfile(record["n"].orEmpty(), "", record["k"]?.toIntOrNull()?.coerceIn(0, 15) ?: 0,
                    record["y"].orEmpty().takeIf { it in MultiplayerProfile.SYMBOLS }.orEmpty(), 1, 1),
                players = record["u"]?.toIntOrNull() ?: 1,
                maximum = record["x"]?.toIntOrNull() ?: 16,
                play = MultiplayerRoom.Play.of(record["m"]),
                game = record["g"].orEmpty(),
                target = MultiplayerRoom.Target(network, password,
                    record["o"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: MultiplayerRoom.PORT),
                app = record["w"] ?: OWN_APP,
            )
        }
        mutableRooms.value = result.values.toList()
    }

}
