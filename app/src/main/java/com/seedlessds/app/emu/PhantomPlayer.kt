package com.seedlessds.app.emu

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.seedlessds.app.R
import com.seedlessds.app.filesystem.PathCache
import com.seedlessds.core.SeedlessCore
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.UUID

internal class PhantomPlayer(private val activity: EmulatorActivity, val primary: Boolean) {
    companion object {
        const val VISIBLE = 7
        const val HIDDEN = 8
        const val RUN = 9

        const val ATTACH = 1
        const val READY = 2
        const val SWITCH = 3
        const val CLOSE = 4
        const val CLOSED = 5
        const val ERROR = 6
        const val SESSION = "phantom_session"
        const val OWNER = "phantom_owner"
    }

    private val id = UUID.fromString(activity.intent.getStringExtra(SESSION))
    private val coordinator = activity.intent.extras?.getBinder(OWNER)?.let(::Messenger)
    private val folder = File(File(File(activity.noBackupFilesDir, "phantom"), id.toString()), if (primary) "primary" else "peer")
    private val mutex = Mutex()
    private var lease: RandomAccessFile? = null
    private val main = Handler(Looper.getMainLooper())
    private val attached = CompletableDeferred<Unit>()
    private var bound = false
    private val ownerConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (service != coordinator?.binder || !service.isBinderAlive) {
                disconnected()
                return
            }
            notify(ATTACH)
            notify(if (visible) VISIBLE else HIDDEN)
            attached.complete(Unit)
        }

        override fun onServiceDisconnected(name: ComponentName) = disconnected()
        override fun onBindingDied(name: ComponentName) = disconnected()
        override fun onNullBinding(name: ComponentName) = disconnected()
    }
    private var visible = false
    var running = false
        private set
    var closing = false
        private set

    fun visibility(value: Boolean) {
        visible = value
        if (attached.isCompleted) notify(if (value) VISIBLE else HIDDEN)
    }

    private val endpoint = Messenger(Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            CLOSE -> disconnected()
            RUN -> {
                running = msg.arg1 == 1
                activity.refreshPauseState()
            }
        }
        true
    })

    fun notify(what: Int, detail: String? = null) {
        val msg = Message.obtain(null, what, if (primary) 0 else 1, activity.taskId)
        msg.data = Bundle().apply { putString(SESSION, id.toString()); putString("detail", detail) }
        msg.replyTo = endpoint
        try {
            coordinator?.send(msg)
        } catch (_: RemoteException) {
            main.post { disconnected() }
        }
    }

    fun attach() {
        try {
            val service = Intent(activity, PhantomCoordinatorService::class.java).setAction(id.toString())
            service.putExtras(Bundle().apply { putBinder(OWNER, checkNotNull(coordinator).binder) })
            bound = activity.applicationContext.bindService(service, ownerConnection, Context.BIND_AUTO_CREATE)
            if (!bound) disconnected()
        } catch (_: Exception) {
            disconnected()
        }
    }

    private fun disconnected() {
        closing = true
        attached.cancel()
        activity.finish()
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        attached.await()
        mutex.withLock {
            check(coordinator?.binder?.isBinderAlive == true && !closing)
            val filename = File(folder.parentFile, "filename").readText()
            check(File(folder, "user/backup/" + filename).isFile)
            val lock = RandomAccessFile(File(folder, "owner"), "rw")
            try {
                check(lock.channel.tryLock() != null) { activity.getString(R.string.phantom_busy) }
                val address = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
                val index = NetworkInterface.getByInetAddress(address).index
                val identity = ByteBuffer.allocate(8).putLong(id.leastSignificantBits).array().copyOf(6)
                identity[0] = 2
                if (!primary) identity[5] = (identity[5].toInt() xor 1).toByte()
                val name = if (primary) "Principal" else "Phantom"
                val local = if (primary) address.address else byteArrayOf(127, 0, 0, 2)
                val code = SeedlessCore.multiplayerStart(if (primary) 1 else 2, name.toByteArray(),
                    local, address.address, index, identity, 2)
                check(code == 0) { activity.getString(R.string.phantom_link_error, code) }
                lease = lock
                PathCache.setDataRoot(folder)
            } catch (e: Throwable) {
                lock.close()
                throw e
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                check(SeedlessCore.multiplayerStop() == 0)
            } finally {
                try {
                    lease?.close()
                    lease = null
                    notify(CLOSED)
                    if (bound) activity.applicationContext.unbindService(ownerConnection)
                } finally {
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        }
    }
}

class PhantomPrimaryActivity : EmulatorActivity()
class PhantomPeerActivity : EmulatorActivity()
