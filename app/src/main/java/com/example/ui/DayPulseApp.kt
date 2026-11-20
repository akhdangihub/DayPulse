package com.example.ui

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.prefs.AppThemeMode
import com.example.service.CaptureServiceStatus
import com.example.ui.components.AnimatedMeshBackdrop
import com.example.ui.components.GlassContainer
import com.example.ui.components.GlassTier
import com.example.ui.screens.CompanionChatScreen
import com.example.ui.screens.EveningReviewScreen
import com.example.ui.screens.TodayCaptureScreen
import com.example.ui.screens.VaultSettingsScreen
import com.example.ui.theme.DayPulseTheme
import com.example.ui.theme.LocalDayPulseHaze
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.EveningReviewViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.widget.WidgetCommands
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

const val ROUTE_TODAY = "today"
const val ROUTE_EVENING = "evening"
const val ROUTE_COMPANION = "companion"
const val ROUTE_VAULT = "vault"

private data class DayPulseDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val Destinations = listOf(
    DayPulseDestination(ROUTE_TODAY, R.string.nav_today, Icons.Rounded.Mic, Icons.Outlined.Mic, "nav_today"),
    DayPulseDestination(ROUTE_EVENING, R.string.nav_evening, Icons.Rounded.Bolt, Icons.Outlined.Bolt, "nav_evening"),
    DayPulseDestination(ROUTE_COMPANION, R.string.nav_companion, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_companion"),
    DayPulseDestination(ROUTE_VAULT, R.string.nav_vault, Icons.Rounded.FolderSpecial, Icons.Outlined.FolderSpecial, "nav_vault")
)

@Composable
fun DayPulseApp(
    startOnEveningTab: Boolean = false,
    widgetCommand: Pair<Long, String>? = null,
    onConsumeWidgetCommand: () -> Unit = {},
    mainViewModel: MainViewModel = viewModel(),
    eveningViewModel: EveningReviewViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_TODAY
    val snackbarHostState = remember { SnackbarHostState() }
    var autoCaptureTrigger by remember { mutableLongStateOf(0L) }
    var autoOpenNoteTrigger by remember { mutableLongStateOf(0L) }

    // MainViewModel states
    val todayCaptures by mainViewModel.todayCaptures.collectAsStateWithLifecycle()
    val todayActions by mainViewModel.todayActions.collectAsStateWithLifecycle()
    val recordingState by mainViewModel.recordingState.collectAsStateWithLifecycle()
    val queuedCount by mainViewModel.queuedCount.collectAsStateWithLifecycle()
    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val isOnline by mainViewModel.isOnline.collectAsStateWithLifecycle()
    val uiMessage by mainViewModel.uiMessage.collectAsStateWithLifecycle()
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by mainViewModel.searchResults.collectAsStateWithLifecycle()
    val allSummaries by mainViewModel.allSummaries.collectAsStateWithLifecycle()

    // EveningReviewViewModel (Quick Review) states
    val selectedDateKey by eveningViewModel.selectedDateKey.collectAsStateWithLifecycle()
    val availableDates by eveningViewModel.availableDateKeys.collectAsStateWithLifecycle()
    val newCapturesCount by eveningViewModel.newCapturesCount.collectAsStateWithLifecycle()
    val summary by eveningViewModel.summary.collectAsStateWithLifecycle()
    val dayAnalytics by eveningViewModel.dayAnalytics.collectAsStateWithLifecycle()
    val pendingActions by eveningViewModel.pendingActions.collectAsStateWithLifecycle()
    val acceptedActions by eveningViewModel.acceptedActions.collectAsStateWithLifecycle()
    val dismissedActions by eveningViewModel.dismissedActions.collectAsStateWithLifecycle()
    val isGoogleConnected by eveningViewModel.isGoogleConnected.collectAsStateWithLifecycle()
    val synthesisState by eveningViewModel.synthesisState.collectAsStateWithLifecycle()
    val editingAction by eveningViewModel.editingAction.collectAsStateWithLifecycle()
    val pendingBadgeCount by eveningViewModel.pendingCount.collectAsStateWithLifecycle()
    val lastDismissed by eveningViewModel.lastDismissed.collectAsStateWithLifecycle()
    val reviewBannerMessage by eveningViewModel.reviewBannerMessage.collectAsStateWithLifecycle()

    // ChatViewModel states
    val chatMessages by chatViewModel.messages.collectAsStateWithLifecycle()
    val chatUiState by chatViewModel.uiState.collectAsStateWithLifecycle()

    val systemDark = isSystemInDarkTheme()
    val resolvedDark = when (preferences.resolvedThemeMode()) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }

    val backdropHazeState = remember { HazeState() }
    val navHazeState = remember { HazeState() }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !resolvedDark
            insetsController.isAppearanceLightNavigationBars = !resolvedDark
        }
    }

    LaunchedEffect(startOnEveningTab) {
        if (startOnEveningTab) {
            navController.navigate(ROUTE_EVENING) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(widgetCommand) {
        val cmd = widgetCommand ?: return@LaunchedEffect
        when (cmd.second) {
            WidgetCommands.CMD_QUICK_REVIEW -> {
                navController.navigate(ROUTE_EVENING) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            WidgetCommands.CMD_START_CAPTURE -> {
                navController.navigate(ROUTE_TODAY) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                autoCaptureTrigger = cmd.first
            }
            WidgetCommands.CMD_QUICK_NOTE -> {
                navController.navigate(ROUTE_TODAY) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                autoOpenNoteTrigger = cmd.first
            }
        }
        onConsumeWidgetCommand()
    }

    LaunchedEffect(uiMessage) {
        val msg = uiMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        mainViewModel.clearUiMessage()
    }

    LaunchedEffect(reviewBannerMessage) {
        val msg = reviewBannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        eveningViewModel.clearBannerMessage()
    }

    LaunchedEffect(lastDismissed) {
        val dismissed = lastDismissed ?: return@LaunchedEffect
        val res = snackbarHostState.showSnackbar(
            message = "Dismissed “${dismissed.title}”",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (res == SnackbarResult.ActionPerformed) {
            eveningViewModel.undoLastDismiss()
        } else {
            eveningViewModel.clearLastDismissed()
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    DayPulseTheme(
        darkTheme = resolvedDark,
        glassEffectsEnabled = preferences.glassEffectsEnabled,
        hazeState = backdropHazeState
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Box(modifier = Modifier.fillMaxSize()) {
                // Background + main content captured for the bottom FloatingPillNavBar's frosted blur
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .haze(state = navHazeState)
                ) {
                    // Animated Midnight Indigo mesh gradient captured for Level 2/3 in-screen glass surfaces
                    AnimatedMeshBackdrop(
                        isRecording = recordingState.status == CaptureServiceStatus.RECORDING,
                        amplitude = recordingState.amplitude,
                        modifier = Modifier.haze(state = backdropHazeState)
                    )

                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        ),
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(bottom = if (!useRail) 96.dp else 12.dp)
                            ) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    shape = RoundedCornerShape(20.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    actionColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (useRail) {
                                GlassContainer(
                                    tier = GlassTier.LEVEL_2,
                                    shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                                    useBackdropBlur = true,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    NavigationRail(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ) {
                                        Destinations.forEach { dest ->
                                            val selected = currentRoute == dest.route
                                            NavigationRailItem(
                                                selected = selected,
                                                onClick = { navigateTo(dest.route) },
                                                icon = {
                                                    DestinationIcon(
                                                        dest = dest,
                                                        selected = selected,
                                                        eveningBadgeCount = pendingBadgeCount,
                                                        tint = if (selected) {
                                                            MaterialTheme.colorScheme.onPrimary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        }
                                                    )
                                                },
                                                label = { Text(stringResource(dest.labelRes)) },
                                                colors = NavigationRailItemDefaults.colors(
                                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.testTag(dest.testTag)
                                            )
                                        }
                                    }
                                }
                            }

                            NavHost(
                                navController = navController,
                                startDestination = ROUTE_TODAY,
                                modifier = Modifier.weight(1f)
                            ) {
                                composable(ROUTE_TODAY) {
                                    TodayCaptureScreen(
                                        captures = todayCaptures,
                                        todayActions = todayActions,
                                        recordingState = recordingState,
                                        queuedCount = queuedCount,
                                        isOnline = isOnline,
                                        preferences = preferences,
                                        autoStartCaptureTrigger = autoCaptureTrigger,
                                        autoOpenNoteTrigger = autoOpenNoteTrigger,
                                        onStartCapture = mainViewModel::startRecording,
                                        onPauseCapture = mainViewModel::pauseRecording,
                                        onResumeCapture = mainViewModel::resumeRecording,
                                        onSplitSegment = mainViewModel::splitSegment,
                                        onStopCapture = mainViewModel::stopRecording,
                                        onAddQuickNote = mainViewModel::addQuickNote,
                                        onImportPhoto = mainViewModel::importPhoto,
                                        onSyncQueue = mainViewModel::syncQueueNow,
                                        onRetryCapture = mainViewModel::retryCapture,
                                        onEditTranscript = mainViewModel::updateCaptureTranscript,
                                        onToggleBookmark = mainViewModel::toggleBookmark,
                                        onDeleteCapture = mainViewModel::deleteCapture
                                    )
                                }

                                composable(ROUTE_EVENING) {
                                    EveningReviewScreen(
                                        selectedDateKey = selectedDateKey,
                                        availableDateKeys = availableDates,
                                        newCapturesCount = newCapturesCount,
                                        summary = summary,
                                        dayAnalytics = dayAnalytics,
                                        pendingActions = pendingActions,
                                        acceptedActions = acceptedActions,
                                        dismissedActions = dismissedActions,
                                        isGoogleConnected = isGoogleConnected,
                                        synthesisState = synthesisState,
                                        editingAction = editingAction,
                                        onSelectDate = eveningViewModel::selectDate,
                                        onSynthesizeReview = eveningViewModel::synthesizeEveningReview,
                                        onAcceptAction = eveningViewModel::acceptAction,
                                        onEditAction = eveningViewModel::openEditSheet,
                                        onAddManualAction = eveningViewModel::openNewActionSheet,
                                        onDismissAction = eveningViewModel::dismissAction,
                                        onRestoreAction = eveningViewModel::restoreAction,
                                        onToggleCompleted = eveningViewModel::toggleActionCompleted,
                                        onSyncActionToGoogle = eveningViewModel::syncActionToGoogle,
                                        onSyncAllApprovedToGoogle = eveningViewModel::syncAllApprovedToGoogle,
                                        onNotifyMessage = eveningViewModel::notifyMessage,
                                        onCloseEditSheet = eveningViewModel::closeEditSheet,
                                        onSaveEditedAction = eveningViewModel::saveAndAcceptEditedAction
                                    )
                                }

                                composable(ROUTE_COMPANION) {
                                    CompanionChatScreen(
                                        messages = chatMessages,
                                        chatUiState = chatUiState,
                                        onSendMessage = chatViewModel::sendMessage,
                                        onClearConversation = chatViewModel::clearConversation,
                                        hasFloatingBottomBar = !useRail
                                    )
                                }

                                composable(ROUTE_VAULT) {
                                    VaultSettingsScreen(
                                        searchQuery = searchQuery,
                                        searchResults = searchResults,
                                        summaries = allSummaries,
                                        preferences = preferences,
                                        onSearchQueryChange = mainViewModel::updateSearchQuery,
                                        onToggleAutoDeleteAudio = mainViewModel::setAutoDeleteAudio,
                                        onSegmentDurationChange = mainViewModel::setSegmentDurationMinutes,
                                        onToggleWifiOnly = mainViewModel::setWifiOnlyProcessing,
                                        onThemeModeChange = mainViewModel::setThemeMode,
                                        onToggleGlassEffects = mainViewModel::setGlassEffectsEnabled,
                                        onEveningReminderChange = mainViewModel::setEveningReminder,
                                        onPurgeLocalAudio = mainViewModel::purgeAllLocalAudioFiles,
                                        onOpenSummaryDate = { dateKey ->
                                            eveningViewModel.selectDate(dateKey)
                                            navigateTo(ROUTE_EVENING)
                                        },
                                        onToggleBookmark = mainViewModel::toggleBookmark,
                                        onDeleteCapture = mainViewModel::deleteCapture
                                    )
                                }
                            }
                        }
                    }
                }

                if (!useRail) {
                    CompositionLocalProvider(LocalDayPulseHaze provides navHazeState) {
                        FloatingPillNavBar(
                            currentRoute = currentRoute,
                            reviewBadgeCount = pendingBadgeCount,
                            onSelectRoute = ::navigateTo,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingPillNavBar(
    currentRoute: String,
    reviewBadgeCount: Int,
    onSelectRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    GlassContainer(
        tier = GlassTier.LEVEL_3,
        shape = RoundedCornerShape(36.dp),
        accentGlow = MaterialTheme.colorScheme.primary,
        useBackdropBlur = true,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .testTag("floating_pill_nav_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Destinations.forEach { dest ->
                val selected = currentRoute == dest.route
                val label = stringResource(dest.labelRes)
                val capsuleColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    label = "pill_tab_bg"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "pill_tab_fg"
                )

                Row(
                    modifier = Modifier
                        .then(if (selected) Modifier.weight(1.45f) else Modifier.weight(0.85f))
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(capsuleColor)
                        .animateContentSize(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectRoute(dest.route)
                        }
                        .semantics { contentDescription = label }
                        .padding(horizontal = if (selected) 14.dp else 8.dp, vertical = 10.dp)
                        .testTag(dest.testTag),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DestinationIcon(
                        dest = dest,
                        selected = selected,
                        eveningBadgeCount = reviewBadgeCount,
                        tint = contentColor
                    )
                    if (selected) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(
    dest: DayPulseDestination,
    selected: Boolean,
    eveningBadgeCount: Int,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    val label = stringResource(dest.labelRes)
    if (dest.route == ROUTE_EVENING && eveningBadgeCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ) {
                    Text(eveningBadgeCount.toString())
                }
            }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
    }
}
