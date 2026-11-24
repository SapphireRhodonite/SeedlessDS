package com.seedlessds.app.emu

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.seedlessds.app.R
import kotlinx.coroutines.flow.MutableSharedFlow

class MultiplayerActivity : ComponentActivity() {
    private val viewModel: MultiplayerViewModel by viewModels()
    private val externalInfo by lazy { ExternalInfoController(this) }
    private val pad = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    private var permissionResult: (() -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionResult?.invoke()
        permissionResult = null
    }
    private val biosPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            BiosTools.install(this, uris)
            viewModel.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("seedless_multiplayer", Context.MODE_PRIVATE)
        val actions = MultiplayerActions(
            exit = { finish() },
            launchRom = { game ->
                RomLibrary.markPlayed(this, game.entry)
                startActivity(Intent(this, GameActivity::class.java)
                    .putExtra(EmulatorActivity.EXTRA_ROM_URI, game.uri.toString())
                    .putExtra(EmulatorActivity.EXTRA_LOAD_SLOT, -1))
            },
            launchFirmware = {
                startActivity(Intent(this, GameActivity::class.java).putExtra(EmulatorActivity.EXTRA_FIRMWARE_BOOT, true))
            },
            openBiosSettings = { biosPicker.launch(arrayOf("*/*")) },
            openWifiSettings = { runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } },
            requestPermission = { done ->
                if (viewModel.directPermitted()) done() else {
                    permissionResult = done
                    val permission = MultiplayerDirectNetwork.permission()
                    permissionLauncher.launch(if (Build.VERSION.SDK_INT < 33)
                        arrayOf(permission, Manifest.permission.ACCESS_COARSE_LOCATION) else arrayOf(permission))
                }
            },
            permissionPrompted = { preferences.getBoolean("permission_prompted", false) },
            markPermissionPrompted = { preferences.edit().putBoolean("permission_prompted", true).apply() },
        )
        setContent {
            SeedlessTheme {
                var bluetoothNotice by rememberSaveable {
                    mutableStateOf(!MultiplayerSession.active && viewModel.room.value == null && bluetoothEnabled())
                }
                val radioSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    bluetoothNotice = false
                }
                if (bluetoothNotice) {
                    Box(Modifier.fillMaxSize().background(seedless.bg))
                    SeedlessConfirmDialog(
                        title = getString(R.string.mp_bluetooth_title),
                        message = getString(R.string.mp_bluetooth_message),
                        confirmLabel = getString(R.string.mp_bluetooth_turn_off),
                        cancelLabel = getString(R.string.mp_bluetooth_keep_on),
                        onConfirm = {
                            if (!openBluetoothControls { radioSettings.launch(it) }) {
                                Toast.makeText(this, R.string.mp_bluetooth_settings_unavailable, Toast.LENGTH_LONG).show()
                                bluetoothNotice = false
                            }
                        },
                        onDismiss = { bluetoothNotice = false },
                        destructive = false,
                    )
                } else {
                    MultiplayerScreen(viewModel, pad, actions, intent.getStringExtra(EXTRA_JOIN))
                }
            }
        }
    }

    private fun bluetoothEnabled(): Boolean = try {
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
    } catch (_: SecurityException) {
        Settings.Global.getInt(contentResolver, Settings.Global.BLUETOOTH_ON, 0) != 0
    }

    private fun openBluetoothControls(launch: (Intent) -> Unit): Boolean {
        val request = Intent("android.bluetooth.adapter.action.REQUEST_DISABLE")
        val handler = request.resolveActivityInfo(packageManager, PackageManager.MATCH_DEFAULT_ONLY)
        if (handler != null && handler.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
            (handler.permission == null || ContextCompat.checkSelfPermission(this, handler.permission) == PackageManager.PERMISSION_GRANTED)) {
            request.setClassName(handler.packageName, handler.name)
            if (runCatching { launch(request) }.isSuccess) return true
        }
        return runCatching { launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }.isSuccess
    }

    override fun onStart() {
        super.onStart()
        externalInfo.attach()
        externalInfo.setContent { MultiplayerExternalInfo(viewModel) }
    }

    override fun onStop() {
        externalInfo.detach()
        super.onStop()
    }

    companion object {
        const val EXTRA_JOIN = "join"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> return super.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, event.action,
                KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source))
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2 -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) pad.tryEmit(event.keyCode)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
