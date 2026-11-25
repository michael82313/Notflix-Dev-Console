package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val NotflixDarkColorScheme =
  darkColorScheme(
    primary = NotflixRed,
    onPrimary = NotflixTextPrimary,
    primaryContainer = NotflixRedDark,
    onPrimaryContainer = NotflixTextPrimary,
    secondary = NotflixGreen,
    onSecondary = NotflixBlack,
    background = NotflixBlack,
    onBackground = NotflixTextPrimary,
    surface = NotflixCharcoal,
    onSurface = NotflixTextPrimary,
    surfaceVariant = NotflixSurface,
    onSurfaceVariant = NotflixTextSecondary,
    outline = NotflixBorder,
    error = NotflixRed,
    onError = NotflixTextPrimary
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Notflix Dev Console is inherently a premium dark studio app
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = NotflixDarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
