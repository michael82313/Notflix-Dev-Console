package com.example.ui.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

sealed interface AiUiState {
  object Idle : AiUiState
  object Loading : AiUiState
  data class Success(val text: String) : AiUiState
  data class Error(val message: String) : AiUiState
}

class AiAssistantViewModel : ViewModel() {
  private val model by lazy {
    Firebase.ai.generativeModel("gemini-3.8-flash")
  }

  private val _uiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
  val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

  fun generateMoviePitch(title: String, prompt: String) {
    viewModelScope.launch {
      _uiState.value = AiUiState.Loading
      try {
        val response = withTimeout(60_000L) {
          model.generateContent("As a professional Notflix content executive and script doctor, write a compelling marketing synopsis, logline, and genre breakdown for a movie titled '$title'. Additional prompt: $prompt")
        }
        val text = response.text
        if (!text.isNullOrBlank()) {
          _uiState.value = AiUiState.Success(text)
        } else {
          _uiState.value = AiUiState.Error("Gemini returned an empty response.")
        }
      } catch (e: TimeoutCancellationException) {
        _uiState.value = AiUiState.Error("AI generation timed out after 60 seconds.")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e("AiVM", "AI generation failed", e)
        _uiState.value = AiUiState.Error(e.localizedMessage ?: "AI generation failed.")
      }
    }
  }
}
