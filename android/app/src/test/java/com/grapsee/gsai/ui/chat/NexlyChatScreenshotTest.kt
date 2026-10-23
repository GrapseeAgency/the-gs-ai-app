package com.grapsee.gsai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BlocksContent(
                    content = NEXLY_THINKING_WIRE,
                    isStreaming = false,
                    onCopyCode = {},
                    glassAssistant = true,
                    statusAtEnd = false,
                    statusCard = {
                        ReferenceThinkingCard(
                            phase = NexlyThinkingPhase.REASONING,
                            effort = "high",
                            isPlan = false
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
        Box(
            modifier = Modifier.fillMaxSize().nexlyPearlBackdrop(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            ) {
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        // Flash: the timetable is the answer; no reasoning card.
                        statusCard = null,
                        planTimestamp = "2026-10-22T21:45:00Z"
                    )
                }
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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

    /**
     * A DIFFERENT real-life question: a prose answer, no plan. The user turn
     * and assistant prose are the live model's; the card is a deterministic
     * reasoning state (the free model did not emit a trace).
     */
    @Composable
    private fun fullProseChat() {
        Box(
            modifier = Modifier.fillMaxSize().nexlyPearlBackdrop(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            ) {
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    UserMessage(
                        message = ChatUiMessage(
                            id = "prose-user",
                            role = "user",
                            content = NEXLY_PROSE_USER,
                            createdAt = "2026-10-22T21:41:00Z"
                        )
                    )
                    BlocksContent(
                        content = NEXLY_THINKING_WIRE,
                        isStreaming = false,
                        onCopyCode = {},
                        glassAssistant = true,
                        statusAtEnd = false,
                        statusCard = {
                            ReferenceThinkingCard(
                                phase = NexlyThinkingPhase.REASONING,
                                effort = "high",
                                isPlan = false
                            )
                        }
                    )
                }
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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

    /** FLASH turn: no reasoning card, just the real user turn + real answer. */
    @Composable
    private fun flashChat(question: String, wire: String) {
        Box(
            modifier = Modifier.fillMaxSize().nexlyPearlBackdrop(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            ) {
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    UserMessage(
                        message = ChatUiMessage(
                            id = "flash-user",
                            role = "user",
                            content = question,
                            createdAt = "2026-10-23T10:30:00Z"
                        )
                    )
                    BlocksContent(
                        content = wire,
                        isStreaming = false,
                        onCopyCode = {},
                        glassAssistant = true,
                        statusCard = null,
                        planTimestamp = "2026-10-23T10:31:00Z"
                    )
                }
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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

    private fun shootFlash(question: String, wire: String, path: String) {
        compose.setContent {
            TheGsAiTheme(darkTheme = false) { flashChat(question, wire) }
        }
        compose.onRoot().captureRoboImage(path)
    }

    private fun shoot(path: String) {
        compose.setContent { TheGsAiTheme(darkTheme = false) { referenceResponse() } }
        compose.onRoot().captureRoboImage(path)
    }

    private fun shootFull(path: String) {
        compose.setContent { TheGsAiTheme(darkTheme = false) { fullChat() } }
        compose.onRoot().captureRoboImage(path)
    }

    private fun shootFullProse(path: String) {
        compose.setContent { TheGsAiTheme(darkTheme = false) { fullProseChat() } }
        compose.onRoot().captureRoboImage(path)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun nexlyFullChatAtPixel6() = shootFull("build/screenshots/nexly-chat-full-411x914.png")

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun nexlyFullChatAtSheetSize() = shootFull("build/screenshots/nexly-chat-full-393x852.png")

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h568dp-mdpi")
    fun nexlyFullChatAtSmallPhone() = shootFull("build/screenshots/nexly-chat-full-320x568.png")

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun nexlyFullChatAtCompact() = shootFull("build/screenshots/nexly-chat-full-360x640.png")

    @Test
    @Config(sdk = [34], qualifiers = "w375dp-h667dp-xxhdpi")
    fun nexlyFullChatAtIphone8() = shootFull("build/screenshots/nexly-chat-full-375x667.png")

    @Test
    @Config(sdk = [34], qualifiers = "w430dp-h932dp-xxhdpi")
    fun nexlyFullChatAtLargePhone() = shootFull("build/screenshots/nexly-chat-full-430x932.png")

    @Test
    @Config(sdk = [34], qualifiers = "w768dp-h1024dp-mdpi")
    fun nexlyFullChatAtTablet() = shootFull("build/screenshots/nexly-chat-full-768x1024.png")

    @Test
    @Config(sdk = [34], qualifiers = "w1024dp-h768dp-mdpi")
    fun nexlyFullChatAtLandscapeTablet() = shootFull("build/screenshots/nexly-chat-full-1024x768.png")

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
    fun nexlyFullProseAtSheetSize() =
        shootFullProse("build/screenshots/nexly-chat-full-prose-393x852.png")

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
    fun nexlyFullProseAtPixel6() =
        shootFullProse("build/screenshots/nexly-chat-full-prose-411x914.png")

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

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h1400dp-xxhdpi")
    fun nexlyFlashCodingQuestion() = shootFlash(
        NEXLY_RANDOM_CODING_USER,
        NEXLY_RANDOM_CODING_WIRE,
        "build/screenshots/nexly-chat-random-coding-393x1400.png"
    )

    @Test
    @Config(sdk = [34], qualifiers = "w393dp-h1400dp-xxhdpi")
    fun nexlyFlashLifeQuestion() = shootFlash(
        NEXLY_RANDOM_LIFE_USER,
        NEXLY_RANDOM_LIFE_WIRE,
        "build/screenshots/nexly-chat-random-life-393x1400.png"
    )
}

/**
 * Captured from a REAL OpenRouter call (scripts/live-nexly-model.ts)
 * using the production SYSTEM_PROMPT + WIDGET_PROMPT and a DIFFERENT real-life
 * question, not the sheet's sample. The server compiler normalised the fence;
 * no hand-authored rows.
 */
private val NEXLY_REFERENCE_WIRE = """
Here’s a balanced schedule that fits your 9–5 shift, a gym session, meal prep, and two solid hours of study.

```plan
{"title":"Here's your balanced schedule for tomorrow:","rows":[{"time":"6:30 – 7:00 AM","text":"Wake up + light stretch","icon":"sunrise","tone":"amber"},{"time":"7:00 – 7:30 AM","text":"Breakfast & coffee","icon":"coffee","tone":"amber"},{"time":"7:30 – 8:15 AM","text":"Commute to work","icon":"bus","tone":"blue"},{"time":"8:15 – 9:00 AM","text":"Prep for shift","icon":"briefcase","tone":"blue"},{"time":"9:00 – 5:00 PM","text":"Work shift","icon":"device-laptop","tone":"blue"},{"time":"5:15 – 6:15 PM","text":"Gym session","icon":"activity","tone":"pink"},{"time":"6:30 – 7:15 PM","text":"Meal prep for tomorrow","icon":"tools-kitchen-2","tone":"green"},{"time":"7:30 – 9:30 PM","text":"Exam study (2 hrs)","icon":"book-2","tone":"lavender"},{"time":"9:45 – 10:15 PM","text":"Wind down & sleep prep","icon":"moon","tone":"lilac"}],"closing":"You've got this! :flame:","closingIcon":"flame","timestamp":"10:00 PM","accent":"#66a80f","variant":"tinted","palette":"meadow"}
```
""".trimIndent()

/** A real-life question, deliberately different from the sheet's sample. */
private const val NEXLY_REFERENCE_USER =
    "I work a 9–5 shift tomorrow and need to fit in a gym session, meal prep, and two hours of study for an exam. Can you plan my day?"

/** The real-life non-plan question rendered by the prose harness. */
private const val NEXLY_PROSE_USER =
    "I keep procrastinating on my thesis. Why does that happen and what is one realistic change I can make this week?"

/** The REAL model prose answer to the procrastination question. */
private val NEXLY_THINKING_WIRE = """
Procrastination on a thesis usually isn't laziness — it's a response to the scale and ambiguity of the work. A thesis is an open-ended, high-stakes project with no daily deadlines, so your brain treats it as a threat and steers you toward tasks that feel finishable (email, formatting, reading one more paper). Perfectionism, fear of evaluation, and the lack of immediate feedback all reinforce the loop.

**One realistic change this week:** pick a *tiny*, time-boxed writing window — 30 minutes, same time each day — and commit to producing *bad* words only. No editing, no research, no formatting. Just move the cursor forward. The goal isn't quality; it's breaking the avoidance signal. After seven sessions you'll have 3.5 hours of raw material and, more importantly, proof that you can show up without the project collapsing.
""".trimIndent()

/**
 * Fresh flash questions asked by the one-shot live CI job
 * (`scripts/live-random-questions.ts`). They are deliberately unrelated to the
 * reference sheet. No thinking card is rendered for these turns.
 */
private const val NEXLY_RANDOM_CODING_USER =
    "Why does my Python list comprehension return None when I use .append() inside it? I want a short, concrete fix."

private val NEXLY_RANDOM_CODING_WIRE = """
`list.append()` returns `None` — it mutates the list in place and does not produce a value. A comprehension collects the *result* of its expression, so you end up with a list of `None`s.

**Fix:** build the list directly in the comprehension instead of appending.

```python
# ❌ returns [None, None, None]
result = [my_list.append(x) for x in source]

# ✅ returns the transformed values
result = [x * 2 for x in source]          # or any expression
```

If you need to accumulate into an existing list, use a plain loop or `extend`:

```python
my_list.extend(x * 2 for x in source)
```
""".trimIndent()

private const val NEXLY_RANDOM_LIFE_USER =
    "I keep hitting snooze and feel behind all morning. What is one change that actually sticks?"

private val NEXLY_RANDOM_LIFE_WIRE = """
Move your phone across the room so you have to stand up to turn off the alarm. That single physical step breaks the snooze loop and gets you vertical before your brain negotiates.

```plan
{"title":"A snooze-proof morning starts the night before","rows":[{"time":"10:00 PM","text":"Phone on charger across the room","icon":"moon","tone":"lilac"},{"time":"10:15 PM","text":"Lights out, no screens","icon":"book-2","tone":"lavender"},{"time":"6:30 AM","text":"Alarm goes off — walk to phone","icon":"alarm","tone":"amber"},{"time":"6:32 AM","text":"Open curtains, drink water","icon":"sunrise","tone":"amber"},{"time":"6:40 AM","text":"Stretch or 2-min movement","icon":"activity","tone":"pink"},{"time":"6:50 AM","text":"Coffee or tea, no phone","icon":"coffee","tone":"green"},{"time":"7:00 AM","text":"Name the day's top priority","icon":"target","tone":"blue"},{"time":"7:10 AM","text":"Shower and dress","icon":"sparkles","tone":"blue"},{"time":"7:30 AM","text":"Out the door on time","icon":"briefcase","tone":"blue"}],"closing":"Stand up once, the rest follows.","closingIcon":"flame","timestamp":"10:00 PM","accent":"#1098ad","variant":"editorial"}
```
""".trimIndent()
