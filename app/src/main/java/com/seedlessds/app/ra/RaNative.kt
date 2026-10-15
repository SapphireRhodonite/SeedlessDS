package com.seedlessds.app.ra

import com.seedlessds.app.ra.RetroAchievements

import androidx.annotation.Keep

@Keep
object RaNative {
    init { System.loadLibrary("seedless_bridge") }

    external fun raInit(): Boolean
    external fun raSetHost(host: String?)
    external fun raLoginPassword(user: String, pass: String)
    external fun raLoginToken(user: String, token: String)
    external fun raServerResponse(handle: Long, status: Int, body: ByteArray?)
    external fun raLoadGame(consoleId: Int, path: String)
    external fun raLoadGameFd(consoleId: Int, fd: Int)
    external fun raUnloadGame()
    external fun raDoFrame()
    external fun raReset()
    external fun raIdle()
    external fun raLogout()
    external fun raSetToggles(spectator: Boolean, unofficial: Boolean, encore: Boolean)
    external fun raGetToken(): String?
    external fun raGetUsername(): String?
    external fun raGetScore(): Int
    external fun raGetHardcoreScore(): Int
    external fun raRichPresence(): String?
    external fun raUserAvatar(): String?
    external fun raUserAgentClause(): String?
    external fun raGameSummary(): String?

    external fun raAchievementList(category: Int, grouping: Int): Array<String>?

    external fun raHasLeaderboards(): Boolean
    external fun raLeaderboardList(grouping: Int): Array<String>?
    external fun raFetchLbEntries(lbId: Int, count: Int, aroundUser: Boolean, nonce: Int)

    external fun raSerializeProgress(): ByteArray?
    external fun raDeserializeProgress(data: ByteArray?): Boolean

    @JvmStatic fun doServerCall(handle: Long, url: String, postData: String?, contentType: String?) =
        RetroAchievements.serverCall(handle, url, postData, contentType)

    @JvmStatic fun onLoginResult(result: Int, error: String?) = RetroAchievements.onLoginResult(result, error)
    @JvmStatic fun onLoadResult(result: Int, error: String?) = RetroAchievements.onLoadResult(result, error)
    @JvmStatic fun onEvent(type: Int, id: Int, points: Int, title: String?, description: String?, badgeUrl: String?) =
        RetroAchievements.onEvent(type, id, points, title, description, badgeUrl)

    @JvmStatic fun onLbEvent(type: Int, lbId: Int, trackerId: Int, rank: Int, total: Int,
                             title: String?, value: String?, best: String?) =
        RetroAchievements.onLbEvent(type, lbId, trackerId, rank, total, title, value, best)

    @JvmStatic fun onLbEntries(nonce: Int, result: Int, userIndex: Int, total: Int,
                               entries: Array<String>?, error: String?) =
        RetroAchievements.onLbEntries(nonce, result, userIndex, total, entries, error)
}
