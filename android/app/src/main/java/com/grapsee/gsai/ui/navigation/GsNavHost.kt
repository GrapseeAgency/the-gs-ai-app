package com.grapsee.gsai.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import com.grapsee.gsai.data.SettingsStore
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.grapsee.gsai.ShortcutBus
import com.grapsee.gsai.data.SessionStore
import com.grapsee.gsai.ui.auth.AuthScreen
import com.grapsee.gsai.ui.auth.OnboardingScreen
import com.grapsee.gsai.ui.auth.ReferenceWelcomeScreen
import com.grapsee.gsai.ui.billing.BillingScreen
import com.grapsee.gsai.ui.chat.ArchivedChatsScreen
import com.grapsee.gsai.ui.chat.ChatInfoScreen
import com.grapsee.gsai.ui.chat.ChatScreen
import com.grapsee.gsai.ui.chat.ChatSearchScreen
import com.grapsee.gsai.ui.chat.ChatsScreen
import com.grapsee.gsai.ui.notifications.NotificationsScreen
import com.grapsee.gsai.ui.profile.ProfileScreen
import com.grapsee.gsai.ui.settings.AboutSettingsScreen
import com.grapsee.gsai.ui.settings.AccessibilitySettingsScreen
import com.grapsee.gsai.ui.settings.AccountSecurityScreen
import com.grapsee.gsai.ui.settings.AiIntelligenceScreen
import com.grapsee.gsai.ui.settings.AppearanceSettingsScreen
import com.grapsee.gsai.ui.settings.ChatSettingsScreen
import com.grapsee.gsai.ui.settings.HelpSettingsScreen
import com.grapsee.gsai.ui.settings.MemorySettingsScreen
import com.grapsee.gsai.ui.settings.ModelDetailsScreen
import com.grapsee.gsai.ui.settings.NotificationsAlertsScreen
import com.grapsee.gsai.ui.settings.PrivacySettingsScreen
import com.grapsee.gsai.ui.settings.SettingsHubScreen
import com.grapsee.gsai.ui.settings.ThemeCustomisationScreen
import com.grapsee.gsai.ui.settings.VoiceSettingsScreen
import com.grapsee.gsai.ui.specimen.GlassSpecimenScreen
import com.grapsee.gsai.ui.voice.VoiceScreen
import com.grapsee.gsai.ui.explore.DiscoverScreen
import com.grapsee.gsai.ui.explore.ExploreScreen
import com.grapsee.gsai.ui.home.HomeDashboardScreen
import com.grapsee.gsai.ui.agents.TravelAgentScreen
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.gsHaptic
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.grapsee.gsai.ui.settings.OnDeviceModelConsentDialog

/**
 * AERUO KINETIC nav graph — the product shell. THE CONVERSATION WORKSPACE IS
 * THE ROOT: the app opens directly into the AI workspace (fresh conversation =
 * the empty state of the same screen that renders the transcript), the drawer
 * is the quiet navigation layer, and everything else is pushed from within.
 * There is no Home launcher and no separate "New chat" page.
 *
 * Shell contract:
 *  · The drawer belongs to the PRODUCT only. Auth/Onboarding sit outside it:
 *    no drawer content and no edge-swipe reveal while the session gate is up.
 *  · "New chat" (drawer row, workspace + button) resets the stack to one
 *    fresh workspace — popUpTo(workspace) inclusive + launchSingleTop — so
 *    the back gesture from a fresh workspace exits the app, never unwinds a
 *    pile of discarded conversations. A conversation already in flight stays
 *    in history (it is persisted) and remains reachable from the drawer.
 *  · Motion is hierarchical: section switches use a fast subtle fade-settle;
 *    detail pushes use the directional slide/parallax grammar; the session
 *    gate fades. Reduce-motion kills all of it.
 *  · The drawer knows the current route and marks it selected.
 */
