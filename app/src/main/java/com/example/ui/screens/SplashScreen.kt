package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NotflixRed
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
  var startAnimation by remember { mutableStateOf(false) }
  val scale = animateFloatAsState(
    targetValue = if (startAnimation) 1.1f else 0.9f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    )
  )

  LaunchedEffect(true) {
    startAnimation = true
    delay(1800)
    onTimeout()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = "NOTFLIX",
        color = NotflixRed,
        fontSize = 42.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 4.sp,
        modifier = Modifier.scale(scale.value)
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "DEV CONSOLE",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 8.sp
      )
    }
  }
}
