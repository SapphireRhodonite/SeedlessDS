package com.seedlessds.app.ra

import com.seedlessds.app.R
import com.seedlessds.app.ra.RaNative

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class RaUnlock(val id: Int, val points: Int, val title: String, val description: String, val badgeUrl: String?)

data class RaAchievement(
    val id: Int, val title: String, val description: String, val points: Int,
    val state: Int, val progress: String?, val percent: Int, val badgeUrl: String?,
) { val unlocked get() = state == 2 }

data class RaBucket(val type: Int, val label: String, val achievements: List<RaAchievement>)

data class RaProgress(val id: Int, val title: String, val progress: String, val badgeUrl: String?)

data class RaChallenge(val badgeUrl: String?, val phase: Int, val title: String)

data class RaLeaderboard(val id: Int, val title: String, val description: String, val state: Int, val trackerValue: String)

data class RaLbBucket(val type: Int, val label: String, val leaderboards: List<RaLeaderboard>)

data class RaLbEntry(val rank: Int, val user: String, val display: String, val isYou: Boolean, val avatarUrl: String?)

data class RaScoreboard(val lbId: Int, val title: String, val submitted: String, val best: String, val rank: Int, val total: Int)

data class RaMastery(val title: String, val isSubset: Boolean, val iconUrl: String?, val count: Int, val nonce: Int)

data class RaSettings(
    val enabled: Boolean = false,
    val notifications: Boolean = true,
    val sound: Boolean = true,
    val richPresence: Boolean = true,
    val encore: Boolean = false,
    val unofficial: Boolean = false,
    val spectator: Boolean = false,
    val showRichPresence: Boolean = false,
)

object RetroAchievements {
    const val RC_CONSOLE_NINTENDO_DS = 18

    private const val CAT_CORE = 1
    private const val CAT_CORE_AND_UNOFFICIAL = 3
    private const val GROUPING_PROGRESS = 1
    private const val LB_GROUPING_TRACKING = 1

    private const val TAG = "RetroAchievements"
    private const val EVENT_ACHIEVEMENT_TRIGGERED = 1
    private const val EVENT_LB_STARTED = 2
    private const val EVENT_LB_FAILED = 3
    private const val EVENT_LB_SUBMITTED = 4
    private const val EVENT_CHALLENGE_SHOW = 5
    private const val EVENT_CHALLENGE_HIDE = 6
    private const val EVENT_PROGRESS_SHOW = 7
    private const val EVENT_PROGRESS_HIDE = 8
    private const val EVENT_PROGRESS_UPDATE = 9
    private const val EVENT_LB_TRACKER_SHOW = 10
    private const val EVENT_LB_TRACKER_HIDE = 11
    private const val EVENT_LB_TRACKER_UPDATE = 12
    private const val EVENT_LB_SCOREBOARD = 13
    private const val EVENT_GAME_COMPLETED = 15
    private const val EVENT_SERVER_ERROR = 16
    private const val EVENT_DISCONNECTED = 17
    private const val EVENT_RECONNECTED = 18
    private const val EVENT_SUBSET_COMPLETED = 19

    private val exec = Executors.newSingleThreadExecutor { Thread(it, "ra-thread") }
    private val http = OkHttpClient()
    private var appCtx: Context? = null
    private fun text(@androidx.annotation.StringRes id: Int, vararg args: Any?): String =
        appCtx?.getString(id, *args).orEmpty()
    @Volatile private var inited = false

    val loggedIn = mutableStateOf(false)
    val username = mutableStateOf<String?>(null)
    val score = mutableStateOf(0)
    val scoreHardcore = mutableStateOf(0)
    val statusMsg = mutableStateOf("")
    val gameTitle = mutableStateOf<String?>(null)
    val richPresence = mutableStateOf<String?>(null)
    val lastUnlock = mutableStateOf<RaUnlock?>(null)
    var settings = RaSettings(); private set

    val userAvatar = mutableStateOf<String?>(null)
    val gameIconUrl = mutableStateOf<String?>(null)
    val welcomeNonce = mutableStateOf(0)

    val achievements = mutableStateOf<List<RaBucket>>(emptyList())
    val achTotal = mutableStateOf(0)
    val achUnlocked = mutableStateOf(0)
    val achLoading = mutableStateOf(false)
    val challenges = androidx.compose.runtime.mutableStateMapOf<Int, RaChallenge>()
    fun clearChallenge(id: Int) { challenges.remove(id) }
    val progressPopup = mutableStateOf<RaProgress?>(null)
    val gameCompleted = mutableStateOf(false)
    val masteryPopup = mutableStateOf<RaMastery?>(null)
    private var masteryNonce = 0

