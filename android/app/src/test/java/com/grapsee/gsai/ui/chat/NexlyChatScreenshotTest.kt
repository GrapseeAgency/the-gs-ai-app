package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.grapsee.gsai.ui.chat.content.BlocksContent
import com.grapsee.gsai.ui.chat.content.nexlyPearlBackdrop
import com.grapsee.gsai.ui.icons.GsIcons
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
 * The REAL model-response composition, rendered on the JVM: the same
 * BlocksContent, ReferenceThinkingCard, NexlyPlanCard, glass cards, orb and
 * pearlescent backdrop the chat screen mounts. No mockup.
 *
 * This exists because code inspection repeatedly said the reference was
 * "close" while the actual pixels had never been seen. The reference wire is
 * the exact nine-row plan from docs/reference/nexly/chat-plan-reference.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NexlyChatScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun onlyOnRequest() {
        assumeTrue(System.getProperty("gs.screenshots") == "true")
    }

    @Composable
    private fun referenceResponse() {
        Box(modifier = Modifier.fillMaxSize().nexlyPearlBackdrop()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BlocksContent(
                    content = NEXLY_REFERENCE_WIRE,
                    isStreaming = false,
                    onCopyCode = {},
                    glassAssistant = true,
                    statusCard = { ReferenceThinkingCard() },
                    planTimestamp = "2026-10-22T21:45:00Z"
                )
            }
        }
    }

    @Composable
    private fun adaptiveThinkingResponse() {
        Box(modifier = Modifier.fillMaxSize().nexlyPearlBackdrop()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BlocksContent(
                    content = NEXLY_THINKING_WIRE,
                    isStreaming = false,
                    onCopyCode = {},
                    glassAssistant = true,
                    statusCard = {
                        ReferenceThinkingCard(
                            phase = NexlyThinkingPhase.REASONING,
                            effort = "high",
                            isPlan = false,
                            reasoningHint = "Checking that the correlation cannot carry a signal."
                        )
                    }
                )
            }
        }
    }

    /**
     * The WHOLE reference screen built from the production pieces: the real
     * brand header, the real UserMessage (peach bubble, avatar, ticks), the
     * real assistant BlocksContent, and the real ComposerRow capsule.
     */
    @Composable
    private fun fullChat() {
        Box(modifier = Modifier.fillMaxSize().nexlyPearlBackdrop()) {
            Column(modifier = Modifier.fillMaxSize()) {
                NexlyChatHeader(
                    sparkleLabel = "New chat",
                    onSparkle = {},
                    onSparkleLong = null,
                    menuIcon = GsIcons.Menu,
                    menuLabel = "Open menu",
                    onMenu = {},
                    onMenuLong = null
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    UserMessage(
                        message = ChatUiMessage(
                            id = "reference-user",
                            role = "user",
                            content = NEXLY_REFERENCE_USER,
                            createdAt = "2026-10-22T21:41:00Z"
                        )
                    )
                    BlocksContent(
                        content = NEXLY_REFERENCE_WIRE,
                        isStreaming = false,
                        onCopyCode = {},
                        glassAssistant = true,
                        statusCard = { ReferenceThinkingCard() },
                        planTimestamp = "2026-10-22T21:45:00Z"
                    )
                }
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    ComposerRow(
                        draftText = { "" },
                        onDraftChange = {},
                        onSend = {},
                        onAttach = {},
                        isStreaming = false,
                        onStop = {},
                        queuedText = { "" },
                        onQueue = {},
                        onUnqueue = {}
                    )
                }
            }
        }
    }

    private fun shoot(path: String) {
        compose.setContent { TheGsAiTheme(darkTheme = false) { referenceResponse() } }
        compose.onRoot().captureRoboImage(path)
    }

    private fun shootFull(path: String) {
        compose.setContent { TheGsAiTheme(darkTheme = false) { fullChat() } }
        compose.onRoot().captureRoboImage(path)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun nexlyFullChatAtPixel6() = shootFull("build/screenshots/nexly-chat-full-411x914.png")

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun nexlyFullChatAtSheetSize() = shootFull("build/screenshots/nexly-chat-full-393x852.png")

    private fun shootAdaptive(path: String) {
        compose.setContent { TheGsAiTheme(darkTheme = false) { adaptiveThinkingResponse() } }
        compose.onRoot().captureRoboImage(path)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun nexlyResponseAtSheetSize() = shoot("build/screenshots/nexly-chat-393x852.png")

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun nexlyAdaptiveThinkingAtSheetSize() =
        shootAdaptive("build/screenshots/nexly-chat-thinking-393x852.png")

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun nexlyResponseAtPixel6() = shoot("build/screenshots/nexly-chat-411x914.png")

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
    fun nexlyResponseAtNarrowWidth() = shoot("build/screenshots/nexly-chat-360x780.png")
}

/**
 * The exact response shown in the reference sheet, written in the wire the
 * model is instructed to emit. No tones omitted: the card is forced to the
 * reference palette, so a render can be compared row by row.
 */
private val NEXLY_REFERENCE_WIRE = """
    Got it! I'll create a balanced plan for your tomorrow — focused on studies, productivity, health and rest.

    Let me think for a moment...

    ```plan
    {
      "title": "Here's your personalized plan for tomorrow:",
      "rows": [
        { "time": "8:00 – 8:30 AM", "icon": "sunrise", "tone": "amber", "text": "Morning routine + light exercise" },
        { "time": "9:00 – 11:00 AM", "icon": "book-2", "tone": "lavender", "text": "Classes" },
        { "time": "11:30 – 1:00 PM", "icon": "device-laptop", "tone": "blue", "text": "Assignment / Study time" },
        { "time": "1:00 – 2:00 PM", "icon": "tools-kitchen-2", "tone": "green", "text": "Lunch + short break" },
        { "time": "2:00 – 4:00 PM", "icon": "file-text", "tone": "lavender", "text": "Continue assignments" },
        { "time": "4:00 – 5:00 PM", "icon": "run", "tone": "pink", "text": "Workout / Physical activity" },
        { "time": "5:00 – 7:00 PM", "icon": "device-gamepad-2", "tone": "blue", "text": "Free time / Hobbies" },
        { "time": "7:00 – 9:00 PM", "icon": "book-2", "tone": "lavender", "text": "Light study / Review" },
        { "time": "9:30 – 10:30 PM", "icon": "moon", "tone": "lilac", "text": "Wind down + Sleep" }
      ],
      "closing": "You got this, Arafat!",
      "closingIcon": "flame",
      "timestamp": "9:45 PM"
    }
    ```
""".trimIndent()

/** The exact user turn from the reference sheet. */
private const val NEXLY_REFERENCE_USER =
    "Can you help me plan a productive day tomorrow? I have classes, " +
        "assignments and also want to stay healthy and get some rest."

/** A non-plan thinking turn: the card copy and active row must adapt. */
private val NEXLY_THINKING_WIRE = """
    Quantum entanglement links two particles so their measured states stay
    correlated, no matter how far apart they are.

    The important part is that the correlation is not a signal: you cannot use
    it to send information faster than light.
""".trimIndent()
