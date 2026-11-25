package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NotflixRed

@Composable
fun SignInScreen(
  onGoogleSignInClicked: (android.content.Context) -> Unit,
  onEmailSignInClicked: (String, String) -> Unit,
  onEmailSignUpClicked: (String, String, String) -> Unit,
  onCreateAccountClicked: () -> Unit,
  errorMessage: String?
) {
  val context = LocalContext.current
  var isSignUp by remember { mutableStateOf(false) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var displayName by remember { mutableStateOf("") }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 460.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Default.Shield,
          contentDescription = null,
          tint = NotflixRed,
          modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "NOTFLIX",
          color = NotflixRed,
          fontSize = 24.sp,
          fontWeight = FontWeight.Black,
          letterSpacing = 2.sp
        )
        Text(
          text = "DEV CONSOLE",
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 4.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (errorMessage != null) {
          Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
          onClick = { onGoogleSignInClicked(context) },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(containerColor = NotflixRed)
        ) {
          Text(
            text = "Sign in with Google",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = if (isSignUp) "Or Sign Up with Email" else "Or Sign In with Email",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isSignUp) {
          OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
          Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          label = { Text("Email Address") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text("Password") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
          onClick = {
            if (email.isNotBlank() && password.isNotBlank()) {
              if (isSignUp) {
                onEmailSignUpClicked(email, password, displayName)
              } else {
                onEmailSignInClicked(email, password)
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Text(
            text = if (isSignUp) "Create Account" else "Sign In with Email",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = { isSignUp = !isSignUp }) {
          Text(
            text = if (isSignUp) "Already have an account? Sign In" else "New Creator? Sign Up with Email",
            color = NotflixRed,
            fontSize = 12.sp
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onCreateAccountClicked) {
          Text(
            text = "Apply for Admin/Publisher Access",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
          )
        }
      }
    }
  }
}
