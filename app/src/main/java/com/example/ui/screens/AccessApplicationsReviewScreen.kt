package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccessApplication
import com.example.data.model.UserAccount
import com.example.ui.theme.NotflixRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessApplicationsReviewScreen(
  applications: List<AccessApplication>,
  currentUser: UserAccount,
  statusBanner: String? = null,
  onDismissBanner: () -> Unit = {},
  onDecision: (applicationId: String, decision: String) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  val callerIsOwner = currentUser.isOwnerAccount()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Access Applications") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("apps_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      if (!statusBanner.isNullOrBlank()) {
        item {
          StatusFeedbackCard(message = statusBanner, onDismiss = onDismissBanner)
        }
      }

      if (applications.isEmpty()) {
        item {
          Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No access applications submitted.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else {
        items(applications, key = { it.id }) { app ->
          val requiresOwner = app.requestedRole.equals("admin", ignoreCase = true)
          val canApprove = app.status == "pending" && (!requiresOwner || callerIsOwner)

          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("app_card_${app.id}")
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(text = app.fullName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Badge(
                  containerColor = when (app.status) {
                    "approved" -> MaterialTheme.colorScheme.secondary
                    "rejected" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                  }
                ) {
                  Text(app.status.uppercase(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp)
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(text = "${app.studioName} • ${app.email}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.height(4.dp))
              Text(text = "Requested Role: ${app.requestedRole.uppercase()}", fontSize = 11.sp, color = NotflixRed, fontWeight = FontWeight.SemiBold)
              if (app.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = app.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
              }

              if (app.status == "pending") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = { onDecision(app.id, "approved") },
                    enabled = canApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f).testTag("app_approve_btn_${app.id}")
                  ) {
                    Text(if (requiresOwner && !callerIsOwner) "Owner Only" else "Approve")
                  }
                  OutlinedButton(
                    onClick = { onDecision(app.id, "rejected") },
                    modifier = Modifier.weight(1f).testTag("app_reject_btn_${app.id}")
                  ) {
                    Text("Reject")
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
