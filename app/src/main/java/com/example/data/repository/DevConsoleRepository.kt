package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class OperationType(val value: String) {
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete"),
  LIST("list"),
  GET("get"),
  WRITE("write"),
}

sealed interface AccountLookupResult {
  data class Found(val account: UserAccount) : AccountLookupResult
  object NotFound : AccountLookupResult
  data class Error(
    val message: String,
    val isPermissionDenied: Boolean,
    val cause: Throwable
  ) : AccountLookupResult
}

class CloudFunctionUnavailableException(
  val functionName: String,
  message: String
) : IllegalStateException(message)

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
  val causes = generateSequence(exception) { it.cause }
  val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
    ?: causes.firstOrNull { it !is CancellationException }
    ?: exception

  val auth = FirebaseAuth.getInstance()
  val currentUser = auth.currentUser

  val providerInfoList = currentUser?.providerData?.map { provider ->
    JSONObject().apply {
      put("providerId", provider.providerId)
      put("email", provider.email)
    }
  } ?: emptyList()

  val authInfoJson = JSONObject().apply {
    put("userId", currentUser?.uid)
    put("email", currentUser?.email)
    put("emailVerified", currentUser?.isEmailVerified)
    put("tenantId", currentUser?.tenantId)
    put("providerInfo", JSONArray(providerInfoList))
  }

  val errorInfoJson = JSONObject().apply {
    put("error", rootError.message ?: rootError.toString())
    put("operationType", operationType.value)
    put("path", path)
    put("authInfo", authInfoJson)
  }

  val jsonString = errorInfoJson.toString()
  Log.e("FirestoreError", "Firestore Error: $jsonString")
  return jsonString
}

internal fun parseUserAccountDocument(
  docId: String,
  data: Map<String, Any?>,
  fallbackEmail: String? = null
): UserAccount {
  val email = (data["email"] as? String) ?: fallbackEmail ?: ""
  val displayName = (data["displayName"] as? String) ?: (data["name"] as? String) ?: "Creator"

  val roleStr = (data["role"] as? String)
    ?: (data["userRole"] as? String)
    ?: (data["type"] as? String)
    ?: (data["access"] as? String)
    ?: "viewer"

  val rolesList = when (val r = data["roles"]) {
    is List<*> -> r.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
    is Map<*, *> -> r.entries.filter { it.value == true }.mapNotNull { it.key?.toString()?.trim() }
    else -> emptyList()
  }

  val isOwner = (data["isOwner"] as? Boolean) ?: (data["owner"] as? Boolean) ?: false
  val isAdmin = (data["isAdmin"] as? Boolean) ?: (data["admin"] as? Boolean) ?: false
  val isPublisher = (data["isPublisher"] as? Boolean) ?: (data["publisher"] as? Boolean) ?: false
  val isDistributor = (data["isDistributor"] as? Boolean) ?: (data["distributor"] as? Boolean) ?: false
  val publisherStatus = (data["publisherStatus"] as? String) ?: ""
  val accountStatus = (data["status"] as? String) ?: (data["accountStatus"] as? String) ?: "active"

  return UserAccount(
    uid = docId,
    email = email,
    displayName = displayName,
    role = roleStr,
    roles = rolesList,
    isAdmin = isAdmin,
    isPublisher = isPublisher,
    isDistributor = isDistributor,
    isOwner = isOwner,
    publisherStatus = publisherStatus,
    status = accountStatus,
    createdAt = data["createdAt"] as? Timestamp
  )
}

class DevConsoleRepository(context: Context) {
  private val dbId = context.getString(R.string.firestore_database_id)
  private val firestore = FirebaseFirestore.getInstance(dbId)
  private val projectId: String = FirebaseApp.getInstance().options.projectId ?: "gen-lang-client-0851231801"
  private val functionsBaseUrl: String = "https://us-central1-$projectId.cloudfunctions.net"
  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(25, TimeUnit.SECONDS)
    .build()

