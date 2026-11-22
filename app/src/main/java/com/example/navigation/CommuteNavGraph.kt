package com.example.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.models.MapTileProvider
import com.example.ui.CommuteViewModel
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassBottomNavBar
import com.example.ui.components.MainNavTab
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JourneyDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SavedScreen
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhite

const val ROUTE_JOURNEY_DETAIL = "journey_detail"

@Composable
fun CommuteNavGraph(viewModel: CommuteViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val savedRoutes by viewModel.savedRoutes.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val crowdStatuses by viewModel.crowdStatuses.collectAsStateWithLifecycle()
    val gtfsMeta by viewModel.gtfsMetadata.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    val navigateToTab: (MainNavTab) -> Unit = { tab ->
        if (tab == MainNavTab.ASSISTANT) {
            viewModel.primeChatForActiveRoute()
        }
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val cycleTileProvider: () -> Unit = {
        val entries = MapTileProvider.entries
        val next = entries[(entries.indexOf(preferences.mapTileProvider) + 1) % entries.size]
        viewModel.updateMapTileProvider(next)
        viewModel.showBanner("Map style: ${next.displayName}")
    }

    LiquidGlassBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = MainNavTab.HOME.route,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() }
            ) {
                composable(MainNavTab.HOME.route) {
                    HomeScreen(
                        uiState = uiState,
                        preferences = preferences,
                        alerts = alerts,
                        crowdStatuses = crowdStatuses,
                        recentSearches = recentSearches,
                        shortcuts = shortcuts,
                        allStops = viewModel.getAllStops(),
                        onOriginChanged = viewModel::onOriginTextChanged,
                        onDestinationChanged = viewModel::onDestinationTextChanged,
                        onFieldFocused = viewModel::onFieldFocused,
                        onSuggestionSelected = viewModel::selectSuggestion,
                        onDismissSuggestions = viewModel::dismissSuggestions,
                        onSwapLocations = viewModel::swapOriginAndDestination,
                        onUseCurrentLocation = viewModel::useCurrentLocationAsOrigin,
                        onRefreshLocation = viewModel::refreshDeviceLocation,
                        onSetDepartureWindow = viewModel::setDepartureWindow,
                        onSearchRoutes = { override -> viewModel.searchRoutes(preferredBadgeOverride = override) },
                        onRouteClicked = { route ->
                            viewModel.selectRoute(route)
                            navController.navigate(ROUTE_JOURNEY_DETAIL)
                        },
                        onOpenExplore = { navigateToTab(MainNavTab.EXPLORE) },
                        onOpenProfile = { navigateToTab(MainNavTab.PROFILE) },
                        onCycleTileProvider = cycleTileProvider,
                        onShortcutClicked = { sc ->
                            viewModel.routeToShortcut(sc)
                        },
                        onRecentSearchClicked = { rec ->
                            viewModel.repeatSearch(
                                rec.originName, rec.originLat, rec.originLon, rec.originStopId,
                                rec.destName, rec.destLat, rec.destLon, rec.destStopId
                            )
                        },
                        onSimulateBusDelay = viewModel::simulateBusDelay,
                        onSimulateTrainDelay = viewModel::simulateTrainDelay,
                        onCycleMetroCrowd = viewModel::cycleMetroCrowd,
                        onResetSimulation = viewModel::resetAwarenessSimulation
                    )
                }

                composable(MainNavTab.EXPLORE.route) {
                    ExploreScreen(
                        uiState = uiState,
                        tileProvider = preferences.mapTileProvider,
                        alerts = alerts,
                        crowdStatuses = crowdStatuses,
                        onToggleMapMode = viewModel::toggleExploreMapMode,
                        onSelectModeFilter = viewModel::setExploreModeFilter,
                        onSelectStop = viewModel::selectExploreStop,
                        onRouteToStop = { stop ->
                            viewModel.setStopAsDestinationAndSearch(stop)
                            navigateToTab(MainNavTab.HOME)
                        },
                        onCycleTileProvider = cycleTileProvider,
                        onRecenterLocation = viewModel::refreshDeviceLocation,
                        onSimulateBusDelay = viewModel::simulateBusDelay,
                        onSimulateTrainDelay = viewModel::simulateTrainDelay,
                        onCycleMetroCrowd = viewModel::cycleMetroCrowd,
                        onResetSimulation = viewModel::resetAwarenessSimulation
                    )
                }

                composable(MainNavTab.SAVED.route) {
                    SavedScreen(
                        savedRoutes = savedRoutes,
                        shortcuts = shortcuts,
                        availableLandmarks = viewModel.getCuratedLandmarks(),
                        onRepeatSavedRoute = { saved ->
                            viewModel.repeatSearch(
                                saved.originName, saved.originLat, saved.originLon, saved.originStopId,
                                saved.destName, saved.destLat, saved.destLon, saved.destStopId
                            )
                            navigateToTab(MainNavTab.HOME)
                        },
                        onRenameSavedRoute = viewModel::renameSavedRoute,
                        onDeleteSavedRoute = viewModel::deleteSavedRoute,
                        onShortcutClicked = { sc ->
                            viewModel.routeToShortcut(sc)
                            navigateToTab(MainNavTab.HOME)
                        },
                        onUpdateShortcut = viewModel::updateShortcut
                    )
                }

                composable(MainNavTab.ASSISTANT.route) {
                    AssistantScreen(
                        messages = uiState.chatMessages,
                        activeRoute = uiState.selectedRoute,
                        isTyping = uiState.isAssistantTyping,
                        onSendMessage = viewModel::sendChatMessage,
                        onClearConversation = viewModel::clearChatHistory,
                        onSaveActiveRoute = { route -> viewModel.saveCurrentOrRoute(route) }
                    )
                }

                composable(MainNavTab.PROFILE.route) {
                    ProfileScreen(
                        preferences = preferences,
                        deviceLocation = uiState.deviceLocation,
                        gtfsMeta = gtfsMeta,
                        isSyncingGtfs = uiState.isSyncingGtfs,
                        onUpdateUserName = viewModel::updateUserName,
                        onUpdateDefaultPreference = viewModel::updateDefaultRoutePreference,
                        onTogglePreferredMode = viewModel::togglePreferredMode,
                        onUpdateMaxWalkMeters = viewModel::updateMaxWalkMeters,
                        onUpdateTileProvider = viewModel::updateMapTileProvider,
                        onUpdateBackendUrl = viewModel::updateBackendUrl,
                        onToggleOfflineMode = viewModel::updateOfflineMode,
                        onSyncGtfsFeed = viewModel::syncGtfsDataset,
                        onRefreshLocation = viewModel::refreshDeviceLocation
                    )
                }

                composable(ROUTE_JOURNEY_DETAIL) {
                    val active = uiState.selectedRoute
                    if (active != null) {
                        JourneyDetailScreen(
                            route = active,
                            allStops = viewModel.getAllStops(),
                            tileProvider = preferences.mapTileProvider,
                            isTrackingActive = uiState.isJourneyTrackingActive,
                            activeStepIndex = uiState.activeTrackingSegmentIndex,
                            onBack = { navController.popBackStack() },
                            onCycleTileProvider = cycleTileProvider,
                            onStartJourney = viewModel::startJourneyTracking,
                            onAdvanceStep = viewModel::advanceJourneyTrackingStep,
                            onStopJourney = viewModel::stopJourneyTracking,
                            onSaveRoute = { viewModel.saveCurrentOrRoute(active) },
                            onAskAi = {
                                viewModel.primeChatForActiveRoute()
                                navigateToTab(MainNavTab.ASSISTANT)
                            }
                        )
                    }
                }
            }

            // Floating Liquid Glass Bottom Navigation Bar on primary tabs
            if (currentRoute != ROUTE_JOURNEY_DETAIL) {
                LiquidGlassBottomNavBar(
                    currentRoute = currentRoute,
                    onTabSelected = navigateToTab,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Snackbar feedback above bottom nav
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = PrimaryInk,
                    contentColor = PureWhite,
                    actionColor = PureWhite
                )
            }
        }
    }
}
