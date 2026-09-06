package com.grapsee.gsai.ui.navigation

import android.net.Uri

/** AERUO KINETIC — route map. Screens are plain composables; this file owns all routing. */
object GsRoutes {
    const val HOME = "home"
    const val CHATS = "chats"
    const val EXPLORE = "explore"
    const val CREATE = "create"
    const val LIBRARY = "library"
    const val PROJECTS = "projects"
    const val ASSISTANTS = "assistants"
    const val SEARCH = "search"
    const val MODELS = "models"
    const val MODEL_COMPARE = "models/compare"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
    const val VOICE = "voice"
    const val ASSISTANT_CREATE = "assistants/create"
    const val CHAT_ARCHIVE = "chats/archived"
    const val CHAT_FOLDERS = "chats/folders"
    const val CHAT_SHARED = "chats/shared"
    const val CHAT_SEARCH = "chats/search"

    // Session — first launch walks Auth → Onboarding before the command centre.
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"

    // Billing / subscription (reached from Profile).
    const val BILLING = "billing"

    // Blueprint workspaces — each Create tool gets its own room, not a chat dump.
    const val RESEARCH = "research"
    const val VISION = "vision"
    const val IMAGE_STUDIO = "create/image"
    const val WRITING_STUDIO = "create/writing"
    const val CODE_WORKSPACE = "create/code"
    const val PROMPT_BUILDER = "create/prompt"

    const val CHAT = "chat/{conversationId}?prompt={prompt}"
    const val ARG_CONVERSATION = "conversationId"
    const val ARG_PROMPT = "prompt"

    /** Voice press-and-hold hands its transcript to the composer via ?prompt=. */
    fun chat(conversationId: String?, prompt: String? = null): String {
        val base = "chat/${conversationId ?: "new"}"
        return if (prompt.isNullOrBlank()) base else "$base?prompt=${Uri.encode(prompt)}"
    }

    const val PROJECT_DETAIL = "project/{projectId}"
    const val ARG_PROJECT = "projectId"
    fun project(id: String) = "project/$id"

    const val ASSISTANT_DETAIL = "assistant/{assistantId}"
    const val ARG_ASSISTANT = "assistantId"
    fun assistant(id: String) = "assistant/$id"
}
