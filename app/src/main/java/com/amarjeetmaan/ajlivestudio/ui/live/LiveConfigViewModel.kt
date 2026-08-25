package com.amarjeetmaan.ajlivestudio.ui.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class LiveConfigViewModel : ViewModel() {

    var config by mutableStateOf(RtmpConfig())
        private set

    fun setServerUrl(url: String) {
        config = config.copy(serverUrl = url)
    }

    fun setStreamKey(key: String) {
        config = config.copy(streamKey = key)
    }
}
