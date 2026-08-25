package com.amarjeetmaan.ajlivestudio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.amarjeetmaan.ajlivestudio.ui.navigation.AppNavigation
import com.amarjeetmaan.ajlivestudio.ui.theme.AJLiveStudioTheme
import com.amarjeetmaan.ajlivestudio.ui.theme.NavyDeep

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AJLiveStudioTheme {
                RequireCameraAndMicPermissions {
                    AppNavigation()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RequireCameraAndMicPermissions(content: @androidx.compose.runtime.Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    // Bluetooth mic (Use Bluetooth mic button) needs BLUETOOTH_CONNECT on API 31+.
    // Not gating app launch on this — it's requested but optional.
    val needsBluetoothPermission = android.os.Build.VERSION.SDK_INT >= 31

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        cameraGranted = results[Manifest.permission.CAMERA] ?: cameraGranted
        micGranted = results[Manifest.permission.RECORD_AUDIO] ?: micGranted
    }

    if (cameraGranted && micGranted) {
        content()
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(NavyDeep),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "AJ Live Studio needs Camera and Microphone access to broadcast.",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                Button(onClick = {
                    val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    if (needsBluetoothPermission) perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                    launcher.launch(perms.toTypedArray())
                }) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}
