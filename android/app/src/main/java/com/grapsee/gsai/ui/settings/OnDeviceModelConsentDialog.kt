package com.grapsee.gsai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.local.ModelCatalog
import com.grapsee.gsai.ui.components.GsDialogShell
import com.grapsee.gsai.ui.components.GsGlassSwitch
import com.grapsee.gsai.ui.components.GsStaggeredLoader
import com.grapsee.gsai.ui.components.RefGlyphWell
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.data.local.ModelDownloader
import com.grapsee.gsai.data.local.ModelStore
import com.grapsee.gsai.data.local.ModelStore.Consent
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The one-time permission gate for an on-device model download.
 *
 * A modal over whatever is already on the stack — no Activity, no
 * ViewController, no nav route, no new screen. Since 0.86.23 it sits in the
 * same glass slab as every other decision in the app ([GsDialogShell]) instead
 * of a stock `material3.AlertDialog`: this is the longest dialog in the build
 * (a size disclosure, a data-policy question, a progress bar, an error), and it
 * was the only one still wearing Compose's default chrome.
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
    // HIDDEN, not ASK. See the enum comment: starting at ASK is why
    // "Not now" did nothing at all on a real device.
    var phase by remember { mutableStateOf(Phase.HIDDEN) }
    var bytes by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    // Captured here, at composition time: LocalContext is a composable-only
    // value and cannot be read inside the coroutine below.
    val context = LocalContext.current

    // Fires once per install. A user who declined is not asked again, because
    // setConsent moves the state off UNDECIDED permanently.
    //
    // This is the ONLY thing that opens the dialog, and the only thing that decides
    // HIDDEN -- so a DECLINED install stays quiet. It previously only ever SET ASK,
    // which is why "Not now" could not close it.
    LaunchedEffect(Unit) {
        phase = if (ModelStore.shouldPrompt()) Phase.ASK else Phase.HIDDEN
    }

    if (model == null) return

    when (phase) {
        // HIDDEN renders NOTHING. Not a no-op branch for the compiler's benefit --
        // this is the state that makes "Not now" work, and it is the default on
        // every launch where consent was already given or declined.
        //
        // Release 37211777086 caught the exhaustiveness half:
        //   'when' expression must be exhaustive. Add the 'HIDDEN' branch
        Phase.HIDDEN -> Unit

        Phase.ASK -> ConsentFrame(
            title = "Enable on-device AI?",
            icon = GsIcons.Cpu,
            tone = GsWellTone.Lilac,
            onDismiss = {
                // A swipe-away is not consent and not refusal. Leaving it
                // UNDECIDED would mean re-prompting next launch, which is the
                // nag the "once per install" promise forbids. A dismissal is a
                // refusal.
                ModelStore.updateConsent(Consent.DECLINED)
                // HIDE OURSELVES: onDismiss() is a NO-OP when the
                // caller passed nothing, which is how MainActivity calls
                // this -- relying on it left the dialog stuck on screen.
                phase = Phase.HIDDEN
                onDismiss()
            },
            primaryLabel = "Enable",
            primaryTag = "gs_consent_accept",
            onPrimary = {
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
            secondaryLabel = "Not now",
            secondaryTag = "gs_consent_decline",
            onSecondary = {
                ModelStore.updateConsent(Consent.DECLINED)
                // HIDE OURSELVES: onDismiss() is a NO-OP when the
                // caller passed nothing, which is how MainActivity calls
                // this -- relying on it left the dialog stuck on screen.
                phase = Phase.HIDDEN
                onDismiss()
            },
        ) {
            Text(
                ModelCatalog.consentMessage(model),
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                fontFamily = GsFonts.Product,
                color = GsTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "This device is ${ModelStore.tier.name} class, so it gets " +
                    "${model.displayName}.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = GsFonts.Product,
                color = GsTheme.colors.textTertiary,
            )
            if (ModelStore.isCellular()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GsTheme.colors.textPrimary.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "You're on mobile data. Allow a ${model.sizeLabel()} " +
                            "download on mobile data?",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = GsFonts.Product,
                        color = GsTheme.colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    GsGlassSwitch(
                        checked = !ModelStore.wifiOnlyConsent,
                        onCheckedChange = { ModelStore.updateWifiOnly(!it) },
                        modifier = Modifier.testTag("gs_consent_cellular"),
                    )
                }
            }
        }

        // Not dismissible: the scrim does nothing, because cancelling has to go
        // through the button that also keeps the partial file.
        Phase.DOWNLOADING -> ConsentFrame(
            title = "Downloading ${model.displayName}",
            icon = GsIcons.Download,
            tone = GsWellTone.Blue,
            onDismiss = null,
            primaryLabel = "Cancel",
            primaryTag = "gs_consent_cancel",
            onPrimary = {
                // Cancel KEEPS the partial file. That is what makes the
                // next attempt a resume rather than a restart.
                job?.cancel()
                ModelStore.updateLastError("Download cancelled. Partial download kept for resume.")
                phase = Phase.ASK
            },
        ) {
            if (total > 0) {
                val pct = ((bytes * 100) / total).coerceIn(0, 100)
                // A drawn bar rather than Material's indicator: one line of
                // ink on the slab, at the slab's own radius.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(GsTheme.colors.textPrimary.copy(alpha = 0.08f))
                        .testTag("gs_consent_progress"),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct / 100f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(GsTheme.colors.accentStrong, GsTheme.colors.accent)
                                )
                            ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "$pct%  ${fmt(bytes)} of ${fmt(total)}",
                    fontSize = 11.sp,
                    fontFamily = GsFonts.Product,
                    color = GsTheme.colors.textSecondary,
                )
            } else {
                // The server sent no Content-Length. A staggered
                // wait-state loader is the honest thing; a percentage
                // would be invented. (testTag kept on the loader row.)
                Row(
                    modifier = Modifier
                        .testTag("gs_consent_progress")
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    GsStaggeredLoader()
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${fmt(bytes)} downloaded",
                    fontSize = 11.sp,
                    fontFamily = GsFonts.Product,
                    color = GsTheme.colors.textSecondary,
                )
            }
        }

        Phase.DONE -> ConsentFrame(
            // Every exit sets HIDDEN itself. `onDismiss` alone is a NO-OP when the
            // caller supplied none, which is how MainActivity calls this dialog --
            // so DONE and FAILED were stuck on screen for exactly the same reason ASK
            // was, and neither has an automated test.
            title = "Ready",
            icon = GsIcons.CheckCircle2,
            tone = GsWellTone.Mint,
            onDismiss = { phase = Phase.HIDDEN; onDismiss() },
            primaryLabel = "Done",
            primaryTag = "gs_consent_done",
            onPrimary = { phase = Phase.HIDDEN; onDismiss() },
        ) {
            Text(
                "On-device AI is installed and works offline. You can delete it any " +
                    "time in Settings.",
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                fontFamily = GsFonts.Product,
                color = GsTheme.colors.textSecondary,
            )
        }

        Phase.FAILED -> ConsentFrame(
            title = "Could not start the download",
            icon = GsIcons.CircleAlert,
            tone = GsWellTone.Rose,
            onDismiss = { phase = Phase.HIDDEN; onDismiss() },
            primaryLabel = "Close",
            primaryTag = "gs_consent_dismiss_error",
            onPrimary = { phase = Phase.HIDDEN; onDismiss() },
        ) {
            Text(
                ModelStore.lastError ?: "unknown error",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = GsFonts.Product,
                color = GsTheme.colors.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Nothing was installed. Any partial download was kept, so " +
                    "trying again continues where this left off.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = GsFonts.Product,
                color = GsTheme.colors.textTertiary,
            )
        }
    }
}

