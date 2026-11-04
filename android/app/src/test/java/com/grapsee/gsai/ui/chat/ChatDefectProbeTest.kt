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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.grapsee.gsai.ui.chat.content.nexlyPearlBackdrop
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
}
