package com.amarjeetmaan.ajlivestudio.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

enum class AudioRoute(val label: String) {
    BUILT_IN("Built-in mic"),
    WIRED("Wired mic"),
    BLUETOOTH("Bluetooth mic"),
    UNKNOWN("Unknown"),
}

/**
 * Wraps Android's AudioManager for mic mute + input-route detection.
 *
 * Mic mute uses AudioManager.setMicrophoneMute(), the same system-level
 * toggle apps like WhatsApp/Zoom use — it mutes the mic input at the OS
 * level, before StreamPack's own capture ever reads it. This is reliable
 * regardless of which StreamPack version is resolved, unlike guessing at
 * an internal audio-source mute API.
 *
 * IMPORTANT: this mute is process-wide while AJ Live Studio holds audio
 * focus. Always un-mute in onStop/onDestroy so it doesn't leak into other
 * apps if the user backgrounds AJ Live Studio while muted.
 */
class AudioController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun setMicMuted(muted: Boolean) {
        audioManager.isMicrophoneMute = muted
    }

    fun isMicMuted(): Boolean = audioManager.isMicrophoneMute

    /**
     * Best-effort read of the current input route. Reflects whichever
     * input device Android is currently prioritizing (Bluetooth SCO /
     * wired headset mic / built-in), based on connected devices — this is
     * informational for the UI, not a hard guarantee of which device
     * StreamPack's capture ends up bound to on every OEM skin.
     */
    fun currentInputRoute(): AudioRoute {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val hasBluetooth = devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        val hasWired = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        return when {
            hasBluetooth && audioManager.isBluetoothScoOn -> AudioRoute.BLUETOOTH
            hasWired -> AudioRoute.WIRED
            devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC } -> AudioRoute.BUILT_IN
            else -> AudioRoute.UNKNOWN
        }
    }

    fun startBluetoothScoIfAvailable() {
        val hasBluetoothMic = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        if (hasBluetoothMic) {
            audioManager.startBluetoothSco()
        }
    }

    fun stopBluetoothSco() {
        audioManager.stopBluetoothSco()
    }
}
