package com.example.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.NotflixRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
  onBack: () -> Unit,
  viewModel: AiAssistantViewModel = viewModel()
) {
  var movieTitle by remember { mutableStateOf("") }
  var conceptPrompt by remember { mutableStateOf("") }
  val uiState by viewModel.uiState.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Gemini AI Content Assistant") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(padding)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NotflixRed)
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = "Powered by Gemini AI", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Generate professional synopses, loglines, and pitch notes instantly.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      item {
        OutlinedTextField(
          value = movieTitle,
          onValueChange = { movieTitle = it },
          label = { Text("Movie / Series Working Title") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }

      item {
        OutlinedTextField(
          value = conceptPrompt,
          onValueChange = { conceptPrompt = it },
          label = { Text("Core Concept / Plot Premise") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3
        )
      }

      item {
        Button(
          onClick = {
            if (movieTitle.isNotBlank() && conceptPrompt.isNotBlank()) {
              viewModel.generateMoviePitch(movieTitle, conceptPrompt)
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
          colors = ButtonDefaults.buttonColors(containerColor = NotflixRed)
        ) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Generate AI Pitch & Synopsis", fontWeight = FontWeight.Bold)
        }
      }

      when (val state = uiState) {
        is AiUiState.Loading -> {
          item {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = NotflixRed)
            }
          }
        }
        is AiUiState.Success -> {
          item {
            Card(
              shape = RoundedCornerShape(12.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "AI Generated Output", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NotflixRed)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = state.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
              }
            }
          }
        }
        is AiUiState.Error -> {
          item {
            Text(text = state.message, color = MaterialTheme.colorScheme.error)
          }
        }
        else -> {}
      }
    }
  }
}
