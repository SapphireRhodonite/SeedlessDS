package com.seedlessds.app.emu

import android.content.Context
import org.json.JSONObject

data class MultiplayerProfile(
    val nickname: String,
    val message: String,
    val colour: Int,
    val symbol: String,
    val birthdayDay: Int,
    val birthdayMonth: Int,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", nickname)
        .put("message", message)
        .put("colour", colour)
        .put("symbol", symbol)

    companion object {
        const val NICKNAME_LENGTH = 10
        const val MESSAGE_LENGTH = 26
        val SYMBOLS = listOf("", "star", "heart", "note", "bolt", "moon", "crown", "smile", "flower", "leaf", "diamond", "pad")

        fun load(context: Context): MultiplayerProfile {
            val settings = SettingsRepo.global
            val preferences = context.applicationContext.getSharedPreferences("seedless_multiplayer", Context.MODE_PRIVATE)
            return MultiplayerProfile(
                nickname = settings.firmwareNick.ifBlank { "Player" }.take(NICKNAME_LENGTH),
                message = preferences.getString("message", "Hello!").orEmpty().take(MESSAGE_LENGTH),
                colour = settings.firmwareColor.coerceIn(0, 15),
                symbol = preferences.getString("symbol", "star").takeIf { it in SYMBOLS }.orEmpty(),
                birthdayDay = settings.bdayDay.coerceIn(1, 31),
                birthdayMonth = settings.bdayMonth.coerceIn(1, 12),
            )
        }

        fun save(context: Context, profile: MultiplayerProfile): Boolean {
            val nickname = profile.nickname.trim().take(NICKNAME_LENGTH)
            if (nickname.isEmpty()) return false
            SettingsRepo.saveGlobal(context.applicationContext, SettingsRepo.global.copy(
                firmwareNick = nickname,
                firmwareColor = profile.colour.coerceIn(0, 15),
                bdayDay = profile.birthdayDay,
                bdayMonth = profile.birthdayMonth,
            ), SettingsRepo.current != SettingsRepo.global)
            SettingsRepo.flush(context.applicationContext)
            return context.applicationContext.getSharedPreferences("seedless_multiplayer", Context.MODE_PRIVATE).edit()
                .putString("message", profile.message.take(MESSAGE_LENGTH))
                .putString("symbol", profile.symbol.takeIf { it in SYMBOLS }.orEmpty())
                .commit()
        }

        fun fromJson(value: JSONObject): MultiplayerProfile = MultiplayerProfile(
            nickname = value.optString("name").take(NICKNAME_LENGTH),
            message = value.optString("message").take(MESSAGE_LENGTH),
            colour = value.optInt("colour").coerceIn(0, 15),
            symbol = value.optString("symbol").takeIf { it in SYMBOLS }.orEmpty(),
            birthdayDay = 1,
            birthdayMonth = 1,
        )
    }
}