    val hasLeaderboards = mutableStateOf(false)
    val leaderboards = mutableStateOf<List<RaLbBucket>>(emptyList())
    val lbLoading = mutableStateOf(false)
    val trackers = androidx.compose.runtime.mutableStateMapOf<Int, String>()
    val scoreboard = mutableStateOf<RaScoreboard?>(null)
    val lbToast = mutableStateOf<String?>(null)
    val lbEntries = mutableStateOf<List<RaLbEntry>>(emptyList())
    val lbEntriesLoading = mutableStateOf(false)
    val lbEntriesError = mutableStateOf<String?>(null)
    val lbEntriesTitle = mutableStateOf("")
    private val lbFetchNonce = java.util.concurrent.atomic.AtomicInteger(0)
    fun clearScoreboard() { scoreboard.value = null }
    fun clearLbToast() { lbToast.value = null }
    fun clearMastery(nonce: Int) { if (masteryPopup.value?.nonce == nonce) masteryPopup.value = null }

    fun init(ctx: Context) {
        if (appCtx == null) appCtx = ctx.applicationContext
        settings = loadSettings()
        exec.execute {
            if (!inited) { inited = RaNative.raInit(); com.seedlessds.app.AppLog.i(TAG, "raInit=$inited") }
            if (inited) {
                RaNative.raSetHost(RaHostOverride.load(ctx))
                applyTogglesNative()
            }
        }
    }

    fun setHostOverride(ctx: Context, clientHost: String?) {
        if (!RaHostOverride.store(ctx, clientHost)) return
        exec.execute { if (inited) RaNative.raSetHost(clientHost) }
    }

    fun isEnabled() = settings.enabled

    fun saveSettings(s: RaSettings) {
        settings = s
        appCtx?.getSharedPreferences("ra_settings", Context.MODE_PRIVATE)?.edit()?.apply {
            putBoolean("enabled", s.enabled); putBoolean("notifications", s.notifications)
            putBoolean("sound", s.sound); putBoolean("richPresence", s.richPresence)
            putBoolean("encore", s.encore); putBoolean("unofficial", s.unofficial)
            putBoolean("spectator", s.spectator); putBoolean("showRichPresence", s.showRichPresence); apply()
        }
        exec.execute { if (inited) applyTogglesNative() }
    }

    private fun loadSettings(): RaSettings {
        val p = appCtx?.getSharedPreferences("ra_settings", Context.MODE_PRIVATE) ?: return RaSettings()
        val d = RaSettings()
        return RaSettings(
            enabled = p.getBoolean("enabled", d.enabled),
            notifications = p.getBoolean("notifications", d.notifications),
            sound = p.getBoolean("sound", d.sound),
            richPresence = p.getBoolean("richPresence", d.richPresence),
            encore = p.getBoolean("encore", d.encore),
            unofficial = p.getBoolean("unofficial", d.unofficial),
            spectator = p.getBoolean("spectator", d.spectator),
            showRichPresence = p.getBoolean("showRichPresence", d.showRichPresence),
        )
    }

    private fun applyTogglesNative() =
        RaNative.raSetToggles(settings.spectator, settings.unofficial, settings.encore)

    fun login(user: String, pass: String) {
        statusMsg.value = text(R.string.ra_signing_in)
        exec.execute { if (inited) RaNative.raLoginPassword(user, pass) }
    }

    fun tryTokenLogin(): Boolean {
        val (u, t) = loadCreds()
        if (u.isNullOrEmpty() || t.isNullOrEmpty()) return false
        statusMsg.value = text(R.string.ra_resuming_session)
        exec.execute { if (inited) RaNative.raLoginToken(u, t) }
        return true
    }

    fun logout() {
        exec.execute { if (inited) RaNative.raLogout() }
        clearCreds()
        loggedIn.value = false; username.value = null; score.value = 0; scoreHardcore.value = 0
        pendingGamePath = null; pendingGameUri = null; bootSidecar = null
        resetGameState()
    }

    @Volatile private var pendingGamePath: String? = null
    @Volatile private var pendingGameUri: Uri? = null
    @Volatile private var bootSidecar: ByteArray? = null
    fun setBootStateSidecar(bytes: ByteArray?) { bootSidecar = bytes }

