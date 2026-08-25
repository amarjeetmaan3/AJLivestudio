package com.amarjeetmaan.ajlivestudio.ui.scene

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayViewModel

class SceneViewModel : ViewModel() {

    var scenes by mutableStateOf(listOf(DefaultScenes.cameraOnly()))
        private set

    var activeSceneId by mutableStateOf(DefaultScenes.cameraOnly().id)
        private set

    /** Saves the current overlay visibility as a new named scene. */
    fun saveCurrentAsScene(name: String, overlayViewModel: OverlayViewModel) {
        if (name.isBlank()) return
        val visibleIds = overlayViewModel.items.filter { it.visible }.map { it.id }.toSet()
        val scene = Scene(name = name, visibleOverlayIds = visibleIds)
        scenes = scenes + scene
        activeSceneId = scene.id
    }

    /** Switches to a scene: shows overlays in its set, hides everything else. */
    fun switchTo(scene: Scene, overlayViewModel: OverlayViewModel) {
        activeSceneId = scene.id
        overlayViewModel.items.forEach { item ->
            val shouldBeVisible = scene.id == DefaultScenes.cameraOnly().id
                || item.id in scene.visibleOverlayIds
            if (item.visible != shouldBeVisible) {
                overlayViewModel.toggleVisible(item.id)
            }
        }
    }

    fun deleteScene(scene: Scene) {
        if (scene.isBuiltIn) return
        scenes = scenes.filterNot { it.id == scene.id }
        if (activeSceneId == scene.id) {
            activeSceneId = DefaultScenes.cameraOnly().id
        }
    }
}
