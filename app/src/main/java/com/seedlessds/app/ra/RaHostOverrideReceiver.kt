package com.seedlessds.app.ra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class RaHostOverrideReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            context.packageName + ACTION_SET_SUFFIX -> RaHostOverride.validate(intent.getStringExtra(EXTRA_HOST))
                .onSuccess { RetroAchievements.setHostOverride(context, it) }
                .onFailure { Log.w(TAG, "Rejected RAOfflineProxy host: ${it.message}") }
            context.packageName + ACTION_CLEAR_SUFFIX -> RetroAchievements.setHostOverride(context, null)
            else -> Log.w(TAG, "Ignored unsupported action")
        }
    }

    companion object {
        const val ACTION_SET_SUFFIX = ".action.SET_RETROACHIEVEMENTS_HOST_OVERRIDE"
        const val ACTION_CLEAR_SUFFIX = ".action.CLEAR_RETROACHIEVEMENTS_HOST_OVERRIDE"
        const val EXTRA_HOST = "host"
        private const val TAG = "RAHostOverrideReceiver"
    }
}