    private fun resetGameState() {
        gameTitle.value = null; richPresence.value = null; statusMsg.value = ""
        achievements.value = emptyList(); achTotal.value = 0; achUnlocked.value = 0
        challenges.clear(); progressPopup.value = null; gameCompleted.value = false
        lastUnlock.value = null
        gameIconUrl.value = null
        masteryPopup.value = null
        hasLeaderboards.value = false; leaderboards.value = emptyList()
        trackers.clear(); scoreboard.value = null; lbToast.value = null
        lbEntries.value = emptyList(); lbEntriesError.value = null; lbEntriesTitle.value = ""
        lbFetchNonce.incrementAndGet(); lbEntriesLoading.value = false
    }

    fun loadGame(filePath: String) {
        if (!settings.enabled) return
        resetGameState()
        if (loggedIn.value) {
            pendingGamePath = null; pendingGameUri = null
            exec.execute { if (inited) RaNative.raLoadGame(RC_CONSOLE_NINTENDO_DS, filePath) }
        } else {
            pendingGamePath = filePath; pendingGameUri = null
        }
    }

    fun loadGameUri(uri: Uri) {
        if (!settings.enabled) return
        resetGameState()
        if (loggedIn.value) {
            pendingGamePath = null; pendingGameUri = null
            exec.execute { if (inited) loadUriNow(uri) }
        } else {
            pendingGameUri = uri; pendingGamePath = null
        }
    }

    private fun loadUriNow(uri: Uri) {
        val ctx = appCtx ?: return
        val pfd = try { ctx.contentResolver.openFileDescriptor(uri, "r") } catch (_: Throwable) { null }
        if (pfd == null) { statusMsg.value = text(R.string.ra_couldn_t_open_the_rom_for_retroachievements); return }
        try { RaNative.raLoadGameFd(RC_CONSOLE_NINTENDO_DS, pfd.fd) } finally { runCatching { pfd.close() } }
    }

    fun unloadGame() {
        pendingGamePath = null; pendingGameUri = null; bootSidecar = null
        exec.execute { if (inited) RaNative.raUnloadGame() }
        resetGameState()
    }
    @Volatile private var suspendFrames = false
    fun doFrame() { if (inited && loggedIn.value && !suspendFrames) exec.execute { if (!suspendFrames) RaNative.raDoFrame() } }
    fun idle() { if (inited && loggedIn.value) exec.execute { RaNative.raIdle() } }

    fun consumeUnlock(): RaUnlock? { val u = lastUnlock.value; lastUnlock.value = null; return u }

    private fun activeForState() = inited && settings.enabled && loggedIn.value && gameTitle.value != null

    fun serializeProgress(timeoutMs: Long = 1500): ByteArray? {
        if (!activeForState()) return null
        return try {
            exec.submit(Callable { RaNative.raSerializeProgress() }).get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Throwable) { null }
    }

    fun beginStateLoad() {
        if (!activeForState()) return
        suspendFrames = true
        try { exec.submit { }.get(500, TimeUnit.MILLISECONDS) } catch (_: Throwable) {}
    }

    fun endStateLoad() { suspendFrames = false }

