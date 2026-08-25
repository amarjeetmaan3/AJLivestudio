package com.amarjeetmaan.ajlivestudio.ui.scene

import java.util.UUID

/**
 * A Scene is a saved snapshot of which overlays should be visible.
 * Switching scenes bulk-toggles overlay visibility (see SceneViewModel).
 *
 * Video-source switching (Camera vs Screen Share) isn't part of a scene
 * yet — Screen Share isn't wired into the broadcast pipeline (Phase 7
 * README), so a scene can't meaningfully promise "shows the screen" until
 * that's done. Scenes are overlay presets for now; once screen share is
 * wired, extending Scene with a videoSource field is a small follow-up.
 */
data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val visibleOverlayIds: Set<String>,
    val isBuiltIn: Boolean = false,
)

object DefaultScenes {
    fun cameraOnly() = Scene(id = "builtin_camera", name = "Camera", visibleOverlayIds = emptySet(), isBuiltIn = true)
}
