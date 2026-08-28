package com.amarjeetmaan.ajlivestudio.audio

import android.content.Context
import android.media.AudioManager

enum class AudioRoute {
    BUILTIN_MIC, BLUETOOTH, WIRED_HEADSET
}

class AudioController(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun isMicMuted(): Boolean = audioManager.isMicrophoneMute

    fun setMicMuted(muted: Boolean) {
        audioManager.isMicrophoneMute = muted
    }

    fun currentInputRoute(): AudioRoute {
        return when {
            audioManager.isBluetoothScoOn -> AudioRoute.BLUETOOTH
            audioManager.isWiredHeadsetOn -> AudioRoute.WIRED_HEADSET
            else -> AudioRoute.BUILTIN_MIC
        }
    }

    fun startBluetoothScoIfAvailable() {
        try {
            audioManager.startBluetoothSco()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
