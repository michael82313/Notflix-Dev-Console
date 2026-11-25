package com.example.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.model.AccessApplication
import com.example.data.model.AuditLog
import com.example.data.model.ContentItem
import com.example.data.model.UserAccount
import com.example.data.repository.DevConsoleRepository
import com.example.ui.ai.AiAssistantScreen
import com.example.ui.auth.AuthState
import com.example.ui.auth.AuthViewModel
import com.example.ui.screens.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
  object Splash : Screen("splash")
  object SignIn : Screen("signin")
  object AccessApplication : Screen("access_application")
  object AccessDenied : Screen("access_denied")
  object AccountSuspended : Screen("account_suspended")
  object VerificationError : Screen("verification_error")
  object OwnerDashboard : Screen("owner_dashboard")
  object AdminDashboard : Screen("admin_dashboard")
  object PublisherDashboard : Screen("publisher_dashboard")
  object ContentManagement : Screen("content_management")
  object ContentSubmission : Screen("content_submission")
  object ContentReview : Screen("content_review")
  object UserManagement : Screen("user_management")
  object AccessApplicationsReview : Screen("access_applications_review")
  object AuditLog : Screen("audit_log")
  object AccountDetails : Screen("account_details")
  object AiAssistant : Screen("ai_assistant")
}

