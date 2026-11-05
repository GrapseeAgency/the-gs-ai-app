package com.grapsee.gsai.ui.navigation

import android.net.Uri

/** AERUO KINETIC — route map. Screens are plain composables; this file owns all routing. */
object GsRoutes {
    const val HOME = "home"
    /** "Start a New Chat" — the pre-chat page (home-new-chat.png). It owns the
     *  composer, the six starting tools, the recent list and the model entry,
     *  so a conversation begins here rather than on a blank transcript. */
    const val NEW_CHAT = "new-chat"
    /** Model Selection (model-selection-v2.png) — the picker the pre-chat page
     *  opens, distinct from the Settings AI page's model details. */
    const val MODEL_PICKER = "model-picker"
    const val TRAVEL_AGENT = "travel-agent"
    const val EXPLORE = "explore"
    const val DISCOVER = "discover"
    /** One Creative Collection. Tapping a collection used to open the mixed
     *  Discover catalogue, which answered a different question. */
    const val EXPLORE_COLLECTION = "explore/collection/{collectionId}"
    const val ARG_COLLECTION = "collectionId"
    /** One tool, explained: what it is for, the three moves it makes, and three
     *  openings. Every Explore/Discover/collection card lands here first. */
    const val EXPLORE_TOOL = "explore/tool/{toolId}"
    const val ARG_TOOL = "toolId"
    const val CHATS = "chats"
    /** Saved Library — every row `saveToLibrary` has ever written. Until this
     *  page existed, saving was a one-way door: the rows were stored, exported
     *  and cleared, but never shown. */
    const val LIBRARY = "library"
    /** One saved item, at full length. The list truncates at 72 characters. */
    const val LIBRARY_ITEM = "library/{itemId}"
    const val ARG_ITEM = "itemId"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    // Reference settings architecture — each row in the hub is a real
    // destination with its own scaffold, not an accordion that grows the hub.
    const val SETTINGS_ACCOUNT = "settings/account"
    const val SETTINGS_IDENTITY = "settings/identity"
    const val SETTINGS_CHAT = "settings/chat"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_THEME = "settings/theme"
    const val SETTINGS_LANGUAGE = "settings/language"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_SEARCH_HISTORY = "settings/search-history"
    const val SETTINGS_PERSONALIZATION = "settings/personalization"
    const val SETTINGS_PERMISSIONS = "settings/permissions"
    /** Data Export & Account Deletion — the owner of the privacy page's
     *  Export, Retention and Delete rows. */
    const val SETTINGS_DATA = "settings/data"
    const val SETTINGS_AI = "settings/ai"
    const val SETTINGS_MODEL = "settings/model"
    const val SETTINGS_MEMORY = "settings/memory"
    const val SETTINGS_VOICE = "settings/voice"
    const val SETTINGS_VOICE_ENGINE = "settings/voice/engine"
    const val SETTINGS_ACCESSIBILITY = "settings/accessibility"
    /** Tone, detail level and language — the three Profile rows that showed a
     *  value and offered no way to change it. */
    const val SETTINGS_RESPONSE_STYLE = "settings/response-style"
    /** Interests and the pinned tools — the two Profile rows that used to be
     *  static text with no editor. */
    const val SETTINGS_PROFILE_PREFS = "settings/profile-preferences"
    const val SETTINGS_HELP = "settings/help"
    /** One help article — the answer, its category and the two ways to get a
     *  human, instead of a dialog that clipped long copy. */
    const val HELP_ARTICLE = "help/article/{topicId}"
    const val ARG_TOPIC = "topicId"
    const val SETTINGS_INTEGRATIONS = "settings/integrations"
    const val SETTINGS_ABOUT = "settings/about"
    const val SETTINGS_LICENCES = "settings/about/licenses"
    const val SETTINGS_PLAN = "settings/plan"
    const val NOTIFICATIONS = "notifications"
    /** One notice, opened from the inbox. The inbox used to answer a tap with
     *  a stock AlertDialog; a notice has a body, a time and a destination, and
     *  each of those needs a page. */
    const val NOTIFICATION_DETAIL = "notification/{noticeId}"
    const val ARG_NOTICE = "noticeId"
    const val VOICE = "voice"
    /** Mandate §3 material specimen. Internal tooling: dispatched directly, never
     *  linked from the app, so it can be screenshotted without a nav path. */
    const val GLASS_SPECIMEN = "glass-specimen"
    // PHASE 2: the fabricated Folders/Shared destinations are gone — Archive
    // (real) remains the only chats hub quick-link.
    const val CHAT_ARCHIVE = "chats/archived"
    const val CHAT_SEARCH = "chats/search"

