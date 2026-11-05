package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.grapsee.gsai.ui.chat.content.nexlyPearlBackdrop
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsGlassSwitch
import com.grapsee.gsai.ui.components.GsOverlayPanel
import com.grapsee.gsai.ui.components.GsTextSizeControl
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A DEFECT PROBE, not a gallery.
 *
 * Every pass before this one argued from the source: "the composer is one
 * control", "the bubble ink is derived", "the actions are icons". The owner
 * kept saying the same three things were still wrong, and he was right to —
 * nothing here had ever been LOOKED at. This renders the real components at
 * phone size so the pixels can be checked against the words.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatDefectProbeTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun onlyOnRequest() {
        assumeTrue(System.getProperty("gs.screenshots") == "true")
    }

    /** The user turn exactly as the screen mounts it, plus the composer in both states. */
    @Composable
    private fun surface(dark: Boolean, draft: String) {
        TheGsAiTheme(darkTheme = dark) {
            Box(modifier = Modifier.fillMaxSize().nexlyPearlBackdrop()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    UserMessage(
                        message = ChatUiMessage(
                            id = "probe-user",
                            role = "user",
                            content = "hello",
                            createdAt = "2026-11-04T16:40:00Z"
                        )
                    )
                    UserMessage(
                        message = ChatUiMessage(
                            id = "probe-user-2",
                            role = "user",
                            content = "I work a 9–5 shift tomorrow and need to fit in a gym session, " +
                                "meal prep, and two hours of study for an exam. Can you plan my day?",
                            createdAt = "2026-11-04T16:41:00Z"
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "action row:",
                        fontSize = 11.sp,
                        color = GsTheme.colors.textTertiary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TurnActionIcon("copy", "Copy") {}
                        TurnActionIcon("refresh", "Regenerate", onClick = {})
                        TurnActionIcon("volume", "Read aloud", onClick = {})
                        TurnActionIcon("share-3", "Share", onClick = {})
                        Spacer(Modifier.width(2.dp))
                        TurnVerdict(verdict = null, onVerdict = {}, modifier = Modifier.weight(1f))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TurnActionIcon("copy", "Copy") {}
                        TurnActionIcon("refresh", "Regenerate", onClick = {})
                        TurnActionIcon("volume", "Read aloud", onClick = {})
                        TurnActionIcon("share-3", "Share", onClick = {})
                        Spacer(Modifier.width(2.dp))
                        TurnVerdict(verdict = "up", onVerdict = {}, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "composer:",
                        fontSize = 11.sp,
                        color = GsTheme.colors.textTertiary
                    )
                    val text = remember { mutableStateOf(draft) }
                    ComposerRow(
                        draftText = { text.value },
                        onDraftChange = { text.value = it },
                        onSend = {},
                        onAttach = {},
                        isStreaming = false,
                        onStop = {},
                        queuedText = { "" },
                        onQueue = {},
                        onUnqueue = {},
                        onVoice = {}
                    )
                }
            }
        }
    }

    private fun shoot(dark: Boolean, draft: String, path: String) {
        compose.setContent { surface(dark, draft) }
        compose.onRoot().captureRoboImage(path)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun lightEmptyDraft() = shoot(false, "", "build/screenshots/probe-light-mic.png")

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun lightTypedDraft() = shoot(false, "hello", "build/screenshots/probe-light-send.png")

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun darkTypedDraft() = shoot(true, "hello", "build/screenshots/probe-dark-send.png")

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h568dp-mdpi")
    fun narrowTypedDraft() = shoot(false, "hello", "build/screenshots/probe-narrow-send.png")

    /** A real settled assistant turn, with its identity row and action row. */
    @Composable
    private fun assistantTurn(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            Box(modifier = Modifier.fillMaxSize().nexlyPearlBackdrop()) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    UserMessage(
                        message = ChatUiMessage(
                            id = "u",
                            role = "user",
                            content = "I keep procrastinating on my thesis. Why?",
                            createdAt = "2026-11-04T16:40:00Z"
                        )
                    )
                    Spacer(Modifier.height(14.dp))
                    AssistantMessage(
                        message = ChatUiMessage(
                            id = "a",
                            role = "assistant",
                            content = "Procrastination on a thesis usually isn't laziness — it's a response to the scale and ambiguity of the work.\n\n**One realistic change this week:** pick a tiny, time-boxed writing window.",
                            createdAt = "2026-11-04T16:41:00Z"
                        ),
                        isSpeaking = false,
                        onCopy = {},
                        onRegenerate = {},
                        onReadAloud = {}
                    )
                }
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun assistantTurnLight() {
        compose.setContent { assistantTurn(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-turn-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun assistantTurnDark() {
        compose.setContent { assistantTurn(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-turn-dark.png")
    }

    /** The pre-chat page, exactly as the NEW_CHAT route mounts it. */
    @Composable
    private fun preChat(dark: Boolean, draft: String) {
        TheGsAiTheme(darkTheme = dark) {
            com.grapsee.gsai.ui.home.NewChatScreen(
                onStartChat = { _, _ -> },
                onOpenConversation = {},
                onOpenAllChats = {},
                onOpenModels = {},
                onOpenMenu = {},
                onOpenExplore = {},
                seedPrompt = draft,
                onSeedConsumed = {},
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun preChatLight() {
        compose.setContent { preChat(false, "") }
        compose.onRoot().captureRoboImage("build/screenshots/probe-prechat-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun preChatTyped() {
        compose.setContent { preChat(false, "hello") }
        compose.onRoot().captureRoboImage("build/screenshots/probe-prechat-typed.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun preChatDark() {
        compose.setContent { preChat(true, "") }
        compose.onRoot().captureRoboImage("build/screenshots/probe-prechat-dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun modelPickerLight() {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                com.grapsee.gsai.ui.settings.ModelSelectionScreen(onBack = {}, onContinue = {})
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/probe-models-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun modelPickerDark() {
        compose.setContent {
            TheGsAiTheme(darkTheme = true) {
                com.grapsee.gsai.ui.settings.ModelSelectionScreen(onBack = {}, onContinue = {})
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/probe-models-dark.png")
    }

    /**
     * The strip the reader looks at every single time, and the one surface the
     * earlier probes never rendered: the chat header. It carries a sparkle, a
     * back arrow, the thread title and a menu — four things that can drift out
     * of alignment without any test noticing.
     */
    @Composable
    private fun header(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            Box(modifier = Modifier.fillMaxSize().nexlyPearlBackdrop()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NexlyChatHeader(
                        sparkleLabel = "New chat",
                        onSparkle = {},
                        onSparkleLong = null,
                        menuIcon = com.grapsee.gsai.ui.icons.GsIcons.MoreVertical,
                        menuLabel = "Conversation options",
                        onMenu = {},
                        onMenuLong = null,
                        onBack = {}
                    )
                }
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun headerLight() {
        compose.setContent { header(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-header-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun headerDark() {
        compose.setContent { header(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-header-dark.png")
    }
    // --- 0.86.13: the overlay and the one-word bubble -----------------------

    /** "Hi" is the whole message. The bubble must be as wide as the word. */
    @Composable
    private fun shortBubbles(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            Box(modifier = Modifier.fillMaxSize().nexlyPearlBackdrop()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserMessage(
                        message = ChatUiMessage(
                            id = "hi",
                            role = "user",
                            content = "Hi",
                            createdAt = "2026-11-04T16:40:00Z"
                        )
                    )
                    UserMessage(
                        message = ChatUiMessage(
                            id = "short",
                            role = "user",
                            content = "what time is it in Tokyo?",
                            createdAt = "2026-11-04T16:41:00Z"
                        )
                    )
                    UserMessage(
                        message = ChatUiMessage(
                            id = "long",
                            role = "user",
                            content = "I work a 9-5 shift tomorrow and need to fit in a gym session, meal prep, and two hours of study for an exam. Can you plan my day?",
                            createdAt = "2026-11-04T16:42:00Z"
                        )
                    )
                }
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun shortBubblesLight() {
        compose.setContent { shortBubbles(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-bubbles-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun shortBubblesDark() {
        compose.setContent { shortBubbles(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-bubbles-dark.png")
    }

    /** The attach panel exactly as the overlay mounts it, over a live page. */
    @Composable
    private fun attachOverlay(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            Box(modifier = Modifier.fillMaxSize().nexlyPearlBackdrop()) {
                // The popover's own container: 248dp, bottom-left, 84dp up —
                // the same numbers `GsAnchoredPopover` uses, so what the probe
                // photographs is what the phone draws.
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 84.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column(
                        modifier = Modifier
                            .width(248.dp)
                            .gsGlass(GsGlassRole.Modal, androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                            .padding(vertical = 6.dp)
                    ) {
                        AttachPanelContent(
                            onDismiss = {},
                            onGallery = {},
                            onCamera = {},
                            onFiles = {},
                            onVoice = {},
                            onUnavailable = {},
                            selectedMode = "auto",
                            onModeSelected = {}
                        )
                    }
                }
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun attachOverlayLight() {
        compose.setContent { attachOverlay(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-attach-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun attachOverlayDark() {
        compose.setContent { attachOverlay(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-attach-dark.png")
    }

    @Composable
    private fun chatMenuOverlay(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            Box(modifier = Modifier.fillMaxSize().nexlyPearlBackdrop()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 68.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Column(
                        modifier = Modifier
                            .width(208.dp)
                            .gsGlass(GsGlassRole.Modal, androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                            .padding(vertical = 6.dp)
                    ) {
                        ConversationOptionsContent(
                            title = "hello",
                            onDismiss = {},
                            onRename = {},
                            onShare = {},
                            onDetails = {},
                            onExport = {},
                            onClear = {},
                            onDelete = {}
                        )
                    }
                }
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun chatMenuOverlayLight() {
        compose.setContent { chatMenuOverlay(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-menu-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun chatMenuOverlayDark() {
        compose.setContent { chatMenuOverlay(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-menu-dark.png")
    }

    // --- 0.86.19: the two controls that were still stock ---------------------

    /**
     * The Accessibility page, which is where both offenders lived: the font-size
     * slider above, four toggles below. Rendered at phone size so the ladder and
     * the switches can be judged as a page, not as an idea.
     */
    @Composable
    private fun accessibilityPage(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            com.grapsee.gsai.ui.settings.AccessibilitySettingsScreen(onBack = {})
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun accessibilityLight() {
        compose.setContent { accessibilityPage(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-a11y-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun accessibilityDark() {
        compose.setContent { accessibilityPage(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-a11y-dark.png")
    }

    /**
     * The controls alone, at three states each, on the pearl backdrop — off/on
     * for the switch and small/middle/large for the ladder. A page shot hides
     * the state that matters (the reader only ever sees one at a time); this is
     * the only view where both states are in the same frame.
     */
    @Composable
    private fun controlsMatrix(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            Box(modifier = Modifier.fillMaxSize().nexlyPearlBackdrop()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GsGlassSwitch(checked = false, onCheckedChange = {})
                        GsGlassSwitch(checked = true, onCheckedChange = {})
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .gsGlass(GsGlassRole.CardLight, androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    ) {
                        GsTextSizeControl(
                            value = 0.85f,
                            onValueChange = {},
                            onValueChangeFinished = {}
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .gsGlass(GsGlassRole.CardLight, androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    ) {
                        GsTextSizeControl(
                            value = 1.05f,
                            onValueChange = {},
                            onValueChangeFinished = {}
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .gsGlass(GsGlassRole.CardLight, androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    ) {
                        GsTextSizeControl(
                            value = 1.30f,
                            onValueChange = {},
                            onValueChangeFinished = {}
                        )
                    }
                }
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun controlsMatrixLight() {
        compose.setContent { controlsMatrix(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-controls-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun controlsMatrixDark() {
        compose.setContent { controlsMatrix(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-controls-dark.png")
    }

    // --- 0.86.21: the pages that were missing ------------------------------

    /** Settings → Voice → Speech engine. No synthesizer exists in this JVM, so
     *  the probe also proves the page's own "unavailable" state is a page and
     *  not a crash. */
    @Composable
    private fun speechEnginePage(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            com.grapsee.gsai.ui.settings.SpeechEngineScreen(onBack = {})
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun speechEngineLight() {
        compose.setContent { speechEnginePage(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-speech-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun speechEngineDark() {
        compose.setContent { speechEnginePage(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-speech-dark.png")
    }

    @Composable
    private fun licencesPage(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            com.grapsee.gsai.ui.settings.LicencesScreen(onBack = {})
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun licencesLight() {
        compose.setContent { licencesPage(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-licences-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun licencesDark() {
        compose.setContent { licencesPage(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-licences-dark.png")
    }

    // --- 0.86.22: the pages that replaced dialogs and dead rows -----------

    @Composable
    private fun responseStylePage(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            com.grapsee.gsai.ui.settings.ResponseStyleScreen(onBack = {})
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun responseStyleLight() {
        compose.setContent { responseStylePage(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-response-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun responseStyleDark() {
        compose.setContent { responseStylePage(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-response-dark.png")
    }

    @Composable
    private fun helpArticlePage(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            com.grapsee.gsai.ui.support.HelpArticleScreen(topicId = "notifications", onBack = {})
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun helpArticleLight() {
        compose.setContent { helpArticlePage(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-article-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun helpArticleDark() {
        compose.setContent { helpArticlePage(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-article-dark.png")
    }

    @Composable
    private fun collectionPage(dark: Boolean) {
        TheGsAiTheme(darkTheme = dark) {
            com.grapsee.gsai.ui.explore.CollectionScreen(
                collectionId = "work",
                onBack = {},
                onOpenChat = { _, _ -> },
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun collectionLight() {
        compose.setContent { collectionPage(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-collection-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun collectionDark() {
        compose.setContent { collectionPage(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-collection-dark.png")
    }

    @Composable
    private fun dialogPage(dark: Boolean) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val notice = androidx.compose.runtime.remember {
            com.grapsee.gsai.data.notifications.NoticeInbox.record(
                context,
                com.grapsee.gsai.data.notifications.GsNotifier.Kind.MESSAGES,
                "Nexly finished your plan",
                "The checklist for \"Q4 launch\" is ready. Three of the five steps are already ticked.",
            )
            com.grapsee.gsai.data.notifications.NoticeInbox.read(context).first()
        }
        TheGsAiTheme(darkTheme = dark) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                com.grapsee.gsai.ui.notifications.NotificationDetailScreen(
                    noticeId = notice.id,
                    onBack = {},
                    now = notice.createdAtMillis + 90 * 60 * 1000L,
                )
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun noticeDetailLight() {
        compose.setContent { dialogPage(false) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-notice-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun noticeDetailDark() {
        compose.setContent { dialogPage(true) }
        compose.onRoot().captureRoboImage("build/screenshots/probe-notice-dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun glassDialogLight() {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    com.grapsee.gsai.ui.components.GsDialog(
                        title = "Delete this chat?",
                        message = "\"Q4 launch plan\" and its messages will be removed from this device and the server. This cannot be undone.",
                        confirmLabel = "Delete",
                        onConfirm = {},
                        onDismiss = {},
                        destructive = true,
                        icon = com.grapsee.gsai.ui.icons.GsIcons.Trash,
                        tone = com.grapsee.gsai.ui.components.GsWellTone.Rose,
                    )
                }
            }
        }
        // A Dialog is its own window, so the app root is not the thing to
        // capture — the dialog root is.
        compose.onNode(androidx.compose.ui.test.isDialog())
            .captureRoboImage("build/screenshots/probe-dialog-light.png")
    }

    /**
     * The facts slab — the shape the session and security popups take. A table
     * flattened into a sentence was the thing this replaced.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun factDialogLight() {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    com.grapsee.gsai.ui.components.GsFactDialog(
                        title = "Current session",
                        facts = listOf(
                            "Device" to "Pixel 8 Pro (Android 14)",
                            "Session" to "3f9c1a72-8b40",
                            "Signed in with" to "owner@grapsee.com",
                            "Plan" to "Nexly Pro · active",
                        ),
                        onDismiss = {},
                    )
                }
            }
        }
        compose.onNode(androidx.compose.ui.test.isDialog())
            .captureRoboImage("build/screenshots/probe-fact-dialog.png")
    }

    /** Quiet Hours, opened from its row — the editor, not a mock of it. */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun quietHoursDialog() {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                com.grapsee.gsai.ui.settings.NotificationsAlertsScreen(onBack = {})
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("Quiet Hours", useUnmergedTree = true).onLast().performClick()
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.isDialog())
            .captureRoboImage("build/screenshots/probe-quiet-hours.png")
    }

    /**
     * A tool's own page. The card used to push a paragraph straight into the
     * composer; this is the middle step that explains the tool first.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun toolPageLight() {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                com.grapsee.gsai.ui.explore.ToolDetailScreen(
                    toolId = "image-studio",
                    onBack = {},
                    onStart = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/probe-tool-light.png")
    }

    /**
     * The personalization editor — the page the two Profile rows used to send
     * to the whole onboarding wizard.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun personalizationPageLight() {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) {
                com.grapsee.gsai.ui.profile.ProfilePersonalizationScreen(
                    onBack = {},
                    initialInterests = setOf("Writing", "Design", "Travel"),
                    initialCapabilities = setOf("Draft and write", "Summarise documents"),
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/probe-personalization.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun toolPageDark() {
        compose.setContent {
            TheGsAiTheme(darkTheme = true) {
                com.grapsee.gsai.ui.explore.ToolDetailScreen(
                    toolId = "trip-planner",
                    onBack = {},
                    onStart = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/probe-tool-dark.png")
    }
}
