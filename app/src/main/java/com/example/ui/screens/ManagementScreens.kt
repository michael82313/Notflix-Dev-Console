package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContentItem
import com.example.data.model.UserAccount
import com.example.ui.theme.NotflixRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentManagementScreen(
  items: List<ContentItem>,
  currentUser: UserAccount,
  initialOnlyMine: Boolean = false,
  statusBanner: String? = null,
  onDismissBanner: () -> Unit = {},
  onBack: () -> Unit,
  onAddNew: () -> Unit,
  onEditItem: (ContentItem) -> Unit
) {
  BackHandler(onBack = onBack)

  var searchQuery by remember { mutableStateOf("") }
  var filterStatus by remember { mutableStateOf("all") }
  var onlyMine by remember(initialOnlyMine, currentUser.uid) {
    mutableStateOf(initialOnlyMine || !currentUser.canAccessAdminTools())
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(if (onlyMine) "My Submissions" else "Content Catalog Management")
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("content_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (currentUser.canAccessPublisherTools()) {
            IconButton(onClick = onAddNew, modifier = Modifier.testTag("content_add_btn")) {
              Icon(Icons.Default.Add, contentDescription = "Add Content")
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { padding ->
    val filteredItems = items.filter { item ->
      val matchesOwnership = !onlyMine || item.publisherId == currentUser.uid
      val matchesSearch = item.title.contains(searchQuery, ignoreCase = true) ||
        item.genre.contains(searchQuery, ignoreCase = true)
      val matchesStatus = filterStatus == "all" || item.status.equals(filterStatus, ignoreCase = true)
      matchesOwnership && matchesSearch && matchesStatus
    }

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

      item {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          label = { Text("Search title or genre...") },
          modifier = Modifier.fillMaxWidth().testTag("content_search_input"),
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true
        )
      }

      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (currentUser.canAccessAdminTools() && currentUser.canAccessPublisherTools()) {
            FilterChip(
              selected = onlyMine,
              onClick = { onlyMine = !onlyMine },
              label = { Text("My Submissions") },
              modifier = Modifier.testTag("filter_my_submissions")
            )
          }
          FilterChip(selected = filterStatus == "all", onClick = { filterStatus = "all" }, label = { Text("All") })
          FilterChip(selected = filterStatus == "approved", onClick = { filterStatus = "approved" }, label = { Text("Approved") })
          FilterChip(selected = filterStatus == "pending", onClick = { filterStatus = "pending" }, label = { Text("Pending") })
          FilterChip(selected = filterStatus == "changes_requested", onClick = { filterStatus = "changes_requested" }, label = { Text("Changes Requested") })
          FilterChip(selected = filterStatus == "draft", onClick = { filterStatus = "draft" }, label = { Text("Drafts") })
          FilterChip(selected = filterStatus == "rejected", onClick = { filterStatus = "rejected" }, label = { Text("Rejected") })
        }
      }

      if (filteredItems.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(24.dp).fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = if (onlyMine) "No submissions found for your account." else "No matching catalog items.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
              )
            }
          }
        }
      }

      items(filteredItems, key = { it.id.ifBlank { it.title } }) { item ->
        val canEdit = item.isEditableBy(currentUser.uid)
        Card(
          onClick = { if (canEdit) onEditItem(item) },
          enabled = canEdit,
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface
          ),
          modifier = Modifier.fillMaxWidth().testTag("content_item_card_${item.id}")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Badge(
                containerColor = when (item.status) {
                  "approved" -> MaterialTheme.colorScheme.secondary
                  "rejected" -> MaterialTheme.colorScheme.error
                  "pending", "changes_requested" -> MaterialTheme.colorScheme.tertiary
                  else -> MaterialTheme.colorScheme.surfaceVariant
                }
              ) {
                Text(
                  text = item.status.replace('_', ' ').uppercase(),
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  fontSize = 10.sp
                )
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Type: ${item.contentType.uppercase()} | Genre: ${item.genre} | Year: ${item.releaseYear}",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Publisher: ${item.publisherName.ifEmpty { item.publisherId }}",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.feedback.isNotBlank()) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(text = "Reviewer Feedback: ${item.feedback}", fontSize = 11.sp, color = NotflixRed)
            }
            if (canEdit) {
              Spacer(modifier = Modifier.height(10.dp))
              OutlinedButton(
                onClick = { onEditItem(item) },
                modifier = Modifier.testTag("edit_submission_btn_${item.id}")
              ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (item.status in setOf("rejected", "changes_requested")) "Edit & Resubmit" else "Edit Submission",
                  fontSize = 12.sp
                )
              }
            } else if (item.publisherId == currentUser.uid && item.status == "approved") {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Published in Notflix catalog (locked)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSubmissionScreen(
  currentUserId: String,
  currentUserName: String,
  existingItem: ContentItem? = null,
  onSubmit: (ContentItem) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  val isEditing = existingItem != null && existingItem.id.isNotBlank()
  var title by remember(existingItem?.id) { mutableStateOf(existingItem?.title ?: "") }
  var description by remember(existingItem?.id) { mutableStateOf(existingItem?.description ?: "") }
  var contentType by remember(existingItem?.id) { mutableStateOf(existingItem?.contentType ?: "movie") }
  var genre by remember(existingItem?.id) { mutableStateOf(existingItem?.genre ?: "Action") }
  var releaseYear by remember(existingItem?.id) { mutableStateOf((existingItem?.releaseYear ?: 2026).toString()) }
  var videoUrl by remember(existingItem?.id) { mutableStateOf(existingItem?.videoUrl ?: "") }
  var posterUrl by remember(existingItem?.id) { mutableStateOf(existingItem?.posterUrl ?: "") }
  var backdropUrl by remember(existingItem?.id) { mutableStateOf(existingItem?.backdropUrl ?: "") }
  var trailerUrl by remember(existingItem?.id) { mutableStateOf(existingItem?.trailerUrl ?: "") }
  var rating by remember(existingItem?.id) { mutableStateOf(existingItem?.rating ?: "PG-13") }
  var validationError by remember { mutableStateOf<String?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(if (isEditing) "Edit & Resubmit Content" else "Submit Content")
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("submission_back_btn")) {
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
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (existingItem != null && existingItem.feedback.isNotBlank()) {
        item {
          Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = "Reviewer Notes (${existingItem.status.replace('_', ' ').uppercase()})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = existingItem.feedback,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
              )
            }
          }
        }
      }

      if (validationError != null) {
        item {
          Text(text = validationError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Button(
            onClick = { contentType = "movie" },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (contentType == "movie") NotflixRed else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.weight(1f).testTag("type_movie_btn")
          ) {
            Text("Movie")
          }
          Button(
            onClick = { contentType = "tv" },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (contentType == "tv") NotflixRed else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.weight(1f).testTag("type_tv_btn")
          ) {
            Text("TV Show")
          }
        }
      }

      item {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it; validationError = null },
          label = { Text("Title *") },
          modifier = Modifier.fillMaxWidth().testTag("submission_title_input"),
          singleLine = true
        )
      }
      item {
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Synopsis / Description") },
          modifier = Modifier.fillMaxWidth().testTag("submission_description_input"),
          minLines = 3
        )
      }
      item {
        OutlinedTextField(
          value = genre,
          onValueChange = { genre = it },
          label = { Text("Genre") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }
      item {
        OutlinedTextField(
          value = releaseYear,
          onValueChange = { releaseYear = it },
          label = { Text("Release Year") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }
      item {
        OutlinedTextField(
          value = videoUrl,
          onValueChange = { videoUrl = it },
          label = { Text("Video Stream URL (HLS / MP4)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }
      item {
        OutlinedTextField(
          value = posterUrl,
          onValueChange = { posterUrl = it },
          label = { Text("Poster Artwork URL") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }
      item {
        OutlinedTextField(
          value = backdropUrl,
          onValueChange = { backdropUrl = it },
          label = { Text("Backdrop Artwork URL") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }
      item {
        OutlinedTextField(
          value = trailerUrl,
          onValueChange = { trailerUrl = it },
          label = { Text("Trailer Video URL") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }
      item {
        OutlinedTextField(
          value = rating,
          onValueChange = { rating = it },
          label = { Text("Age Rating (e.g. PG-13, TV-MA)") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )
      }

      item {
        fun buildPayload(targetStatus: String): ContentItem? {
          if (title.isBlank()) {
            validationError = "Title is required."
            return null
          }
          val parsedYear = releaseYear.trim().toIntOrNull() ?: 2026
          return ContentItem(
            id = existingItem?.id ?: "",
            title = title.trim(),
            description = description.trim(),
            contentType = contentType,
            genre = genre.trim().ifBlank { "Action" },
            releaseYear = parsedYear.coerceIn(1900, 2100),
            videoUrl = videoUrl.trim(),
            posterUrl = posterUrl.trim(),
            backdropUrl = backdropUrl.trim(),
            trailerUrl = trailerUrl.trim(),
            rating = rating.trim().ifBlank { "PG-13" },
            runtimeMinutes = existingItem?.runtimeMinutes ?: 120,
            publisherId = existingItem?.publisherId?.ifBlank { currentUserId } ?: currentUserId,
            publisherName = existingItem?.publisherName?.ifBlank { currentUserName } ?: currentUserName,
            status = targetStatus,
            feedback = if (targetStatus == "pending") "" else (existingItem?.feedback ?: ""),
            createdAt = existingItem?.createdAt
          )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedButton(
            onClick = {
              buildPayload("draft")?.let { item ->
                onSubmit(item)
                onBack()
              }
            },
            modifier = Modifier.weight(1f).height(50.dp).testTag("save_draft_btn")
          ) {
            Text(if (isEditing) "Update Draft" else "Save as Draft")
          }

          Button(
            onClick = {
              buildPayload("pending")?.let { item ->
                onSubmit(item)
                onBack()
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = NotflixRed),
            modifier = Modifier.weight(1f).height(50.dp).testTag("submit_review_btn")
          ) {
            Text(
              text = if (isEditing) "Resubmit for Review" else "Submit for Review",
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentReviewScreen(
  items: List<ContentItem>,
  currentUserUid: String,
  statusBanner: String? = null,
  onDismissBanner: () -> Unit = {},
  onReviewAction: (ContentItem, String, String) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  val feedbackMap = remember { mutableStateMapOf<String, String>() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Content Review Queue") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("review_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { padding ->
    val pendingItems = items.filter { it.status == "pending" || it.status == "changes_requested" }

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

      if (pendingItems.isEmpty()) {
        item {
          Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No pending submissions in review queue.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else {
        items(pendingItems, key = { it.id }) { item ->
          val isOwnSubmission = item.publisherId == currentUserUid
          val currentFeedback = feedbackMap[item.id] ?: item.feedback

          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("review_card_${item.id}")
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(text = item.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Type: ${item.contentType.uppercase()} | Publisher: ${item.publisherName.ifEmpty { item.publisherId }}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(text = item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
              Spacer(modifier = Modifier.height(12.dp))

              if (isOwnSubmission) {
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      Icons.Default.Lock,
                      contentDescription = "Locked",
                      tint = MaterialTheme.colorScheme.onErrorContainer,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = "Own Submission — Self-approval is prohibited. Another Admin or Owner must review this title.",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onErrorContainer,
                      fontWeight = FontWeight.Medium
                    )
                  }
                }
              } else {
                OutlinedTextField(
                  value = currentFeedback,
                  onValueChange = { feedbackMap[item.id] = it },
                  label = { Text("Reviewer Feedback / Revision Notes") },
                  modifier = Modifier.fillMaxWidth().testTag("review_feedback_${item.id}"),
                  singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = { onReviewAction(item, "approved", currentFeedback) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f).testTag("approve_btn_${item.id}")
                  ) {
                    Text("Approve", fontSize = 12.sp)
                  }
                  OutlinedButton(
                    onClick = { onReviewAction(item, "changes_requested", currentFeedback) },
                    modifier = Modifier.weight(1f).testTag("changes_btn_${item.id}")
                  ) {
                    Text("Changes", fontSize = 12.sp)
                  }
                  Button(
                    onClick = { onReviewAction(item, "rejected", currentFeedback) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).testTag("reject_btn_${item.id}")
                  ) {
                    Text("Reject", fontSize = 12.sp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
  users: List<UserAccount>,
  currentUser: UserAccount,
  statusBanner: String? = null,
  onDismissBanner: () -> Unit = {},
  onUpdateRole: (uid: String, newRole: String, grantPublisher: Boolean?) -> Unit,
  onUpdateStatus: (uid: String, newStatus: String) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  val callerIsOwner = currentUser.isOwnerAccount()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (callerIsOwner) "Users & Role Governance" else "Publisher & Viewer Management") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("users_back_btn")) {
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

      if (!callerIsOwner) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "Only the Platform Owner can appoint or remove Admins. Admins can manage Publishers and Viewers via trusted server functions.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(12.dp)
            )
          }
        }
      }

      items(users, key = { it.uid }) { u ->
        val isSelf = u.uid == currentUser.uid
        val isTargetOwner = u.isOwnerAccount()
        val isTargetAdmin = u.hasExplicitAdminRole()
        val canManageTarget = !isSelf && !isTargetOwner && (callerIsOwner || !isTargetAdmin)

        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth().testTag("user_card_${u.uid}")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(text = u.displayName.ifEmpty { "User" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = u.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Roles: ${u.getRoleBadges().joinToString(" + ")} | Status: ${u.status.uppercase()}",
              fontSize = 11.sp,
              color = NotflixRed,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            when {
              isSelf -> {
                Text("Current signed-in account (self-role change blocked)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              isTargetOwner -> {
                Text("Platform Owner (protected)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              !canManageTarget -> {
                Text("Admin account (Owner authorization required)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              else -> {
                Row(
                  modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  if (callerIsOwner) {
                    val targetWillBeAdmin = !isTargetAdmin
                    OutlinedButton(
                      onClick = {
                        val nextRole = if (targetWillBeAdmin) "admin" else if (u.isPublisherAccount()) "publisher" else "viewer"
                        onUpdateRole(u.uid, nextRole, u.isPublisherAccount())
                      },
                      modifier = Modifier.testTag("toggle_admin_btn_${u.uid}")
                    ) {
                      Text(if (targetWillBeAdmin) "Appoint Admin" else "Remove Admin", fontSize = 11.sp)
                    }
                  }

                  val targetHasPub = u.isPublisherAccount()
                  OutlinedButton(
                    onClick = {
                      val baseRole = if (isTargetAdmin) "admin" else if (targetHasPub) "viewer" else "publisher"
                      onUpdateRole(u.uid, baseRole, !targetHasPub)
                    },
                    modifier = Modifier.testTag("toggle_pub_btn_${u.uid}")
                  ) {
                    Text(if (targetHasPub) "Revoke Publisher" else "Grant Publisher", fontSize = 11.sp)
                  }

                  OutlinedButton(
                    onClick = {
                      val nextStatus = if (u.isSuspended()) "active" else "suspended"
                      onUpdateStatus(u.uid, nextStatus)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                      contentColor = if (u.isSuspended()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("toggle_status_btn_${u.uid}")
                  ) {
                    Text(if (u.isSuspended()) "Activate" else "Suspend", fontSize = 11.sp)
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

@Composable
internal fun StatusFeedbackCard(message: String, onDismiss: () -> Unit) {
  Card(
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = message,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f)
      )
      IconButton(onClick = onDismiss) {
        Icon(Icons.Default.Close, contentDescription = "Dismiss")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailsScreen(
  user: UserAccount,
  onBack: () -> Unit,
  onSignOut: () -> Unit
) {
  BackHandler(onBack = onBack)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Account Details") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("account_back_btn")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(padding)
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(24.dp)) {
          Text(text = "Name: ${user.displayName}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = "Email: ${user.email}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Permissions: ${user.getRoleBadges().joinToString(" • ")}",
            fontSize = 14.sp,
            color = NotflixRed,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = "Account Status: ${user.status.uppercase()}", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
          if (user.publisherStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Publisher Status: ${user.publisherStatus.uppercase()}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = "UID: ${user.uid}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Spacer(modifier = Modifier.height(24.dp))
      Button(
        onClick = onSignOut,
        colors = ButtonDefaults.buttonColors(containerColor = NotflixRed),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("account_signout_btn")
      ) {
        Text("Sign Out")
      }
    }
  }
}
