package com.seedlessds.app.emu

import android.system.ErrnoException
import com.seedlessds.app.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

internal class HostRoomDiagnostics {
    private var phase = "selection"
    private var operation = "resolve_game"
    private var failure: String? = null

    fun at(phase: String, operation: String) {
        this.phase = phase
        this.operation = operation
    }

    fun failed(result: Int, native: String? = null) {
        record(native?.let { "$it result=$result" } ?: "operation=$operation code=$result")
    }

    fun failed(error: Throwable) {
        if (error is CancellationException && error !is TimeoutCancellationException) return
        val code = when (error) {
            is ErrnoException -> error.errno.toString()
            is MultiplayerDirectNetwork.ActionFailure -> error.reason.toString()
            is TimeoutCancellationException -> "timeout"
            is SecurityException -> "permission_denied"
            else -> when (operation) {
                "check_wifi_enabled" -> "disabled"
                "check_p2p_permission" -> "permission_denied"
                "check_p2p_support", "p2p_manager", "p2p_channel" -> "unavailable"
                else -> "failed"
            }
        }
        record("operation=$operation code=$code")
    }

    private fun record(detail: String) {
        if (failure == null) failure = "phase=$phase $detail"
    }

    fun write() {
        val detail = failure ?: return
        if (SettingsRepo.global.appLogFile) AppLog.w("HostRoomDiag", detail)
    }
}
