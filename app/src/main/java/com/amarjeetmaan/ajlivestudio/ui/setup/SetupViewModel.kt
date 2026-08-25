package com.amarjeetmaan.ajlivestudio.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class SetupViewModel : ViewModel() {

    var state by mutableStateOf(StudioSetupState())
        private set

    fun setResolution(resolution: Resolution) {
        state = state.copy(resolution = resolution)
    }

    fun setFrameRate(frameRate: FrameRate) {
        state = state.copy(frameRate = frameRate)
    }

    fun setBitrate(bitrate: BitratePreset) {
        state = state.copy(bitrate = bitrate)
    }

    fun setOrientation(orientation: StreamOrientation) {
        state = state.copy(orientation = orientation)
    }
}