  suspend fun getUserAccountResult(uid: String, authEmail: String?): AccountLookupResult {
    val path = "users/$uid"
    return try {
      val doc = firestore.collection("users").document(uid).get().await()
      if (doc.exists()) {
        val data = doc.data ?: emptyMap<String, Any?>()
        AccountLookupResult.Found(parseUserAccountDocument(doc.id, data, authEmail))
      } else {
        AccountLookupResult.NotFound
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: FirebaseFirestoreException) {
      handleFirestoreError(e, OperationType.GET, path)
      val isDenied = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
      val msg = if (isDenied) {
        "Firestore permission denied reading users/$uid."
      } else {
        "Unable to verify account (${e.code.name}): ${e.localizedMessage ?: "Network error"}"
      }
      AccountLookupResult.Error(msg, isDenied, e)
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      AccountLookupResult.Error(
        message = "Network or backend error verifying account: ${e.localizedMessage ?: "Unknown error"}",
        isPermissionDenied = false,
        cause = e
      )
    }
  }

  suspend fun createViewerAccountOnSignUp(account: UserAccount): Result<Unit> {
    val path = "users/${account.uid}"
    return try {
      val existing = firestore.collection("users").document(account.uid).get().await()
      if (existing.exists()) {
        return Result.success(Unit)
      }
      val data = mapOf(
        "uid" to account.uid,
        "email" to account.email,
        "displayName" to account.displayName.take(120),
        "role" to "viewer",
        "status" to "active",
        "createdAt" to FieldValue.serverTimestamp()
      )
      firestore.collection("users").document(account.uid).set(data).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  fun observeUsers(): Flow<List<UserAccount>> = callbackFlow {
    val listener = firestore.collection("users").addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.LIST, "users")
        trySend(emptyList())
        return@addSnapshotListener
      }
      val users = snapshot?.documents?.map { doc ->
        parseUserAccountDocument(doc.id, doc.data ?: emptyMap())
      } ?: emptyList()
      trySend(users)
    }
    awaitClose { listener.remove() }
  }

