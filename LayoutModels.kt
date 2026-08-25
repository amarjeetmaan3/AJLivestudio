package com.amarjeetmaan.ajlivestudio.ui.layout

/**
 * Layout presets combining a "primary" zone (Camera) and "secondary" zone
 * (Screen Share). Since Screen Share isn't wired into the broadcast yet
 * (Phase 7 README), the secondary zone renders as a labeled placeholder —
 * this screen lets you plan/design the layout now so wiring it in later
 * is just swapping the placeholder for the real screen-share texture.
 */
enum class LayoutPreset(val label: String) {
    FULL_CAMERA("Camera full"),
    SPLIT_50_50("50 / 50"),
    SPLIT_70_30("70 / 30"),
    SPLIT_30_70("30 / 70"),
    PIP_TOP_LEFT("PiP top-left"),
    PIP_TOP_RIGHT("PiP top-right"),
    PIP_BOTTOM_LEFT("PiP bottom-left"),
    PIP_BOTTOM_RIGHT("PiP bottom-right"),
}
