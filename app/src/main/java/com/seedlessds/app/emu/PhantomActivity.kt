package com.seedlessds.app.emu

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import android.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.seedlessds.app.R
import java.io.File

class PhantomCoordinatorService : Service() {
    override fun onBind(intent: Intent): IBinder? = intent.extras?.getBinder(PhantomPlayer.OWNER)
}

abstract class PhantomPlayerService : Service() {
    override fun onBind(intent: Intent): IBinder = Binder()
}

class PhantomPrimaryService : PhantomPlayerService()
class PhantomPeerService : PhantomPlayerService()

class PhantomActivity : ComponentActivity() {
    companion object {
        const val ROM_NAME = "phantom_rom_name"
    }
    private val saves by lazy { PhantomSaves(this) }
    private val activityManager get() = getSystemService(ACTIVITY_SERVICE) as ActivityManager
    private val rom get() = requireNotNull(intent.data)
    private val romName get() = requireNotNull(intent.getStringExtra(ROM_NAME))
    private val main = Handler(Looper.getMainLooper())
    private val inbox = Messenger(Handler(Looper.getMainLooper()) { msg -> receive(msg); true })
    private val players = arrayOfNulls<Messenger>(2)
    private val bindings = arrayOfNulls<ServiceConnection>(2)
    private val tasks = intArrayOf(-1, -1)
    private val launched = BooleanArray(2)
    private var directory: File? = null
    private val visible = BooleanArray(2)
    private val stopped = BooleanArray(2)
    private var closing = false
    private var dialog: AlertDialog? = null
    private var reason: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = FrameLayout(this)
        content.addView(ProgressBar(this), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(content)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { end() }
        })
        lifecycleScope.launch {
            try {
                checkIdle()
                val pending = withContext(Dispatchers.IO) { saves.pending(rom) }
                if (pending != null) {
                    directory = pending
                    showResult()
                } else {
                    directory = withContext(Dispatchers.IO) { saves.prepare(rom, romName) }
                    launchPlayer(0)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e.localizedMessage) }
        }
    }

    private fun checkIdle() {
        val emulators = setOf(GameActivity::class.java.name, PhantomPrimaryActivity::class.java.name, PhantomPeerActivity::class.java.name)
        val busy = if (Build.VERSION.SDK_INT >= 23) {
            activityManager.appTasks.any { it.taskInfo.topActivity?.className in emulators }
        } else {
            @Suppress("DEPRECATION")
            activityManager.getRunningTasks(Int.MAX_VALUE).any { task ->
                task.topActivity?.let { it.packageName == packageName && it.className in emulators } == true
            }
        }
        check(!MultiplayerSession.active && !busy) {
            getString(R.string.phantom_busy)
        }
    }

    private fun launchPlayer(role: Int) {
        val component = if (role == 0) PhantomPrimaryActivity::class.java else PhantomPeerActivity::class.java
        val args = Bundle().apply {
            putString(PhantomPlayer.SESSION, checkNotNull(directory).name)
            putBinder(PhantomPlayer.OWNER, inbox.binder)
            putString(EmulatorActivity.EXTRA_ROM_URI, rom.toString())
        }
        val launch = Intent(this, component).putExtras(args).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = if (Build.VERSION.SDK_INT >= 26) ActivityOptions.makeBasic().apply {
            launchDisplayId = ContextCompat.getDisplayOrDefault(this@PhantomActivity).displayId
        }.toBundle() else null
        startActivity(launch, options)
        launched[role] = true
        main.postDelayed({
            if (launched[role] && players[role] == null && !isFinishing) {
                reason = getString(R.string.phantom_unavailable)
                launched[role] = false
                end()
            }
        }, 20000)
    }

    private fun receive(msg: Message) {
        val role = msg.arg1
        if (role !in players.indices || directory?.name != msg.data.getString(PhantomPlayer.SESSION)) return
        when (msg.what) {
            PhantomPlayer.ATTACH -> {
                val endpoint = msg.replyTo ?: return
                players[role] = endpoint
                tasks[role] = msg.arg2
                val death = IBinder.DeathRecipient { main.post {
                    if (players[role] === endpoint) {
                        if (!stopped[role] && !closing) reason = getString(R.string.phantom_unavailable)
                        exited(role)
                    }
                } }
                try { endpoint.binder.linkToDeath(death, 0) } catch (_: android.os.RemoteException) { death.binderDied() }
                if (!closing && !bindPlayer(role)) {
                    reason = getString(R.string.phantom_unavailable)
                    end()
                    return
                }
                if (closing) stop(role)
            }
            PhantomPlayer.READY -> if (role == 0 && !closing && !launched[1]) {
                try { launchPlayer(1) } catch (e: Exception) { reason = e.localizedMessage; end() }
            }
            PhantomPlayer.VISIBLE, PhantomPlayer.HIDDEN -> {
                visible[role] = msg.what == PhantomPlayer.VISIBLE
                for (endpoint in players.filterNotNull()) {
                    try {
                        endpoint.send(Message.obtain(null, PhantomPlayer.RUN, if (visible.any { it }) 1 else 0, 0))
                    } catch (_: android.os.RemoteException) {
                        end()
                    }
                }
            }
            PhantomPlayer.SWITCH -> if (!closing) bringToFront(tasks[1 - role])
            PhantomPlayer.CLOSE -> if (!closing) {
                bringToFront(taskId)
                end()
            }
            PhantomPlayer.ERROR -> { reason = msg.data.getString("detail"); end() }
            PhantomPlayer.CLOSED -> {
                stopped[role] = true
                unbindPlayer(role)
                closing = true
                stop(1 - role)
            }
        }
    }

    private fun exited(role: Int) {
        unbindPlayer(role)
        launched[role] = false
        players[role] = null
        tasks[role] = -1
        end()
    }

    private fun stop(role: Int) {
        try { players[role]?.send(Message.obtain(null, PhantomPlayer.CLOSE)) }
        catch (_: android.os.RemoteException) {
            unbindPlayer(role)
            players[role] = null
            launched[role] = false
        }
    }

    private fun bindPlayer(role: Int): Boolean {
        if (bindings[role] != null) return true
        val type = if (role == 0) PhantomPrimaryService::class.java else PhantomPeerService::class.java
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) = Unit
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        if (!bindService(Intent(this, type), connection, BIND_AUTO_CREATE)) return false
        bindings[role] = connection
        return true
    }

    private fun unbindPlayer(role: Int) {
        val connection = bindings[role] ?: return
        bindings[role] = null
        unbindService(connection)
    }

    private fun end() {
        closing = true
        players.indices.forEach(::stop)
        if (launched.none { it }) {
            if (directory == null) finish() else showResult()
        }
    }

    private fun bringToFront(target: Int) {
        activityManager.appTasks.firstOrNull {
            @Suppress("DEPRECATION") val id = it.taskInfo.id
            id == target
        }?.moveToFront()
    }

    private fun showResult() {
        if (isFinishing || dialog?.isShowing == true) return
        bringToFront(taskId)
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.phantom_result_title)
            .setMessage(listOfNotNull(reason, getString(R.string.phantom_result_body)).joinToString("\n\n"))
            .setPositiveButton(R.string.phantom_keep) { _, _ -> finalizeSaves(true) }
            .setNegativeButton(R.string.phantom_discard) { _, _ -> finalizeSaves(false) }
            .setNeutralButton(android.R.string.cancel) { _, _ -> finish() }
            .setCancelable(false).show()
    }

    private fun finalizeSaves(keep: Boolean) {
        val folder = directory ?: return
        lifecycleScope.launch {
            try {
                checkIdle()
                withContext(Dispatchers.IO) { saves.finish(folder, keep) }
                Toast.makeText(this@PhantomActivity, if (keep) R.string.phantom_kept else R.string.phantom_discarded, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e.localizedMessage) }
        }
    }

    private fun fail(message: String?) {
        AlertDialog.Builder(this).setTitle(R.string.phantom_title).setMessage(message ?: getString(R.string.phantom_unavailable))
            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }.setOnCancelListener { finish() }.show()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        players.indices.forEach { role ->
            stop(role)
            unbindPlayer(role)
        }
        super.onDestroy()
    }
}
