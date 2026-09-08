package com.grapsee.gsai.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.grapsee.gsai.ui.chat.ConversationFoldersScreen
import com.grapsee.gsai.ui.chat.SharedChatsScreen
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
 * AERUO KINETIC nav graph. The home canvas is the hub; the obsidian drawer
 * is the primary navigation (benchmark AI-app pattern) and everything else
 * is pushed from within.
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

    // Drawer rows carry two kinds of routes and each gets the platform-idiomatic
    // navigation options:
    //  - SECTION switches (chats/explore/create/library/settings/…) use the
    //    canonical bottom-nav pattern: launchSingleTop + popUpTo(home) with
    //    saveState, restoreState — the back stack never accumulates duplicate
    //    section destinations, and back from a section lands on Home instead of
    //    unwinding every screen the reader passed through.
    //  - DETAIL pushes (chat / assistant / project) stay plain navigate so every
    //    tap opens a fresh instance — crucially "New chat" (chat(null)) must
    //    create a new conversation each tap, never dedupe onto the current one.
    val drawerSectionRoutes = setOf(
        GsRoutes.HOME, GsRoutes.CHATS, GsRoutes.CHAT_ARCHIVE, GsRoutes.EXPLORE,
        GsRoutes.CREATE, GsRoutes.LIBRARY, GsRoutes.PROJECTS, GsRoutes.ASSISTANTS,
        GsRoutes.MODELS, GsRoutes.SEARCH, GsRoutes.BILLING, GsRoutes.NOTIFICATIONS,
        GsRoutes.PROFILE, GsRoutes.SETTINGS
    )
    val openFromDrawer: (String) -> Unit = { route ->
        closeDrawer()
        if (route in drawerSectionRoutes) {
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
        drawerContent = {
            GsDrawerContent(
                onNavigate = openFromDrawer,
                onClose = closeDrawer
            )
        }
    ) {
    NavHost(
        navController = navController,
        startDestination = start,
        modifier = modifier,
        // Platform push/pop choreography: the incoming screen slides from the
        // trailing edge while the outgoing one parallax-fades backwards — the
        // same horizontal grammar Android system apps use. Navigation 2.8
        // seeks these pop transitions under the Android 14+ predictive-back
        // gesture, so the back swipe scrubs the animation, not skip it.
        enterTransition = {
            slideInHorizontally(tween(if (GsMotion.reduced) GsMotion.REDUCED_TWEEN_MS else GsMotion.NAV_TWEEN_MS, easing = FastOutSlowInEasing)) { it } +
                fadeIn(tween(200, easing = FastOutSlowInEasing))
        },
        exitTransition = {
            slideOutHorizontally(tween(if (GsMotion.reduced) GsMotion.REDUCED_TWEEN_MS else GsMotion.NAV_TWEEN_MS, easing = FastOutSlowInEasing)) { -it / 4 } +
                fadeOut(tween(200, easing = FastOutSlowInEasing))
        },
        popEnterTransition = {
            slideInHorizontally(tween(if (GsMotion.reduced) GsMotion.REDUCED_TWEEN_MS else GsMotion.NAV_TWEEN_MS, easing = FastOutSlowInEasing)) { -it / 4 } +
                fadeIn(tween(200, easing = FastOutSlowInEasing))
        },
        popExitTransition = {
            slideOutHorizontally(tween(if (GsMotion.reduced) GsMotion.REDUCED_TWEEN_MS else GsMotion.NAV_TWEEN_MS, easing = FastOutSlowInEasing)) { it } +
                fadeOut(tween(200, easing = FastOutSlowInEasing))
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

        composable(GsRoutes.SETTINGS) { SettingsScreen(onBack = back) }
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
        composable(GsRoutes.CHAT_FOLDERS) { ConversationFoldersScreen(onBack = back) }
        composable(GsRoutes.CHAT_SHARED) { SharedChatsScreen(onBack = back) }
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
                }
            )
        ) { entry ->
            val id = entry.arguments?.getString(GsRoutes.ARG_CONVERSATION)
            val prompt = entry.arguments?.getString(GsRoutes.ARG_PROMPT).orEmpty()
            ChatScreen(
                conversationId = if (id == "new") null else id,
                prefillPrompt = prompt.takeIf { it.isNotBlank() },
                onBack = back,
                onNavigateVoice = { navController.navigate(GsRoutes.VOICE) }
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
