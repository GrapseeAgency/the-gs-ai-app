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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.grapsee.gsai.ShortcutBus
import com.grapsee.gsai.data.SessionStore
import com.grapsee.gsai.ui.assistants.AssistantCreateScreen
import com.grapsee.gsai.ui.assistants.AssistantDetailScreen
import com.grapsee.gsai.ui.assistants.AssistantsScreen
import com.grapsee.gsai.ui.auth.AuthScreen
import com.grapsee.gsai.ui.auth.OnboardingScreen
import com.grapsee.gsai.ui.billing.BillingScreen
import com.grapsee.gsai.ui.create.CodeWorkspaceScreen
import com.grapsee.gsai.ui.create.ImageStudioScreen
import com.grapsee.gsai.ui.create.PromptBuilderScreen
import com.grapsee.gsai.ui.create.WritingStudioScreen
import com.grapsee.gsai.ui.chat.ArchivedChatsScreen
import com.grapsee.gsai.ui.chat.ChatScreen
import com.grapsee.gsai.ui.chat.ChatSearchScreen
import com.grapsee.gsai.ui.chat.ChatsScreen
import com.grapsee.gsai.ui.create.CreateScreen
import com.grapsee.gsai.ui.explore.ExploreScreen
import com.grapsee.gsai.ui.home.HomeScreen
import com.grapsee.gsai.ui.library.LibraryScreen
import com.grapsee.gsai.ui.models.ModelCentreScreen
import com.grapsee.gsai.ui.models.ModelCompareScreen
import com.grapsee.gsai.ui.notifications.NotificationsScreen
import com.grapsee.gsai.ui.profile.ProfileScreen
import com.grapsee.gsai.ui.projects.ProjectDetailScreen
import com.grapsee.gsai.ui.projects.ProjectsScreen
import com.grapsee.gsai.ui.research.ResearchScreen
import com.grapsee.gsai.ui.search.SearchScreen
import com.grapsee.gsai.ui.vision.VisionScreen
import com.grapsee.gsai.ui.settings.SettingsScreen
import com.grapsee.gsai.ui.voice.VoiceScreen
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.gsHaptic
import kotlinx.coroutines.launch

/**
 * AERUO KINETIC nav graph — the product shell. The home canvas is the hub,
 * the drawer is the primary navigation (benchmark AI-app pattern) and
 * everything else is pushed from within.
 *
 * STEP 2 shell contract:
 *  · The drawer belongs to the PRODUCT only. Auth/Onboarding sit outside it:
 *    no drawer content and no edge-swipe reveal while the session gate is up.
 *  · Motion is hierarchical, not one-size: section switches (drawer →
 *    Home/Chats/Explore/…) use a fast subtle fade-settle; detail pushes use
 *    the directional slide/parallax grammar; the session gate fades. The
 *    predictive-back gesture scrubs the pop transitions on Android 14+.
 *  · The drawer knows the current route and marks it selected.
 */
