package com.example.data.model

import com.google.firebase.Timestamp

data class UserAccount(
  val uid: String = "",
  val email: String = "",
  val displayName: String = "",
  val role: String = "viewer", // "owner", "admin", "publisher", "viewer", "distributor", "creator"
  val roles: List<String> = emptyList(),
  val isAdmin: Boolean = false,
  val isPublisher: Boolean = false,
  val isDistributor: Boolean = false,
  val isOwner: Boolean = false,
  val publisherStatus: String = "", // "approved", "pending", "rejected", "none"
  val status: String = "active", // "active", "suspended", "pending"
  val createdAt: Timestamp? = null
) {
  private fun normalizedRole(): String = role.trim().lowercase()
  private fun normalizedRoles(): List<String> = roles.map { it.trim().lowercase() }

  fun isSuspended(): Boolean = status.trim().equals("suspended", ignoreCase = true)

  fun isOwnerAccount(): Boolean {
    val r = normalizedRole()
    val list = normalizedRoles()
    return isOwner ||
      r == "owner" || r.contains("owner") ||
      list.any { it == "owner" || it.contains("owner") }
  }

  fun hasExplicitAdminRole(): Boolean {
    val r = normalizedRole()
    val list = normalizedRoles()
    return isAdmin ||
      r == "admin" || r == "administrator" || r.contains("admin") ||
      list.any { it == "admin" || it == "administrator" || it.contains("admin") }
  }

  fun isAdminAccount(): Boolean = isOwnerAccount() || hasExplicitAdminRole()

  fun isPublisherAccount(): Boolean {
    val r = normalizedRole()
    val list = normalizedRoles()
    val pubStatus = publisherStatus.trim().lowercase()
    return isOwnerAccount() ||
      isPublisher ||
      isDistributor ||
      pubStatus == "approved" ||
      pubStatus == "active" ||
      r == "publisher" || r == "distributor" || r == "creator" ||
      r.contains("publisher") || r.contains("distributor") || r.contains("creator") ||
      list.any {
        it.contains("publisher") || it.contains("distributor") || it.contains("creator") || it.contains("staff")
      }
  }

  fun canAccessAdminTools(): Boolean = !isSuspended() && (isOwnerAccount() || isAdminAccount())

  fun canAccessPublisherTools(): Boolean = !isSuspended() && (isOwnerAccount() || isPublisherAccount())

  fun isAuthorizedDevConsole(): Boolean =
    !isSuspended() && (isOwnerAccount() || isAdminAccount() || isPublisherAccount())

  fun getEffectiveRole(): String {
    return when {
      isOwnerAccount() -> "owner"
      isAdminAccount() -> "admin"
      isPublisherAccount() -> "publisher"
      else -> "viewer"
    }
  }

  fun getRoleBadges(): List<String> {
    val badges = mutableListOf<String>()
    if (isOwnerAccount()) badges.add("OWNER")
    if (hasExplicitAdminRole()) badges.add("ADMIN")
    if (isPublisher || isDistributor || publisherStatus.equals("approved", ignoreCase = true) ||
      normalizedRole().contains("publisher") || normalizedRole().contains("distributor") ||
      normalizedRoles().any { it.contains("publisher") || it.contains("distributor") }
    ) {
      badges.add("PUBLISHER")
    }
    if (badges.isEmpty()) badges.add("VIEWER")
    return badges.distinct()
  }
}

data class Episode(
  val episodeNumber: Int = 1,
  val title: String = "",
  val description: String = "",
  val thumbnailUrl: String = "",
  val runtimeMinutes: Int = 45,
  val releaseDate: String = "",
  val videoUrl: String = ""
)

data class Season(
  val seasonNumber: Int = 1,
  val title: String = "",
  val episodes: List<Episode> = emptyList()
)

data class ContentItem(
  val id: String = "",
  val title: String = "",
  val description: String = "",
  val contentType: String = "movie", // "movie" or "tv"
  val genre: String = "Action",
  val categories: List<String> = emptyList(),
  val releaseYear: Int = 2026,
  val releaseDate: String = "",
  val videoUrl: String = "",
  val posterUrl: String = "",
  val backdropUrl: String = "",
  val trailerUrl: String = "",
  val rating: String = "PG-13",
  val runtimeMinutes: Int = 120,
  val publisherId: String = "",
  val publisherName: String = "",
  val status: String = "draft", // "draft", "pending", "approved", "rejected", "changes_requested"
  val feedback: String = "",
  val seasons: List<Season> = emptyList(),
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null
) {
  fun isEditableBy(uid: String): Boolean {
    return publisherId == uid && status.lowercase() in setOf("draft", "rejected", "changes_requested", "pending")
  }

  fun canBeReviewedBy(uid: String): Boolean {
    return publisherId != uid && status.lowercase() in setOf("pending", "changes_requested")
  }
}

data class AccessApplication(
  val id: String = "",
  val uid: String = "",
  val email: String = "",
  val fullName: String = "",
  val studioName: String = "",
  val requestedRole: String = "publisher",
  val reason: String = "",
  val status: String = "pending", // "pending", "approved", "rejected"
  val createdAt: Timestamp? = null
)

data class Category(
  val id: String = "",
  val name: String = "",
  val slug: String = ""
)

data class LiveStream(
  val id: String = "",
  val title: String = "",
  val streamUrl: String = "",
  val scheduleTime: String = "",
  val isLive: Boolean = false,
  val creatorId: String = ""
)

data class SubscriptionPlan(
  val id: String = "",
  val name: String = "",
  val price: String = "",
  val features: List<String> = emptyList()
)

data class PlatformSettings(
  val maintenanceMode: Boolean = false,
  val announcement: String = "",
  val allowRegistration: Boolean = false
)

data class AuditLog(
  val id: String = "",
  val adminId: String = "",
  val adminEmail: String = "",
  val action: String = "",
  val target: String = "",
  val timestamp: Timestamp? = null
)
