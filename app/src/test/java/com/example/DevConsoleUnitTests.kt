package com.example

import com.example.data.model.ContentItem
import com.example.data.model.UserAccount
import com.example.data.repository.parseUserAccountDocument
import com.example.ui.auth.mergeAccountWithClaims
import org.junit.Assert.*
import org.junit.Test

class DevConsoleUnitTests {

  @Test
  fun parseUserAccountDocument_separatesAccountStatusAndPublisherStatus() {
    val account = parseUserAccountDocument(
      docId = "admin_pub_1",
      data = mapOf(
        "email" to "adminpub@notflix.dev",
        "displayName" to "Dual User",
        "role" to "admin",
        "isAdmin" to true,
        "publisherStatus" to "approved"
      )
    )

    assertEquals("active", account.status)
    assertEquals("approved", account.publisherStatus)
    assertTrue(account.isAdminAccount())
    assertTrue(account.isPublisherAccount())
    assertTrue(account.canAccessAdminTools())
    assertTrue(account.canAccessPublisherTools())
    assertEquals("admin", account.getEffectiveRole())
    assertEquals(listOf("ADMIN", "PUBLISHER"), account.getRoleBadges())
  }

  @Test
  fun parseUserAccountDocument_pendingPublisherWithoutRoleRemainsViewer() {
    val account = parseUserAccountDocument(
      docId = "pending_1",
      data = mapOf(
        "email" to "viewer@notflix.dev",
        "role" to "viewer",
        "publisherStatus" to "pending",
        "status" to "active"
      )
    )

    assertFalse(account.isAuthorizedDevConsole())
    assertFalse(account.canAccessPublisherTools())
    assertEquals("active", account.status)
    assertEquals("viewer", account.getEffectiveRole())
  }

  @Test
  fun mergeAccountWithClaims_preservesExistingDualPermissionsWithoutDowngrade() {
    val docAccount = UserAccount(
      uid = "admin_pub_2",
      email = "adminpub@notflix.dev",
      displayName = "Admin+Pub",
      role = "admin",
      isAdmin = true,
      publisherStatus = "approved",
      status = "active"
    )

    // Even if custom claims are empty or only mention publisher, admin permission from doc is preserved
    val merged = mergeAccountWithClaims(
      docAccount = docAccount,
      uid = "admin_pub_2",
      email = "adminpub@notflix.dev",
      displayName = "Admin+Pub",
      claims = mapOf("publisher" to true)
    )

    assertTrue(merged.canAccessAdminTools())
    assertTrue(merged.canAccessPublisherTools())
    assertEquals("admin", merged.getEffectiveRole())
    assertEquals(listOf("ADMIN", "PUBLISHER"), merged.getRoleBadges())
  }

  @Test
  fun mergeAccountWithClaims_missingDocWithNoClaimsRemainsViewerWithoutMutation() {
    val account = mergeAccountWithClaims(
      docAccount = null,
      uid = "unknown_1",
      email = "stranger@notflix.dev",
      displayName = "Stranger",
      claims = emptyMap()
    )

    assertEquals("viewer", account.getEffectiveRole())
    assertFalse(account.isAuthorizedDevConsole())
  }

  @Test
  fun suspendedAccount_isDeniedBothAdminAndPublisherAccess() {
    val suspended = UserAccount(
      uid = "sus_1",
      role = "admin",
      isAdmin = true,
      isPublisher = true,
      status = "suspended"
    )

    assertFalse(suspended.isAuthorizedDevConsole())
    assertFalse(suspended.canAccessAdminTools())
    assertFalse(suspended.canAccessPublisherTools())
  }

  @Test
  fun contentItem_editingAndAntiSelfApprovalRules() {
    val draft = ContentItem(id = "c1", publisherId = "admin_pub_1", status = "draft")
    val rejected = ContentItem(id = "c2", publisherId = "admin_pub_1", status = "rejected")
    val changes = ContentItem(id = "c3", publisherId = "admin_pub_1", status = "changes_requested")
    val pendingOwn = ContentItem(id = "c4", publisherId = "admin_pub_1", status = "pending")
    val pendingOther = ContentItem(id = "c5", publisherId = "other_pub", status = "pending")
    val approvedOwn = ContentItem(id = "c6", publisherId = "admin_pub_1", status = "approved")

    assertTrue(draft.isEditableBy("admin_pub_1"))
    assertTrue(rejected.isEditableBy("admin_pub_1"))
    assertTrue(changes.isEditableBy("admin_pub_1"))
    assertFalse(approvedOwn.isEditableBy("admin_pub_1"))
    assertFalse(draft.isEditableBy("other_pub"))

    // Admin+Publisher CANNOT review their own submission, CAN review another's submission
    assertFalse(pendingOwn.canBeReviewedBy("admin_pub_1"))
    assertTrue(pendingOther.canBeReviewedBy("admin_pub_1"))
  }
}
