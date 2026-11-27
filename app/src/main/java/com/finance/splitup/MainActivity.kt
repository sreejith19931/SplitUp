package com.finance.splitup

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import kotlinx.coroutines.tasks.await
import com.finance.splitup.ui.navigation.*
import com.finance.splitup.ui.screens.*
import com.finance.splitup.ui.theme.SplitUpTheme
import com.finance.splitup.viewmodel.FinanceViewModel
import com.finance.splitup.data.UiMessage
import com.finance.splitup.ui.theme.SplitUpType

class MainActivity : FragmentActivity() {
    private val currentIntentState = mutableStateOf<android.content.Intent?>(null)

    companion object {
        var isExternalActionInProgress: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIntentState.value = intent
        enableEdgeToEdge()
        setContent {
            val viewModel: FinanceViewModel = viewModel()
            val authViewModel: com.finance.splitup.viewmodel.AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            val context = LocalContext.current
            val prefs = remember { com.finance.splitup.data.EncryptedPrefs.getPreferences(context, "splitup_prefs") }
            val currentUserId = when (val state = authState) {
                is com.finance.splitup.viewmodel.AuthState.Authenticated -> state.user.uid
                is com.finance.splitup.viewmodel.AuthState.EmailVerificationPending -> state.user.email ?: "pending"
                is com.finance.splitup.viewmodel.AuthState.LocalDemo -> "local_demo"
                else -> "guest"
            }
            var hasCompletedOnboarding by remember(currentUserId) {
                mutableStateOf(prefs.getBoolean("has_completed_onboarding_$currentUserId", false))
            }
            var isCheckingCloudOnboarding by remember(currentUserId) {
                mutableStateOf(currentUserId != "guest" && currentUserId != "local_demo" && !hasCompletedOnboarding)
            }

            LaunchedEffect(currentUserId) {
                if (currentUserId != "guest" && currentUserId != "local_demo" && !hasCompletedOnboarding) {
                    isCheckingCloudOnboarding = true
                    try {
                        val userDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(currentUserId)
                            .get()
                            .await()
                        if (userDoc.exists() && (userDoc.getBoolean("isOnboardingCompleted") == true || userDoc.getBoolean("has_completed_onboarding") == true)) {
                            prefs.edit()
                                .putBoolean("has_completed_onboarding_$currentUserId", true)
                                .putBoolean("has_completed_onboarding", true)
                                .apply()
                            hasCompletedOnboarding = true
                        }
                    } catch (_: Exception) {}
                    isCheckingCloudOnboarding = false
                } else {
                    isCheckingCloudOnboarding = false
                }
            }

            LaunchedEffect(authState) {
                when (authState) {
                    is com.finance.splitup.viewmodel.AuthState.Authenticated -> {
                        viewModel.triggerCloudSync()
                        com.finance.splitup.data.SplitUpMessagingService.uploadTokenToFirestore()
                    }
                    is com.finance.splitup.viewmodel.AuthState.Unauthenticated -> {
                        viewModel.clearLocalDataOnSignOut()
                    }
                    else -> {}
                }
            }

            SplitUpTheme(themeMode = viewModel.themeMode) {
                when (val state = authState) {
                    is com.finance.splitup.viewmodel.AuthState.LocalDemo,
                    is com.finance.splitup.viewmodel.AuthState.Authenticated -> {
                        if (isCheckingCloudOnboarding) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (!hasCompletedOnboarding) {
                            OnboardingSetupScreen(
                                viewModel = viewModel,
                                onSetupComplete = {
                                    prefs.edit().putBoolean("has_completed_onboarding_$currentUserId", true).apply()
                                    hasCompletedOnboarding = true
                                }
                            )
                        } else {
                            SplitUpApp(viewModel = viewModel, authViewModel = authViewModel, currentIntent = currentIntentState.value)
                        }
                    }
                    is com.finance.splitup.viewmodel.AuthState.EmailVerificationPending -> {
                        EmailVerificationScreen(
                            authViewModel = authViewModel,
                            userEmail = state.user.email ?: "",
                            onResend = { authViewModel.resendEmailVerification() },
                            onCheckStatus = { authViewModel.checkEmailVerificationStatus() },
                            onSignOut = {
                                authViewModel.signOut {
                                    viewModel.clearLocalDataOnSignOut()
                                }
                            },
                            message = authViewModel.verificationMessage,
                            isResending = authViewModel.isResendingEmail
                        )
                    }
                    else -> {
                        AuthScreen(authViewModel = authViewModel, onLoginSuccess = {})
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
    }
}

@Composable
fun SplitUpApp(
    viewModel: FinanceViewModel = viewModel(),
    authViewModel: com.finance.splitup.viewmodel.AuthViewModel = viewModel(),
    currentIntent: android.content.Intent? = null
) {
    var isAppUnlocked by rememberSaveable { mutableStateOf(false) }
    val backStack = rememberNavBackStack(DashboardRoute)
    val currentRoute = backStack.lastOrNull()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var isSplashFinished by remember { mutableStateOf(false) }

    if (!isSplashFinished) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                com.finance.splitup.ui.components.AnimatedSplitUpLaunchLogo(
                    modifier = Modifier.width(260.dp),
                    onAnimationFinished = {
                        isSplashFinished = true
                    }
                )
            }
        }
        return
    }

    LaunchedEffect(Unit) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
            .build()
        val recurringWork = androidx.work.PeriodicWorkRequestBuilder<com.finance.splitup.data.RecurringExpenseWorker>(1, java.util.concurrent.TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "SplitUpRecurringExpenseWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            recurringWork
        )

        val syncConstraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val syncWork = androidx.work.PeriodicWorkRequestBuilder<com.finance.splitup.data.CloudSyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(syncConstraints)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "SplitUpCloudSyncWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncWork
        )

