package com.amarjeetmaan.ajlivestudio.ui.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amarjeetmaan.ajlivestudio.youtube.YouTubeApiClient
import com.amarjeetmaan.ajlivestudio.youtube.YouTubeBroadcastResult
import com.amarjeetmaan.ajlivestudio.youtube.YouTubePrivacy
import kotlinx.coroutines.launch

enum class YouTubeFlowState { SIGNED_OUT, SIGNED_IN, CREATING, READY, ERROR }

data class YouTubeConfigState(
    val flowState: YouTubeFlowState = YouTubeFlowState.SIGNED_OUT,
    val accountEmail: String? = null,
    val title: String = "",
    val description: String = "",
    val privacy: YouTubePrivacy = YouTubePrivacy.UNLISTED,
    val result: YouTubeBroadcastResult? = null,
    val errorMessage: String? = null,
)

class YouTubeConfigViewModel : ViewModel() {

    var state by mutableStateOf(YouTubeConfigState())
        private set

    fun onSignedIn(email: String?) {
        state = state.copy(flowState = YouTubeFlowState.SIGNED_IN, accountEmail = email, errorMessage = null)
    }

    fun onSignInFailed(message: String) {
        state = state.copy(flowState = YouTubeFlowState.SIGNED_OUT, errorMessage = message)
    }

    fun onSignedOut() {
        state = YouTubeConfigState()
    }

    fun setTitle(title: String) { state = state.copy(title = title) }
    fun setDescription(desc: String) { state = state.copy(description = desc) }
    fun setPrivacy(privacy: YouTubePrivacy) { state = state.copy(privacy = privacy) }

    fun createBroadcast(accessToken: String) {
        if (state.title.isBlank()) return
        state = state.copy(flowState = YouTubeFlowState.CREATING, errorMessage = null)
        viewModelScope.launch {
            val client = YouTubeApiClient(accessToken)
            client.createLiveBroadcastAndStream(state.title, state.description, state.privacy)
                .onSuccess { result ->
                    state = state.copy(flowState = YouTubeFlowState.READY, result = result)
                }
                .onFailure { e ->
                    state = state.copy(flowState = YouTubeFlowState.ERROR, errorMessage = e.message ?: "Failed to create broadcast")
                }
        }
    }
}
