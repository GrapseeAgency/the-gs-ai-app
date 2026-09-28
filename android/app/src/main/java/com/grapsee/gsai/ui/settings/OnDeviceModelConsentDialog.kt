package com.grapsee.gsai.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.local.ModelCatalog
import com.grapsee.gsai.data.local.ModelDownloader
import com.grapsee.gsai.data.local.ModelStore
import com.grapsee.gsai.data.local.ModelStore.Consent
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The one-time permission gate for an on-device model download.
 *
 * A Compose `AlertDialog`, which is what this app already uses for every other
 * modal (AssistantCreateScreen, AssistantDetailScreen, AssistantsScreen all use
 * `androidx.compose.material3.AlertDialog`). No Activity, no ViewController, no
 * nav route, no new design token, no new screen: it is a dialog over whatever is
 * already on the stack, which is what a permission gate is.
 *
 * ## The ordering, which is the whole point
 *
 *     1. ask          the user consents to the SIZE, before any bytes move
 *     2. ask again    the mobile-data question, separately, because 1.1 GB of
 *                     someone's data plan is a different decision from "yes, use
 *                     a local model"
 *     3. download     only now
 *
 * Answering "yes" to step 1 records GRANTED but does NOT start a download. If
 * consent implied "fetch it now", a user who enabled the feature and then closed
 * the app would come back to 400 MB of unexpected mobile data.
 *
 * `TestTag`s are on every control so an instrumented test can drive this
 * without matching on copy, which breaks the moment the wording changes.
 */
@Composable
fun OnDeviceModelConsentDialog(
    onDismiss: () -> Unit = {},
) {
    val model = ModelStore.target()
    var phase by remember { mutableStateOf(Phase.ASK) }
    var bytes by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    // Captured here, at composition time: LocalContext is a composable-only
    // value and cannot be read inside the coroutine below.
    val context = LocalContext.current

    // Fires once per install. A user who declined is not asked again, because
    // setConsent moves the state off UNDECIDED permanently.
    LaunchedEffect(Unit) {
        if (ModelStore.shouldPrompt()) phase = Phase.ASK
    }

    if (model == null) return

    when (phase) {
        Phase.ASK -> AlertDialog(
            onDismissRequest = {
                // A swipe-away is not consent and not refusal. Leaving it
                // UNDECIDED would mean re-prompting next launch, which is the
                // nag the "once per install" promise forbids. A dismissal is a
                // refusal.
                ModelStore.updateConsent(Consent.DECLINED)
                onDismiss()
            },
            title = { Text("Enable on-device AI?") },
            text = {
                Column {
                    Text(ModelCatalog.consentMessage(model))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This device is ${ModelStore.tier.name} class, so it gets " +
                            "${model.displayName}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (ModelStore.isCellular()) {
                        Spacer(Modifier.height(12.dp))
                        Row2 {
                            Text(
                                "You're on mobile data. Allow a ${model.sizeLabel()} " +
                                    "download on mobile data?",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = !ModelStore.wifiOnlyConsent,
                                onCheckedChange = { ModelStore.updateWifiOnly(!it) },
                                modifier = Modifier.testTag("gs_consent_cellular"),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ModelStore.updateConsent(Consent.GRANTED)
                        // Re-check rather than assume: the size, the checksum and
                        // the network policy are all enforced again here, so a
                        // GRANTED state is a permission, not a guarantee.
                        when (val gate = ModelStore.mayDownload()) {
                            is ModelStore.Result.Allowed -> {
                                phase = Phase.DOWNLOADING
                                job = scope.launch {
                                    val dest = ModelCatalog.destination(context, model)
                                    val partial = ModelCatalog.partialDestination(context, model)
                                    val r = ModelDownloader.download(model, dest, partial) { b, t ->
                                        bytes = b; total = t
                                    }
                                    when (r) {
                                        is ModelDownloader.Result.Complete -> {
                                            ModelStore.recordInstalled(r.file, model.id)
                                            ModelStore.updateLastError(null)
                                            phase = Phase.DONE
                                        }
                                        is ModelDownloader.Result.Failed -> {
                                            ModelStore.updateLastError(r.reason)
                                            // Stay on the dialog with the reason
                                            // shown, rather than closing and
                                            // failing silently.
                                            phase = Phase.FAILED
                                        }
                                    }
                                }
                            }
                            is ModelStore.Result.Refused -> {
                                ModelStore.updateLastError(gate.reason)
                                phase = Phase.FAILED
                            }
                        }
                    },
                    modifier = Modifier.testTag("gs_consent_accept"),
                ) { Text("Enable") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        ModelStore.updateConsent(Consent.DECLINED)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("gs_consent_decline"),
                ) { Text("Not now") }
            },
        )

        Phase.DOWNLOADING -> AlertDialog(
            onDismissRequest = { /* not dismissible: cancelling goes through the button */ },
            title = { Text("Downloading ${model.displayName}") },
            text = {
                Column {
                    if (total > 0) {
                        val pct = ((bytes * 100) / total).coerceIn(0, 100)
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.testTag("gs_consent_progress"),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${pct}%  ${fmt(bytes)} of ${fmt(total)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        // The server sent no Content-Length. An indeterminate bar
                        // is the honest thing; a percentage would be invented.
                        LinearProgressIndicator(
                            modifier = Modifier.testTag("gs_consent_progress"),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${fmt(bytes)} downloaded",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Cancel KEEPS the partial file. That is what makes the
                        // next attempt a resume rather than a restart.
                        job?.cancel()
                        ModelStore.updateLastError("Download cancelled. Partial download kept for resume.")
                        phase = Phase.ASK
                    },
                    modifier = Modifier.testTag("gs_consent_cancel"),
                ) { Text("Cancel") }
            },
        )

        Phase.DONE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ready") },
            text = { Text("On-device AI is installed and works offline. You can delete it any time in Settings.") },
            confirmButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("gs_consent_done")) {
                    Text("Done")
                }
            },
        )

        Phase.FAILED -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Could not start the download") },
            text = {
                Column {
                    Text(
                        ModelStore.lastError ?: "unknown error",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Nothing was installed. Any partial download was kept, so " +
                            "trying again continues where this left off.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("gs_consent_dismiss_error")) {
                    Text("Close")
                }
            },
        )
    }
}

private enum class Phase { ASK, DOWNLOADING, DONE, FAILED }

/** A Row that keeps this file's imports short without pulling in more. */
@Composable
private fun Row2(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        content = content,
    )
}

private fun fmt(b: Long): String = when {
    b >= 1_000_000_000L -> String.format("%.2f GB", b / 1e9)
    b >= 1_000_000L -> String.format("%.0f MB", b / 1e6)
    b >= 1_000L -> String.format("%.0f KB", b / 1e3)
    else -> "$b B"
}
