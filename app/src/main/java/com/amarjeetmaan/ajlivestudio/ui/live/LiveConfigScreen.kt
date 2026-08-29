package com.amarjeetmaan.ajlivestudio.ui.live

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amarjeetmaan.ajlivestudio.ui.setup.StudioSetupState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveConfigScreen(
    setupState: StudioSetupState,
    onContinue: (RtmpConfig) -> Unit,
    onBack: () -> Unit,
    viewModel: YouTubeConfigViewModel = viewModel()
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YouTube Live Setup") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text("YouTube Broadcast Details", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Broadcast Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.createBroadcast(title, description) },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && !uiState.isLoading
            ) {
                Text(if (uiState.isLoading) "Creating Broadcast..." else "Create Broadcast")
            }

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
            }

            if (uiState.rtmpConfig != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Broadcast Created Successfully!", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onContinue(uiState.rtmpConfig) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Continue to Preview & Go Live")
                }
            }
        }
    }
}