    fun resetRuntime(timeoutMs: Long = 1000): Boolean {
        if (!activeForState()) { suspendFrames = false; return false }
        val f = exec.submit(Callable {
            RaNative.raReset()
            challenges.clear()
            trackers.clear()
            suspendFrames = false
            true
        })
        return try { f.get(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: Throwable) { false }
    }

    fun deserializeProgress(bytes: ByteArray?, timeoutMs: Long = 1500): Boolean {
        if (!activeForState()) { suspendFrames = false; return false }
        val f = exec.submit(Callable {
            val ok = RaNative.raDeserializeProgress(bytes)
            suspendFrames = false
            ok
        })
        return try { f.get(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: Throwable) { false }
    }

    fun refreshAchievements() {
        if (!inited) return
        achLoading.value = true
        exec.execute {
            val cat = if (settings.unofficial) CAT_CORE_AND_UNOFFICIAL else CAT_CORE
            val raw = try { RaNative.raAchievementList(cat, GROUPING_PROGRESS) } catch (_: Throwable) { null }
            achievements.value = parseAchievements(raw)
            refreshSummaryNow()
            achLoading.value = false
        }
    }

    fun refreshRichPresence() {
        if (!inited || !loggedIn.value) return
        exec.execute { richPresence.value = RaNative.raRichPresence()?.takeIf { it.isNotBlank() } }
    }

    private fun refreshSummaryNow() {
        val sum = RaNative.raGameSummary()?.split(0x1F.toChar()) ?: return
        achTotal.value = sum.getOrNull(2)?.toIntOrNull() ?: achTotal.value
        achUnlocked.value = sum.getOrNull(3)?.toIntOrNull() ?: achUnlocked.value
        score.value = RaNative.raGetScore()
    }

    private fun parseAchievements(raw: Array<String>?): List<RaBucket> {
        if (raw.isNullOrEmpty()) return emptyList()
        data class Row(val bType: Int, val bLabel: String, val a: RaAchievement)
        val rows = raw.mapNotNull { line ->
            val f = line.split(0x1F.toChar())
            if (f.size < 10) return@mapNotNull null
            Row(
                f[0].toIntOrNull() ?: 0, f[1],
                RaAchievement(
                    id = f[2].toIntOrNull() ?: 0, title = f[3], description = f[4],
                    points = f[5].toIntOrNull() ?: 0, state = f[6].toIntOrNull() ?: 0,
                    progress = f[7].ifBlank { null }, percent = f[8].toIntOrNull() ?: 0,
                    badgeUrl = f[9].ifBlank { null },
                )
            )
        }
        val order = LinkedHashMap<Int, Pair<String, MutableList<RaAchievement>>>()
        for (r in rows) order.getOrPut(r.bType) { r.bLabel to mutableListOf() }.second.add(r.a)
        return order.map { (type, v) -> RaBucket(type, bucketLabel(type, v.first), v.second) }
    }

    private fun bucketLabel(type: Int, fallback: String): String = when (type) {
        1 -> text(R.string.ra_locked); 2 -> text(R.string.ra_unlocked); 3 -> text(R.string.ra_unsupported); 4 -> text(R.string.ra_unofficial)
        5 -> text(R.string.ra_recently_unlocked); 6 -> text(R.string.ra_active_challenges); 7 -> text(R.string.ra_almost_there); 8 -> text(R.string.ra_unsynced)
        else -> fallback.ifBlank { text(R.string.common_achievements) }
    }

    @Volatile private var cachedUA: String? = null
    private fun userAgent(): String {
        var ua = cachedUA
        if (ua == null) { ua = "SeedlessDS/${com.seedlessds.app.BuildConfig.VERSION_NAME} " + (RaNative.raUserAgentClause() ?: "rcheevos"); cachedUA = ua }
        return ua
    }

    fun serverCall(handle: Long, url: String, postData: String?, contentType: String?) {
        val b = Request.Builder().url(url).header("User-Agent", userAgent())
        if (postData != null) {
            val mt = (contentType ?: "application/x-www-form-urlencoded").toMediaTypeOrNull()
            b.post(postData.toByteArray().toRequestBody(mt))
        } else b.get()
        http.newCall(b.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                exec.execute { RaNative.raServerResponse(handle, 0, null) }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = try { response.body?.bytes() } catch (_: Throwable) { null }
                val code = response.code
                response.close()
                exec.execute { RaNative.raServerResponse(handle, code, body) }
            }
        })
    }

    fun onLoginResult(result: Int, error: String?) {
        if (result == 0) {
            val u = RaNative.raGetUsername(); val t = RaNative.raGetToken()
            if (!u.isNullOrEmpty() && !t.isNullOrEmpty()) saveCreds(u, t)
            username.value = u; score.value = RaNative.raGetScore()
            scoreHardcore.value = RaNative.raGetHardcoreScore(); loggedIn.value = true
            userAvatar.value = RaNative.raUserAvatar()
            statusMsg.value = text(R.string.ra_signed_in_as, u)
            com.seedlessds.app.AppLog.i(TAG, "login OK: $u")
            if (settings.enabled) {
                val p = pendingGamePath; val u = pendingGameUri
                pendingGamePath = null; pendingGameUri = null
                p?.let { exec.execute { if (inited) RaNative.raLoadGame(RC_CONSOLE_NINTENDO_DS, it) } }
                u?.let { exec.execute { if (inited) loadUriNow(it) } }
            }
        } else {
            loggedIn.value = false
            statusMsg.value = error ?: text(R.string.ra_sign_in_failed)
            com.seedlessds.app.AppLog.e(TAG, "login FAILED: $error")
        }
    }

