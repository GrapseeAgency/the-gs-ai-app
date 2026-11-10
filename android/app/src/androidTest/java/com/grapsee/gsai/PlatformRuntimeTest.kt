package com.grapsee.gsai

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import com.grapsee.gsai.ui.chat.content.parseWidget
import com.grapsee.gsai.ui.chat.content.Widget
import com.grapsee.gsai.ui.chat.content.CalcWidgetView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.data.notifications.AppForeground
import com.grapsee.gsai.data.notifications.ReplyNotifier
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.theme.GsHaptics
import com.grapsee.gsai.ui.theme.TheGsAiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real OS notification/lifecycle + injected touch tests. No model or network required.
 * A delayed main-loop callback is background CONTINUATION, not a scheduled wake.
 * The recording View proves haptic dispatch/gating, not vibration on physical hardware.
 */
@RunWith(AndroidJUnit4::class)
class PlatformRuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun actualVoiceAttachmentPlaysNativeBytesWithMeasuredTimeAndClosesCleanly() {
        val context = compose.activity
        val directory = java.io.File(context.filesDir, "attachments/runtime-voice").apply { mkdirs() }
        val file = java.io.File(directory, "real-voice.wav")
        // A real five-second mono PCM fixture, not a simulated player timer.
        val sampleRate = 8000
        val pcmSize = sampleRate * 5 * 2
        val bytes = java.nio.ByteBuffer.allocate(44 + pcmSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + pcmSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(sampleRate)
            .putInt(sampleRate * 2).putShort(2.toShort()).putShort(16.toShort())
            .put("data".toByteArray()).putInt(pcmSize)
        file.writeBytes(bytes.array())
        val draft = com.grapsee.gsai.data.attachment.AttachmentDraft(
            id = "runtime-voice", kind = com.grapsee.gsai.data.attachment.AttachmentKind.Audio,
            source = com.grapsee.gsai.data.attachment.AttachmentSource.Files,
            displayName = file.name, mimeType = "audio/wav", localPath = file.absolutePath,
            byteSize = file.length(), phase = com.grapsee.gsai.data.attachment.AttachmentPhase.Ready)
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme { com.grapsee.gsai.ui.attachment.AttachmentChipRow(listOf(draft), editable = false) }
        }
        try {
            compose.onNodeWithContentDescription("Open real-voice.wav").performClick()
            compose.waitUntil(8000) { compose.onAllNodesWithText("0:00 / 0:05").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Play audio").performClick()
            compose.onNodeWithText("Pause audio").assertIsDisplayed()
            compose.waitUntil(5000) { compose.onAllNodesWithText("0:01 / 0:05").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Pause audio").performClick()
            compose.onNodeWithText("Play audio").assertIsDisplayed()
            compose.onNodeWithText("Close preview").performClick()
            compose.onNodeWithText("Play audio").assertDoesNotExist()
            compose.onNodeWithContentDescription("Open real-voice.wav").assertExists()
        } finally { file.delete(); directory.delete() }
    }

    @Test fun generatedChecklistEditsDeletesAddsAndKeepsCompletionWithTheActualTask() {
        var kind by androidx.compose.runtime.mutableStateOf("checklist")
        var payload by androidx.compose.runtime.mutableStateOf("""{"title":"Actual tasks","items":[{"label":"Original source","detail":"Only supplied detail"},{"label":"Keep source"}]}""")
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    com.grapsee.gsai.ui.chat.content.WidgetBlockView(
                        com.grapsee.gsai.ui.chat.content.Block.WidgetBlock(kind, payload, false, 0), false)
                }
            }
        }
        compose.onNodeWithText("0 of 2 completed").assertExists()
        compose.onNodeWithText("Keep source").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Edit task: Original source").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Renamed source")
        compose.onNodeWithText("Save task").performScrollTo().performClick()
        compose.onNodeWithText("Only supplied detail").assertExists()
        compose.onNodeWithText("Keep source").assertIsOn()
        compose.onNodeWithContentDescription("Delete task: Renamed source").performClick()
        compose.onNodeWithText("1 of 1 completed").assertExists()
        compose.onNodeWithText("Keep source").assertIsOn()
        compose.onNodeWithText("Add Task").performClick()
        compose.onNodeWithText("Add", useUnmergedTree = false).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("Added task")
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Added task").performClick().assertIsOn()
        compose.onNodeWithText("2 of 2 completed").assertExists()
        compose.onNodeWithContentDescription("Edit task: Added task").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Added edited")
        compose.onNodeWithText("Save task").performClick()
        compose.onNodeWithText("Added edited").assertIsOn()
        compose.onNodeWithContentDescription("Delete task: Keep source").performClick()
        compose.onNodeWithText("Added edited").assertIsOn()
        compose.onNodeWithText("1 of 1 completed").assertExists()
        compose.runOnIdle { payload = """{"title":"New tasks","items":[{"label":"New supplied task"}]}""" }
        compose.onNodeWithText("0 of 1 completed").assertExists()
        compose.onNodeWithText("Added edited").assertDoesNotExist()
        compose.onNodeWithText("New supplied task").assertIsOff()
        compose.runOnIdle {
            kind = "app"
            payload = """{"type":"tasks","title":"App tasks","items":[{"title":"App first","subtitle":"Supplied app detail","done":true},{"title":"App second"}]}"""
        }
        compose.onNodeWithText("1 of 2 completed").assertExists()
        compose.onNodeWithText("Supplied app detail").assertExists()
        compose.onNodeWithContentDescription("Edit task: App first").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("App first edited")
        compose.onNodeWithText("Save task").performClick()
        compose.onNodeWithText("App first edited").assertIsOn()
        compose.onNodeWithContentDescription("Delete task: App second").performClick()
        compose.onNodeWithText("1 of 1 completed").assertExists()
    }

    @Test fun shortSourceTableShowsEveryProductAndMeasureWithoutHiddenPagesOrColumns() {
        val payload = """{"title":"Actual short source","columns":["Product","Sales","Revenue","Growth"],"rows":[["Nexly Pro","482","$12,460","18.4%"],["Nexly Starter","361","$7,240","12.1%"],["Nexly Business","284","$5,930","9.7%"],["Nexly Enterprise","126","$3,120","6.2%"]]}"""
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    com.grapsee.gsai.ui.chat.content.WidgetBlockView(
                        com.grapsee.gsai.ui.chat.content.Block.WidgetBlock("table", payload, false, 0), false)
                }
            }
        }
        compose.onNodeWithText("Nexly Enterprise").assertIsDisplayed()
        compose.onNodeWithText("$3,120").assertIsDisplayed()
        compose.onNodeWithText("Revenue", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Growth", substring = true).assertIsDisplayed()
        compose.onNodeWithText("1–4 of 4").assertIsDisplayed()
    }

    @Test fun vanishedDashboardTabCannotHideReplacementWithSameBlockCount() {
        var wire by androidx.compose.runtime.mutableStateOf("# Source dashboard\n\n| Name | Value |\n| --- | --- |\n| A | 1 |\n\n- [ ] Source task\n\nSource footer")
        val replacement = "# Replacement dashboard\n\n## Supplied heading\n\nReplacement stays visible\n\nSupplied replacement footer"
        assertEquals(com.grapsee.gsai.ui.chat.content.parseBlocks(wire).size,
            com.grapsee.gsai.ui.chat.content.parseBlocks(replacement).size)
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    com.grapsee.gsai.ui.chat.content.BlocksContent(wire, false, {})
                }
            }
        }
        compose.onNodeWithText("Table").performClick()
        compose.onNodeWithText("Source footer").assertDoesNotExist()
        compose.runOnIdle { wire = replacement }
        compose.onNodeWithText("Replacement stays visible").assertExists()
        compose.onNodeWithText("Supplied replacement footer").assertExists()
    }

    @Test fun ordinaryMarkdownTasksEditDeletePreserveDetailsAndDoNotMoveCompletion() {
        var wire by androidx.compose.runtime.mutableStateOf("## Next actions\n- [ ] Original task\n  Supplied source detail\n  - Supplied nested detail\n- [x] Keep completed")
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    com.grapsee.gsai.ui.chat.content.BlocksContent(wire, false, {})
                }
            }
        }
        compose.onNodeWithText("1 of 2 completed").assertExists()
        compose.onNodeWithText("Original task").performClick()
        compose.onNodeWithText("2 of 2 completed").assertExists()
        compose.onNodeWithContentDescription("Edit task: Original task").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Edited source")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Supplied source detail").assertExists()
        compose.onNodeWithText("Supplied nested detail").assertExists()
        compose.onNodeWithContentDescription("Edit task: Supplied nested detail").assertDoesNotExist()
        compose.onNodeWithText("2 of 2 completed").assertExists()
        compose.onNodeWithContentDescription("Delete task: Keep completed").performClick()
        compose.onNodeWithText("1 of 1 completed").assertExists()
        fun add(text: String) {
            compose.onNodeWithText("Add Task").performClick()
            compose.onNodeWithText("Add").assertIsNotEnabled()
            compose.onNode(hasSetTextAction()).performTextReplacement(text)
            compose.onNode(hasSetTextAction()).performImeAction()
        }
        add("First added task")
        compose.onNodeWithText("First added task").performClick()
        add("Second added task")
        compose.onNodeWithContentDescription("Delete task: First added task").performClick()
        compose.onNodeWithText("1 of 2 completed").assertExists()
        compose.onNodeWithContentDescription("Edit task: Second added task").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Second edited")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("1 of 2 completed").assertExists()
        compose.onNodeWithText("Second edited").performClick()
        compose.onNodeWithText("2 of 2 completed").assertExists()
        compose.runOnIdle { wire = "## Next actions\n- [ ] Replacement source" }
        compose.onNodeWithText("0 of 1 completed").assertExists()
        compose.onNodeWithText("Edited source").assertDoesNotExist()
        compose.onNodeWithText("Replacement source").assertExists()
    }

    @Test fun generatedTimerUsesActualSecondsPausesResetsAndNeverUsesPrices() {
        var payload by androidx.compose.runtime.mutableStateOf("""{"type":"timer","items":[{"title":"Not time","price":25}]}""")
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    com.grapsee.gsai.ui.chat.content.WidgetBlockView(
                        com.grapsee.gsai.ui.chat.content.Block.WidgetBlock("app", payload, false, 0), false)
                }
            }
        }
        compose.onNodeWithText("00:00").assertExists()
        compose.onNodeWithText("Start").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("2")
        compose.onNodeWithText("Start").assertIsEnabled().performClick()
        compose.onNodeWithText("Pause").performClick()
        val paused = compose.onNodeWithTag("timer.remaining").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        android.os.SystemClock.sleep(1100)
        compose.onNodeWithText(paused).assertExists()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("00:02").assertExists()
        compose.onNode(hasSetTextAction()).assertIsEnabled()
        compose.onNodeWithText("Start").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Done — countdown completed.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("00:00").assertExists()
        compose.runOnIdle { payload = """{"type":"timer","durationSeconds":5}""" }
        compose.onNodeWithText("00:05").assertExists()
        compose.onNodeWithText("Start").performClick()
        compose.runOnIdle { payload = """{"type":"timer","items":[{"price":999}]}""" }
        compose.onNodeWithText("00:00").assertExists()
        compose.onNodeWithText("Start").assertIsNotEnabled()
        compose.onAllNodesWithText("Done — countdown completed.").assertCountEquals(0)
        compose.onNodeWithText("Local countdown only. No background alarm or notification is scheduled.").assertExists()
    }

    @Test fun generatedBookingSavesOnlyActualLocalSelectionWithoutReservation() {
        var payload by androidx.compose.runtime.mutableStateOf("""{"type":"booking","title":"Actual slots","slots":["09:00","10:00"]}""")
        val prompts = mutableListOf<String>()
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    com.grapsee.gsai.ui.chat.content.WidgetBlockView(
                        com.grapsee.gsai.ui.chat.content.Block.WidgetBlock("app", payload, false, 0), false, { prompts += it })
                }
            }
        }
        compose.onNodeWithText("Save slot locally").assertIsNotEnabled()
        compose.onNodeWithText("09:00").performClick()
        compose.onNodeWithText("Save slot locally").assertIsEnabled().performClick()
        compose.onNodeWithText("Saved locally: 09:00. Preview only; no reservation has been made.").assertExists()
        compose.onNodeWithText("10:00").performClick()
        compose.onAllNodes(hasText("Saved locally:", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Save slot locally").performClick()
        compose.onNodeWithText("Saved locally: 10:00. Preview only; no reservation has been made.").assertExists()
        compose.runOnIdle {
            assertTrue("Local selection must never silently send a model booking request", prompts.isEmpty())
            payload = """{"type":"booking","title":"New slots","slots":["11:00"]}"""
        }
        compose.onAllNodes(hasText("Saved locally:", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Save slot locally").assertIsNotEnabled()
        compose.onNodeWithText("11:00").assertExists()
    }

    @Test fun contextualActionsEditActualCodeShowDataAndLaunchPlatformSharing() {
        val prompts = mutableListOf<String>()
        var content by androidx.compose.runtime.mutableStateOf("```javascript\nconsole.log(7)\n```")
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    com.grapsee.gsai.ui.chat.content.BlocksContent(content, false, {}, onSendPrompt = { prompts += it })
                }
            }
        }
        compose.onNodeWithText("Edit Code").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("console.log(7)")
        compose.onNode(hasSetTextAction()).performTextReplacement("console.log(42)")
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("Edit Code").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("console.log(42)")
        compose.runOnIdle {
            assertTrue("Local Edit Code must never become a model request", prompts.isEmpty())
            content = "| Name | Amount |\n| --- | --- |\n| Actual | 42 |"
        }
        compose.onNodeWithText("View Full Report").performScrollTo().performClick()
        compose.onNodeWithText("Actual\t42", substring = true).assertExists()
        compose.onNodeWithText("Close details").performClick()
        compose.onNodeWithText("Share").performScrollTo().performClick()
        val device = UiDevice.getInstance(instrumentation)
        assertTrue("Share must launch the real Android chooser, not copy text or ask the model",
            device.wait(Until.hasObject(By.pkg("com.android.intentresolver")), 10_000L) ||
                device.hasObject(By.text("Share response")))
        device.pressBack()
        compose.runOnIdle { assertTrue(prompts.isEmpty()) }
    }

    @Test fun generatedCodeEditorAndWorkerPreserveUnicodeAndStopLoops() {
        val source = """const data = {"😀":7,"🌅":9}; console.log(data["😀"]+data["🌅"], "😀".length);"""
        val parsed = com.grapsee.gsai.ui.chat.content.parseBlocks("```javascript\n$source\n```")
            .filterIsInstance<com.grapsee.gsai.ui.chat.content.Block.CodeBlock>().single()
        assertEquals(source, parsed.code)
        val encoded = com.grapsee.gsai.ui.chat.content.CodeDisplay.encodeEditor(source)
        assertEquals(source, com.grapsee.gsai.ui.chat.content.CodeDisplay.decodeEditor(encoded))
        assertFalse(encoded.contains("😀"))
        for (invalid in listOf("'not JSON'", "\"unfinished", "\"ok\" false", "[]", "\"bad\\x\"")) {
            assertTrue(runCatching { com.grapsee.gsai.ui.chat.content.CodeDisplay.decodeEditor(invalid) }.isFailure)
        }
        var copied: String? = null
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    com.grapsee.gsai.ui.chat.content.CodeBlockCard(parsed.language, parsed.code, false) { copied = it }
                }
            }
        }
        compose.onAllNodes(hasText("😀", substring = true)).assertCountEquals(0)
        compose.onNodeWithContentDescription("Copy code").performClick()
        compose.runOnIdle { assertEquals(source, copied) }
        compose.onNodeWithText("Edit").performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals(encoded)
        compose.onNode(hasSetTextAction()).performTextReplacement("\"unfinished")
        compose.onNodeWithText("Run in Browser").performScrollTo().performClick()
        compose.onNodeWithText("Not executed: correct the JSON-encoded source draft first.").assertExists()
        compose.onNodeWithText("Reset").performScrollTo().performClick()
        compose.onNodeWithText("Edit").assertExists()
        compose.onNodeWithText("Run in Browser").performScrollTo().performClick()
        fun findWeb(view: View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
            return null
        }
        var web: android.webkit.WebView? = null
        awaitObserved("native Run creates an actual worker WebView") {
            instrumentation.runOnMainSync { web = findWeb(compose.activity.window.decorView) }; web != null
        }
        fun evaluate(script: String): String? {
            val done = CountDownLatch(1); var result: String? = null
            instrumentation.runOnMainSync { web!!.evaluateJavascript(script) { result = it; done.countDown() } }
            assertTrue(done.await(5, TimeUnit.SECONDS)); return result
        }
        awaitObserved("actual Unicode-key computation reaches native worker console") {
            evaluate("!!document.getElementById('out') && document.getElementById('out').textContent.includes('16 2') && document.getElementById('out').textContent.includes('Completed.')") == "true"
        }
        // Script literals MUST retain source bytes; innerText measures the rendered surface.
        assertEquals("false", evaluate("document.body.innerText.includes(String.fromCodePoint(0x1f600))"))
        val network = """self.addEventListener('securitypolicyviolation', e=>console.log('BLOCKED '+e.effectiveDirective));try{await fetch('https://github.com/');}catch{console.log('FETCH REFUSED');}await new Promise(r=>setTimeout(r,100));"""
        fun load(sourceCode: String) {
            instrumentation.runOnMainSync {
                web!!.loadDataWithBaseURL("https://gsai.local/", com.grapsee.gsai.ui.chat.content.JavaScriptWorkerSandboxAndroid.document(instrumentation.targetContext, sourceCode), "text/html", "UTF-8", null)
            }
        }
        load(network)
        try {
            awaitObserved("real Worker CSP blocks network, not merely a DNS or CORS failure") {
                evaluate("!!document.getElementById('out') && document.getElementById('out').textContent.includes('BLOCKED connect-src')") == "true"
            }
        } catch (error: Throwable) {
            throw AssertionError("Actual worker output: " + evaluate("document.body.innerText"), error)
        }
        load("while(true) {}")
        awaitObserved("infinite worker loop is actually terminated while native UI remains responsive") {
            evaluate("!!document.getElementById('out') && document.getElementById('out').textContent.includes('Stopped: execution exceeded 2 seconds.')") == "true"
        }
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertExists()
    }

    @Test fun generatedReactTsxRunsActualTasksPersistsAndIsolatesResponses() {
        val contract = instrumentation.targetContext.assets.open("react-preview-acceptance.json").bufferedReader().use { org.json.JSONObject(it.readText()) }
        val tasks = contract.getString("source")
        var source by androidx.compose.runtime.mutableStateOf(tasks)
        val originalScope = "react-acceptance:" + java.util.UUID.randomUUID()
        var scope by androidx.compose.runtime.mutableStateOf(originalScope)
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                androidx.compose.runtime.key(scope, source) {
                    androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        // Use the real ChatScreen parent and its actual message
                        // storage identity, not a manually scoped child renderer.
                        com.grapsee.gsai.ui.chat.AssistantMessage(
                            message = com.grapsee.gsai.ui.chat.ChatUiMessage(
                                id = scope, role = "assistant", content = "```tsx\n$source\n```",
                                createdAt = "2026-10-20T10:30:00Z"
                            ),
                            conversationId = "react-runtime-acceptance",
                            isSpeaking = false, showIdentity = false,
                            onCopy = {}, onRegenerate = {}
                        )
                    }
                }
            }
        }
        fun findWeb(view: View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
            return null
        }
        var web: android.webkit.WebView? = null
        fun evaluate(script: String): String? {
            val done = CountDownLatch(1); var result: String? = null
            instrumentation.runOnMainSync { web!!.evaluateJavascript(script) { result = it; done.countDown() } }
            assertTrue(done.await(5, TimeUnit.SECONDS)); return result
        }
        fun run(expectError: Boolean = false) {
            compose.onNodeWithText("Run in Browser").performScrollTo().performClick()
            awaitObserved("real native React WebView exists") {
                instrumentation.runOnMainSync { web = findWeb(compose.activity.window.decorView) }; web != null
            }
            try {
                awaitObserved("actual TSX compiler and React mount settle") {
                    evaluate("document.getElementById('status')?.textContent === '${if (expectError) "React preview error" else "React preview running"}'") == "true"
                }
            } catch (error: Throwable) { throw AssertionError("Actual React document: " + evaluate("document.body.innerText"), error) }
        }
        fun replaceInput(selector: String, text: String) {
            evaluate("(function(){const input=document.querySelector(${org.json.JSONObject.quote(selector)});Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,${org.json.JSONObject.quote(text)});input.dispatchEvent(new Event('input',{bubbles:true}));return true;})()")
        }
        run()
        awaitObserved("offline Tailwind applies actual task layout") {
            evaluate("getComputedStyle(document.querySelector('section')).paddingTop === getComputedStyle(document.documentElement).fontSize && getComputedStyle(document.querySelector('h1')).fontWeight === '700'") == "true"
        }
        replaceInput("[aria-label='New task']", "Android actual task")
        assertEquals("true", evaluate("document.querySelector('button[type=submit]').click();true"))
        awaitObserved("actual React add handler updates source-derived count") { evaluate("document.getElementById('count').textContent === '0/1 complete'") == "true" }
        evaluate("document.querySelector('input[type=checkbox]').click();true")
        awaitObserved("actual React toggle handler changes completion") { evaluate("document.getElementById('count').textContent === '1/1 complete'") == "true" }
        replaceInput("[aria-label='Task title']", "Edited Android task")
        awaitObserved("actual React edit effect persists to WebView storage") { evaluate("JSON.parse(localStorage.getItem('tasks'))[0].title === 'Edited Android task'") == "true" }
        run()
        assertEquals("true", evaluate("document.querySelector('[aria-label=\"Task title\"]').value === 'Edited Android task' && document.querySelector('input[type=checkbox]').checked"))
        compose.runOnIdle { scope = originalScope + ":other-response" }
        run()
        assertEquals("true", evaluate("document.getElementById('count').textContent === '0/0 complete'"))
        compose.runOnIdle { scope = originalScope }
        run()
        assertEquals("true", evaluate("document.querySelector('[aria-label=\"Task title\"]').value === 'Edited Android task'"))
        evaluate("document.querySelector('li button').click();true")
        awaitObserved("delete changes actual task data and count") { evaluate("document.getElementById('count').textContent === '0/0 complete' && localStorage.getItem('tasks') === '[]'") == "true" }
        run()
        assertEquals("true", evaluate("document.querySelectorAll('li').length === 0"))
        assertEquals("true", evaluate("!window.Android && !window.webkit && window.__gsDisplayEmojiPolicy === true"))
        compose.runOnIdle { source = "import missing from 'not-bundled';export default function App(){return <p>{missing}</p>}" }
        run(expectError = true)
        assertEquals("true", evaluate("document.getElementById('error').textContent.includes('Unsupported dependency')"))
        compose.runOnIdle { source = "export default function App( { return <p>Invalid</p> }" }
        run(expectError = true)
        assertEquals("true", evaluate("document.getElementById('error').textContent.includes('Unexpected')"))
        // Also run the exact reference answer via its actual parser and the
        // production AssistantMessage scope/parent, not a static source card.
        val references = org.json.JSONObject(instrumentation.targetContext.assets.open("response-section-references.json").bufferedReader().use { it.readText() }).getJSONArray("fixtures")
        val reference = (0 until references.length()).map { references.getJSONObject(it) }.single { it.getString("id") == "03-react-code" }
        val referenceSource = com.grapsee.gsai.ui.chat.content.parseBlocks(reference.getString("wire"))
            .filterIsInstance<com.grapsee.gsai.ui.chat.content.Block.CodeBlock>().single { it.language == "jsx" }.code
        compose.runOnIdle { source = referenceSource; scope = originalScope + ":reference" }
        run()
        for (title in listOf("Reference completed", "Reference active")) {
            replaceInput("[aria-label='New task']", title)
            evaluate("document.querySelector('form button').click();true")
            awaitObserved("reference form saves its actual UUID task") { evaluate("JSON.parse(localStorage.getItem('tasks') || '[]').some(t=>t.title===${org.json.JSONObject.quote(title)})") == "true" }
        }
        evaluate("document.querySelector('article input[type=checkbox]').click();true")
        fun selectFilter(value: String) {
            evaluate("(function(){const select=document.querySelector('[aria-label=\"Task filter\"]');select.value=${org.json.JSONObject.quote(value)};select.dispatchEvent(new Event('change',{bubbles:true}));return true;})()")
        }
        selectFilter("completed")
        awaitObserved("reference Completed exposes only the true completed task") { evaluate("document.querySelectorAll('article').length===1 && document.querySelector('article input[type=checkbox]').checked") == "true" }
        selectFilter("active")
        awaitObserved("reference Active exposes the actual remaining task") { evaluate("document.querySelectorAll('article').length===1 && document.querySelector('article input[type=checkbox]').getAttribute('aria-label')==='Reference active'") == "true" }
        replaceInput("[aria-label='Edit Reference active']", "Reference edited")
        awaitObserved("reference edit really persists") { evaluate("JSON.parse(localStorage.getItem('tasks')).some(t=>t.title==='Reference edited' && !t.done)") == "true" }
        selectFilter("all")
        awaitObserved("reference All exposes both actual tasks") { evaluate("document.querySelectorAll('article').length===2") == "true" }
        run()
        awaitObserved("reference remount restores both edited and completed state") { evaluate("document.querySelectorAll('article').length===2 && document.querySelector('[aria-label=\"Reference completed\"]').checked && document.querySelector('[aria-label=\"Edit Reference edited\"]').value==='Reference edited'") == "true" }
        selectFilter("completed")
        awaitObserved("reference filtered delete selects only its actual completed record") { evaluate("document.querySelectorAll('article').length===1") == "true" }
        evaluate("document.querySelector('article button').click();true")
        awaitObserved("reference filtered delete preserves the other real record") { evaluate("document.querySelectorAll('article').length===0 && JSON.parse(localStorage.getItem('tasks')).length===1 && JSON.parse(localStorage.getItem('tasks'))[0].title==='Reference edited'") == "true" }
    }

    @Test fun generatedPollUsesRealTotalsAndResetsForNewPayload() {
        val payload = androidx.compose.runtime.mutableStateOf("""{"title":"First poll","options":[{"label":"A"},{"label":"B"}]}""")
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                com.grapsee.gsai.ui.chat.content.WidgetBlockView(
                    com.grapsee.gsai.ui.chat.content.Block.WidgetBlock("poll", payload.value, false, 0), false)
            }
        }
        compose.onNodeWithText("0 local votes", substring = true).assertExists()
        compose.onNodeWithText("A").performClick()
        compose.onNodeWithText("1 local vote", substring = true).assertExists()
        compose.onNodeWithText("100% (1)").assertExists()
        compose.onNodeWithText("B").performTouchInput { click() }
        compose.onNodeWithText("1 local vote", substring = true).assertExists()
        compose.onNodeWithText("100% (1)").assertExists()
        compose.runOnIdle { payload.value = """{"title":"New poll","options":[{"label":"C","votes":2},{"label":"D","votes":3}]}""" }
        compose.onNodeWithText("5 local votes", substring = true).assertExists()
        compose.onNodeWithText("40% (2)").assertExists()
        compose.onNodeWithText("C").performClick()
        compose.onNodeWithText("6 local votes", substring = true).assertExists()
        compose.onAllNodesWithText("50% (3)").assertCountEquals(2)
        assertNull(parseWidget("poll", """{"options":[{"label":"Invalid","votes":-1},{"label":"Other"}]}"""))
    }

    @Test fun backgroundReplyAppearsInOsAndSettingsSuppressIt() {
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val thread = "platform-runtime-${System.nanoTime()}"
        val id = thread.hashCode()
        val oldMaster = SettingsStore.pushNotifications
        val oldMessages = SettingsStore.noticeEnabled("messages")
        val oldQuiet = SettingsStore.quietHoursEnabled
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, android.Manifest.permission.POST_NOTIFICATIONS
            )
        }
        fun post() = ReplyNotifier.postReplyReady(context, thread, "Runtime reply from the test callback")
        try {
            compose.runOnIdle {
                SettingsStore.updatePushNotifications(true)
                SettingsStore.updateNoticeEnabled("messages", true)
                SettingsStore.updateQuietHoursEnabled(false)
            }
            awaitObserved("initial activity foreground") { AppForeground.inForeground }
            post()
            assertFalse("Foreground reply must not post", manager.activeNotifications.any { it.id == id })

            // Home drives MainActivity.ON_STOP; do NOT assign AppForeground in the test.
            assertTrue(UiDevice.getInstance(instrumentation).pressHome())
            awaitObserved("Home backgrounds activity") { !AppForeground.inForeground }
            val callback = CountDownLatch(1)
            Handler(Looper.getMainLooper()).postDelayed({ post(); callback.countDown() }, 1_000)
            assertTrue("Background callback never ran", callback.await(10, TimeUnit.SECONDS))
            awaitObserved("background reply becomes an active OS notification") {
                manager.activeNotifications.any { it.id == id }
            }
            val notice = manager.activeNotifications.single { it.id == id }.notification
            assertEquals("Reply ready", notice.extras.getString(Notification.EXTRA_TITLE))
            assertEquals("Runtime reply from the test callback", notice.extras.getString(Notification.EXTRA_TEXT))
            assertEquals(ReplyNotifier.CHANNEL_ID, notice.channelId)
            assertNotNull("Reply must route back to its conversation", notice.contentIntent)
            // A real shade tap is a user-initiated launch. Calling send() on
            // the PendingIntent from a background test is NOT equivalent:
            // Android 14 correctly applies background activity launch limits.
            val device = UiDevice.getInstance(instrumentation)
            assertTrue("Could not open notification shade", device.openNotification())
            val shadeRow = device.wait(Until.findObject(By.text("Runtime reply from the test callback")), 5_000)
            assertNotNull("OS shade did not expose the actual reply", shadeRow)
            shadeRow!!.click()
            awaitObserved("notification tap returns to foreground") { AppForeground.inForeground }
            awaitObserved("notification auto-cancels after real shade tap") {
                manager.activeNotifications.none { it.id == id }
            }
            assertTrue(device.pressHome())
            awaitObserved("second Home backgrounds activity") { !AppForeground.inForeground }

            instrumentation.runOnMainSync { SettingsStore.updatePushNotifications(false) }
            post()
            assertFalse(manager.activeNotifications.any { it.id == id })
            instrumentation.runOnMainSync {
                SettingsStore.updatePushNotifications(true)
                SettingsStore.updateNoticeEnabled("messages", false)
            }
            post()
            assertFalse(manager.activeNotifications.any { it.id == id })
        } finally {
            manager.cancel(id)
            instrumentation.runOnMainSync {
                SettingsStore.updatePushNotifications(oldMaster)
                SettingsStore.updateNoticeEnabled("messages", oldMessages)
                SettingsStore.updateQuietHoursEnabled(oldQuiet)
            }
        }
    }

    private fun awaitObserved(stage: String, condition: () -> Boolean) {
        try { compose.waitUntil(10_000, condition) }
        catch (error: Throwable) {
            throw AssertionError("Runtime stage failed: $stage; foreground=${AppForeground.inForeground}", error)
        }
    }

    @Test fun realCardDistinguishesTapFromLongPress() {
        var taps = 0
        var holds = 0
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                GsCard(Modifier.testTag("runtime-card"), onClick = { taps++ }, onLongClick = { holds++ }) {
                    Text("Runtime gesture target")
                }
            }
        }
        compose.onNodeWithTag("runtime-card").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, taps); assertEquals(0, holds) }
        compose.onNodeWithTag("runtime-card").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, taps); assertEquals(1, holds) }
    }

    @Test fun generatedTableAllDisplaysEveryActualRow() {
        val rows = (1..31).joinToString(",") { "[\"Item $it\",$it]" }
        val widget = parseWidget("table", """{"columns":["Name","Amount"],"rows":[$rows]}""")!!
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme { com.grapsee.gsai.ui.chat.content.WidgetView(widget) }
        }
        compose.onNodeWithText("Item 5").assertExists()
        compose.onNodeWithText("Item 6").assertDoesNotExist()
        compose.onNodeWithText("Show: 5 ▾").performClick()
        compose.onNodeWithText("Item 10").assertExists()
        compose.onNodeWithText("Item 11").assertDoesNotExist()
        compose.onNodeWithText("Show: 10 ▾").performClick()
        compose.onNodeWithText("Show: All ▾").assertExists()
        compose.onNode(hasText("Item 31") and !hasSetTextAction()).assertExists()
        compose.onNodeWithText("1–31 of 31").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("Item 31")
        compose.onNode(hasText("Item 31") and !hasSetTextAction()).assertExists()
        compose.onNodeWithText("Item 1").assertDoesNotExist()
        compose.onNodeWithText("1–1 of 1").assertExists()
        // Ordinary Markdown has a separate renderer; test it too, including
        // the emoji-only fallback that formerly restored rejected text.
        val markdown = "`😀` [😀](https://example.org)\n\n| Name | Amount |\n| --- | --- |\n" +
            (1..31).joinToString("\n") { "| Item $it | $it |" }
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                com.grapsee.gsai.ui.chat.content.BlocksContent(markdown, false, {})
            }
        }
        compose.onAllNodes(hasText("😀", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Link", substring = true).assertExists()
        compose.onNodeWithText("Show: 5 ▾").performClick()
        compose.onNodeWithText("Show: 10 ▾").performClick()
        compose.onNodeWithText("Show: All ▾").assertExists()
        compose.onNode(hasText("Item 31") and !hasSetTextAction()).assertExists()
        compose.onNodeWithText("1–31 of 31").assertExists()
    }

    @Test fun generatedCalculatorKeepsLaterInputsSixthChoiceNinthOutputAndSixthPresetUsable() {
        val fields = (1..13).joinToString(",") { """{"key":"x$it","label":"Input $it","value":$it}""" }
        val choices = (1..6).joinToString(",") { """{"label":"Option $it","value":$it}""" }
        val outputs = (1..8).joinToString(",") { """{"label":"Result $it","expr":"x1"}""" }
        val presets = (1..6).joinToString(",") { """{"label":"Preset $it","values":{"x12":20,"x13":30,"option":6}}""" }
        val source = """{"title":"All actual controls","fields":[$fields,{"key":"option","label":"Choice","value":1,"choices":[$choices]}],"outputs":[$outputs,{"label":"Final result","expr":"x12+x13+option"}],"presets":[$presets]}"""
        val widget = parseWidget("calc", source) as Widget.Calc
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    CalcWidgetView(widget.w)
                }
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Input 12")).performScrollTo().performTextReplacement("120")
        compose.onNode(hasSetTextAction() and hasText("Input 13")).performScrollTo().performTextReplacement("130")
        compose.onNodeWithText("Option 6").performScrollTo().performClick()
        compose.onNodeWithText("Final result").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("256").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Preset 6").performScrollTo().performClick()
        compose.onNodeWithText("56").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reset").performScrollTo().performClick()
        compose.onNodeWithText("26").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasText("$", substring = true)).assertCountEquals(0)
    }

    @Test fun generatedCalculatorAcceptsExactNumbersAndNeverRendersEmoji() {
        val json = """{"type":"finance","title":"\ud83d\ude80 Growth","fields":[{"key":"amount","label":"Amount \u00a9\ufe0f \ud83d\ude00","value":100}],"outputs":[{"label":"Double","expr":"amount*2","primary":true}]}"""
        val widget = parseWidget("calc", json) as Widget.Calc
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme { CalcWidgetView(widget.w) }
        }
        compose.onNodeWithText("200").assertExists()
        compose.onAllNodes(hasText("©", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("😀", substring = true)).assertCountEquals(0)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).assertCountEquals(0)
        compose.onNode(hasSetTextAction()).performTextReplacement("12.3456")
        compose.onNodeWithText("24.69").assertExists()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("Enter valid numbers within the supplied limits").assertExists()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("200").assertExists()
    }

    @Test fun generatedChartRangeAndPieSwitchUseOnlyActualWindowObservations() {
        val long = (0..7).joinToString(",") { "[$it,${it + 1}]" }
        val short = (0..4).joinToString(",") { "[$it,${100 + it}]" }
        val labels = (0..7).joinToString(",") { "\"P$it\"" }
        val source = """{"type":"line","labels":[$labels],"series":[{"name":"Long","data":[$long]},{"name":"Short","data":[$short]},{"name":"Absent","data":[[0,9]]}]}"""
        val widget = parseWidget("chart", source) as Widget.Chart
        var displayed by androidx.compose.runtime.mutableStateOf<Widget>(widget)
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))) {
                    com.grapsee.gsai.ui.chat.content.WidgetView(displayed)
                }
            }
        }
        compose.onNodeWithText("All (8 pts) ▾").performClick()
        compose.onNodeWithText("Last 6 pts ▾").performClick()
        compose.onNodeWithText("Last 4 pts ▾").assertExists()
        compose.onNodeWithText("Lowest").assertExists()
        compose.onNodeWithText("5").assertExists()
        compose.onNodeWithText("Pie").assertDoesNotExist()
        // Both the native legend and the real series selector can expose
        // these names. Test source presence without assuming a single node.
        compose.onAllNodesWithText("Long").onFirst().assertExists()
        compose.onAllNodesWithText("Short").onFirst().assertExists()
        compose.onNodeWithText("Absent").assertDoesNotExist()
        // A new, genuine single-series dataset can offer shares. Controls must
        // reset with that source, not preserve the previous window or series.
        compose.runOnIdle { displayed = Widget.Chart(widget.w.copy(series = listOf(widget.w.series.first()))) }
        compose.onNodeWithText("All (8 pts) ▾").assertExists()
        compose.onNodeWithText("Pie").performClick()
        compose.onNodeWithText("P7 — 22%").assertExists()
        compose.onNodeWithText("N/A (slice values)").assertExists()
        compose.onNodeWithText("Line").performClick()
        compose.onNodeWithText("All (8 pts) ▾").assertExists()
    }

    @Test fun generatedTerminalUsesRealFilesWorkerAndUnsupportedRefusal() {
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                com.grapsee.gsai.ui.chat.content.WidgetBlockView(
                    com.grapsee.gsai.ui.chat.content.Block.WidgetBlock("terminal",
                        """{"welcome":"Provided \ud83d\ude00 $& $$ __GS_WORKER_CONTRACT_LITERAL__","commands":[{"name":"deploy","description":"Help only","output":"deployed"}]}""", false, 0), false)
            }
        }
        fun findWeb(view: View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
            return null
        }
        var web: android.webkit.WebView? = null
        awaitObserved("production terminal creates its real sandbox") {
            instrumentation.runOnMainSync { web = findWeb(compose.activity.window.decorView) }; web != null
        }
        fun evaluate(script: String): String? {
            val done = CountDownLatch(1); var result: String? = null
            instrumentation.runOnMainSync { web!!.evaluateJavascript(script) { result = it; done.countDown() } }
            assertTrue(done.await(5, TimeUnit.SECONDS)); return result
        }
        awaitObserved("shared terminal asset loads and preserves token-looking data") {
            evaluate("!!document.getElementById('command') && document.getElementById('log').textContent.includes('__GS_WORKER_CONTRACT_LITERAL__')") == "true"
        }
        val cssWidth = evaluate("innerWidth")!!.toDouble()
        val nativeWidthDp = web!!.width / instrumentation.targetContext.resources.displayMetrics.density
        assertEquals("The actual viewport must use device-independent CSS pixels", nativeWidthDp.toDouble(), cssWidth, 2.0)
        val device = UiDevice.getInstance(instrumentation)
        fun requireWorkerOutput(text: String) {
            // UiDevice waits do not advance Compose's test frame clock. Drive
            // the real measure/layout pass after asynchronous WebView bridge
            // delivery; otherwise state changes are observed but never drawn.
            awaitObserved("terminal content height is actually applied before inspecting accessibility") {
                compose.waitForIdle()
                evaluate("innerHeight + 2 >= document.body.scrollHeight") == "true"
            }
            if (!device.wait(Until.hasObject(By.textContains(text)), 15_000L)) {
                // This is failure diagnostics only; scrolling must NOT turn a
                // clipped terminal into a passing auto-layout test. It reveals
                // whether execution failed or its genuine output was hidden.
                val position = IntArray(2)
                var width = 0
                var height = 0
                instrumentation.runOnMainSync {
                    web!!.getLocationOnScreen(position); width = web!!.width; height = web!!.height
                }
                repeat(2) { device.swipe(position[0] + width / 2, position[1] + height * 4 / 5, position[0] + width / 2, position[1] + height / 5, 20) }
                val dump = java.io.ByteArrayOutputStream()
                device.dumpWindowHierarchy(dump)
                val state = evaluate("JSON.stringify({text:document.body.innerText,height:innerHeight,bodyHeight:document.body.scrollHeight,bridge:typeof GsHost,installed:!!window.__gsNativeHeightBridge,reportedHeight:window.__gsReportedHeight,bridgeError:window.__gsHeightBridgeError,nativeBridgeReceived:window.__gsNativeBridgeReceived,composeHeight:window.__gsComposeHeight,nativeMeasuredHeight:window.__gsNativeMeasuredHeight,frame:document.querySelector('#execution iframe')?.getBoundingClientRect().toJSON()})")
                val tree = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(java.io.ByteArrayInputStream(dump.toByteArray())).getElementsByTagName("node")
                val labels = (0 until tree.length).mapNotNull { index ->
                    val attributes = tree.item(index).attributes
                    if (attributes.getNamedItem("package")?.nodeValue != "com.grapsee.gsai") null
                    else listOf("text", "content-desc").mapNotNull { attributes.getNamedItem(it)?.nodeValue?.takeIf { value -> value.isNotBlank() } }.joinToString(" | ").takeIf { it.isNotBlank() }
                }
                throw AssertionError("Actual Worker output missing: $text; DOM state: $state; actual app accessibility: " + labels.joinToString("; ").take(4000))
            }
        }
        fun submit(command: String) {
            assertEquals("true", evaluate("document.getElementById('command').value=" + org.json.JSONObject.quote(command) + ";true"))
            val encoded = evaluate("(function(){var r=document.querySelector('button[type=submit]').getBoundingClientRect();return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2,vw:innerWidth});})()")
            val bounds = org.json.JSONObject(org.json.JSONTokener(encoded!!).nextValue() as String)
            val position = IntArray(2); var scale = 1.0
            instrumentation.runOnMainSync { web!!.getLocationOnScreen(position); scale = web!!.width / bounds.getDouble("vw") }
            assertTrue(device.click((position[0] + bounds.getDouble("x") * scale).toInt(), (position[1] + bounds.getDouble("y") * scale).toInt()))
            awaitObserved("actual OS Run tap submits terminal command") { evaluate("document.getElementById('command').value === ''") == "true" }
        }
        submit("mkdir project"); submit("cd project"); submit("touch actual.txt"); submit("ls")
        assertEquals("true", evaluate("document.getElementById('log').textContent.endsWith('actual.txt')"))
        submit("rm actual.txt"); submit("ls")
        assertEquals("true", evaluate("document.getElementById('log').textContent.endsWith('(empty)')"))
        submit("js [1,2,3].map(n=>n*2)")
        requireWorkerOutput("[2,4,6]")
        submit("js while(true) {}")
        requireWorkerOutput("exceeded 2 seconds")
        submit("python print(7)")
        assertEquals("true", evaluate("document.getElementById('log').textContent.includes('python: not executed') && document.getElementById('execution').children.length === 0"))
        submit("deploy")
        assertEquals("true", evaluate("document.getElementById('log').textContent.endsWith('deploy: not executed — no OS shell or command runtime is connected.')"))
        assertEquals("false", evaluate("document.body.innerText.includes(String.fromCodePoint(0x1f600))"))
        assertEquals("true", evaluate("document.getElementById('reset').click();document.getElementById('path').textContent === '/home/guest $'"))
        submit("ls")
        assertEquals("true", evaluate("document.getElementById('log').textContent.endsWith('README.md')"))
    }

    @Test fun generatedHtmlRunsRealButtonLogicAndSuppressesDynamicEmoji() {
        val source = """<button id="run" title="Increase &#x1F600;">Increase &#x1F600;</button><output id="result">0</output><svg data-custom-glass="true"><path d="M0 0L8 8"/></svg><script>window.marker=String.fromCodePoint(0x1F600);window.count=0;document.getElementById('run').onclick=function(){window.count++;document.getElementById('result').textContent=window.count+window.marker;};window.closedRoot=document.body.appendChild(document.createElement('div')).attachShadow({mode:'closed'});closedRoot.innerHTML='<button>Shadow &#x1F600;</button><output>0</output>';window.shadowCount=0;closedRoot.querySelector('button').onclick=function(){shadowCount++;closedRoot.querySelector('output').textContent=shadowCount+marker;};var canvas=document.body.appendChild(document.createElement('canvas'));canvas.id='emoji-canvas';canvas.width=100;canvas.height=40;var ctx=canvas.getContext('2d');ctx.font='28px sans-serif';ctx.fillText(marker,0,30);ctx.strokeText(marker,40,30);</script>"""
        compose.activity.setContentForPlatformTest {
            TheGsAiTheme {
                com.grapsee.gsai.ui.chat.content.HtmlBlockView(
                    com.grapsee.gsai.ui.chat.content.Block.HtmlBlock("html", source, false, 0), false)
            }
        }
        fun findWebView(view: View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        var web: android.webkit.WebView? = null
        awaitObserved("production HtmlBlock creates a WebView") {
            instrumentation.runOnMainSync { web = findWebView(compose.activity.window.decorView) }
            web != null
        }
        fun evaluate(script: String): String? {
            val done = CountDownLatch(1)
            var value: String? = null
            instrumentation.runOnMainSync {
                web!!.evaluateJavascript(script) { value = it; done.countDown() }
            }
            assertTrue("WebView JavaScript callback must execute", done.await(5, TimeUnit.SECONDS))
            return value
        }
        awaitObserved("authored script executes with bundled display policy") {
            evaluate("window.__gsDisplayEmojiPolicy === true && typeof window.marker === 'string'") == "true"
        }
        assertEquals("true", evaluate("document.getElementById('run').textContent === 'Increase ' && document.getElementById('run').title === 'Increase '"))
        val device = UiDevice.getInstance(instrumentation)
        // Tap the real rendered button centre through UiDevice. Accessibility
        // text caching in Android WebView is asynchronous and is not the DOM
        // execution contract under test. Coordinates come from its actual
        // layout, never a guessed screen position or a JavaScript click.
        val encoded = evaluate("(function(){var r=document.getElementById('run').getBoundingClientRect();return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2,vw:innerWidth});})()")
        val bounds = org.json.JSONObject(org.json.JSONTokener(encoded!!).nextValue() as String)
        val position = IntArray(2)
        var scale = 1.0
        instrumentation.runOnMainSync {
            web!!.getLocationOnScreen(position)
            scale = web!!.width / bounds.getDouble("vw")
        }
        assertTrue("Rendered HTML button must receive an actual OS pointer tap", device.click(
            (position[0] + bounds.getDouble("x") * scale).toInt(),
            (position[1] + bounds.getDouble("y") * scale).toInt()))
        awaitObserved("OS tap runs authored logic and cleans dynamic output") {
            evaluate("window.count === 1 && document.getElementById('result').textContent === '1'") == "true"
        }
        assertEquals("128512", evaluate("window.marker.codePointAt(0)"))
        assertEquals("true", evaluate("window.closedRoot.querySelector('button').textContent === 'Shadow '"))
        evaluate("window.closedRoot.querySelector('button').click(); true")
        awaitObserved("actual closed-shadow event logic suppresses dynamic emoji without changing source strings") {
            evaluate("window.shadowCount === 1 && window.closedRoot.querySelector('output').textContent === '1'") == "true"
        }
        assertEquals("true", evaluate("Array.from(document.getElementById('emoji-canvas').getContext('2d').getImageData(0,0,100,40).data).every(value=>value===0)"))
        assertEquals("true", evaluate("(function(){const ctx=document.getElementById('emoji-canvas').getContext('2d');ctx.fillRect(0,0,4,4);return ctx.getImageData(0,0,1,1).data[3]===255;})()"))
        assertEquals("true", evaluate("!!document.querySelector('svg[data-custom-glass] path')"))
    }

    @Test fun hapticDispatchUsesPlatformConstantsAndHonorsToggle() {
        val old = SettingsStore.haptics
        val calls = mutableListOf<Int>()
        try {
            compose.runOnIdle {
                val view = object : View(compose.activity) {
                    override fun performHapticFeedback(constant: Int): Boolean {
                        calls.add(constant)
                        return super.performHapticFeedback(constant)
                    }
                }
                SettingsStore.updateHaptics(true)
                GsHaptics.press(view)
                GsHaptics.success(view)
                GsHaptics.warning(view)
                GsHaptics.error(view)
                assertEquals(listOf(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.CONFIRM, HapticFeedbackConstants.GESTURE_END,
                    HapticFeedbackConstants.REJECT), calls)
                SettingsStore.updateHaptics(false)
                GsHaptics.press(view); GsHaptics.success(view); GsHaptics.warning(view); GsHaptics.error(view)
                assertEquals("Disabled haptics must not dispatch", 4, calls.size)
            }
        } finally { compose.runOnIdle { SettingsStore.updateHaptics(old) } }
    }
}

private fun MainActivity.setContentForPlatformTest(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
        setContent(content = content)
    }
}
