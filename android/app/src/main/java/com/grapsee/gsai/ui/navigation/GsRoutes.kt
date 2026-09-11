package com.grapsee.gsai.ui.navigation

import android.net.Uri

/** AERUO KINETIC — route map. Screens are plain composables; this file owns all routing. */
object GsRoutes {
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
    // PHASE 2: the fabricated Folders/Shared destinations are gone — Archive
    // (real) remains the only chats hub quick-link.
    const val CHAT_ARCHIVE = "chats/archived"
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

    const val CHAT = "chat/{conversationId}?prompt={prompt}&send={send}"
    const val ARG_CONVERSATION = "conversationId"
    const val ARG_PROMPT = "prompt"
    const val ARG_SEND = "send"

    /**
     * Open a conversation. [prompt] prefills the composer; [autoSend] sends it
     * immediately on arrival.
     *
     * PHASE 3 workspace model: this is the ROOT surface. `chat(null)` (the
     * fresh conversation) is the app's start destination — the empty state of
     * the same screen the transcript renders in. There is no separate Home
     * launcher and no separate "New chat" page.
     */
    fun chat(conversationId: String?, prompt: String? = null, autoSend: Boolean = false): String {
        val base = "chat/${conversationId ?: "new"}"
        val params = mutableListOf<String>()
        if (!prompt.isNullOrBlank()) params += "prompt=${Uri.encode(prompt)}"
        if (autoSend) params += "send=1"
        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }

    const val PROJECT_DETAIL = "project/{projectId}"
    const val ARG_PROJECT = "projectId"
    fun project(id: String) = "project/$id"

    const val ASSISTANT_DETAIL = "assistant/{assistantId}"
    const val ARG_ASSISTANT = "assistantId"
    fun assistant(id: String) = "assistant/$id"

    // --- Shell navigation classes ---------------------------------------------
    //
    // The route map distinguishes three navigation classes so the shell can
    // treat each with its own grammar instead of one generic transition:
    //
    //  SECTION  — root-level product/account areas, switched from the drawer
    //             with the canonical section pattern (launchSingleTop +
    //             popUpTo(workspace) + saveState/restoreState). Back from a
    //             section lands on the conversation workspace; the stack never
    //             accumulates duplicates.
    //  SESSION  — the auth/onboarding gate. The drawer must never exist here
    //             (no content, no edge-swipe) — an unauthenticated user has no
    //             product navigation to reveal.
    //  DETAIL   — everything else: pushed on top of the current context with
    //             the directional transition and its own back affordance.

    /** Root-level destinations the drawer's More surface switches to. */
    val SECTION_ROUTES = setOf(
        CHATS, EXPLORE, CREATE, LIBRARY,
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
