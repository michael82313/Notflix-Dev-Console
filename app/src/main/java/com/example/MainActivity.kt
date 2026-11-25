package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.ai.configureAppCheck
import com.example.ui.navigation.NotflixNavGraph
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Configure Firebase App Check for AI Logic
    configureAppCheck(this, intent)

    setContent {
      MyApplicationTheme {
        NotflixNavGraph()
      }
    }
  }
}
