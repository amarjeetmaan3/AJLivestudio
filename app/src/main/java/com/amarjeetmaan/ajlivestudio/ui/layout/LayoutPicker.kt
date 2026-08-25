package com.amarjeetmaan.ajlivestudio.ui.layout

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun LayoutPickerMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (LayoutPreset) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        LayoutPreset.entries.forEach { preset ->
            DropdownMenuItem(
                text = { Text(preset.label) },
                onClick = { onSelect(preset); onDismiss() }
            )
        }
    }
}
