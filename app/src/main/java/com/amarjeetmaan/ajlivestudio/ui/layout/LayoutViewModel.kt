package com.amarjeetmaan.ajlivestudio.ui.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class LayoutViewModel : ViewModel() {
    var preset by mutableStateOf(LayoutPreset.FULL_CAMERA)
        private set

    fun selectPreset(newPreset: LayoutPreset) {
        preset = newPreset
    }
}