        com.finance.splitup.data.CloudSyncManager.startListeningForIncomingNotifications(context)

        val currentUser = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (currentUser != null) {
            try {
                com.onesignal.OneSignal.login(currentUser.uid)
                currentUser.email?.let { email ->
                    if (email.isNotBlank()) {
                        com.onesignal.OneSignal.User.addEmail(email)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed initializing OneSignal user identity", e)
            }
        }
    }

    LaunchedEffect(currentIntent) {
        val activity = context as? FragmentActivity
        val intent = currentIntent ?: activity?.intent ?: return@LaunchedEffect
        val data = intent.data

        if (data != null && (data.scheme == "splitup" || ((data.scheme == "https" || data.scheme == "http") && (data.host == "splitupfin.vercel.app" || data.host == "splitup.finance" || data.host == "splitup.app")))) {
            val groupId = data.getQueryParameter("id")
                ?: data.getQueryParameter("groupId")
                ?: if (data.path?.startsWith("/group") == true || data.path?.startsWith("/invite") == true || data.path?.startsWith("/join") == true) data.lastPathSegment else null
            val userEmail = data.getQueryParameter("email")
            val userName = data.getQueryParameter("name") ?: userEmail?.substringBefore("@") ?: ""

            if (!groupId.isNullOrBlank() && groupId != "invite" && groupId != "group" && groupId != "user" && groupId != "join") {
                viewModel.joinGroupViaInvite(
                    groupId = groupId,
                    onJoined = {
                        backStack.add(GroupDetailRoute(groupId))
                    },
                    onError = {
                        backStack.add(GroupDetailRoute(groupId))
                    }
                )
                return@LaunchedEffect
            } else if (!userEmail.isNullOrBlank()) {
                viewModel.addRecentFriend(userName, userEmail)
                backStack.add(FindFriendsRoute)
                UiMessage.show("Added $userName to friends!")
                return@LaunchedEffect
            }
        }

        if (intent.getBooleanExtra(com.finance.splitup.data.PaymentNotificationListenerService.EXTRA_REVIEW_DETECTED_TRANSACTION, false)) {
            val amount = intent.getDoubleExtra(com.finance.splitup.data.PaymentNotificationListenerService.EXTRA_DETECTED_AMOUNT, 0.0)
            val title = intent.getStringExtra(com.finance.splitup.data.PaymentNotificationListenerService.EXTRA_DETECTED_TITLE)
            val isIncome = intent.getBooleanExtra(com.finance.splitup.data.PaymentNotificationListenerService.EXTRA_DETECTED_IS_INCOME, false)
            val category = intent.getStringExtra(com.finance.splitup.data.PaymentNotificationListenerService.EXTRA_DETECTED_CATEGORY)
            val currency = intent.getStringExtra(com.finance.splitup.data.PaymentNotificationListenerService.EXTRA_DETECTED_CURRENCY)

            intent.removeExtra(com.finance.splitup.data.PaymentNotificationListenerService.EXTRA_REVIEW_DETECTED_TRANSACTION)

            backStack.add(
                AddExpenseRoute(
                    initialAmount = if (amount > 0) amount else null,
                    initialTitle = title,
                    initialCategory = category,
                    initialIsIncome = isIncome,
                    initialCurrency = currency
                )
            )
            return@LaunchedEffect
        }

        val targetScreen = intent.getStringExtra("extra_target_screen")
        if (!targetScreen.isNullOrBlank()) {
            intent.removeExtra("extra_target_screen")
            when (targetScreen) {
                "group_detail" -> {
                    val gId = intent.getStringExtra("extra_group_id")
                    if (!gId.isNullOrBlank()) {
                        backStack.add(GroupDetailRoute(gId))
                    } else {
                        backStack.clear()
                        backStack.add(DashboardRoute)
                        backStack.add(GroupsRoute)
                    }
                }
                "groups" -> {
                    backStack.clear()
                    backStack.add(DashboardRoute)
                    backStack.add(GroupsRoute)
                }
                "personal_expenses" -> {
                    val cat = intent.getStringExtra("extra_category")
                    backStack.clear()
                    backStack.add(DashboardRoute)
                    backStack.add(PersonalExpensesRoute(initialCategory = cat))
                }
                "settings" -> {
                    backStack.clear()
                    backStack.add(DashboardRoute)
                    backStack.add(SettingsRoute)
                }
                "edit_profile" -> {
                    backStack.clear()
                    backStack.add(DashboardRoute)
                    backStack.add(SettingsRoute)
                    backStack.add(EditProfileRoute)
                }
                "find_friends" -> {
                    backStack.clear()
                    backStack.add(DashboardRoute)
                    backStack.add(GroupsRoute)
                    backStack.add(FindFriendsRoute)
                }
                "dashboard" -> {
                    backStack.clear()
                    backStack.add(DashboardRoute)
                }
            }
        }
    }

    var lastBackgroundTimestamp by remember { mutableLongStateOf(0L) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel.isBiometricLockEnabled, viewModel.biometricLockTimeoutMins) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && viewModel.isBiometricLockEnabled) {
                val activity = context as? FragmentActivity
                if (activity?.isChangingConfigurations != true && !MainActivity.isExternalActionInProgress) {
                    lastBackgroundTimestamp = System.currentTimeMillis()
                    if (viewModel.biometricLockTimeoutMins == 0) {
                        isAppUnlocked = false
                    }
                }
                MainActivity.isExternalActionInProgress = false
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START && viewModel.isBiometricLockEnabled) {
                if (viewModel.biometricLockTimeoutMins > 0 && lastBackgroundTimestamp > 0L) {
                    val elapsedMins = (System.currentTimeMillis() - lastBackgroundTimestamp) / 60000L
                    if (elapsedMins >= viewModel.biometricLockTimeoutMins) {
                        isAppUnlocked = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (viewModel.isBiometricLockEnabled && !isAppUnlocked) {
        BiometricLockOverlay(onUnlocked = { isAppUnlocked = true })
        return
    }

    // Standard sequential back handling for nested routes
    BackHandler(enabled = currentRoute !is DashboardRoute) {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        } else {
            backStack.clear()
            backStack.add(DashboardRoute)
        }
    }

    // Observe budget warnings & snackbar messages
    val warningMessage = viewModel.budgetWarningMessage
    LaunchedEffect(warningMessage) {
        warningMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBudgetWarning()
        }
    }

    // Render app-wide UiMessage broadcasts (replacing Toasts) as snackbars.
    LaunchedEffect(Unit) {
        com.finance.splitup.data.UiMessage.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg, duration = androidx.compose.material3.SnackbarDuration.Short)
        }
    }

    val isTopLevelRoute = currentRoute is DashboardRoute ||
            currentRoute is PersonalExpensesRoute ||
            currentRoute is GroupsRoute ||
            currentRoute is SettingsRoute

    val navItemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )

    val navLayoutType = if (isTopLevelRoute) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    } else {
        NavigationSuiteType.None
    }

    var showDemoTour by remember { mutableStateOf(false) }
    var currentTourStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val prefs = com.finance.splitup.data.EncryptedPrefs.getPreferences(context, "splitup_prefs")
        val seenTour = prefs.getBoolean("has_seen_demo_tour", false)
        val savedStep = prefs.getInt("demo_tour_step", 0)
        if (!seenTour) {
            currentTourStep = savedStep
            showDemoTour = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
        layoutType = navLayoutType,
        navigationSuiteItems = {
            if (isTopLevelRoute) {
                item(
                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontWeight = FontWeight.Bold) },
                    selected = currentRoute is DashboardRoute,
                    onClick = {
                        if (currentRoute !is DashboardRoute) {
                            backStack.clear()
                            backStack.add(DashboardRoute)
                        }
                    },
                    colors = navItemColors
                )
                item(
                    icon = { Icon(Icons.Rounded.Person, contentDescription = "Personal") },
                    label = { Text("Personal", fontWeight = FontWeight.Bold) },
                    selected = currentRoute is PersonalExpensesRoute,
                    onClick = {
                        if (currentRoute !is PersonalExpensesRoute) {
                            backStack.clear()
                            backStack.add(PersonalExpensesRoute(initialCategory = null))
                        }
                    },
                    colors = navItemColors
                )
                item(
                    icon = { Icon(Icons.Rounded.Groups, contentDescription = "Groups") },
                    label = { Text("Groups", fontWeight = FontWeight.Bold) },
                    selected = currentRoute is GroupsRoute,
                    onClick = {
                        if (currentRoute !is GroupsRoute) {
                            backStack.clear()
                            backStack.add(GroupsRoute)
                        }
                    },
                    colors = navItemColors
                )
                item(
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontWeight = FontWeight.Bold) },
                    selected = currentRoute is SettingsRoute,
                    onClick = {
                        if (currentRoute !is SettingsRoute) {
                            backStack.clear()
                            backStack.add(SettingsRoute)
                        }
                    },
                    colors = navItemColors
                )
            }
        },
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = MaterialTheme.colorScheme.surface,
            navigationRailContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                val isNavigatingToNotifications = targetState.key == NotificationsRoute || targetState.key is NotificationsRoute
                if (isNavigatingToNotifications) {
                    slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(350)
                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> -fullHeight / 4 },
                        animationSpec = tween(350)
                    ) + fadeOut(animationSpec = tween(300))
                } else if (isTopLevelTabTransition(initialState.key, targetState.key)) {
                    // Bottom-nav tab switches should feel like tabs (fade-through),
                    // not like drilling into a detail page.
                    fadeIn(animationSpec = tween(220, delayMillis = 70)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(220, delayMillis = 70)
                    ) togetherWith fadeOut(animationSpec = tween(90))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth / 3 },
                        animationSpec = tween(300)
                    ) + fadeOut(animationSpec = tween(300))
                }
            },
            popTransitionSpec = {
                val isPoppingFromNotifications = initialState.key == NotificationsRoute || initialState.key is NotificationsRoute
                if (isPoppingFromNotifications) {
                    slideInVertically(
                        initialOffsetY = { fullHeight -> -fullHeight / 4 },
                        animationSpec = tween(350)
                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(350)
                    ) + fadeOut(animationSpec = tween(300))
                } else if (isTopLevelTabTransition(initialState.key, targetState.key)) {
                    fadeIn(animationSpec = tween(220, delayMillis = 70)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(220, delayMillis = 70)
                    ) togetherWith fadeOut(animationSpec = tween(90))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 3 },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300)
                    ) + fadeOut(animationSpec = tween(300))
                }
            },
            predictivePopTransitionSpec = {
                val isPoppingFromNotifications = initialState.key == NotificationsRoute || initialState.key is NotificationsRoute
                if (isPoppingFromNotifications) {
                    slideInVertically(
                        initialOffsetY = { fullHeight -> -fullHeight / 4 },
                        animationSpec = tween(350)
                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(350)
                    ) + fadeOut(animationSpec = tween(300))
                } else if (isTopLevelTabTransition(initialState.key, targetState.key)) {
                    fadeIn(animationSpec = tween(220, delayMillis = 70)) + scaleIn(
                        initialScale = 0.94f,
                        animationSpec = tween(220, delayMillis = 70)
                    ) togetherWith fadeOut(animationSpec = tween(90))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 3 },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300)) togetherWith
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(300)
                    ) + fadeOut(animationSpec = tween(300))
                }
            },
            entryProvider = { key ->
                when (key) {
                    is DashboardRoute -> NavEntry(key) {
                        DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToAddExpense = { isIncome ->
                                backStack.add(AddExpenseRoute(initialIsIncome = isIncome))
                            },
                            onNavigateToEditExpense = { expId ->
                                backStack.add(AddExpenseRoute(expId))
                            },
                            onNavigateToPersonalExpenses = { cat ->
                                backStack.clear()
                                backStack.add(DashboardRoute)
                                backStack.add(PersonalExpensesRoute(initialCategory = cat))
                            },
                            onNavigateToGroups = {
                                backStack.clear()
                                backStack.add(DashboardRoute)
                                backStack.add(GroupsRoute)
                            },
                            onNavigateToGroupDetail = { gId, tab ->
                                backStack.add(GroupDetailRoute(gId, initialTab = tab))
                            },
                            onNavigateToCreateGroup = {
                                backStack.add(CreateGroupRoute)
                            },
                            onNavigateToGlobalSearch = {
                                backStack.add(GlobalSearchRoute)
                            },
                            onNavigateToAddGroupExpense = { gId ->
                                backStack.add(AddGroupExpenseRoute(gId))
                            },
                            onNavigateToScanner = {
                                viewModel.targetGroupIdForReceipt = null
                                backStack.add(ReceiptScannerRoute)
                            },
                            onNavigateToInsights = {
                                backStack.add(InsightsRoute)
                            },
                            onNavigateToSubscriptions = {
                                backStack.add(SubscriptionsRoute)
                            },
                            onNavigateToNotifications = {
                                backStack.add(NotificationsRoute)
                            },
                            onNavigateToBudgets = {
                                backStack.add(BudgetRoute)
                            }
                        )
                    }
                    is PersonalExpensesRoute -> NavEntry(key) {
                        PersonalExpensesScreen(
                            viewModel = viewModel,
                            initialCategory = key.initialCategory,
                            onNavigateToAddExpense = { expId, isIncome ->
                                backStack.add(AddExpenseRoute(expenseId = expId, initialIsIncome = isIncome))
                            },
                            onNavigateToEditInvestment = { invId ->
                                if (invId == null) {
                                    backStack.add(AddExpenseRoute(initialCategory = "INVESTMENT"))
                                } else {
                                    backStack.add(AddExpenseRoute(investmentId = invId))
                                }
                            },
                            onNavigateToEditExpense = { expId ->
                                backStack.add(AddExpenseRoute(expId))
                            },
                            onNavigateToGlobalSearch = {
                                backStack.add(GlobalSearchRoute)
                            },
                            onNavigateToSubscriptions = {
                                backStack.add(SubscriptionsRoute)
                            },
                            onNavigateToGroups = {
                                backStack.clear()
                                backStack.add(DashboardRoute)
                                backStack.add(GroupsRoute)
                            }
                        )
                    }
                    is GroupsRoute -> NavEntry(key) {
                        GroupsScreen(
                            viewModel = viewModel,
                            onNavigateToGroupDetail = { groupId ->
                                backStack.add(GroupDetailRoute(groupId))
                            },
                            onNavigateToCreateGroup = {
                                backStack.add(CreateGroupRoute)
                            },
                            onNavigateToGlobalSearch = {
                                backStack.add(GlobalSearchRoute)
                            },
                            onNavigateToFindFriends = {
                                backStack.add(FindFriendsRoute)
                            },
                            onNavigateToSettings = {
                                backStack.clear()
                                backStack.add(DashboardRoute)
                                backStack.add(SettingsRoute)
                            }
                        )
                    }
                    is GroupDetailRoute -> NavEntry(key) {
                        GroupDetailScreen(
                            groupId = key.groupId,
                            viewModel = viewModel,
                            initialTab = key.initialTab,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            },
                            onNavigateToAddExpense = { gId ->
                                backStack.add(AddGroupExpenseRoute(gId))
                            },
                            onNavigateToEditGroupExpense = { gId, expId ->
                                backStack.add(AddGroupExpenseRoute(gId, expId))
                            },
                            onNavigateToReceiptScan = { gId ->
                                viewModel.targetGroupIdForReceipt = gId
                                backStack.add(ReceiptScannerRoute)
                            }
                        )
                    }
                    is AddGroupExpenseRoute -> NavEntry(key) {
                        AddGroupExpenseScreen(
                            groupId = key.groupId,
                            expenseId = key.expenseId,
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            },
                            onNavigateToScanner = {
                                viewModel.targetGroupIdForReceipt = key.groupId
                                backStack.add(ReceiptScannerRoute)
                            }
                        )
                    }
                    is CreateGroupRoute -> NavEntry(key) {
                        CreateGroupScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            },
                            onGroupCreated = { groupId ->
                                backStack.remove(key)
                                backStack.add(GroupDetailRoute(groupId))
                            }
                        )
                    }
                    is FindFriendsRoute -> NavEntry(key) {
                        FindFriendsScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            }
                        )
                    }
                    is GlobalSearchRoute -> NavEntry(key) {
                        GlobalSearchScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            },
                            onNavigateToEditExpense = { expId ->
                                backStack.add(AddExpenseRoute(expId))
                            },
                            onNavigateToGroupDetail = { gId ->
                                backStack.add(GroupDetailRoute(gId))
                            }
                        )
                    }
                    is ReceiptScannerRoute -> NavEntry(key) {
                        ReceiptScannerScreen(
                            viewModel = viewModel,
                            onReceiptExtracted = {
                                backStack.add(ReceiptExtractionRoute)
                            }
                        )
                    }
                    is ReceiptExtractionRoute -> NavEntry(key) {
                        ReceiptExtractionScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(ReceiptExtractionRoute)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            },
                            onProceedToAllocate = {
                                backStack.add(ReceiptAllocationRoute)
                            }
                        )
                    }
                    is ReceiptAllocationRoute -> NavEntry(key) {
                        ReceiptAllocationScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(ReceiptAllocationRoute)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            },
                            onExpenseAdded = {
                                backStack.clear()
                                backStack.add(DashboardRoute)
                                backStack.add(GroupsRoute)
                            }
                        )
                    }
                    is SettingsRoute -> NavEntry(key) {
                        SettingsScreen(
                            viewModel = viewModel,
                            authViewModel = authViewModel,
                            onNavigateToEditProfile = { backStack.add(EditProfileRoute) },
                            onNavigateToSubscriptions = { backStack.add(SubscriptionsRoute) },
                            onNavigateToInsights = { backStack.add(InsightsRoute) },
                            onNavigateToAbout = { backStack.add(AboutRoute) },
                            onNavigateToSecurity = { backStack.add(SecuritySettingsRoute) },
                            onNavigateToDataManagement = { backStack.add(DataManagementRoute) },
                            onNavigateToDashboard = {
                                backStack.clear()
                                backStack.add(DashboardRoute)
                            }
                        )
                    }
                    is SecuritySettingsRoute -> NavEntry(key) {
                        SecuritySettingsScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                    backStack.add(SettingsRoute)
                                }
                            }
                        )
                    }
                    is DataManagementRoute -> NavEntry(key) {
                        DataManagementScreen(
                            viewModel = viewModel,
                            authViewModel = authViewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                    backStack.add(SettingsRoute)
                                }
                            }
                        )
                    }
                    is AboutRoute -> NavEntry(key) {
                        AboutScreen(
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                    backStack.add(SettingsRoute)
                                }
                            },
                            onStartDemoTour = {
                                val prefs = com.finance.splitup.data.EncryptedPrefs.getPreferences(context, "splitup_prefs")
                                prefs.edit().remove("has_seen_demo_tour").remove("demo_tour_step").apply()
                                currentTourStep = 0
                                showDemoTour = true
                                backStack.clear()
                                backStack.add(DashboardRoute)
                            }
                        )
                    }
                    is SubscriptionsRoute -> NavEntry(key) {
                        SubscriptionsScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                    backStack.add(SettingsRoute)
                                }
                            }
                        )
                    }
                    is NotificationsRoute -> NavEntry(key) {
                        NotificationsScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            },
                            onNavigateToGroupDetail = { gId, tab ->
                                backStack.add(GroupDetailRoute(gId, initialTab = tab))
                            },
                            onNavigateToSubscriptions = {
                                backStack.add(SubscriptionsRoute)
                            },
                            onNavigateToInsights = {
                                backStack.add(InsightsRoute)
                            },
                            onNavigateToBudgets = {
                                backStack.add(BudgetRoute)
                            },
                            onNavigateToPersonalExpenses = { cat ->
                                backStack.add(PersonalExpensesRoute(initialCategory = cat))
                            }
                        )
                    }
                    is BudgetRoute -> NavEntry(key) {
                        BudgetScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            }
                        )
                    }
                    is InsightsRoute -> NavEntry(key) {
                        InsightsScreen(
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                    backStack.add(SettingsRoute)
                                }
                            }
                        )
                    }
                    is EditProfileRoute -> NavEntry(key) {
                        EditProfileScreen(
                            viewModel = viewModel,
                            authViewModel = authViewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                    backStack.add(SettingsRoute)
                                }
                            }
                        )
                    }
                    is AddExpenseRoute -> NavEntry(key) {
                        AddExpenseScreen(
                            expenseId = key.expenseId,
                            initialAmount = key.initialAmount,
                            initialTitle = key.initialTitle,
                            initialCategory = key.initialCategory,
                            initialIsIncome = key.initialIsIncome,
                            initialCurrency = key.initialCurrency,
                            viewModel = viewModel,
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.remove(key)
                                } else {
                                    backStack.clear()
                                    backStack.add(DashboardRoute)
                                }
                            },
                            onNavigateToScanner = {
                                viewModel.targetGroupIdForReceipt = null
                                backStack.add(ReceiptScannerRoute)
                            }
                        )
                    }
                    else -> NavEntry(key) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Unknown Screen")
                        }
                    }
                }
            }
        )
      }
    }

    val onTourBack: () -> Unit = {
        val prefs = com.finance.splitup.data.EncryptedPrefs.getPreferences(context, "splitup_prefs")
        when (currentTourStep) {
            1 -> {
                currentTourStep = 0
                prefs.edit().putInt("demo_tour_step", 0).apply()
            }
            2 -> {
                currentTourStep = 1
                prefs.edit().putInt("demo_tour_step", 1).apply()
                backStack.clear()
                backStack.add(DashboardRoute)
            }
            3 -> {
                currentTourStep = 2
                prefs.edit().putInt("demo_tour_step", 2).apply()
                backStack.clear()
                backStack.add(DashboardRoute)
                backStack.add(PersonalExpensesRoute(null))
            }
            4 -> {
                currentTourStep = 3
                prefs.edit().putInt("demo_tour_step", 3).apply()
                backStack.clear()
                backStack.add(DashboardRoute)
                backStack.add(GroupsRoute)
            }
        }
    }

    val onTourSkip: () -> Unit = {
        val prefs = com.finance.splitup.data.EncryptedPrefs.getPreferences(context, "splitup_prefs")
        prefs.edit().putBoolean("has_seen_demo_tour", true).remove("demo_tour_step").apply()
        showDemoTour = false
        backStack.clear()
        backStack.add(DashboardRoute)
    }

    // The tour owns the system back button: on step 0 back dismisses the tour,
    // otherwise it rewinds one step instead of popping the nav stack out from
    // under the spotlight.
    BackHandler(enabled = showDemoTour) {
        if (currentTourStep > 0) onTourBack() else onTourSkip()
    }

    if (showDemoTour) {
        com.finance.splitup.ui.components.DemoTourOverlay(
            currentStep = currentTourStep,
            totalSteps = 5,
            onNext = {
                val prefs = com.finance.splitup.data.EncryptedPrefs.getPreferences(context, "splitup_prefs")
                when (currentTourStep) {
                    0 -> {
                        currentTourStep = 1
                        prefs.edit().putInt("demo_tour_step", 1).apply()
                    }
                    1 -> {
                        currentTourStep = 2
                        prefs.edit().putInt("demo_tour_step", 2).apply()
                        backStack.clear()
                        backStack.add(DashboardRoute)
                        backStack.add(PersonalExpensesRoute(null))
                    }
                    2 -> {
                        currentTourStep = 3
                        prefs.edit().putInt("demo_tour_step", 3).apply()
                        backStack.clear()
                        backStack.add(DashboardRoute)
                        backStack.add(GroupsRoute)
                    }
                    3 -> {
                        currentTourStep = 4
                        prefs.edit().putInt("demo_tour_step", 4).apply()
                        backStack.clear()
                        backStack.add(DashboardRoute)
                        backStack.add(SettingsRoute)
                    }
                    4 -> {
                        prefs.edit().putBoolean("has_seen_demo_tour", true).remove("demo_tour_step").apply()
                        showDemoTour = false
                        backStack.clear()
                        backStack.add(DashboardRoute)
                    }
                }
            },
            onBack = onTourBack,
            onSkip = onTourSkip
        )
    }
}
}