    // Session — first launch walks Auth → Onboarding before the command centre.
    const val AUTH = "auth"
    const val ONBOARDING = "onboarding"
    const val ONBOARDING_SETUP = "onboarding/setup"

    // Billing / subscription (reached from Profile).
    const val BILLING = "billing"

    // Every optional argument the builder below can emit MUST be declared in
    // the pattern. `attach` was missing: `chat(new?attach=1)` matched no
    // destination at all, so the pre-chat page's paperclip threw instead of
    // opening the sheet, and `?prompt=…&send=1&attach=1` silently dropped it.
    const val CHAT = "chat/{conversationId}?prompt={prompt}&send={send}&attach={attach}"
    const val ARG_CONVERSATION = "conversationId"
    const val ARG_PROMPT = "prompt"
    const val ARG_SEND = "send"
    const val ARG_ATTACH = "attach"

    /** Reference "Chat Details" — info and stats for one conversation. */
    const val CHAT_INFO = "chat-info/{conversationId}"

    fun chatInfo(conversationId: String): String = "chat-info/${Uri.encode(conversationId)}"

    fun notification(noticeId: Long): String = "notification/$noticeId"

    fun helpArticle(topicId: String): String = "help/article/${Uri.encode(topicId)}"

    fun collection(collectionId: String): String = "explore/collection/${Uri.encode(collectionId)}"

    fun tool(toolId: String): String = "explore/tool/${Uri.encode(toolId)}"

    fun libraryItem(itemId: String): String = "library/${Uri.encode(itemId)}"

    /**
     * Open a conversation. [prompt] prefills the composer; [autoSend] sends it
     * immediately on arrival.
     *
     * PHASE 3 workspace model: this is the ROOT surface. `chat(null)` (the
     * fresh conversation) is the app's start destination — the empty state of
     * the same screen the transcript renders in. There is no separate Home
     * launcher and no separate "New chat" page.
     */
    fun chat(
        conversationId: String?,
        prompt: String? = null,
        autoSend: Boolean = false,
        // The pre-chat page's paperclip/image buttons: open the chat with its
        // real attach sheet up (camera, photos, files), not an empty room.
        attach: Boolean = false,
    ): String {
        val base = "chat/${conversationId ?: "new"}"
        val params = mutableListOf<String>()
        if (!prompt.isNullOrBlank()) params += "prompt=${Uri.encode(prompt)}"
        // THE SEND BUG (0.86.10): these were emitted as `send=1` / `attach=1`.
        // Navigation's BoolType parses with String.toBoolean(), which is true
        // ONLY for "true" — so "1" arrived as FALSE. A send from the pre-chat
        // page therefore prefilled the composer without dispatching, the chat
        // surface found an empty transcript and handed straight back to the
        // pre-chat page: tapping send appeared to do nothing at all.
        if (autoSend) params += "send=true"
        if (attach) params += "attach=true"
        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }

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

    /** Root-level destinations the drawer switches to. */
    val SECTION_ROUTES = setOf(
        HOME, CHATS,
        PROFILE, NOTIFICATIONS, SETTINGS, BILLING
    )

    /** Auth/onboarding — outside the product shell; no drawer, no product nav. */
    val SESSION_ROUTES = setOf(AUTH, ONBOARDING, ONBOARDING_SETUP)

    /** True for the drawer's section-switch destinations (exact route match —
     *  pattern routes like [CHAT] are always detail pushes). */
    fun isSectionRoute(route: String?): Boolean = route in SECTION_ROUTES

    /** True inside the session gate — the drawer is architecturally absent. */
    fun isSessionRoute(route: String?): Boolean = route in SESSION_ROUTES
}