/**
 * One slab, four phases: well + title, the caller's body, one primary pill and
 * an optional quiet row under it.
 *
 * [onDismiss] is nullable on purpose — a download in flight must not be
 * swipeable, and the scrim's tap is wired to the same lambda as the back
 * gesture, so `null` closes both at once instead of leaving one of them live.
 */
@Composable
private fun ConsentFrame(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: GsWellTone,
    onDismiss: (() -> Unit)?,
    primaryLabel: String,
    primaryTag: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    secondaryTag: String? = null,
    onSecondary: (() -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    val colors = GsTheme.colors
    GsDialogShell(onDismiss = { onDismiss?.invoke() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RefGlyphWell(glyph = "", tone = tone, size = 40.dp, icon = icon)
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
            }
            Spacer(Modifier.height(12.dp))
            body()
            Spacer(Modifier.height(18.dp))

            val pill = RoundedCornerShape(999.dp)
            Text(
                text = primaryLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(pill)
                    .background(
                        Brush.verticalGradient(listOf(colors.accentStrong, colors.accent))
                    )
                    .clickable(role = Role.Button, onClick = onPrimary)
                    .testTag(primaryTag)
                    .padding(vertical = 13.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            if (secondaryLabel != null && onSecondary != null) {
                Text(
                    text = secondaryLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(pill)
                        .clickable(role = Role.Button, onClick = onSecondary)
                        .then(
                            if (secondaryTag != null) Modifier.testTag(secondaryTag)
                            else Modifier
                        )
                        .padding(vertical = 12.dp),
                    fontSize = 12.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * HIDDEN EXISTS BECAUSE "Not now" DID NOTHING, and it was reported from a real
 * phone: the dialog reappeared on every launch with no way past it.
 *
 * The cause was that `phase` was INITIALISED to ASK unconditionally:
 *
 *     var phase by remember { mutableStateOf(Phase.ASK) }
 *     LaunchedEffect(Unit) { if (ModelStore.shouldPrompt()) phase = Phase.ASK }
 *
 * That effect can only ever SET ASK. There was no state meaning "not showing", so
 * writing `Consent.DECLINED` changed nothing on screen -- and `MainActivity` renders
 * this dialog with NO `onDismiss` argument, so the default no-op ran and the dialog
 * simply stayed. The comment near the decline path talks about a "once per install"
 * promise that this state machine could not honour.
 *
 * So: start HIDDEN, and let `shouldPrompt()` be the only thing that opens OR closes
 * it. A gate that starts open and is merely told to close cannot be trusted to stay
 * closed.
 */
private enum class Phase { HIDDEN, ASK, DOWNLOADING, DONE, FAILED }

private fun fmt(b: Long): String = when {
    b >= 1_000_000_000L -> String.format("%.2f GB", b / 1e9)
    b >= 1_000_000L -> String.format("%.0f MB", b / 1e6)
    b >= 1_000L -> String.format("%.0f KB", b / 1e3)
    else -> "$b B"
}
