package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccessApplication
import com.example.ui.theme.NotflixRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessApplicationScreen(
  currentUid: String,
  currentEmail: String,
  onSubmit: (AccessApplication) -> Unit,
  onBackToSignIn: () -> Unit
) {
  var fullName by remember { mutableStateOf("") }
  var studioName by remember { mutableStateOf("") }
  var requestedRole by remember { mutableStateOf("publisher") }
  var reason by remember { mutableStateOf("") }
  var submitted by remember { mutableStateOf(false) }

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
        .widthIn(max = 500.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Start
        ) {
          IconButton(onClick = onBackToSignIn) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
          }
        }

        Icon(
          imageVector = Icons.Default.Description,
          contentDescription = null,
          tint = NotflixRed,
          modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Apply for Dev Console Access",
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Notflix Dev Console is exclusively available to approved Administrators and Creators. Submit your application below.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.sp,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (submitted) {
          Text(
            text = "Application submitted successfully! An administrator or owner will review your credentials.",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(24.dp))
          Button(
            onClick = onBackToSignIn,
            colors = ButtonDefaults.buttonColors(containerColor = NotflixRed),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Return to Sign In")
          }
        } else {
          OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = studioName,
            onValueChange = { studioName = it },
            label = { Text("Studio / Production Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Reason / Catalog Overview") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
          )
          Spacer(modifier = Modifier.height(24.dp))

          Button(
            onClick = {
              if (fullName.isNotBlank() && studioName.isNotBlank()) {
                val app = AccessApplication(
                  uid = currentUid,
                  email = currentEmail,
                  fullName = fullName,
                  studioName = studioName,
                  requestedRole = requestedRole,
                  reason = reason,
                  status = "pending"
                )
                onSubmit(app)
                submitted = true
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NotflixRed)
          ) {
            Text("Submit Application", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