@Composable
fun GsNavHost(
    modifier: Modifier = Modifier,
    /**
     * Hoistable so the shell above the graph can see where the user is. The
     * default keeps every existing call site, preview and test unchanged; the
     * only caller that passes one is MainActivity, which stops painting the
     * ambient canvas once Home has settled over the top of it.
     */
    navController: NavHostController = rememberNavController(),
) {
    val open: (String) -> Unit = { route -> navController.navigate(route) }
    val back: () -> Unit = { navController.popBackStack() }
    val context = LocalContext.current
    val view = LocalView.current
    // The on-device download consent is hosted once for the whole graph: both
    // AI Intelligence and Model Details can request it, and the dialog belongs
    // above whichever page asked.
    var askLocalConsent by remember { mutableStateOf(false) }
    // Session gate — first launch walks Auth → Onboarding; a signed-in user
    // who has NOT finished onboarding is still inside the gate. Before this,
    // only the session flag was read, so finishing auth (or restoring a
    // session) dropped the user straight onto Home and the onboarding pages
    // were never seen in a normal run.
    val start = when {
        !SessionStore.isSessionActive(context) -> GsRoutes.AUTH
        !SessionStore.isOnboarded(context) -> GsRoutes.ONBOARDING
        else -> GsRoutes.HOME
    }

    // The current destination drives two shell behaviours at once: whether the
    // drawer may exist at all (never inside the session gate) and which row
    // the drawer marks as selected. currentBackStackEntryAsState keeps both
    // reactive to navigation without recomposing the whole graph.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerAllowed = !GsRoutes.isSessionRoute(currentRoute)

    // New chat = reset the workspace. The stack is trimmed back to a single
    // fresh conversation surface no matter where the call comes from — the
    // drawer row, the workspace header "+", or the launcher shortcut.
    val newChat: () -> Unit = {
        navController.navigate(GsRoutes.chat(null)) {
            popUpTo(GsRoutes.CHAT) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Home-screen quick actions (New chat / New image / Ask GS): consume exactly
    // once and navigate with a route the drawer already uses. Dropped silently
    // when the session gate is still at Auth — no signed-out navigation. The
    // New-chat shortcut resets the workspace instead of stacking a new one.
    val shortcutRoute by ShortcutBus.route.collectAsStateWithLifecycle()
    LaunchedEffect(shortcutRoute) {
        val route = shortcutRoute ?: return@LaunchedEffect
        ShortcutBus.consume()
        if (start == GsRoutes.HOME || start == GsRoutes.CHAT) {
            if (route == GsRoutes.chat(null)) newChat() else open(route)
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    // Drawer rows carry two navigation classes, each with platform-idiomatic
    // options:
    //  - SECTION switches (chats/explore/create/library/…/settings) use the
    //    canonical pattern: launchSingleTop + popUpTo(WORKSPACE) with
    //    saveState/restoreState — the stack never accumulates duplicate
    //    section destinations, and back from a section lands on the
    //    conversation workspace instead of unwinding every screen the reader
    //    passed through.
    //  - DETAIL pushes (chat / assistant / project / archive / …)
    //    stay plain navigate so every tap opens a fresh instance — sub-screens
    //    keep the context they were opened from (back from Archived returns to
    //    Chats, not the workspace).
    val openFromDrawer: (String) -> Unit = { route ->
        closeDrawer()
        if (GsRoutes.isSectionRoute(route)) {
            navController.navigate(route) {
                launchSingleTop = true
                popUpTo(GsRoutes.CHAT) { saveState = true }
                restoreState = true
            }
        } else {
            navController.navigate(route)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Measured from the sheet: 0.18-0.22 black. Left unset, Material 3
        // composites its own 0.32 and the drawer's strip reads 20 L darker than
        // the reference; the app's SCRIM_LIGHT (0.50) is darker still. The gate
        // measures this as a ratio against the Home capture of the same run.
        scrimColor = com.grapsee.gsai.ui.navigation.drawer.DrawerMetrics.scrim,
        // The drawer is product chrome. Inside the session gate (Auth /
        // Onboarding) neither the content nor the edge-swipe gesture exists —
        // an unauthenticated user never inherits the authenticated shell.
        gesturesEnabled = drawerAllowed,
        drawerContent = {
            if (drawerAllowed) {
                GsDrawerContent(
                    selectedRoute = currentRoute,
                    onNavigate = openFromDrawer,
                    onClose = closeDrawer
                )
            }
        }
    ) {
    NavHost(
        navController = navController,
        startDestination = start,
        modifier = modifier,
        // Motion hierarchy (STEP 2) — transitions are chosen per navigation
        // class, not applied uniformly:
        //
        //  · SECTION switches (drawer → Home/Chats/Explore/Create/Library…)
        //    are root-level navigation: a fast fade with a slight vertical
        //    settle — instant area change, no theatrical slide.
        //  · DETAIL pushes/pops (chat, assistant, project, workspaces, …)
        //    keep the platform push/pop choreography: the incoming screen
        //    slides from the trailing edge while the outgoing one
        //    parallax-fades backwards — the same horizontal grammar Android
        //    system apps use. Navigation 2.8 seeks these pop transitions
        //    under the Android 14+ predictive-back gesture, so the back
        //    swipe scrubs the animation instead of skipping it.
        //  · SESSION screens (Auth ↔ Onboarding) just cross-fade — the gate
        //    is not a place in the product, it is a state change.
        //
        // Reduce-motion is honoured as the platform defines it: NO
        // transition, not a faster one — a 150 ms slide is still motion.
        enterTransition = {
            when {
                reducedMotion() -> EnterTransition.None
                GsRoutes.isSessionRoute(targetState.destination.route) -> fadeIn(tween(200, easing = FastOutSlowInEasing))
                GsRoutes.isSectionRoute(targetState.destination.route) -> sectionEnter()
                else -> pushEnter()
            }
        },
        exitTransition = {
            when {
                reducedMotion() -> ExitTransition.None
                GsRoutes.isSessionRoute(initialState.destination.route) -> fadeOut(tween(200, easing = FastOutSlowInEasing))
                GsRoutes.isSectionRoute(initialState.destination.route) -> sectionExit()
                else -> pushExit()
            }
        },
        popEnterTransition = {
            when {
                reducedMotion() -> EnterTransition.None
                GsRoutes.isSessionRoute(targetState.destination.route) -> fadeIn(tween(200, easing = FastOutSlowInEasing))
                GsRoutes.isSectionRoute(targetState.destination.route) -> sectionEnter()
                else -> popEnter()
            }
        },
        popExitTransition = {
            when {
                reducedMotion() -> ExitTransition.None
                GsRoutes.isSessionRoute(initialState.destination.route) -> fadeOut(tween(200, easing = FastOutSlowInEasing))
                GsRoutes.isSectionRoute(initialState.destination.route) -> sectionExit()
                else -> popExit()
            }
        }
    ) {
        composable(GsRoutes.HOME) {
            HomeDashboardScreen(
                onNewChat = { navController.navigate(GsRoutes.chat(null)) },
                onStartPrompt = { prompt -> navController.navigate(GsRoutes.chat(null, prompt)) },
                onOpenConversation = { id -> navController.navigate(GsRoutes.chat(id)) },
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenChats = { open(GsRoutes.CHATS) },
                onOpenAgent = { open(GsRoutes.EXPLORE) },
                onOpenVoice = { open(GsRoutes.VOICE) },
                onOpenSettings = { open(GsRoutes.SETTINGS) },
            )
        }
        composable(GsRoutes.TRAVEL_AGENT) {
            TravelAgentScreen(onBack = back, onSendToChat = { prompt -> navController.navigate(GsRoutes.chat(null, prompt)) })
        }
        composable(GsRoutes.EXPLORE) {
            ExploreScreen(
                onOpenChat = { prompt, autoSend ->
                    navController.navigate(GsRoutes.chat(null, prompt, autoSend))
                },
                onOpenDiscover = { open(GsRoutes.DISCOVER) },
                onBack = back,
                // the reference dock's Settings is the same push every
                // other surface uses; its Home is the plain pop (onBack).
                onOpenSettings = { open(GsRoutes.SETTINGS) },
            )
        }
        composable(GsRoutes.DISCOVER) {
            DiscoverScreen(
                onOpenChat = { prompt, autoSend ->
                    navController.navigate(GsRoutes.chat(null, prompt, autoSend))
                },
                onBack = back,
            )
        }
        composable(GsRoutes.CHATS) { ChatsScreen(onNavigate = open) }
        composable(GsRoutes.PROFILE) { ProfileScreen(onNavigate = open, onBack = back) }

        composable(GsRoutes.SETTINGS) {
            SettingsHubScreen(
                onBack = back,
                onNavigate = open,
                onLocalAiRequested = { askLocalConsent = true },
                // The mock's Log Out card is real: same SessionStore exit the
                // Account page uses, back to AUTH with the stack cleared.
                onLogout = {
                    SessionStore.signOut(context)
                    navController.navigate(GsRoutes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // --- Settings sub-pages -------------------------------------------
        // Each hub row is a real destination with its own scaffold and back
        // gesture. SETTINGS_PLAN deliberately reuses Billing — one purchase
        // surface, reached from two places.
        composable(GsRoutes.SETTINGS_ACCOUNT) {
            AccountSecurityScreen(
                onBack = back,
                onNavigate = open,
            )
        }
        composable(GsRoutes.SETTINGS_CHAT) {
            ChatSettingsScreen(onBack = back, onNavigate = open)
        }
        composable(GsRoutes.SETTINGS_APPEARANCE) {
            AppearanceSettingsScreen(onBack = back, onNavigate = open)
        }
        composable(GsRoutes.SETTINGS_THEME) {
            ThemeCustomisationScreen(onBack = back)
        }
        composable(GsRoutes.SETTINGS_NOTIFICATIONS) {
            NotificationsAlertsScreen(onBack = back)
        }
        composable(GsRoutes.SETTINGS_PRIVACY) {
            PrivacySettingsScreen(onBack = back, onNavigate = open)
        }
        composable(GsRoutes.SETTINGS_AI) {
            AiIntelligenceScreen(
                onBack = back,
                onLocalAiRequested = { askLocalConsent = true },
                onOpenModelDetails = { navController.navigate(GsRoutes.SETTINGS_MODEL) },
                onTryComponents = {
                    navController.navigate(
                        GsRoutes.chat(
                            null,
                            "Show me a gallery of rendered components — charts, tables, a timeline, a map and a code block — so I can tap them."
                        )
                    )
                }
            )
        }
        composable(GsRoutes.SETTINGS_MODEL) {
            ModelDetailsScreen(
                onBack = back,
                onDownloadRequested = {
                    back()
                    askLocalConsent = true
                }
            )
        }

        composable(GsRoutes.SETTINGS_MEMORY) {
            MemorySettingsScreen(onBack = back, onNavigate = open)
        }
        composable(GsRoutes.SETTINGS_VOICE) {
            VoiceSettingsScreen(onBack = back, onNavigate = open)
        }
        composable(GsRoutes.SETTINGS_ACCESSIBILITY) {
            AccessibilitySettingsScreen(onBack = back)
        }
        composable(GsRoutes.SETTINGS_HELP) {
            HelpSettingsScreen(onBack = back)
        }
        composable(GsRoutes.SETTINGS_ABOUT) {
            AboutSettingsScreen(onBack = back)
        }
        composable(GsRoutes.SETTINGS_PLAN) {
            BillingScreen(onBack = back)
        }
        composable(GsRoutes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = back,
                onNavigate = { navController.navigate(it) }
            )
        }
        composable(GsRoutes.GLASS_SPECIMEN) {
            GlassSpecimenScreen(onBack = back)
        }
        composable(GsRoutes.VOICE) {
            VoiceScreen(
                onBack = back,
                onSendToChat = { spoken ->
                    navController.navigate(GsRoutes.chat(conversationId = null, prompt = spoken))
                }
            )
        }
        composable(GsRoutes.CHAT_ARCHIVE) {
            ArchivedChatsScreen(
                onBack = back,
                onOpenConversation = { id -> navController.navigate(GsRoutes.chat(id)) }
            )
        }
        composable(GsRoutes.CHAT_SEARCH) {
            ChatSearchScreen(
                onBack = back,
                onOpenConversation = { id -> navController.navigate(GsRoutes.chat(id)) },
                onOpenArchive = { open(GsRoutes.CHAT_ARCHIVE) },
                onOpenSaved = { open(GsRoutes.SETTINGS_MEMORY) },
                onViewAllChats = { open(GsRoutes.CHATS) },
                onNewChat = { open(GsRoutes.chat(null)) },
            )
        }

        // Session
        composable(GsRoutes.AUTH) {
            AuthScreen(
                onFinished = {
                    navController.navigate(GsRoutes.ONBOARDING) {
                        popUpTo(GsRoutes.AUTH) { inclusive = true }
                    }
                }
            )
        }
        composable(GsRoutes.ONBOARDING) {
            ReferenceWelcomeScreen(
                onExplore = { navController.navigate(GsRoutes.ONBOARDING_SETUP) },
                onSkip = {
                    SessionStore.activateSession(context)
                    SessionStore.setOnboarded(context, true)
                    navController.navigate(GsRoutes.HOME) { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable(GsRoutes.ONBOARDING_SETUP) {
            OnboardingScreen(
                onFinished = {
                    SessionStore.activateSession(context)
                    SessionStore.setOnboarded(context, true)
                    navController.navigate(GsRoutes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Billing
        composable(GsRoutes.BILLING) { BillingScreen(onBack = back) }

        composable(
            route = GsRoutes.CHAT,
            arguments = listOf(
                navArgument(GsRoutes.ARG_CONVERSATION) {
                    type = NavType.StringType; defaultValue = "new"
                },
                navArgument(GsRoutes.ARG_PROMPT) {
                    type = NavType.StringType; defaultValue = ""
                },
                navArgument(GsRoutes.ARG_SEND) {
                    type = NavType.BoolType; defaultValue = false
                }
            )
        ) { entry ->
            val id = entry.arguments?.getString(GsRoutes.ARG_CONVERSATION)
            val prompt = entry.arguments?.getString(GsRoutes.ARG_PROMPT).orEmpty()
            val autoSend = entry.arguments?.getBoolean(GsRoutes.ARG_SEND) ?: false
            // The workspace root (the start destination, nothing beneath it)
            // shows the drawer menu in the header instead of a back arrow.
            val atWorkspaceRoot = navController.previousBackStackEntry == null
            ChatScreen(
                conversationId = if (id == "new") null else id,
                prefillPrompt = prompt.takeIf { it.isNotBlank() },
                autoSendInitialPrompt = autoSend,
                atWorkspaceRoot = atWorkspaceRoot,
                onOpenDrawer = {
                    // The menu reveal gets the platform's light list tick.
                    view.gsHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    scope.launch { drawerState.open() }
                },
                onNewChat = { newChat() },
                onBack = back,
                onNavigateVoice = { navController.navigate(GsRoutes.VOICE) },
                onOpenChatDetails = { chatId -> navController.navigate(GsRoutes.chatInfo(chatId)) }
            )
        }
        // Reference "Chat Details" — a real page with a back arrow, not a sheet.
        composable(GsRoutes.CHAT_INFO) { entry ->
            ChatInfoScreen(
                conversationId = entry.arguments?.getString(GsRoutes.ARG_CONVERSATION).orEmpty(),
                onBack = back
            )
        }
        }
        if (askLocalConsent) {
            OnDeviceModelConsentDialog(onDismiss = { askLocalConsent = false })
        }
        // First-run model consent: only inside the workspace. Composing it on
        // the auth/onboarding routes covered the welcome pages with a dialog
        // the user has not been introduced to the product for yet.
        val routeNow = backStackEntry?.destination?.route
        if (routeNow != null && !GsRoutes.isSessionRoute(routeNow)) {
            OnDeviceModelConsentDialog()
        }
    }
}

// --- Shell motion hierarchy (STEP 2) -----------------------------------------
//
// Private transition factories consumed by the NavHost lambdas above. Each
// navigation class owns one grammar; the lambdas pick per route so no two
// different kinds of navigation share an animation by accident.

private fun reducedMotion(): Boolean =
    SettingsStore.reduceAnimations || SettingsStore.reduceMotion

/** SECTION — root-level area switch: fast fade with a slight vertical settle. */
private fun sectionEnter(): EnterTransition =
    fadeIn(tween(GsMotion.SECTION_TWEEN_MS, easing = FastOutSlowInEasing)) +
        slideInVertically(tween(GsMotion.SECTION_TWEEN_MS, easing = FastOutSlowInEasing)) { it / 24 }

private fun sectionExit(): ExitTransition =
    fadeOut(tween(GsMotion.SECTION_TWEEN_MS, easing = FastOutSlowInEasing))

/** DETAIL push — the incoming screen slides in from the trailing edge. */
private fun pushEnter(): EnterTransition =
    slideInHorizontally(tween(GsMotion.NAV_TWEEN_MS, easing = FastOutSlowInEasing)) { it } +
        fadeIn(tween(200, easing = FastOutSlowInEasing))

/** DETAIL push — the covered screen parallax-fades backwards. */
private fun pushExit(): ExitTransition =
    slideOutHorizontally(tween(GsMotion.NAV_TWEEN_MS, easing = FastOutSlowInEasing)) { -it / 4 } +
        fadeOut(tween(200, easing = FastOutSlowInEasing))

/** DETAIL pop — the revealed screen parallax-returns from the leading edge. */
private fun popEnter(): EnterTransition =
    slideInHorizontally(tween(GsMotion.NAV_TWEEN_MS, easing = FastOutSlowInEasing)) { -it / 4 } +
        fadeIn(tween(200, easing = FastOutSlowInEasing))

/** DETAIL pop — the dismissed screen slides out toward the trailing edge. */
private fun popExit(): ExitTransition =
    slideOutHorizontally(tween(GsMotion.NAV_TWEEN_MS, easing = FastOutSlowInEasing)) { it } +
        fadeOut(tween(200, easing = FastOutSlowInEasing))
