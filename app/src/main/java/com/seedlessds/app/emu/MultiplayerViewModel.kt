package com.seedlessds.app.emu

import android.content.Context
import java.util.Date
import android.content.pm.PackageManager
import android.app.Application
import com.seedlessds.app.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MultiplayerViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context = application

    data class Nearby(
        val key: String,
        val code: String,
        val host: MultiplayerProfile,
        val players: Int,
        val maximum: Int,
        val play: MultiplayerRoom.Play?,
        val game: String,
        val target: MultiplayerRoom.Target,
        val app: String = MultiplayerNearby.OWN_APP,
    )

    data class Seat(
        val key: String,
        val profile: MultiplayerProfile,
        val status: Int,
        val ping: Int,
        val local: Boolean,
        val host: Boolean,
        val known: Boolean,
    )

    sealed class Failure {
        data class Message(val text: Int) : Failure()
        data class Direct(val room: Nearby) : Failure()
    }

    private val mutableProfile = MutableStateFlow(MultiplayerProfile.load(context))
    private val mutableStatus = MutableStateFlow(JSONObject())
    private val mutableBios = MutableStateFlow(false)
    private val mutableBusy = MutableStateFlow(false)
    private val mutableFailure = MutableStateFlow<Failure?>(null)
    private val mutableJoining = MutableStateFlow<Nearby?>(null)
    private val mutableJoinStep = MutableStateFlow(0)
    private val catalog by lazy { MultiplayerCatalog(context.resources) }
    private val cartridgeIndex by lazy { CartridgeIndex(context) }
    private var libraryJob: Job? = null
    private var lease: Any? = null

    val profile: StateFlow<MultiplayerProfile> = mutableProfile.asStateFlow()
    val status: StateFlow<JSONObject> = mutableStatus.asStateFlow()
    val bios: StateFlow<Boolean> = mutableBios.asStateFlow()
    val nearby: StateFlow<List<Nearby>> = MultiplayerNearby.rooms
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()
    val failure: StateFlow<Failure?> = mutableFailure.asStateFlow()
    val joining: StateFlow<Nearby?> = mutableJoining.asStateFlow()
    val joinStep: StateFlow<Int> = mutableJoinStep.asStateFlow()
    val room = MultiplayerRoom.state
    val starts = MultiplayerRoom.starts
    private val mutableRoms = MutableStateFlow<List<LibraryGame>>(emptyList())
    val roms: StateFlow<List<LibraryGame>> = mutableRoms.asStateFlow()
    val page = MutableStateFlow(MultiplayerPage.HUB)
    val focus = MutableStateFlow<MultiplayerFocus?>(null)

    internal suspend fun gameCatalog(): MultiplayerCatalog = withContext(Dispatchers.IO) { catalog }

    private fun loadRoms() {
        libraryJob?.cancel()
        libraryJob = viewModelScope.launch(Dispatchers.IO) {
            libraryLock.withLock {
                val entries = RomLibrary.scan(context)
                val cartridges = cartridgeIndex.load(entries)
                val favorites = RomLibrary.favorites(context)
                val games = entries.mapNotNull { entry ->
                    ensureActive()
                    val id = cartridges[entry.uri]
                    if (id?.unitCode == 3) return@mapNotNull null
                    LibraryGame(entry, RomLibrary.stats(context, entry.uri).lastPlayed.takeIf { it > 0 }?.let { Date(it) },
                        entry.uri.toString() in favorites, id)
                }
                ensureActive()
                mutableRoms.value = games.sortedBy { it.name.lowercase() }
            }
        }
    }

    val directSupported: Boolean = MultiplayerDirectNetwork.supported(context)

    fun directPermitted(): Boolean =
        context.checkSelfPermission(MultiplayerDirectNetwork.permission()) == PackageManager.PERMISSION_GRANTED

    fun refresh() {
        loadRoms()
        viewModelScope.launch {
            mutableProfile.value = withContext(Dispatchers.IO) { MultiplayerProfile.load(context) }
            mutableBios.value = withContext(Dispatchers.IO) { BiosTools.status(context).values.all { it } }
        }
    }

    suspend fun poll() {
        val value = runCatching { MultiplayerSession.status() }.getOrElse { JSONObject() }
        mutableStatus.value = value
        val connected = players(value).count { it.status == 1 || it.status == 2 }
        MultiplayerRoom.players(connected.coerceAtLeast(1))
    }

    fun search() {
        if (lease == null) lease = MultiplayerNearby.acquire(context) else MultiplayerNearby.resume(context)
    }

    fun stopSearch() {
        MultiplayerNearby.release(lease)
        lease = null
    }

    fun host(play: MultiplayerRoom.Play, rom: LibraryGame?, maximum: Int, showAll: Boolean, done: (Boolean) -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableFailure.value = null
        stopSearch()
        viewModelScope.launch {
            val ok = try {
                val game = resolveHostSelection(play, rom, showAll)
                if (play != MultiplayerRoom.Play.CHAT && game == null) {
                    false
                } else {
                    val result = MultiplayerRoom.host(context, profile.value, play, game, maximum)
                    if (result != 0) mutableFailure.value = Failure.Message(code(result))
                    result == 0
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                com.seedlessds.app.AppLog.e("Multiplayer", "Could not open the room", error)
                mutableFailure.value = Failure.Message(R.string.multiplayer_direct_failed)
                false
            } finally {
                mutableBusy.value = false
            }
            poll()
            done(ok)
        }
    }

    private var joinJob: Job? = null

    fun cancelJoin(done: () -> Unit) {
        val job = joinJob ?: return done()
        job.cancel()
        viewModelScope.launch {
            job.join()
            runCatching { MultiplayerRoom.leave() }
            mutableBusy.value = false
            mutableJoining.value = null
            poll()
            done()
        }
    }

    fun join(room: Nearby, done: (Boolean) -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableFailure.value = null
        mutableJoining.value = room
        mutableJoinStep.value = 1
        joinJob = viewModelScope.launch {
            val ok = try {
                MultiplayerNearby.pause()
                val result = MultiplayerRoom.join(context, profile.value, room.target, false) { mutableJoinStep.value = 2 }
                if (result != 0) mutableFailure.value = Failure.Message(code(result))
                result == 0
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                com.seedlessds.app.AppLog.e("Multiplayer", "Could not join the room", error)
                mutableFailure.value = Failure.Direct(room)
                false
            } finally {
                mutableBusy.value = false
            }
            if (ok) mutableJoinStep.value = 3
            poll()
            done(ok)
        }
    }

    fun leave(done: (Boolean) -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            val ok = try {
                val result = MultiplayerRoom.leave()
                if (result != 0) mutableFailure.value = Failure.Message(code(result))
                result == 0
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableFailure.value = Failure.Message(R.string.multiplayer_direct_stop_failed)
                false
            } finally {
                mutableBusy.value = false
            }
            poll()
            done(ok)
        }
    }

    fun directUnavailable() {
        mutableFailure.value = Failure.Message(R.string.mp_direct_unavailable)
    }

    fun permissionDenied() {
        mutableFailure.value = Failure.Message(R.string.mp_direct_permission)
    }

    fun dismissFailure() {
        mutableFailure.value = null
    }

    fun change(play: MultiplayerRoom.Play, rom: LibraryGame?, showAll: Boolean, done: () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableFailure.value = null
        viewModelScope.launch {
            try {
                val game = resolveHostSelection(play, rom, showAll)
                if (play != MultiplayerRoom.Play.CHAT && game == null) return@launch
                MultiplayerRoom.update(play, game)
                done()
            } finally {
                mutableBusy.value = false
            }
        }
    }

    fun saveProfile(value: MultiplayerProfile, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { MultiplayerProfile.save(context, value) }
            if (saved) {
                val loaded = withContext(Dispatchers.IO) { MultiplayerProfile.load(context) }
                mutableProfile.value = loaded
                MultiplayerRoom.profile(loaded, false)
            }
            done(saved)
        }
    }

    private suspend fun resolveHostSelection(play: MultiplayerRoom.Play, rom: LibraryGame?, showAll: Boolean): MultiplayerRoom.Game? {
        if (play == MultiplayerRoom.Play.CHAT) return null
        val game = withContext(Dispatchers.IO) {
            libraryLock.withLock {
                val games = roms.value
                val current = games.firstOrNull { it.uri == rom?.uri } ?: return@withLock null
                val id = CartridgeId.read(context, current.entry)
                if (id != current.cartridge) {
                    cartridgeIndex.invalidate(current.uri)
                    mutableRoms.value = if (id?.unitCode == 3) games - current
                        else games.map { if (it.uri == current.uri) it.copy(cartridge = id) else it }
                }
                if (id == null || id != rom?.cartridge || id.unitCode == 3 ||
                    (!showAll && (catalog[id]?.players(play) ?: 0) < 2)) return@withLock null
                MultiplayerRoom.Game(current.name, id.gameCode, current.fileName)
            }
        }
        if (game == null) mutableFailure.value = Failure.Message(R.string.mp_selection_changed)
        return game
    }

    suspend fun find(game: MultiplayerRoom.Game?): LibraryGame? = withContext(Dispatchers.IO) {
        if (game == null) return@withContext null
        val candidates = roms.value
        if (game.code.isEmpty()) return@withContext candidates.firstOrNull { it.fileName == game.file }
        val ordered = candidates.sortedBy {
            when {
                it.fileName == game.file -> 0
                it.cartridge?.gameCode == game.code -> 1
                else -> 2
            }
        }
        ordered.firstOrNull {
            ensureActive()
            val id = CartridgeId.read(context, it.entry)
            id?.gameCode == game.code && id.unitCode != 3
        }
    }

    fun shareGame(hasGame: Boolean) {
        MultiplayerRoom.profile(profile.value, hasGame)
    }

    fun start() = MultiplayerRoom.start()

    fun players(value: JSONObject = mutableStatus.value): List<Seat> {
        val players = value.optJSONArray("players") ?: return emptyList()
        val members = room.value?.info?.members.orEmpty().toMutableList()
        fun take(predicate: (MultiplayerRoom.Member) -> Boolean) = members.firstOrNull(predicate)?.also { members.remove(it) }
        val entries = (0 until players.length()).mapNotNull { players.optJSONObject(it) }
        val claimed = entries.map { player ->
            val address = player.optString("ip")
            when {
                player.optInt("status") == 2 -> take { it.key == MultiplayerRoom.HOST_KEY }
                address.isNotEmpty() -> take { it.key != MultiplayerRoom.HOST_KEY && it.address == address }
                else -> null
            }
        }
        return entries.mapIndexed { index, player ->
            val name = player.optString("name")
            val local = player.optBoolean("local")
            val host = player.optInt("status") == 2
            val member = if (local) null else claimed[index]
                ?: take { it.key != MultiplayerRoom.HOST_KEY && it.profile.nickname == name }
            val profile = when {
                local -> profile.value.copy(nickname = name)
                member != null -> member.profile
                else -> MultiplayerProfile(name, "", nameColour(name), "", 1, 1)
            }
            Seat("${player.optInt("id")}:$name", profile, player.optInt("status"), player.optInt("ping"), local, host,
                local || member != null)
        }
    }

    private fun code(result: Int): Int = when (result) {
        -2 -> R.string.multiplayer_finish_game
        else -> R.string.multiplayer_connection_failed
    }

    override fun onCleared() {
        stopSearch()
    }

    companion object {
        private val libraryLock = Mutex()
        private const val PEEK_REFRESH_MS = 5_000L

        fun nameColour(name: String): Int = (name.lowercase().hashCode() and Int.MAX_VALUE) % 16
    }
}
