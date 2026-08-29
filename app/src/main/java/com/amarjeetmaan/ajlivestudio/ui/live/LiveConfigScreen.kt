package com.amarjeetmaan.ajlivestudio.ui.live

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amarjeetmaan.ajlivestudio.youtube.YouTubeAuthController
import com.amarjeetmaan.ajlivestudio.youtube.YouTubePrivacy
import com.amarjeetmaan.ajlivestudio.ui.theme.GoldPrimary
import com.amarjeetmaan.ajlivestudio.ui.theme.NavySurface
import com.amarjeetmaan.ajlivestudio.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun LiveConfigScreen(
    rtmpViewModel: LiveConfigViewModel = viewModel(),
    youTubeViewModel: YouTubeConfigViewModel = viewModel(),
    onBack: () -> Unit,
    onContinueToPreview: (RtmpConfig) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavySurface)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp)
    ) {
        Text("YouTube Live", style = MaterialTheme.typography.headlineMedium, color = androidx.compose.ui.graphics.Color.White)
        Spacer(modifier = Modifier.height(24.dp))

        YouTubeDirectSection(youTubeViewModel, onBack, onContinueToPreview)
    }
}

@Composable
private fun ColumnScope.YouTubeDirectSection(
    viewModel: YouTubeConfigViewModel,
    onBack: () -> Unit,
    onContinueToPreview: (RtmpConfig) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val authController = remember { YouTubeAuthController(context) }
    val coroutineScope = rememberCoroutineScope()
    val state = viewModel.state

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authController.handleSignInResult(result.data)
            .onSuccess { account -> viewModel.onSignedIn(account.email) }
            .onFailure { error -> viewModel.onSignInFailed(error.message ?: "Sign-in failed") }
    }

    LaunchedEffect(Unit) {
        authController.lastSignedInAccount(context)?.let { viewModel.onSignedIn(it.email) }
    }

    when (state.flowState) {
        YouTubeFlowState.SIGNED_OUT -> {
            Text(
                "Sign in with the Google account for your YouTube channel.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
                color = androidx.compose.ui.graphics.Color.White
            )
            Text(
                "Needs one-time setup in Google Cloud Console first — see README.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(
                onClick = { signInLauncher.launch(authController.signInIntent()) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Sign in with Google", fontWeight = FontWeight.Bold) }
            state.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    message,
                    color = androidx.compose.ui.graphics.Color(0xFFE23B4E),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            NavButtons(onBack = onBack, continueEnabled = false, onContinue = {})
        }

        YouTubeFlowState.SIGNED_IN, YouTubeFlowState.ERROR -> {
            Text("Signed in as ${state.accountEmail}", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("Broadcast title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldPrimary, cursorColor = GoldPrimary),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldPrimary, cursorColor = GoldPrimary),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                YouTubePrivacy.entries.forEach { privacy ->
                    val selected = state.privacy == privacy
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) GoldPrimary else androidx.compose.ui.graphics.Color(0xFF1C2650))
                            .clickable2 { viewModel.setPrivacy(privacy) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(privacy.label, color = if (selected) androidx.compose.ui.graphics.Color(0xFF0B1330) else androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
            state.errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = androidx.compose.ui.graphics.Color(0xFFE23B4E), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val account = authController.lastSignedInAccount(context) ?: return@Button
                    coroutineScope.launch {
                        val act = activity ?: return@launch
                        val token = authController.fetchAccessToken(act, account)
                        if (token != null) viewModel.createBroadcast(token)
                        else viewModel.onSignedIn(account.email)
                    }
                },
                enabled = state.title.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Create broadcast", fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        }

        YouTubeFlowState.CREATING -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GoldPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Creating broadcast on YouTube…", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }

        YouTubeFlowState.READY -> {
            val result = state.result
            if (result != null) {
                Text("Broadcast created", style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Watch URL: ${result.watchUrl}", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        onContinueToPreview(RtmpConfig(serverUrl = result.ingestUrl, streamKey = result.streamKey))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Continue to Preview", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun NavButtons(onBack: () -> Unit, continueEnabled: Boolean, onContinue: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp)) {
            Text("Back")
        }
        Spacer(modifier = Modifier.width(12.dp))
        Button(
            onClick = onContinue,
            enabled = continueEnabled,
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Continue", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Modifier.clickable2(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            indication = null,
            interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            onClick = onClick
        )
    )
