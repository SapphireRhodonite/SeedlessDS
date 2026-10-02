package com.seedlessds.app.ra

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

object RaSound {
    private const val SR = 44100

    private val pcm: ByteArray by lazy { synth() }

    fun playUnlock() {
        try {
            val data = pcm
            @Suppress("DEPRECATION")
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC, SR, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, data.size, AudioTrack.MODE_STATIC
            )
            track.write(data, 0, data.size)
            track.setNotificationMarkerPosition(data.size / 2)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { runCatching { t.stop(); t.release() } }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        } catch (_: Throwable) { }
    }

    private fun synth(): ByteArray {
        val n1f = 659.25; val n1d = 0.13
        val n2f = 987.77; val n2d = 0.26
        val total = ((n1d + n2d) * SR).toInt()
        val out = ShortArray(total)
        var i = 0
        fun note(freq: Double, dur: Double) {
            val len = (dur * SR).toInt()
            for (k in 0 until len) {
                val t = k.toDouble() / SR
                val env = minOf(1.0, t / 0.008) * Math.exp(-t * 6.0)
                val s = Math.sin(2.0 * Math.PI * freq * t) * env * 0.5
                if (i < total) out[i++] = (s * 32767.0).toInt().toShort()
            }
        }
        note(n1f, n1d)
        note(n2f, n2d)
        val bytes = ByteArray(out.size * 2)
        for (j in out.indices) {
            val v = out[j].toInt()
            bytes[j * 2] = (v and 0xFF).toByte()
            bytes[j * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