@Composable
fun NotflixNavGraph() {
  val navController = rememberNavController()
  val authViewModel: AuthViewModel = viewModel()
  val authState by authViewModel.authState.collectAsState()
  val errorMessage by authViewModel.errorMessage.collectAsState()

  val context = LocalContext.current
  val repository = remember { DevConsoleRepository(context) }
  val scope = rememberCoroutineScope()

  var contentItems by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
  var users by remember { mutableStateOf<List<UserAccount>>(emptyList()) }
  var auditLogs by remember { mutableStateOf<List<AuditLog>>(emptyList()) }
  var accessApplications by remember { mutableStateOf<List<AccessApplication>>(emptyList()) }

  var editingContentItem by remember { mutableStateOf<ContentItem?>(null) }
  var initialOnlyMine by remember { mutableStateOf(false) }
  var actionFeedbackBanner by remember { mutableStateOf<String?>(null) }

  // Gate all Firestore snapshot listeners strictly to active, authorized SignedIn accounts
  val signedInUser = (authState as? AuthState.SignedIn)?.user
  LaunchedEffect(signedInUser?.uid, signedInUser?.role, signedInUser?.isAdmin, signedInUser?.isPublisher, signedInUser?.publisherStatus) {
    if (signedInUser == null) {
      contentItems = emptyList()
      users = emptyList()
      auditLogs = emptyList()
      accessApplications = emptyList()
      editingContentItem = null
      return@LaunchedEffect
    }

    if (signedInUser.canAccessAdminTools()) {
      launch { repository.observeAllContentItems().collect { contentItems = it } }
      launch { repository.observeUsers().collect { users = it } }
      launch { repository.observeAuditLogs().collect { auditLogs = it } }
      launch { repository.observeAccessApplications().collect { accessApplications = it } }
    } else if (signedInUser.canAccessPublisherTools()) {
      launch { repository.observeMyContentItems(signedInUser.uid).collect { contentItems = it } }
    }
  }

  fun navigateForAuthState(state: AuthState, popRoute: String) {
    when (state) {
      is AuthState.SignedIn -> {
        val target = when (state.user.getEffectiveRole()) {
          "owner" -> Screen.OwnerDashboard.route
          "admin" -> Screen.AdminDashboard.route
          "publisher" -> Screen.PublisherDashboard.route
          else -> Screen.AccessDenied.route
        }
        navController.navigate(target) { popUpTo(popRoute) { inclusive = true } }
      }
      is AuthState.AccessDenied -> {
        navController.navigate(Screen.AccessDenied.route) { popUpTo(popRoute) { inclusive = true } }
      }
      is AuthState.AccountSuspended -> {
        navController.navigate(Screen.AccountSuspended.route) { popUpTo(popRoute) { inclusive = true } }
      }
      is AuthState.VerificationError -> {
        navController.navigate(Screen.VerificationError.route) { popUpTo(popRoute) { inclusive = true } }
      }
      is AuthState.SignedOut -> {
        navController.navigate(Screen.SignIn.route) { popUpTo(popRoute) { inclusive = true } }
      }
      AuthState.Loading -> {}
    }
  }

  NavHost(navController = navController, startDestination = Screen.Splash.route) {
    composable(Screen.Splash.route) {
      SplashScreen(
        onTimeout = {
          if (authState !is AuthState.Loading) {
            navigateForAuthState(authState, Screen.Splash.route)
          }
        }
      )
      LaunchedEffect(authState) {
        if (authState !is AuthState.Loading) {
          navigateForAuthState(authState, Screen.Splash.route)
        }
      }
    }

    composable(Screen.SignIn.route) {
      SignInScreen(
        onGoogleSignInClicked = { ctx -> authViewModel.signInWithGoogle(ctx) },
        onEmailSignInClicked = { email, pass -> authViewModel.signInWithEmail(email, pass) },
        onEmailSignUpClicked = { email, pass, name -> authViewModel.signUpWithEmail(email, pass, name) },
        onCreateAccountClicked = { navController.navigate(Screen.AccessApplication.route) },
        errorMessage = errorMessage
      )

      LaunchedEffect(authState) {
        if (authState !is AuthState.SignedOut && authState !is AuthState.Loading) {
          navigateForAuthState(authState, Screen.SignIn.route)
        }
      }
    }

    composable(Screen.AccessApplication.route) {
      val currentUser = FirebaseAuth.getInstance().currentUser
      AccessApplicationScreen(
        currentUid = currentUser?.uid ?: "anonymous",
        currentEmail = currentUser?.email ?: "",
        onSubmit = { app ->
          scope.launch {
            repository.submitAccessApplication(app)
          }
        },
        onBackToSignIn = { navController.popBackStack() }
      )
    }

    composable(Screen.AccessDenied.route) {
      AccessDeniedScreen(
        onSignOut = {
          authViewModel.signOut()
          navController.navigate(Screen.SignIn.route) { popUpTo(0) { inclusive = true } }
        },
        onRequestAccess = { navController.navigate(Screen.AccessApplication.route) }
      )
    }

    composable(Screen.VerificationError.route) {
      val errState = authState as? AuthState.VerificationError
      VerificationErrorScreen(
        message = errState?.message ?: "Unable to verify account permissions.",
        isPermissionDenied = errState?.isPermissionDenied ?: false,
        onRetry = {
          authViewModel.checkCurrentUser()
        },
        onSignOut = {
          authViewModel.signOut()
          navController.navigate(Screen.SignIn.route) { popUpTo(0) { inclusive = true } }
        }
      )
      LaunchedEffect(authState) {
        if (authState !is AuthState.VerificationError && authState !is AuthState.Loading) {
          navigateForAuthState(authState, Screen.VerificationError.route)
        }
      }
    }

    composable(Screen.AccountSuspended.route) {
      AccountSuspendedScreen(
        onSignOut = {
          authViewModel.signOut()
          navController.navigate(Screen.SignIn.route) { popUpTo(0) { inclusive = true } }
        }
      )
    }

    composable(Screen.OwnerDashboard.route) {
      val user = (authState as? AuthState.SignedIn)?.user
      if (user != null) {
        key(user.uid) {
          OwnerDashboardScreen(
            user = user,
            onNavigateToContent = {
              initialOnlyMine = false
              actionFeedbackBanner = null
              navController.navigate(Screen.ContentManagement.route)
            },
            onNavigateToMySubmissions = {
              initialOnlyMine = true
              actionFeedbackBanner = null
              navController.navigate(Screen.ContentManagement.route)
            },
            onNavigateToSubmit = {
              editingContentItem = null
              navController.navigate(Screen.ContentSubmission.route)
            },
            onNavigateToReview = {
              actionFeedbackBanner = null
              navController.navigate(Screen.ContentReview.route)
            },
            onNavigateToUsers = {
              actionFeedbackBanner = null
              navController.navigate(Screen.UserManagement.route)
            },
            onNavigateToApplications = {
              actionFeedbackBanner = null
              navController.navigate(Screen.AccessApplicationsReview.route)
            },
            onNavigateToAudit = { navController.navigate(Screen.AuditLog.route) },
            onNavigateToAi = { navController.navigate(Screen.AiAssistant.route) },
            onNavigateToAccount = { navController.navigate(Screen.AccountDetails.route) },
            onSignOut = {
              authViewModel.signOut()
              navController.navigate(Screen.SignIn.route) { popUpTo(0) { inclusive = true } }
            }
          )
        }
      }
    }

    composable(Screen.AdminDashboard.route) {
      val user = (authState as? AuthState.SignedIn)?.user
      if (user != null) {
        key(user.uid) {
          AdminDashboardScreen(
            user = user,
            onNavigateToContent = {
              initialOnlyMine = false
              actionFeedbackBanner = null
              navController.navigate(Screen.ContentManagement.route)
            },
            onNavigateToReview = {
              actionFeedbackBanner = null
              navController.navigate(Screen.ContentReview.route)
            },
            onNavigateToUsers = {
              actionFeedbackBanner = null
              navController.navigate(Screen.UserManagement.route)
            },
            onNavigateToApplications = {
              actionFeedbackBanner = null
              navController.navigate(Screen.AccessApplicationsReview.route)
            },
            onNavigateToAudit = { navController.navigate(Screen.AuditLog.route) },
            onNavigateToSubmit = {
              editingContentItem = null
              navController.navigate(Screen.ContentSubmission.route)
            },
            onNavigateToMySubmissions = {
              initialOnlyMine = true
              actionFeedbackBanner = null
              navController.navigate(Screen.ContentManagement.route)
            },
            onNavigateToAi = { navController.navigate(Screen.AiAssistant.route) },
            onNavigateToAccount = { navController.navigate(Screen.AccountDetails.route) },
            onSignOut = {
              authViewModel.signOut()
              navController.navigate(Screen.SignIn.route) { popUpTo(0) { inclusive = true } }
            }
          )
        }
      }
    }

    composable(Screen.PublisherDashboard.route) {
      val user = (authState as? AuthState.SignedIn)?.user
      if (user != null) {
        key(user.uid) {
          PublisherDashboardScreen(
            user = user,
            onNavigateToSubmit = {
              editingContentItem = null
              navController.navigate(Screen.ContentSubmission.route)
            },
            onNavigateToMyContent = {
              initialOnlyMine = true
              actionFeedbackBanner = null
              navController.navigate(Screen.ContentManagement.route)
            },
            onNavigateToAi = { navController.navigate(Screen.AiAssistant.route) },
            onNavigateToAccount = { navController.navigate(Screen.AccountDetails.route) },
            onSignOut = {
              authViewModel.signOut()
              navController.navigate(Screen.SignIn.route) { popUpTo(0) { inclusive = true } }
            }
          )
        }
      }
    }

    composable(Screen.ContentManagement.route) {
      val user = (authState as? AuthState.SignedIn)?.user
      if (user != null) {
        ContentManagementScreen(
          items = contentItems,
          currentUser = user,
          initialOnlyMine = initialOnlyMine,
          statusBanner = actionFeedbackBanner,
          onDismissBanner = { actionFeedbackBanner = null },
          onBack = { navController.popBackStack() },
          onAddNew = {
            editingContentItem = null
            navController.navigate(Screen.ContentSubmission.route)
          },
          onEditItem = { item ->
            editingContentItem = item
            navController.navigate(Screen.ContentSubmission.route)
          }
        )
      }
    }

    composable(Screen.ContentSubmission.route) {
      val user = (authState as? AuthState.SignedIn)?.user
      if (user != null) {
        ContentSubmissionScreen(
          currentUserId = user.uid,
          currentUserName = user.displayName,
          existingItem = editingContentItem,
          onSubmit = { item ->
            scope.launch {
              val res = repository.saveContentItem(
                item = item,
                currentUid = user.uid,
                currentUserName = user.displayName,
                currentEmail = user.email
              )
              actionFeedbackBanner = res.fold(
                onSuccess = { it },
                onFailure = { "Error: ${it.localizedMessage}" }
              )
            }
          },
          onBack = {
            editingContentItem = null
            navController.popBackStack()
          }
        )
      }
    }

    composable(Screen.ContentReview.route) {
      val user = (authState as? AuthState.SignedIn)?.user
      if (user != null) {
        ContentReviewScreen(
          items = contentItems,
          currentUserUid = user.uid,
          statusBanner = actionFeedbackBanner,
          onDismissBanner = { actionFeedbackBanner = null },
          onReviewAction = { item, status, feedback ->
            scope.launch {
              val res = repository.reviewContentItem(
                item = item,
                currentUid = user.uid,
                currentEmail = user.email,
                newStatus = status,
                feedback = feedback
              )
              actionFeedbackBanner = res.fold(
                onSuccess = { it },
                onFailure = { "Review blocked: ${it.localizedMessage}" }
              )
            }
          },
          onBack = { navController.popBackStack() }
        )
      }
    }

    composable(Screen.UserManagement.route) {
      val user = (authState as? AuthState.SignedIn)?.user
      if (user != null) {
        UserManagementScreen(
          users = users,
          currentUser = user,
          statusBanner = actionFeedbackBanner,
          onDismissBanner = { actionFeedbackBanner = null },
          onUpdateRole = { uid, newRole, grantPublisher ->
            scope.launch {
              val res = repository.setUserRoleViaBackend(uid, newRole, grantPublisher)
              actionFeedbackBanner = res.fold(
                onSuccess = { it },
                onFailure = { "Action failed: ${it.localizedMessage}" }
              )
            }
          },
          onUpdateStatus = { uid, newStatus ->
            scope.launch {
              val res = repository.setUserStatusViaBackend(uid, newStatus)
              actionFeedbackBanner = res.fold(
                onSuccess = { it },
                onFailure = { "Action failed: ${it.localizedMessage}" }
              )
            }
          },
          onBack = { navController.popBackStack() }
        )
      }
    }

    composable(Screen.AccessApplicationsReview.route) {
      val user = (authState as? AuthState.SignedIn)?.user
      if (user != null) {
        AccessApplicationsReviewScreen(
          applications = accessApplications,
          currentUser = user,
          statusBanner = actionFeedbackBanner,
          onDismissBanner = { actionFeedbackBanner = null },
          onDecision = { appId, decision ->
            scope.launch {
              val res = repository.reviewAccessApplicationViaBackend(appId, decision)
              actionFeedbackBanner = res.fold(
                onSuccess = { it },
                onFailure = { "Action failed: ${it.localizedMessage}" }
              )
            }
          },
          onBack = { navController.popBackStack() }
        )
      }
    }

    composable(Screen.AuditLog.route) {
      AuditLogScreen(
        logs = auditLogs,
        onBack = { navController.popBackStack() }
      )
    }

    composable(Screen.AiAssistant.route) {
      AiAssistantScreen(
        onBack = { navController.popBackStack() }
      )
    }

    composable(Screen.AccountDetails.route) {
      val user = (authState as? AuthState.SignedIn)?.user
      if (user != null) {
        AccountDetailsScreen(
          user = user,
          onBack = { navController.popBackStack() },
          onSignOut = {
            authViewModel.signOut()
            navController.navigate(Screen.SignIn.route) { popUpTo(0) { inclusive = true } }
          }
        )
      }
    }
  }
}