@Composable
fun GsNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val open: (String) -> Unit = { route -> navController.navigate(route) }
    val back: () -> Unit = { navController.popBackStack() }
    val context = LocalContext.current
    val view = LocalView.current
    // Session gate — first launch walks Auth → Onboarding; later launches go straight Home.
    val start = if (SessionStore.isSessionActive(context)) GsRoutes.HOME else GsRoutes.AUTH

    // The current destination drives two shell behaviours at once: whether the
    // drawer may exist at all (never inside the session gate) and which row
    // the drawer marks as selected. currentBackStackEntryAsState keeps both
    // reactive to navigation without recomposing the whole graph.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerAllowed = !GsRoutes.isSessionRoute(currentRoute)

    // Home-screen quick actions (New chat / New image / Ask GS): consume exactly
    // once and navigate with a route the drawer already uses. Dropped silently
    // when the session gate is still at Auth — no signed-out navigation.
    val shortcutRoute by ShortcutBus.route.collectAsState()
    LaunchedEffect(shortcutRoute) {
        val route = shortcutRoute ?: return@LaunchedEffect
        ShortcutBus.consume()
        if (start == GsRoutes.HOME) open(route)
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    // Drawer rows carry two navigation classes, each with platform-idiomatic
    // options:
    //  - SECTION switches (home/chats/explore/create/library/…/settings) use
    //    the canonical bottom-nav pattern: launchSingleTop + popUpTo(home)
    //    with saveState, restoreState — the back stack never accumulates
    //    duplicate section destinations, and back from a section lands on
    //    Home instead of unwinding every screen the reader passed through.
    //    Recent chats, archive/folders/shared and account routes that need
    //    their own back affordance are deliberately NOT sections — see
    //    GsRoutes.SECTION_ROUTES for the exact membership.
    //  - DETAIL pushes (chat / assistant / project / archive / folders / …)
    //    stay plain navigate so every tap opens a fresh instance — crucially
    //    "New chat" (chat(null)) must create a new conversation each tap,
    //    never dedupe onto the current one, and sub-screens keep the context
    //    they were opened from (back from Archived returns to Chats, not Home).
    val openFromDrawer: (String) -> Unit = { route ->
        closeDrawer()
        if (GsRoutes.isSectionRoute(route)) {
            navController.navigate(route) {
                launchSingleTop = true
                popUpTo(GsRoutes.HOME) { saveState = true }
                restoreState = true
            }
        } else {
            navController.navigate(route)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
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
            HomeScreen(
                onNavigate = open,
                onOpenDrawer = {
                    // The menu reveal gets the platform's light list tick.
                    view.gsHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    scope.launch { drawerState.open() }
                }
            )
        }
        composable(GsRoutes.CHATS) { ChatsScreen(onNavigate = open) }
        composable(GsRoutes.EXPLORE) { ExploreScreen(onNavigate = open) }
        composable(GsRoutes.CREATE) { CreateScreen(onNavigate = open) }
        composable(GsRoutes.LIBRARY) { LibraryScreen(onNavigate = open) }
        composable(GsRoutes.PROJECTS) { ProjectsScreen(onNavigate = open) }
        composable(GsRoutes.SEARCH) { SearchScreen(onNavigate = open) }
        composable(GsRoutes.ASSISTANTS) { AssistantsScreen(onNavigate = open) }
        composable(GsRoutes.MODELS) { ModelCentreScreen(onNavigate = open) }
        composable(GsRoutes.PROFILE) { ProfileScreen(onNavigate = open) }

        composable(GsRoutes.SETTINGS) { SettingsScreen(onBack = back, onNavigate = open) }
        composable(GsRoutes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = back,
                onNavigate = { navController.navigate(it) }
            )
        }
        composable(GsRoutes.VOICE) {
            VoiceScreen(
                onBack = back,
                onSendToChat = { spoken ->
                    navController.navigate(GsRoutes.chat(conversationId = null, prompt = spoken))
                }
            )
        }
        composable(GsRoutes.MODEL_COMPARE) { ModelCompareScreen(onBack = back) }
        composable(GsRoutes.ASSISTANT_CREATE) { AssistantCreateScreen(onBack = back) }
        composable(
            route = GsRoutes.ASSISTANT_EDIT,
            arguments = listOf(navArgument(GsRoutes.ARG_ASSISTANT) { type = NavType.StringType })
        ) { entry ->
            AssistantCreateScreen(
                assistantId = entry.arguments?.getString(GsRoutes.ARG_ASSISTANT),
                onBack = back
            )
        }
        composable(GsRoutes.CHAT_ARCHIVE) { ArchivedChatsScreen(onBack = back) }
        composable(GsRoutes.CHAT_SEARCH) {
            ChatSearchScreen(
                onBack = back,
                onOpenConversation = { id -> navController.navigate(GsRoutes.chat(id)) }
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

        // Blueprint workspaces
        composable(GsRoutes.RESEARCH) { ResearchScreen(onBack = back) }
        composable(GsRoutes.VISION) { VisionScreen(onBack = back) }
        composable(GsRoutes.IMAGE_STUDIO) { ImageStudioScreen(onBack = back) }
        composable(GsRoutes.WRITING_STUDIO) { WritingStudioScreen(onBack = back) }
        composable(GsRoutes.CODE_WORKSPACE) { CodeWorkspaceScreen(onBack = back) }
        composable(GsRoutes.PROMPT_BUILDER) {
            PromptBuilderScreen(onBack = back, onNavigate = open)
        }

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
            ChatScreen(
                conversationId = if (id == "new") null else id,
                prefillPrompt = prompt.takeIf { it.isNotBlank() },
                autoSendInitialPrompt = autoSend,
                onBack = back,
                onNavigateVoice = { navController.navigate(GsRoutes.VOICE) },
                onNavigateModels = { navController.navigate(GsRoutes.MODELS) }
            )
        }

        composable(
            route = GsRoutes.PROJECT_DETAIL,
            arguments = listOf(navArgument(GsRoutes.ARG_PROJECT) { type = NavType.StringType })
        ) { entry ->
            ProjectDetailScreen(
                projectId = entry.arguments?.getString(GsRoutes.ARG_PROJECT).orEmpty(),
                onBack = back
            )
        }

        composable(
            route = GsRoutes.ASSISTANT_DETAIL,
            arguments = listOf(navArgument(GsRoutes.ARG_ASSISTANT) { type = NavType.StringType })
        ) { entry ->
            AssistantDetailScreen(
                assistantId = entry.arguments?.getString(GsRoutes.ARG_ASSISTANT).orEmpty(),
                onBack = back,
                onStartChat = { route -> navController.navigate(route) },
                onEdit = { id -> navController.navigate(GsRoutes.assistantEdit(id)) }
            )
        }
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