    fun onLoadResult(result: Int, error: String?) {
        if (result == 0) {
            val sum = RaNative.raGameSummary()
            if (sum != null) {
                val p = sum.split(0x1F.toChar())
                gameTitle.value = p.getOrNull(0)
                achTotal.value = p.getOrNull(2)?.toIntOrNull() ?: 0
                achUnlocked.value = p.getOrNull(3)?.toIntOrNull() ?: 0
                gameIconUrl.value = p.getOrNull(4)?.takeIf { it.isNotBlank() }
                welcomeNonce.value += 1
                statusMsg.value = text(R.string.common_achievements_2, p.getOrNull(0), achUnlocked.value, achTotal.value)
                com.seedlessds.app.AppLog.i(TAG, "loadGame OK: ${p.getOrNull(0)} (id=${p.getOrNull(1)}) ${achUnlocked.value}/${achTotal.value} achievements")
                bootSidecar?.let { b -> bootSidecar = null; RaNative.raDeserializeProgress(b) }
                refreshAchievements()
                refreshLeaderboards()
            } else { resetGameState(); statusMsg.value = text(R.string.ra_game_has_no_achievements_in_ra); com.seedlessds.app.AppLog.i(TAG, "loadGame OK but no set") }
        } else {
            resetGameState()
            statusMsg.value = text(R.string.ra_no_achievements_for_this_game)
            com.seedlessds.app.AppLog.i(TAG, "loadGame: $error")
        }
    }

    fun onEvent(type: Int, id: Int, points: Int, title: String?, description: String?, badgeUrl: String?) {
        when (type) {
            EVENT_ACHIEVEMENT_TRIGGERED -> {
                com.seedlessds.app.AppLog.i(TAG, "UNLOCK id=$id +$points '$title'")
                score.value = RaNative.raGetScore()
                achUnlocked.value += 1
                challenges[id]?.let { challenges[id] = it.copy(phase = 1) }
                if (settings.notifications)
                    lastUnlock.value = RaUnlock(id, points, title ?: "", description ?: "", badgeUrl)
                if (settings.sound) RaSound.playUnlock()
                refreshAchievements()
            }
            EVENT_CHALLENGE_SHOW -> challenges[id] = RaChallenge(badgeUrl, 0, title ?: "")
            EVENT_CHALLENGE_HIDE -> challenges[id]?.let {
                if (it.phase == 0) challenges[id] = it.copy(phase = 2)
            }
            EVENT_PROGRESS_SHOW, EVENT_PROGRESS_UPDATE ->
                progressPopup.value = RaProgress(id, title ?: "", description ?: "", badgeUrl)
            EVENT_PROGRESS_HIDE -> progressPopup.value = null
            EVENT_GAME_COMPLETED -> {
                gameCompleted.value = true
                statusMsg.value = text(R.string.ra_game_completed)
                masteryNonce += 1
                masteryPopup.value = RaMastery(
                    title = gameTitle.value ?: text(R.string.ra_game_complete), isSubset = false,
                    iconUrl = gameIconUrl.value, count = achTotal.value, nonce = masteryNonce
                )
                com.seedlessds.app.AppLog.i(TAG, "GAME COMPLETED")
            }
            EVENT_SUBSET_COMPLETED -> {
                masteryNonce += 1
                masteryPopup.value = RaMastery(
                    title = title?.ifBlank { text(R.string.ra_subset_complete) } ?: text(R.string.ra_subset_complete), isSubset = true,
                    iconUrl = badgeUrl ?: gameIconUrl.value, count = 0, nonce = masteryNonce
                )
                com.seedlessds.app.AppLog.i(TAG, "SUBSET COMPLETED: $title")
            }
            EVENT_SERVER_ERROR -> { statusMsg.value = text(R.string.library_ra_2, description ?: text(R.string.ra_server_error)); com.seedlessds.app.AppLog.e(TAG, "server_error: $description") }
            EVENT_DISCONNECTED -> statusMsg.value = text(R.string.ra_ra_offline_unlocks_queued)
            EVENT_RECONNECTED -> statusMsg.value = text(R.string.ra_ra_reconnected)
        }
    }

