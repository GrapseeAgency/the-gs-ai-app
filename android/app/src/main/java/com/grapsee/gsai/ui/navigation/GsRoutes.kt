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
    const val ASSISTANT_EDIT = "assistants/edit/{assistantId}"
    fun assistantEdit(id: String) = "assistants/edit/$id"
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

    // --- Shell navigation classes (STEP 2) -----------------------------------
    //
    // The route map distinguishes three navigation classes so the shell can
    // treat each with its own grammar instead of one generic transition:
    //
    //  SECTION  — root-level product/account areas, switched from the drawer
    //             with the canonical section pattern (launchSingleTop +
    //             popUpTo(home) + saveState/restoreState). Back from a section
    //             lands on Home; the stack never accumulates duplicates.
    //  SESSION  — the auth/onboarding gate. The drawer must never exist here
    //             (no content, no edge-swipe) — an unauthenticated user has no
    //             product navigation to reveal.
    //  DETAIL   — everything else: pushed on top of the current context with
    //             the directional transition and its own back affordance.

    /** Root-level destinations the drawer switches between (Home included —
     *  the drawer is reachable from every screen, so an explicit Home row
     *  gives section screens a one-tap return to the main canvas). */
    val SECTION_ROUTES = setOf(
        HOME, CHATS, EXPLORE, CREATE, LIBRARY,
        PROJECTS, ASSISTANTS, MODELS, SEARCH,
        PROFILE, NOTIFICATIONS, SETTINGS, BILLING
    )

    /** Auth/onboarding — outside the product shell; no drawer, no product nav. */
    val SESSION_ROUTES = setOf(AUTH, ONBOARDING)

    /** True for the drawer's section-switch destinations (exact route match —
     *  pattern routes like [CHAT] are always detail pushes). */
    fun isSectionRoute(route: String?): Boolean = route in SECTION_ROUTES

    /** True inside the session gate — the drawer is architecturally absent. */
    fun isSessionRoute(route: String?): Boolean = route in SESSION_ROUTES
}
