package com.amarjeetmaan.ajlivestudio.ui.overlay

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amarjeetmaan.ajlivestudio.ui.theme.GoldPrimary
import com.amarjeetmaan.ajlivestudio.ui.theme.NavySurface

/**
 * Bottom sheet: add / manage overlays. Positioning is done by dragging the
 * item directly on the preview (see OverlayLayer) — this panel is for
 * adding new overlays and adjusting opacity/scale/visibility.
 */
@Composable
fun OverlayPanel(
    viewModel: OverlayViewModel,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NavySurface) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Overlays", style = MaterialTheme.typography.titleLarge)
            Text(
                "Drag an overlay on the preview to reposition it",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            AddOverlayControls(viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            if (viewModel.items.isEmpty()) {
                Text(
                    "No overlays yet",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(viewModel.items, key = { it.id }) { item ->
                        OverlayRow(item = item, viewModel = viewModel)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AddOverlayControls(viewModel: OverlayViewModel) {
    var textInput by remember { mutableStateOf("") }
    var lowerThirdTitle by remember { mutableStateOf("") }
    var lowerThirdSubtitle by remember { mutableStateOf("") }
    var showTextField by remember { mutableStateOf(false) }
    var showLowerThirdFields by remember { mutableStateOf(false) }
    var showWebField by remember { mutableStateOf(false) }
    var webUrlInput by remember { mutableStateOf("") }

    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.addLogo(it) } }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text("+ Logo") }

        OutlinedButton(onClick = { showTextField = !showTextField }) { Text("+ Text") }
        OutlinedButton(onClick = { showLowerThirdFields = !showLowerThirdFields }) { Text("+ Lower third") }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row {
        OutlinedButton(onClick = { showWebField = !showWebField }) { Text("+ Web overlay") }
    }

    if (showWebField) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = webUrlInput,
                onValueChange = { webUrlInput = it },
                placeholder = { Text("scoreboard.example.com") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                viewModel.addWeb(webUrlInput)
                webUrlInput = ""
                showWebField = false
            }) { Text("Add", color = GoldPrimary) }
        }
        Text(
            "Your existing Firebase overlay.html can go here too",
            style = MaterialTheme.typography.labelSmall,
        )
    }

    if (showTextField) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Overlay text") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                viewModel.addText(textInput)
                textInput = ""
                showTextField = false
            }) { Text("Add", color = GoldPrimary) }
        }
    }

    if (showLowerThirdFields) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = lowerThirdTitle,
            onValueChange = { lowerThirdTitle = it },
            placeholder = { Text("Title (e.g. AMARJEET MAAN)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = lowerThirdSubtitle,
            onValueChange = { lowerThirdSubtitle = it },
            placeholder = { Text("Subtitle (e.g. LIVE FROM AMARGARH)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = {
                viewModel.addLowerThird(lowerThirdTitle, lowerThirdSubtitle)
                lowerThirdTitle = ""
                lowerThirdSubtitle = ""
                showLowerThirdFields = false
            },
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
            shape = RoundedCornerShape(10.dp)
        ) { Text("Add lower third") }
    }
}

@Composable
private fun OverlayRow(item: OverlayItem, viewModel: OverlayViewModel) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (item.type) {
                    OverlayType.LOGO -> "Logo"
                    OverlayType.TEXT -> item.text.ifBlank { "Text" }
                    OverlayType.LOWER_THIRD -> item.text.ifBlank { "Lower third" }
                    OverlayType.WEB -> "Web: ${item.webUrl.take(28)}"
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.toggleVisible(item.id) }) {
                Icon(
                    if (item.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = "Toggle visibility"
                )
            }
            IconButton(onClick = { viewModel.remove(item.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Opacity", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
            Slider(
                value = item.opacity,
                onValueChange = { viewModel.updateOpacity(item.id, it) },
                colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary),
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
            Slider(
                value = item.scale,
                onValueChange = { viewModel.updateScale(item.id, it) },
                valueRange = 0.2f..3f,
                colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary),
                modifier = Modifier.weight(1f)
            )
        }
        if (item.type == OverlayType.WEB) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Corners", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                Slider(
                    value = item.webCornerRadiusDp.toFloat(),
                    onValueChange = { viewModel.updateWebCornerRadius(item.id, it.toInt()) },
                    valueRange = 0f..40f,
                    colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.webBordered,
                        onCheckedChange = { viewModel.toggleWebBorder(item.id) },
                        colors = CheckboxDefaults.colors(checkedColor = GoldPrimary)
                    )
                    Text("Border", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { viewModel.reloadWebOverlays() }) {
                    Text("Refresh", color = GoldPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
