package com.grapsee.gsai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.grapsee.gsai.ui.assistants.AssistantCreateScreen
import com.grapsee.gsai.ui.assistants.AssistantDetailScreen
import com.grapsee.gsai.ui.assistants.AssistantsScreen
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
import com.grapsee.gsai.ui.search.SearchScreen
import com.grapsee.gsai.ui.settings.SettingsScreen
import com.grapsee.gsai.ui.voice.VoiceScreen

/**
 * AERUO KINETIC nav graph. Bottom tabs: Home · Chats · Explore · Create · Library.
 * Everything else (Projects, Assistants, Models, Search, Profile, Settings,
 * Voice, Notifications) is pushed from within — nav bar stays clean.
 */
@Composable
fun GsNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val open: (String) -> Unit = { route -> navController.navigate(route) }
    val back: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = GsRoutes.HOME, modifier = modifier) {
        composable(GsRoutes.HOME) { HomeScreen(onNavigate = open) }
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
        composable(GsRoutes.NOTIFICATIONS) { NotificationsScreen(onBack = back) }
        composable(GsRoutes.VOICE) { VoiceScreen(onBack = back) }
        composable(GsRoutes.MODEL_COMPARE) { ModelCompareScreen(onBack = back) }
        composable(GsRoutes.ASSISTANT_CREATE) { AssistantCreateScreen(onBack = back) }
        composable(GsRoutes.CHAT_ARCHIVE) { ArchivedChatsScreen(onBack = back) }
        composable(GsRoutes.CHAT_FOLDERS) { ConversationFoldersScreen(onBack = back) }
        composable(GsRoutes.CHAT_SHARED) { SharedChatsScreen(onBack = back) }
        composable(GsRoutes.CHAT_SEARCH) { ChatSearchScreen(onBack = back) }

        composable(
            route = GsRoutes.CHAT,
            arguments = listOf(navArgument(GsRoutes.ARG_CONVERSATION) {
                type = NavType.StringType; defaultValue = "new"
            })
        ) { entry ->
            val id = entry.arguments?.getString(GsRoutes.ARG_CONVERSATION)
            ChatScreen(
                conversationId = if (id == "new") null else id,
                onBack = back
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
                onBack = back
            )
        }
    }
}
