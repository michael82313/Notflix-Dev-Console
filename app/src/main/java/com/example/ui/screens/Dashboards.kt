package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserAccount
import com.example.ui.theme.NotflixRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(
  user: UserAccount,
  onNavigateToContent: () -> Unit,
  onNavigateToMySubmissions: () -> Unit,
  onNavigateToSubmit: () -> Unit,
  onNavigateToReview: () -> Unit,
  onNavigateToUsers: () -> Unit,
  onNavigateToApplications: () -> Unit,
  onNavigateToAudit: () -> Unit,
  onNavigateToAi: () -> Unit,
  onNavigateToAccount: () -> Unit,
  onSignOut: () -> Unit
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Notflix Owner Console", fontWeight = FontWeight.Bold) },
        actions = {
          IconButton(onClick = onNavigateToAccount, modifier = Modifier.testTag("owner_account_btn")) {
            Icon(Icons.Default.AccountCircle, contentDescription = "Account")
          }
          IconButton(onClick = onSignOut, modifier = Modifier.testTag("owner_signout_btn")) {
            Icon(Icons.Default.Logout, contentDescription = "Sign Out")
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
            Text(text = "Welcome, Owner ${user.displayName}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Roles: ${user.getRoleBadges().joinToString(" • ")}",
              color = NotflixRed,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Full platform administrative control, Admin appointment, & Publisher Studio.",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp
            )
          }
        }
      }

      item {
        Text(text = "Platform Management", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NotflixRed)
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          DashboardActionCard(
            title = "All Content",
            subtitle = "Manage Catalog",
            icon = Icons.Default.Movie,
            modifier = Modifier.weight(1f).testTag("owner_all_content_card"),
            onClick = onNavigateToContent
          )
          DashboardActionCard(
            title = "Review Queue",
            subtitle = "Approve / Reject",
            icon = Icons.Default.RateReview,
            modifier = Modifier.weight(1f).testTag("owner_review_queue_card"),
            onClick = onNavigateToReview
          )
        }
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          DashboardActionCard(
            title = "Users & Roles",
            subtitle = "Appoint Admins & Access",
            icon = Icons.Default.SupervisorAccount,
            modifier = Modifier.weight(1f).testTag("owner_users_card"),
            onClick = onNavigateToUsers
          )
          DashboardActionCard(
            title = "Access Requests",
            subtitle = "Review Applications",
            icon = Icons.Default.HowToReg,
            modifier = Modifier.weight(1f).testTag("owner_applications_card"),
            onClick = onNavigateToApplications
          )
        }
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          DashboardActionCard(
            title = "Audit Logs",
            subtitle = "System Activity",
            icon = Icons.Default.History,
            modifier = Modifier.weight(1f).testTag("owner_audit_card"),
            onClick = onNavigateToAudit
          )
          DashboardActionCard(
            title = "Gemini AI Studio",
            subtitle = "Pitches & Synopses",
            icon = Icons.Default.AutoAwesome,
            modifier = Modifier.weight(1f).testTag("owner_ai_card"),
            onClick = onNavigateToAi
          )
        }
      }

      item {
        Text(text = "Publisher Studio Tools", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NotflixRed)
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          DashboardActionCard(
            title = "Submit Content",
            subtitle = "New Movie / Show",
            icon = Icons.Default.AddBox,
            modifier = Modifier.weight(1f).testTag("owner_submit_card"),
            onClick = onNavigateToSubmit
          )
          DashboardActionCard(
            title = "My Submissions",
            subtitle = "Edit Drafts & Track",
            icon = Icons.Default.ListAlt,
            modifier = Modifier.weight(1f).testTag("owner_my_submissions_card"),
            onClick = onNavigateToMySubmissions
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
  user: UserAccount,
  onNavigateToContent: () -> Unit,
  onNavigateToReview: () -> Unit,
  onNavigateToUsers: () -> Unit,
  onNavigateToApplications: () -> Unit,
  onNavigateToAudit: () -> Unit,
  onNavigateToSubmit: () -> Unit,
  onNavigateToMySubmissions: () -> Unit,
  onNavigateToAi: () -> Unit,
  onNavigateToAccount: () -> Unit,
  onSignOut: () -> Unit
) {
  val hasPublisherTools = user.canAccessPublisherTools()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Notflix Admin Console", fontWeight = FontWeight.Bold) },
        actions = {
          IconButton(onClick = onNavigateToAccount, modifier = Modifier.testTag("admin_account_btn")) {
            Icon(Icons.Default.AccountCircle, contentDescription = "Account")
          }
          IconButton(onClick = onSignOut, modifier = Modifier.testTag("admin_signout_btn")) {
            Icon(Icons.Default.Logout, contentDescription = "Sign Out")
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
            Text(text = "Welcome, Admin ${user.displayName}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Active Permissions: ${user.getRoleBadges().joinToString(" • ")}",
              color = NotflixRed,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = if (hasPublisherTools) {
                "Content review, catalog oversight, and inherited Publisher Studio access."
              } else {
                "Content review, approvals, and publisher management."
              },
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp
            )
          }
        }
      }

      item {
        Text(text = "Administration", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NotflixRed)
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          DashboardActionCard(
            title = "Review Queue",
            subtitle = "Approve / Reject Others",
            icon = Icons.Default.RateReview,
            modifier = Modifier.weight(1f).testTag("admin_review_queue_card"),
            onClick = onNavigateToReview
          )
          DashboardActionCard(
            title = "Content Catalog",
            subtitle = "All Submissions",
            icon = Icons.Default.Movie,
            modifier = Modifier.weight(1f).testTag("admin_catalog_card"),
            onClick = onNavigateToContent
          )
        }
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          DashboardActionCard(
            title = "Publishers & Users",
            subtitle = "Manage Creators",
            icon = Icons.Default.People,
            modifier = Modifier.weight(1f).testTag("admin_users_card"),
            onClick = onNavigateToUsers
          )
          DashboardActionCard(
            title = "Access Requests",
            subtitle = "Publisher Applications",
            icon = Icons.Default.HowToReg,
            modifier = Modifier.weight(1f).testTag("admin_applications_card"),
            onClick = onNavigateToApplications
          )
        }
      }

      item {
        DashboardActionCard(
          title = "Audit Logs",
          subtitle = "Review Administrative Activity",
          icon = Icons.Default.History,
          modifier = Modifier.fillMaxWidth().testTag("admin_audit_card"),
          onClick = onNavigateToAudit
        )
      }

      if (hasPublisherTools) {
        item {
          Text(text = "Publisher & AI Tools", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NotflixRed)
        }

        item {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardActionCard(
              title = "Submit Content",
              subtitle = "New Movie / Show",
              icon = Icons.Default.AddBox,
              modifier = Modifier.weight(1f).testTag("admin_submit_content_card"),
              onClick = onNavigateToSubmit
            )
            DashboardActionCard(
              title = "My Submissions",
              subtitle = "Edit Drafts & Track",
              icon = Icons.Default.ListAlt,
              modifier = Modifier.weight(1f).testTag("admin_my_submissions_card"),
              onClick = onNavigateToMySubmissions
            )
          }
        }

        item {
          DashboardActionCard(
            title = "Gemini AI Assistant",
            subtitle = "Generate Pitches & Synopses",
            icon = Icons.Default.AutoAwesome,
            modifier = Modifier.fillMaxWidth().testTag("admin_ai_card"),
            onClick = onNavigateToAi
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublisherDashboardScreen(
  user: UserAccount,
  onNavigateToSubmit: () -> Unit,
  onNavigateToMyContent: () -> Unit,
  onNavigateToAi: () -> Unit,
  onNavigateToAccount: () -> Unit,
  onSignOut: () -> Unit
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Notflix Publisher Studio", fontWeight = FontWeight.Bold) },
        actions = {
          IconButton(onClick = onNavigateToAccount, modifier = Modifier.testTag("pub_account_btn")) {
            Icon(Icons.Default.AccountCircle, contentDescription = "Account")
          }
          IconButton(onClick = onSignOut, modifier = Modifier.testTag("pub_signout_btn")) {
            Icon(Icons.Default.Logout, contentDescription = "Sign Out")
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
            Text(text = "Creator Studio: ${user.displayName}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Roles: ${user.getRoleBadges().joinToString(" • ")}",
              color = NotflixRed,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Submit movies and TV shows for Notflix publication.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
          }
        }
      }

      item {
        Text(text = "Publisher & AI Tools", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NotflixRed)
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          DashboardActionCard(
            title = "Submit Content",
            subtitle = "New Movie / Show",
            icon = Icons.Default.AddBox,
            modifier = Modifier.weight(1f).testTag("pub_submit_card"),
            onClick = onNavigateToSubmit
          )
          DashboardActionCard(
            title = "My Submissions",
            subtitle = "Edit Drafts & Track",
            icon = Icons.Default.ListAlt,
            modifier = Modifier.weight(1f).testTag("pub_my_submissions_card"),
            onClick = onNavigateToMyContent
          )
        }
      }

      item {
        DashboardActionCard(
          title = "Gemini AI Assistant",
          subtitle = "Generate Pitches & Synopses",
          icon = Icons.Default.AutoAwesome,
          modifier = Modifier.fillMaxWidth().testTag("pub_ai_card"),
          onClick = onNavigateToAi
        )
      }
    }
  }
}

@Composable
fun DashboardActionCard(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Card(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier.height(110.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalArrangement = Arrangement.Center
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = NotflixRed, modifier = Modifier.size(28.dp))
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
      Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
