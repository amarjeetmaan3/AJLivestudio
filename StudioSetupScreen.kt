package com.amarjeetmaan.ajlivestudio.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amarjeetmaan.ajlivestudio.ui.theme.GoldPrimary
import com.amarjeetmaan.ajlivestudio.ui.theme.NavyElevated
import com.amarjeetmaan.ajlivestudio.ui.theme.NavySurface

/**
 * Studio Setup Screen — shown BEFORE the camera opens.
 * User picks resolution / FPS / bitrate / orientation here first.
 * Tapping "Continue to Preview" moves to CameraPreviewScreen.
 */
@Composable
fun StudioSetupScreen(
    viewModel: SetupViewModel = viewModel(),
    onContinueToPreview: (StudioSetupState) -> Unit,
) {
    val state = viewModel.state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavySurface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Studio Setup",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Configure your broadcast before going to preview",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        SetupSection(title = "Resolution") {
            SegmentedRow(
                options = Resolution.entries,
                selected = state.resolution,
                label = { it.label },
                onSelect = viewModel::setResolution
            )
        }

        SetupSection(title = "Frame Rate") {
            SegmentedRow(
                options = FrameRate.entries,
                selected = state.frameRate,
                label = { "${it.label} fps" },
                onSelect = viewModel::setFrameRate
            )
        }

        SetupSection(title = "Bitrate") {
            SegmentedRow(
                options = BitratePreset.entries,
                selected = state.bitrate,
                label = { it.label },
                onSelect = viewModel::setBitrate
            )
        }

        SetupSection(title = "Orientation") {
            SegmentedRow(
                options = StreamOrientation.entries,
                selected = state.orientation,
                label = { it.label },
                onSelect = viewModel::setOrientation
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onContinueToPreview(state) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Continue to Preview", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SetupSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 18.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NavyElevated),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) GoldPrimary else NavyElevated)
                    .then(
                        Modifier.clickableSimple { onSelect(option) }
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected)
                        androidx.compose.ui.graphics.Color(0xFF0B1330)
                    else
                        androidx.compose.ui.graphics.Color(0xFFB9C0DC),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
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
