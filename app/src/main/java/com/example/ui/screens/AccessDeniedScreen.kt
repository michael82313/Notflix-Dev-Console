package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NotflixRed

@Composable
fun AccessDeniedScreen(onSignOut: () -> Unit, onRequestAccess: () -> Unit) {
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
        .widthIn(max = 440.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Default.Block,
          contentDescription = null,
          tint = NotflixRed,
          modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "Access Restricted",
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Your account does not have Owner, Admin, or Publisher permissions for Notflix Dev Console.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 13.sp,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
          onClick = onRequestAccess,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("apply_access_btn"),
          colors = ButtonDefaults.buttonColors(containerColor = NotflixRed)
        ) {
          Text("Apply for Access")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
          onClick = onSignOut,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("access_denied_signout_btn")
        ) {
          Text("Sign Out")
        }
      }
    }
  }
}

@Composable
fun VerificationErrorScreen(
  message: String,
  isPermissionDenied: Boolean,
  onRetry: () -> Unit,
  onSignOut: () -> Unit
) {
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
        .widthIn(max = 440.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Default.CloudOff,
          contentDescription = "Verification error",
          tint = NotflixRed,
          modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = if (isPermissionDenied) "Permission Lookup Error" else "Verification Unavailable",
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "$message\n\nYour existing account permissions were NOT modified.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 13.sp,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
          onClick = onRetry,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("retry_verification_btn"),
          colors = ButtonDefaults.buttonColors(containerColor = NotflixRed)
        ) {
          Text("Retry Verification")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
          onClick = onSignOut,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("verification_error_signout_btn")
        ) {
          Text("Sign Out")
        }
      }
    }
  }
}

@Composable
fun AccountSuspendedScreen(onSignOut: () -> Unit) {
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
        .widthIn(max = 440.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Default.Block,
          contentDescription = null,
          tint = NotflixRed,
          modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "Account Suspended",
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Your account has been suspended by platform administration. Contact support for details.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 13.sp,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
          onClick = onSignOut,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("suspended_signout_btn"),
          colors = ButtonDefaults.buttonColors(containerColor = NotflixRed)
        ) {
          Text("Sign Out")
        }
      }
    }
  }
}
