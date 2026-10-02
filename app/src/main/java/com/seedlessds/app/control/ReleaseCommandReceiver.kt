package com.seedlessds.app.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ReleaseCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != context.packageName + ".CONTROL") return
        val request = intent.getStringExtra("request_id") ?: ""
        val command = intent.getStringExtra("command") ?: ""
        val result = JSONObject().put("protocol", 1).put("request_id", request)
            .put("command", command).put("pid", Process.myPid())
        val pending = goAsync()
        if (!busy.compareAndSet(false, true)) {
            pending.resultCode = 1
            pending.resultData = result.put("ok", false).put("error", "Command busy").toString()
            pending.finish()
            return
        }
        executor.execute {
            try {
                require(request.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid request identifier" }
                val property = ProcessBuilder("/system/bin/getprop", "debug.seedlessds.commands").start()
                val enabled = property.inputStream.bufferedReader().use { it.readText().trim() }
                check(property.waitFor() == 0 && enabled == "1") { "Release commands are disabled" }
                val payload = intent.getStringExtra("args") ?: "{}"
                require(payload.length <= 16384) { "Command payload too large" }
                result.put("status", RuntimeControl.command(context.applicationContext, command, JSONObject(payload)))
                result.put("ok", true)
                pending.resultCode = 0
            } catch (error: Exception) {
                result.put("ok", false).put("error", error.message ?: error.javaClass.simpleName)
                pending.resultCode = 1
            } finally {
                val json = result.toString()
                Log.i("SeedlessControl", json)
                pending.resultData = json
                busy.set(false)
                pending.finish()
            }
        }
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor { work -> Thread(work, "seedless-control") }
        private val busy = AtomicBoolean(false)
    }
}
