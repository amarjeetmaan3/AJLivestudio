package com.amarjeetmaan.ajlivestudio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amarjeetmaan.ajlivestudio.ui.camera.CameraPreviewScreen
import com.amarjeetmaan.ajlivestudio.ui.live.LiveConfigScreen
import com.amarjeetmaan.ajlivestudio.ui.live.RtmpConfig
import com.amarjeetmaan.ajlivestudio.ui.setup.StudioSetupScreen
import com.amarjeetmaan.ajlivestudio.ui.setup.StudioSetupState

private const val ROUTE_SETUP = "setup"
private const val ROUTE_RTMP_CONFIG = "rtmp_config"
private const val ROUTE_PREVIEW = "preview"

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var pendingSetup by remember { mutableStateOf(StudioSetupState()) }
    var pendingRtmpConfig by remember { mutableStateOf(RtmpConfig()) }

    NavHost(navController = navController, startDestination = ROUTE_SETUP) {
        composable(ROUTE_SETUP) {
            StudioSetupScreen(
                onContinueToPreview = { setupState ->
                    pendingSetup = setupState
                    navController.navigate(ROUTE_RTMP_CONFIG)
                }
            )
        }
        composable(ROUTE_RTMP_CONFIG) {
            LiveConfigScreen(
                onBack = { navController.popBackStack() },
                onContinueToPreview = { rtmpConfig ->
                    pendingRtmpConfig = rtmpConfig
                    navController.navigate(ROUTE_PREVIEW)
                }
            )
        }
        composable(ROUTE_PREVIEW) {
            CameraPreviewScreen(
                setupState = pendingSetup,
                rtmpConfig = pendingRtmpConfig,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
