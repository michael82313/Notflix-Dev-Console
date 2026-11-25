package com.example.ui.auth

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.UserAccount
import com.example.data.repository.AccountLookupResult
import com.example.data.repository.DevConsoleRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface AuthState {
  object Loading : AuthState
  object SignedOut : AuthState
  data class SignedIn(val user: UserAccount, val firebaseUser: FirebaseUser) : AuthState
  object AccessDenied : AuthState
  object AccountSuspended : AuthState
  data class VerificationError(val message: String, val isPermissionDenied: Boolean) : AuthState
}

internal fun mergeAccountWithClaims(
  docAccount: UserAccount?,
  uid: String,
  email: String?,
  displayName: String?,
  claims: Map<String, Any?>
): UserAccount {
  val claimRole = ((claims["role"] as? String) ?: (claims["access"] as? String) ?: "").trim()
  val claimRoles = when (val r = claims["roles"]) {
    is List<*> -> r.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
    is Map<*, *> -> r.entries.filter { it.value == true }.mapNotNull { it.key?.toString()?.trim() }
    else -> emptyList()
  }
  val claimIsOwner = (claims["isOwner"] as? Boolean) == true || (claims["owner"] as? Boolean) == true ||
    claimRole.equals("owner", ignoreCase = true) || claimRoles.any { it.equals("owner", ignoreCase = true) }
  val claimIsAdmin = claimIsOwner || (claims["isAdmin"] as? Boolean) == true || (claims["admin"] as? Boolean) == true ||
    claimRole.equals("admin", ignoreCase = true) || claimRoles.any { it.equals("admin", ignoreCase = true) }
  val claimPublisherStatus = (claims["publisherStatus"] as? String) ?: ""
  val claimIsPublisher = claimIsOwner ||
    (claims["isPublisher"] as? Boolean) == true || (claims["publisher"] as? Boolean) == true ||
    (claims["isDistributor"] as? Boolean) == true || (claims["distributor"] as? Boolean) == true ||
    claimPublisherStatus.equals("approved", ignoreCase = true) ||
    claimRole.equals("publisher", ignoreCase = true) || claimRole.equals("distributor", ignoreCase = true) ||
    claimRoles.any { it.equals("publisher", ignoreCase = true) || it.equals("distributor", ignoreCase = true) }

  val base = docAccount ?: UserAccount(
    uid = uid,
    email = email.orEmpty(),
    displayName = displayName?.ifBlank { "Creator" } ?: "Creator",
    role = claimRole.ifBlank {
      when {
        claimIsOwner -> "owner"
        claimIsAdmin -> "admin"
        claimIsPublisher -> "publisher"
        else -> "viewer"
      }
    },
    roles = claimRoles,
    isOwner = claimIsOwner,
    isAdmin = claimIsAdmin,
    isPublisher = claimIsPublisher,
    publisherStatus = claimPublisherStatus,
    status = "active"
  )

  val mergedOwner = base.isOwnerAccount() || claimIsOwner
  val mergedAdmin = mergedOwner || base.hasExplicitAdminRole() || claimIsAdmin
  val mergedPublisher = mergedOwner || base.isPublisherAccount() || claimIsPublisher
  val mergedRoles = (base.roles + claimRoles).distinct()
  val mergedPubStatus = when {
    base.publisherStatus.isNotBlank() -> base.publisherStatus
    claimPublisherStatus.isNotBlank() -> claimPublisherStatus
    mergedPublisher -> "approved"
    else -> ""
  }
  val mergedPrimaryRole = when {
    mergedOwner -> "owner"
    mergedAdmin -> "admin"
    mergedPublisher -> "publisher"
    base.role.isNotBlank() -> base.role
    else -> "viewer"
  }

  return base.copy(
    role = mergedPrimaryRole,
    roles = mergedRoles,
    isOwner = mergedOwner,
    isAdmin = mergedAdmin,
    isPublisher = mergedPublisher,
    publisherStatus = mergedPubStatus
  )
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = DevConsoleRepository(application)
  private val firebaseAuth = FirebaseAuth.getInstance()

  private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
  val authState: StateFlow<AuthState> = _authState.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  init {
    checkCurrentUser()
  }

  fun checkCurrentUser() {
    val currentFirebaseUser = firebaseAuth.currentUser
    if (currentFirebaseUser == null) {
      _authState.value = AuthState.SignedOut
    } else {
      _authState.value = AuthState.Loading
      viewModelScope.launch {
        verifyAccount(currentFirebaseUser)
      }
    }
  }

  fun signInWithEmail(email: String, pass: String) {
    viewModelScope.launch {
      _errorMessage.value = null
      _authState.value = AuthState.Loading
      try {
        val result = firebaseAuth.signInWithEmailAndPassword(email, pass).await()
        val firebaseUser = result.user
        if (firebaseUser != null) {
          verifyAccount(firebaseUser)
        } else {
          _authState.value = AuthState.SignedOut
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e("AuthVM", "Email sign in failed", e)
        _errorMessage.value = e.localizedMessage ?: "Sign in failed"
        _authState.value = AuthState.SignedOut
      }
    }
  }

  fun signUpWithEmail(email: String, pass: String, displayName: String) {
    viewModelScope.launch {
      _errorMessage.value = null
      _authState.value = AuthState.Loading
      try {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, pass).await()
        val firebaseUser = result.user
        if (firebaseUser != null) {
          val account = UserAccount(
            uid = firebaseUser.uid,
            email = email,
            displayName = displayName.ifEmpty { "Creator" },
            role = "viewer",
            status = "active"
          )
          repository.createViewerAccountOnSignUp(account)
          _authState.value = AuthState.AccessDenied
        } else {
          _authState.value = AuthState.SignedOut
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e("AuthVM", "Email sign up failed", e)
        _errorMessage.value = e.localizedMessage ?: "Sign up failed"
        _authState.value = AuthState.SignedOut
      }
    }
  }

  private suspend fun verifyAccount(firebaseUser: FirebaseUser) {
    try {
      val claims: Map<String, Any?> = try {
        firebaseUser.getIdToken(true).await().claims
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w("AuthVM", "Forced token refresh failed, using cached token claims", e)
        try {
          firebaseUser.getIdToken(false).await().claims
        } catch (inner: Exception) {
          emptyMap()
        }
      }

      when (val lookup = repository.getUserAccountResult(firebaseUser.uid, firebaseUser.email)) {
        is AccountLookupResult.Error -> {
          // CRITICAL: Never overwrite or downgrade an existing user account on network or permission error!
          Log.e("AuthVM", "Account lookup failed without modifying Firestore: ${lookup.message}")
          _errorMessage.value = lookup.message
          _authState.value = AuthState.VerificationError(lookup.message, lookup.isPermissionDenied)
        }
        is AccountLookupResult.NotFound -> {
          // CRITICAL: Do NOT write a "viewer" doc over missing/unmatched accounts during login verification.
          val claimsOnlyAccount = mergeAccountWithClaims(
            docAccount = null,
            uid = firebaseUser.uid,
            email = firebaseUser.email,
            displayName = firebaseUser.displayName,
            claims = claims
          )
          if (claimsOnlyAccount.isAuthorizedDevConsole()) {
            _authState.value = AuthState.SignedIn(claimsOnlyAccount, firebaseUser)
          } else {
            _authState.value = AuthState.AccessDenied
          }
        }
        is AccountLookupResult.Found -> {
          val mergedAccount = mergeAccountWithClaims(
            docAccount = lookup.account,
            uid = firebaseUser.uid,
            email = firebaseUser.email,
            displayName = firebaseUser.displayName,
            claims = claims
          )
          when {
            mergedAccount.isSuspended() -> {
              _authState.value = AuthState.AccountSuspended
            }
            mergedAccount.isAuthorizedDevConsole() -> {
              _authState.value = AuthState.SignedIn(mergedAccount, firebaseUser)
            }
            else -> {
              _authState.value = AuthState.AccessDenied
            }
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e("AuthVM", "Exception during verification", e)
      val msg = "Authorization verification failed: ${e.localizedMessage ?: "Unknown error"}"
      _errorMessage.value = msg
      _authState.value = AuthState.VerificationError(msg, isPermissionDenied = false)
    }
  }

  fun signInWithGoogle(context: Context) {
    viewModelScope.launch {
      _errorMessage.value = null
      _authState.value = AuthState.Loading
      try {
        val webClientId = context.getString(R.string.default_web_client_id)
        val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
          .build()

        val request = GetCredentialRequest.Builder()
          .addCredentialOption(googleIdOption)
          .build()

        val credentialManager = CredentialManager.create(context)
        val result = credentialManager.getCredential(context, request)
        handleCredentialResult(result)
      } catch (e: GetCredentialCancellationException) {
        Log.w("AuthVM", "Sign-in cancelled by user", e)
        _errorMessage.value = "Sign-in cancelled."
        checkCurrentUser()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e("AuthVM", "Sign-in failed", e)
        _errorMessage.value = "Sign-in failed: ${e.localizedMessage}"
        _authState.value = AuthState.SignedOut
      }
    }
  }

  private suspend fun handleCredentialResult(result: GetCredentialResponse) {
    val credential = result.credential
    if (credential is androidx.credentials.CustomCredential &&
      credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      try {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val googleIdToken = googleIdTokenCredential.idToken
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
        val firebaseUser = authResult.user
        if (firebaseUser != null) {
          verifyAccount(firebaseUser)
        } else {
          _authState.value = AuthState.SignedOut
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e("AuthVM", "Firebase auth failed", e)
        _errorMessage.value = "Authentication failed: ${e.localizedMessage}"
        _authState.value = AuthState.SignedOut
      }
    } else {
      _authState.value = AuthState.SignedOut
    }
  }

  fun signOut() {
    viewModelScope.launch {
      withContext(NonCancellable) {
        firebaseAuth.signOut()
      }
      _errorMessage.value = null
      _authState.value = AuthState.SignedOut
    }
  }
}