  fun observeAllContentItems(): Flow<List<ContentItem>> = callbackFlow {
    val listener = firestore.collection("movies").addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.LIST, "movies")
        trySend(emptyList())
        return@addSnapshotListener
      }
      val items = snapshot?.documents?.map { doc -> parseContentDocument(doc) } ?: emptyList()
      trySend(items)
    }
    awaitClose { listener.remove() }
  }

  fun observeMyContentItems(publisherId: String): Flow<List<ContentItem>> = callbackFlow {
    val listener = firestore.collection("movies")
      .whereEqualTo("publisherId", publisherId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.LIST, "movies")
          trySend(emptyList())
          return@addSnapshotListener
        }
        val items = snapshot?.documents?.map { doc -> parseContentDocument(doc) } ?: emptyList()
        trySend(items)
      }
    awaitClose { listener.remove() }
  }

  private fun parseContentDocument(doc: DocumentSnapshot): ContentItem {
    val data = doc.data ?: emptyMap<String, Any?>()
    val categoriesList = when (val cats = data["categories"]) {
      is List<*> -> cats.mapNotNull { it?.toString() }
      else -> emptyList()
    }
    return ContentItem(
      id = doc.id,
      title = (data["title"] as? String) ?: "",
      description = (data["description"] as? String) ?: "",
      contentType = (data["contentType"] as? String) ?: "movie",
      genre = (data["genre"] as? String) ?: "Action",
      categories = categoriesList,
      releaseYear = (data["releaseYear"] as? Number)?.toInt() ?: 2026,
      releaseDate = (data["releaseDate"] as? String) ?: "",
      videoUrl = (data["videoUrl"] as? String) ?: "",
      posterUrl = (data["posterUrl"] as? String) ?: "",
      backdropUrl = (data["backdropUrl"] as? String) ?: "",
      trailerUrl = (data["trailerUrl"] as? String) ?: "",
      rating = (data["rating"] as? String) ?: "PG-13",
      runtimeMinutes = (data["runtimeMinutes"] as? Number)?.toInt() ?: 120,
      publisherId = (data["publisherId"] as? String) ?: "",
      publisherName = (data["publisherName"] as? String) ?: "",
      status = (data["status"] as? String) ?: "draft",
      feedback = (data["feedback"] as? String) ?: "",
      createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
      updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    )
  }

  suspend fun saveContentItem(
    item: ContentItem,
    currentUid: String,
    currentUserName: String,
    currentEmail: String
  ): Result<String> {
    val isEdit = item.id.isNotBlank()
    val safeStatus = if (item.status == "pending") "pending" else "draft"
    val ref = if (isEdit) {
      firestore.collection("movies").document(item.id)
    } else {
      firestore.collection("movies").document()
    }
    val path = "movies/${ref.id}"

    return try {
      if (isEdit) {
        if (item.publisherId.isNotBlank() && item.publisherId != currentUid) {
          return Result.failure(IllegalStateException("You can only edit your own submissions."))
        }
        if (item.status == "approved") {
          return Result.failure(IllegalStateException("Published catalog titles cannot be edited here."))
        }
        // Preserve existing document ID, publisherId, and createdAt on in-place update
        val updateMap = mapOf(
          "id" to ref.id,
          "title" to item.title.trim().take(200),
          "description" to item.description.trim().take(5000),
          "contentType" to if (item.contentType == "tv") "tv" else "movie",
          "genre" to item.genre.trim().take(100),
          "releaseYear" to item.releaseYear.coerceIn(1900, 2100),
          "videoUrl" to item.videoUrl.trim().take(2000),
          "posterUrl" to item.posterUrl.trim().take(2000),
          "backdropUrl" to item.backdropUrl.trim().take(2000),
          "trailerUrl" to item.trailerUrl.trim().take(2000),
          "rating" to item.rating.trim().take(20),
          "runtimeMinutes" to item.runtimeMinutes.coerceIn(0, 10000),
          "publisherName" to item.publisherName.ifBlank { currentUserName }.take(120),
          "status" to safeStatus,
          "feedback" to if (safeStatus == "pending") "" else item.feedback.take(1000),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        ref.update(updateMap).await()
      } else {
        val createMap = mapOf(
          "id" to ref.id,
          "title" to item.title.trim().take(200),
          "description" to item.description.trim().take(5000),
          "contentType" to if (item.contentType == "tv") "tv" else "movie",
          "genre" to item.genre.trim().take(100),
          "releaseYear" to item.releaseYear.coerceIn(1900, 2100),
          "videoUrl" to item.videoUrl.trim().take(2000),
          "posterUrl" to item.posterUrl.trim().take(2000),
          "backdropUrl" to item.backdropUrl.trim().take(2000),
          "trailerUrl" to item.trailerUrl.trim().take(2000),
          "rating" to item.rating.trim().take(20),
          "runtimeMinutes" to item.runtimeMinutes.coerceIn(0, 10000),
          "publisherId" to currentUid,
          "publisherName" to currentUserName.ifBlank { "Creator" }.take(120),
          "status" to safeStatus,
          "feedback" to "",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        ref.set(createMap).await()
      }

      val actionName = when {
        isEdit && safeStatus == "pending" -> "RESUBMIT_CONTENT"
        isEdit -> "UPDATE_DRAFT_CONTENT"
        safeStatus == "pending" -> "SUBMIT_CONTENT"
        else -> "CREATE_DRAFT_CONTENT"
      }
      logAuditSafe(currentUid, currentEmail, actionName, ref.id)

      val msg = when {
        isEdit && safeStatus == "pending" -> "Submission '${item.title}' resubmitted for review."
        isEdit -> "Draft '${item.title}' updated."
        safeStatus == "pending" -> "Submission '${item.title}' sent for review."
        else -> "Draft '${item.title}' saved."
      }
      Result.success(msg)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun reviewContentItem(
    item: ContentItem,
    currentUid: String,
    currentEmail: String,
    newStatus: String,
    feedback: String
  ): Result<String> {
    if (item.publisherId.isNotBlank() && item.publisherId == currentUid) {
      return Result.failure(
        IllegalStateException("Self-approval is prohibited: you cannot approve or review your own submission.")
      )
    }
    val payload = mapOf(
      "contentId" to item.id,
      "status" to newStatus,
      "feedback" to feedback.trim().take(1000)
    )
    val cfResult = callCloudFunction("reviewContentSubmission", payload)
    if (cfResult.isSuccess) {
      return Result.success("Submission '${item.title}' marked as ${newStatus.replace('_', ' ')}.")
    }

    val cfError = cfResult.exceptionOrNull()
    if (cfError is CloudFunctionUnavailableException) {
      // Cloud Functions not deployed yet: fall back to rule-enforced in-place Firestore update
      // (firestore.rules still blocks existing().publisherId == request.auth.uid).
      val path = "movies/${item.id}"
      return try {
        firestore.collection("movies").document(item.id).update(
          mapOf(
            "status" to newStatus,
            "feedback" to feedback.trim().take(1000),
            "reviewedBy" to currentUid,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
        logAuditSafe(currentUid, currentEmail, "REVIEW_CONTENT_${newStatus.uppercase()}", item.id)
        Result.success("Submission '${item.title}' marked as ${newStatus.replace('_', ' ')}.")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, path)
        Result.failure(e)
      }
    }

    return Result.failure(cfError ?: IllegalStateException("Review failed"))
  }

  suspend fun setUserRoleViaBackend(
    targetUid: String,
    targetRole: String,
    grantPublisher: Boolean? = null
  ): Result<String> {
    val payload = mutableMapOf<String, Any?>(
      "targetUid" to targetUid,
      "targetRole" to targetRole
    )
    if (grantPublisher != null) {
      payload["grantPublisher"] = grantPublisher
    }
    return callCloudFunction("setUserRole", payload).map {
      "Role updated to ${targetRole.uppercase()} via trusted server function."
    }
  }

  suspend fun setUserStatusViaBackend(targetUid: String, newStatus: String): Result<String> {
    val payload = mapOf(
      "targetUid" to targetUid,
      "newStatus" to newStatus
    )
    return callCloudFunction("setUserStatus", payload).map {
      "User status updated to ${newStatus.uppercase()} via trusted server function."
    }
  }

  fun observeAccessApplications(): Flow<List<AccessApplication>> = callbackFlow {
    val listener = firestore.collection("access_applications").addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.LIST, "access_applications")
        trySend(emptyList())
        return@addSnapshotListener
      }
      val apps = snapshot?.documents?.map { doc ->
        val data = doc.data ?: emptyMap<String, Any?>()
        AccessApplication(
          id = doc.id,
          uid = (data["uid"] as? String) ?: "",
          email = (data["email"] as? String) ?: "",
          fullName = (data["fullName"] as? String) ?: "",
          studioName = (data["studioName"] as? String) ?: "",
          requestedRole = (data["requestedRole"] as? String) ?: "publisher",
          reason = (data["reason"] as? String) ?: "",
          status = (data["status"] as? String) ?: "pending",
          createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
      } ?: emptyList()
      trySend(apps)
    }
    awaitClose { listener.remove() }
  }

  suspend fun submitAccessApplication(app: AccessApplication): Result<Unit> {
    val ref = firestore.collection("access_applications").document()
    val path = "access_applications/${ref.id}"
    return try {
      val map = mapOf(
        "id" to ref.id,
        "uid" to app.uid,
        "email" to app.email.take(320),
        "fullName" to app.fullName.trim().take(120),
        "studioName" to app.studioName.trim().take(120),
        "requestedRole" to if (app.requestedRole == "admin") "admin" else "publisher",
        "reason" to app.reason.trim().take(2000),
        "status" to "pending",
        "createdAt" to FieldValue.serverTimestamp()
      )
      ref.set(map).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun reviewAccessApplicationViaBackend(applicationId: String, decision: String): Result<String> {
    val payload = mapOf(
      "applicationId" to applicationId,
      "decision" to decision
    )
    return callCloudFunction("reviewAccessApplication", payload).map {
      "Application $decision via trusted server function."
    }
  }

  fun observeAuditLogs(): Flow<List<AuditLog>> = callbackFlow {
    val listener = firestore.collection("audit_logs").addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.LIST, "audit_logs")
        trySend(emptyList())
        return@addSnapshotListener
      }
      val logs = snapshot?.documents?.map { doc ->
        val data = doc.data ?: emptyMap<String, Any?>()
        AuditLog(
          id = doc.id,
          adminId = (data["adminId"] as? String) ?: "",
          adminEmail = (data["adminEmail"] as? String) ?: "",
          action = (data["action"] as? String) ?: "",
          target = (data["target"] as? String) ?: "",
          timestamp = doc.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
      }?.sortedByDescending { it.timestamp?.seconds ?: 0L } ?: emptyList()
      trySend(logs)
    }
    awaitClose { listener.remove() }
  }

  private suspend fun logAuditSafe(adminId: String, adminEmail: String, action: String, target: String) {
    val ref = firestore.collection("audit_logs").document()
    val path = "audit_logs/${ref.id}"
    try {
      val map = mapOf(
        "id" to ref.id,
        "adminId" to adminId,
        "adminEmail" to adminEmail.take(320),
        "action" to action.take(120),
        "target" to target.take(200),
        "timestamp" to FieldValue.serverTimestamp()
      )
      ref.set(map).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
    }
  }

  private suspend fun callCloudFunction(name: String, payload: Map<String, Any?>): Result<JSONObject> {
    return try {
      val user = FirebaseAuth.getInstance().currentUser
        ?: return Result.failure(IllegalStateException("You must be signed in."))
      val idToken = user.getIdToken(false).await()?.token
        ?: return Result.failure(IllegalStateException("Unable to acquire Firebase ID token."))

      val appCheckToken = try {
        FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
      } catch (_: Exception) {
        null
      }

      val bodyJson = JSONObject().apply {
        put("data", JSONObject(payload))
      }.toString()

      withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
          .url("$functionsBaseUrl/$name")
          .addHeader("Authorization", "Bearer $idToken")
          .addHeader("Content-Type", "application/json")
          .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (!appCheckToken.isNullOrBlank()) {
          requestBuilder.addHeader("X-Firebase-AppCheck", appCheckToken)
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        val rawBody = response.body?.string().orEmpty()
        if (response.code == 404) {
          return@withContext Result.failure(
            CloudFunctionUnavailableException(
              name,
              "Cloud Function '$name' is not deployed on Firebase yet. Deploy functions/ with 'firebase deploy --only functions'."
            )
          )
        }
        val parsed = runCatching { JSONObject(rawBody) }.getOrNull()
        if (!response.isSuccessful) {
          val errorObj = parsed?.optJSONObject("error")
          val msg = errorObj?.optString("message")?.takeIf { it.isNotBlank() }
            ?: "Server rejected request (${response.code})."
          return@withContext Result.failure(IllegalStateException(msg))
        }
        Result.success(parsed?.optJSONObject("result") ?: JSONObject())
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: java.io.IOException) {
      Result.failure(
        CloudFunctionUnavailableException(
          name,
          "Cloud Function '$name' unreachable (${e.localizedMessage}). Ensure functions/ is deployed."
        )
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
