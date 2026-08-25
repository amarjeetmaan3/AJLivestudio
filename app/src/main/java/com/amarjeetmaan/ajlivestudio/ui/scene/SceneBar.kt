package com.amarjeetmaan.ajlivestudio.ui.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayViewModel
import com.amarjeetmaan.ajlivestudio.ui.theme.GoldPrimary
import com.amarjeetmaan.ajlivestudio.ui.theme.NavyElevated

/**
 * Horizontal strip of scene chips. Tap to switch instantly (bulk-toggles
 * overlay visibility). "+" saves whatever overlays are currently visible
 * as a new named scene.
 */
@Composable
fun SceneBar(
    sceneViewModel: SceneViewModel,
    overlayViewModel: OverlayViewModel,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var newSceneName by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sceneViewModel.scenes.forEach { scene ->
            val isActive = scene.id == sceneViewModel.activeSceneId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isActive) GoldPrimary else NavyElevated)
                    .clickableSimple { sceneViewModel.switchTo(scene, overlayViewModel) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    scene.name,
                    color = if (isActive)
                        androidx.compose.ui.graphics.Color(0xFF0B1330)
                    else
                        Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        IconButton(onClick = { showSaveDialog = true }) {
            Icon(Icons.Filled.Add, contentDescription = "Save current as scene", tint = Color.White)
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save scene") },
            text = {
                OutlinedTextField(
                    value = newSceneName,
                    onValueChange = { newSceneName = it },
                    placeholder = { Text("e.g. Sponsor, Break") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    sceneViewModel.saveCurrentAsScene(newSceneName, overlayViewModel)
                    newSceneName = ""
                    showSaveDialog = false
                }) { Text("Save", color = GoldPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Modifier.clickableSimple(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            indication = null,
            interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        ) { onClick() }
    )
