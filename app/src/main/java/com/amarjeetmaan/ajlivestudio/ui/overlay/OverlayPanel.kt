package com.amarjeetmaan.ajlivestudio.ui.overlay

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayPanel(viewModel: OverlayViewModel, onDismiss: () -> Unit) {
    var textInput by remember { mutableStateOf("") }
    var webUrlInput by remember { mutableStateOf("https://") }
    var lowerThirdName by remember { mutableStateOf("") }
    var lowerThirdTitle by remember { mutableStateOf("") }
    
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.addLogo(it) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("Overlays Manager", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Text Option
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Enter Text") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { viewModel.addText(textInput); textInput = "" }) { Text("Add") }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Lower Third Option
            Text("Lower Third", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = lowerThirdName,
                    onValueChange = { lowerThirdName = it },
                    label = { Text("Name") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = lowerThirdTitle,
                    onValueChange = { lowerThirdTitle = it },
                    label = { Text("Title") },
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = { 
                    if(lowerThirdName.isNotBlank()) {
                        viewModel.addLowerThird(lowerThirdName, lowerThirdTitle)
                        lowerThirdName = ""
                        lowerThirdTitle = ""
                    }
                }, 
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Add Lower Third") }
            Spacer(modifier = Modifier.height(16.dp))

            // Logo Option
            Button(onClick = { logoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Pick Logo from Gallery") }
            Spacer(modifier = Modifier.height(16.dp))

            // Web Option
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = webUrlInput,
                    onValueChange = { webUrlInput = it },
                    label = { Text("Web URL") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { viewModel.addWeb(webUrlInput); webUrlInput = "https://" }) { Text("Add") }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Active List
            Text("Active Overlays", style = MaterialTheme.typography.titleMedium)
            viewModel.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("${item.type.name} Overlay", modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = { viewModel.remove(item.id) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}                Button(onClick = { viewModel.addText(textInput); textInput = "" }) { Text("Add") }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Add Lower Third (RESTORED)
            Text("Lower Third", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = lowerThirdName,
                    onValueChange = { lowerThirdName = it },
                    label = { Text("Name") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = lowerThirdTitle,
                    onValueChange = { lowerThirdTitle = it },
                    label = { Text("Title") },
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = { 
                    if(lowerThirdName.isNotBlank()) {
                        viewModel.addLowerThird(lowerThirdName, lowerThirdTitle)
                        lowerThirdName = ""
                        lowerThirdTitle = ""
                    }
                }, 
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Add Lower Third")
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Add Logo
            Button(onClick = { logoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Pick Logo from Gallery") }
            Spacer(modifier = Modifier.height(16.dp))

            // Add Web Widget
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = webUrlInput,
                    onValueChange = { webUrlInput = it },
                    label = { Text("Web URL") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { viewModel.addWeb(webUrlInput); webUrlInput = "https://" }) { Text("Add") }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Active Overlays List
            Text("Active Overlays", style = MaterialTheme.typography.titleMedium)
            viewModel.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("${item.type.name} Overlay", modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = { viewModel.remove(item.id) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