fun hashSecurityPin(pin: String): String = com.finance.splitup.data.PinSecurity.legacyHash(pin)

/**
 * True when both the current and target destinations are bottom-navigation
 * tabs, so the switch uses a fade-through instead of a push-style slide.
 */
private fun isTopLevelTabTransition(from: Any, to: Any): Boolean {
    val topLevel = setOf<Any>(DashboardRoute, PersonalExpensesRoute(), GroupsRoute, SettingsRoute)
    return from in topLevel && to in topLevel
}

@Composable
fun BiometricLockOverlay(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var isPinMode by remember { mutableStateOf(false) }

    fun promptBiometrics() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            onUnlocked()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricManager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("SplitUp Security")
                .setSubtitle("Unlock SplitUp using biometrics or device PIN")
                .setAllowedAuthenticators(authenticators)
                .build()

            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onUnlocked()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        isPinMode = true
                    }
                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        isPinMode = true
                    }
                }
            )
            biometricPrompt.authenticate(promptInfo)
        } else {
            isPinMode = true
        }
    }

    LaunchedEffect(Unit) {
        promptBiometrics()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(90.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Lock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "SplitUp Security",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Biometric & Security Protection Active",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            var pinInput by remember { mutableStateOf("") }
            var isPinMode by remember { mutableStateOf(false) }
            var pinError by remember { mutableStateOf<String?>(null) }
            val prefs = com.finance.splitup.data.EncryptedPrefs.getPreferences(context, "splitup_prefs")
            val defaultHash = com.finance.splitup.data.PinSecurity.legacyHash("1234")
            val savedPin = prefs.getString("security_pin_hash", null) ?: prefs.getString("security_pin", defaultHash) ?: defaultHash

            if (isPinMode) {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                            pinInput = it
                            if (com.finance.splitup.data.PinSecurity.verify(it, savedPin)) {
                                // Transparently upgrade legacy (unsalted) PIN storage
                                // to the salted PBKDF2 format after a successful match.
                                if (com.finance.splitup.data.PinSecurity.needsUpgrade(savedPin)) {
                                    com.finance.splitup.data.PinSecurity.hash(it)?.let { upgraded ->
                                        prefs.edit().putString("security_pin_hash", upgraded).apply()
                                    }
                                }
                                onUnlocked()
                            } else if (it.length == 4) {
                                pinError = "Incorrect PIN"
                            } else {
                                pinError = null
                            }
                        }
                    },
                    label = { Text("Enter 4-Digit Security PIN") },
                    isError = pinError != null,
                    supportingText = { pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { isPinMode = false; promptBiometrics() }) {
                    Text("Use Biometrics / Device Credentials")
                }
            } else {
                Button(
                    onClick = { promptBiometrics() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Biometrics", style = SplitUpType.CardTitle)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { isPinMode = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Pin, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter PIN Fallback", fontSize = 14.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplitUpAppPreview() {
    SplitUpTheme {
        SplitUpApp()
    }
}
