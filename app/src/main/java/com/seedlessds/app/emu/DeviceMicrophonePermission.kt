package com.seedlessds.app.emu

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.seedlessds.app.R
import com.seedlessds.core.SeedlessCore

@Keep
internal class DeviceMicrophonePermission(private val activity: ComponentActivity) {
    var pending = false
        private set
    private var deferred = false
    private var settingsOffered = false
    private var settingsDialog: AlertDialog? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        pending = false
        if (allowed) {
            settingsOffered = false
            if (game === this) SeedlessCore.retryMicrophonePermission()
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            offerSettings()
        }
    }

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                val registry = activity.savedStateRegistry
                pending = registry.consumeRestoredStateForKey(permission)?.getBoolean("pending") == true
                registry.registerSavedStateProvider(permission) { Bundle().apply { putBoolean("pending", pending) } }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                detachGame()
                settingsDialog?.dismiss()
            }
        })
    }

    fun attachGame() {
        game = this
        SeedlessCore.retryMicrophonePermission()
    }

    fun detachGame() {
        if (game === this) game = null
    }

    fun resumeGame() {
        if (game === this && (deferred || granted())) {
            deferred = false
            SeedlessCore.retryMicrophonePermission()
        }
    }

    private fun granted() = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    fun requestFromSettings() {
        settingsOffered = false
        request()
    }

    private fun request() {
        if (activity.isFinishing || activity.isDestroyed || pending || settingsDialog != null) return
        if (granted()) {
            if (game === this) SeedlessCore.retryMicrophonePermission()
            return
        }
        if (settingsOffered) return
        pending = true
        launcher.launch(permission)
    }

    private fun offerSettings() {
        if (activity.isFinishing || activity.isDestroyed || settingsOffered) return
        settingsOffered = true
        settingsDialog = AlertDialog.Builder(activity)
            .setMessage(R.string.microphone_permission_settings)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.common_settings) { _, _ ->
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.packageName)))
            }
            .setOnDismissListener { settingsDialog = null }
            .show()
    }

    companion object {
        private const val permission = Manifest.permission.RECORD_AUDIO
        @Volatile private var game: DeviceMicrophonePermission? = null

        @Keep @JvmStatic fun allowDeviceCapture(): Boolean {
            val owner = game ?: return false
            if (owner.granted()) return true
            owner.activity.runOnUiThread {
                if (game === owner && SettingsRepo.current.micEnabled) {
                    if (owner.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) owner.request()
                    else if (!owner.pending) owner.deferred = true
                }
            }
            return false
        }
    }
}