    fun onLbEvent(type: Int, lbId: Int, trackerId: Int, rank: Int, total: Int, title: String?, value: String?, best: String?) {
        when (type) {
            EVENT_LB_TRACKER_SHOW, EVENT_LB_TRACKER_UPDATE -> trackers[trackerId] = value ?: ""
            EVENT_LB_TRACKER_HIDE -> trackers.remove(trackerId)
            EVENT_LB_STARTED -> com.seedlessds.app.AppLog.i(TAG, "LB started $lbId '$title'")
            EVENT_LB_FAILED -> { lbToast.value = text(R.string.ra_leaderboard_attempt_failed); com.seedlessds.app.AppLog.i(TAG, "LB failed $lbId") }
            EVENT_LB_SUBMITTED -> com.seedlessds.app.AppLog.i(TAG, "LB submitted $lbId '$value'")
            EVENT_LB_SCOREBOARD -> {
                scoreboard.value = RaScoreboard(lbId, title ?: "", value ?: "", best ?: "", rank, total)
                com.seedlessds.app.AppLog.i(TAG, "LB scoreboard $lbId rank=$rank/$total val=$value best=$best")
            }
        }
    }

    fun onLbEntries(nonce: Int, result: Int, userIndex: Int, total: Int, entries: Array<String>?, error: String?) {
        if (nonce != lbFetchNonce.get()) return
        if (result != 0) {
            lbEntries.value = emptyList()
            lbEntriesError.value = error?.ifBlank { null } ?: text(R.string.common_couldn_t_load_entries)
            lbEntriesLoading.value = false
            return
        }
        val list = entries?.mapIndexedNotNull { i, line ->
            val f = line.split(0x1F.toChar())
            if (f.size < 5) return@mapIndexedNotNull null
            RaLbEntry(
                rank = f[0].toIntOrNull() ?: 0, user = f[1], display = f[2],
                isYou = (i == userIndex), avatarUrl = f[4].ifBlank { null }
            )
        } ?: emptyList()
        lbEntries.value = list
        lbEntriesError.value = null
        lbEntriesLoading.value = false
    }

    fun refreshLeaderboards() {
        if (!inited) return
        lbLoading.value = true
        exec.execute {
            hasLeaderboards.value = try { RaNative.raHasLeaderboards() } catch (_: Throwable) { false }
            val raw = try { RaNative.raLeaderboardList(LB_GROUPING_TRACKING) } catch (_: Throwable) { null }
            leaderboards.value = parseLeaderboards(raw)
            lbLoading.value = false
        }
    }

    fun fetchLbEntries(lbId: Int, title: String, count: Int = 25, aroundUser: Boolean = false) {
        if (!inited) return
        val nonce = lbFetchNonce.incrementAndGet()
        lbEntriesTitle.value = title
        lbEntries.value = emptyList()
        lbEntriesError.value = null
        lbEntriesLoading.value = true
        exec.execute { RaNative.raFetchLbEntries(lbId, count, aroundUser, nonce) }
    }

    private fun parseLeaderboards(raw: Array<String>?): List<RaLbBucket> {
        if (raw.isNullOrEmpty()) return emptyList()
        data class Row(val bType: Int, val bLabel: String, val lb: RaLeaderboard)
        val rows = raw.mapNotNull { line ->
            val f = line.split(0x1F.toChar())
            if (f.size < 7) return@mapNotNull null
            Row(f[0].toIntOrNull() ?: 0, f[1],
                RaLeaderboard(f[2].toIntOrNull() ?: 0, f[3], f[4], f[5].toIntOrNull() ?: 0, f[6]))
        }
        val order = LinkedHashMap<Int, Pair<String, MutableList<RaLeaderboard>>>()
        for (r in rows) order.getOrPut(r.bType) { r.bLabel to mutableListOf() }.second.add(r.lb)
        return order.map { (type, v) -> RaLbBucket(type, if (type in 1..4) lbBucketLabel(type) else v.first.ifBlank { lbBucketLabel(type) }, v.second) }
    }

    private fun lbBucketLabel(type: Int) = when (type) {
        1 -> text(R.string.ra_inactive); 2 -> text(R.string.common_active_4); 3 -> text(R.string.ra_unsupported); 4 -> text(R.string.common_all); else -> text(R.string.common_leaderboards)
    }

    private fun securePrefs(): SharedPreferences? = runCatching {
        val ctx = appCtx ?: return null
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "ra_creds", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    private fun saveCreds(user: String, token: String) =
        securePrefs()?.edit()?.putString("user", user)?.putString("token", token)?.apply()

    private fun loadCreds(): Pair<String?, String?> {
        val p = securePrefs() ?: return null to null
        return p.getString("user", null) to p.getString("token", null)
    }

    private fun clearCreds() = securePrefs()?.edit()?.clear()?.apply()
}
