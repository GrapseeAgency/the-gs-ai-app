package com.grapsee.gsai.ui.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.DataExport
import com.grapsee.gsai.data.DataRetention
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefSheetRow
import com.grapsee.gsai.ui.components.RefSheetToggle
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark
import kotlinx.coroutines.launch

/**
 * Data Export & Account Deletion —
 * docs/reference/pending/data-export-account-deletion.png.
 *
 * The reference's three groups, in its order and at the sheet kit's measured
 * type: Export Your Data (two exports, the format tiles, the complete archive),
 * Data Retention (period, auto-delete, clear history) and Delete Your Account
 * (the warning card with its consent gate).
 *
 * HONESTY:
 *  - The export writes a real file in the chosen format. It is NOT emailed:
 *    this build has no mail transport, so the archive row says "saved to a file
 *    you choose" and its pill says "Save Archive".
 *  - Attachment RECORDS travel with a chat export; attachment bytes do not, so
 *    the row says "attachment details".
 *  - Auto-delete is OFF by default even though the mock draws it on. Turning it
 *    on runs the purge immediately, and the same purge runs at launch, so the
 *    switch is never a stored value with no reader.
 *  - Clear Chat History removes conversations, messages and staged attachment
 *    copies; Library saves stay. Delete Account removes all of that plus saved
 *    items and signs the session out.
 */
@Composable
fun DataExportAccountDeletionScreen(
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(DataExport.Format.JSON) }
    var pendingSave by remember { mutableStateOf<Pair<DataExport.Scope, DataExport.Format>?>(null) }
    var understood by remember { mutableStateOf(false) }
    var showRetention by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingSave
        pendingSave = null
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && pending != null) {
            scope.launch {
                val ok = DataExport.write(context, uri, pending.second, pending.first)
                Toast.makeText(
                    context,
                    if (ok) "Export saved" else "Could not save the export",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun save(exportScope: DataExport.Scope) {
        pendingSave = exportScope to format
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = format.mime
            putExtra(Intent.EXTRA_TITLE, DataExport.fileName(format, exportScope))
        }
        runCatching { saveLauncher.launch(intent) }.onFailure {
            pendingSave = null
            Toast.makeText(context, "No file picker is available", Toast.LENGTH_SHORT).show()
        }
    }

    fun purgeNow() {
        scope.launch {
            val removed = runCatching { DataRetention.purgeExpired() }.getOrDefault(0)
            if (removed > 0) {
                Toast.makeText(
                    context,
                    "$removed inactive conversation${if (removed == 1) "" else "s"} removed",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    RefSettingsPage(
        title = "Data Export & Account Deletion",
        subtitle = "Take control of your data. Export what you need or delete your account when you're ready.",
        onBack = onBack,
    ) {
        // ---- Export Your Data ---------------------------------------------
        RefGroupHeader("Export Your Data", "Download your information")
        RefCard(role = GsGlassRole.CardLight) {
            RefSheetRow(
                title = "Export Conversations",
                subtitle = "Download your chat history, messages, and attachment details.",
                onClick = { save(DataExport.Scope.CONVERSATIONS) },
                leading = { RefWellTile(GsIcons.CloudDownload, GsWellTone.Lilac, 34.dp) },
                trailing = { RefActionPill("Export") },
            )
            RefRowDivider()
            RefSheetRow(
                title = "Personal Data Archive",
                subtitle = "Get a copy of your account data, profile information, and settings.",
                onClick = { save(DataExport.Scope.PERSONAL_ARCHIVE) },
                leading = { RefWellTile(GsIcons.UserRound, GsWellTone.Rose, 34.dp) },
                trailing = { RefActionPill("Export") },
            )
            RefRowDivider(startIndent = 12.dp)

            // The mock's format block: a heading, its whisper and three tiles.
            Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
                Text(
                    "Export Format",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    "Choose your preferred file format for export.",
                    fontSize = 8.5.sp,
                    lineHeight = 11.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DataExport.Format.entries.forEach { option ->
                        ExportFormatTile(
                            format = option,
                            selected = format == option,
                            onClick = { format = option },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            RefRowDivider(startIndent = 12.dp)
            RefSheetRow(
                title = "Download Complete Archive",
                // No mail leaves this build; the file is written where the
                // reader chooses. That is the only honest completion of the
                // reference's "prepared and sent to your email" line.
                subtitle = "Saved to a file you choose on this device. Large exports may take some time.",
                onClick = { save(DataExport.Scope.COMPLETE) },
                leading = { RefWellTile(GsIcons.CloudDownload, GsWellTone.Mint, 34.dp) },
                trailing = { RefActionPill("Save Archive") },
            )
        }

        // ---- Data Retention ------------------------------------------------
        RefGroupHeader("Data Retention", "Control how long we keep your data")
        RefCard(role = GsGlassRole.CardLight) {
            RefSheetRow(
                title = "Data Retention Period",
                subtitle = "Choose how long to keep conversations you have not opened.",
                onClick = { showRetention = true },
                leading = { RefWellTile(GsIcons.Clock, GsWellTone.Blue, 34.dp) },
                trailing = {
                    RefActionPill(
                        "${SettingsStore.dataRetentionMonths} month${if (SettingsStore.dataRetentionMonths == 1) "" else "s"}"
                    )
                },
            )
            RefRowDivider()
            RefSheetToggle(
                title = "Auto-Delete Inactive Data",
                subtitle = "Automatically delete conversations after the retention period.",
                checked = SettingsStore.autoDeleteInactive,
                onCheckedChange = { on ->
                    SettingsStore.updateAutoDeleteInactive(on)
                    if (on) purgeNow()
                },
                leading = { RefWellTile(GsIcons.Trash2, GsWellTone.Lilac, 34.dp) },
            )
            RefRowDivider()
            RefSheetRow(
                title = "Clear Chat History",
                subtitle = "Permanently remove all conversations on this device.",
                onClick = { showClear = true },
                leading = { RefWellTile(GsIcons.MessageCircle, GsWellTone.Peach, 34.dp) },
                trailing = { RefActionPill("Clear History") },
            )
        }

        // ---- Delete Your Account ------------------------------------------
        RefGroupHeader("Delete Your Account", "This action is permanent")
        DeleteAccountCard(
            understood = understood,
            busy = busy,
            onUnderstoodChange = { understood = it },
            onDelete = { showDelete = true },
        )
        Spacer(Modifier.height(10.dp))
    }

    if (showRetention) {
        RetentionPeriodDialog(
            selected = SettingsStore.dataRetentionMonths,
            onSelect = { months ->
                SettingsStore.updateDataRetentionMonths(months)
                showRetention = false
                if (SettingsStore.autoDeleteInactive) purgeNow()
            },
            onDismiss = { showRetention = false },
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("Clear chat history?") },
            text = {
                Text(
                    "Every conversation and message on this device will be removed, along with staged attachment copies. Library saves stay. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClear = false
                    scope.launch {
                        runCatching { DataRetention.clearChatHistory() }
                            .onSuccess { Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, "Could not clear chat history", Toast.LENGTH_SHORT).show() }
                    }
                }) { Text("Clear history") }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("Cancel") }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { if (!busy) showDelete = false },
            title = { Text("Delete everything on this device?") },
            text = {
                Text(
                    "Your conversations, saved items, attachment files and account details will be removed, and you'll be signed out. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { DataRetention.deleteAllContent() }
                                .onSuccess { onAccountDeleted() }
                                .onFailure {
                                    busy = false
                                    showDelete = false
                                    Toast.makeText(
                                        context,
                                        "Could not delete everything",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                        }
                    },
                ) { Text(if (busy) "Deleting…" else "Delete account") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showDelete = false }) { Text("Cancel") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

/**
 * The mock's lilac action pill: content-sized, ~19pt tall, accent ink and a
 * chevron. Drawn as a label because the ROW owns the click; two nested touch
 * targets inside one 44pt row would split the same action in two.
 */
@Composable
private fun RefActionPill(label: String, modifier: Modifier = Modifier) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    // The mock's pills are paper-white with a lavender cast, not a saturated
    // accent fill. Blending 10% accent into white gives that paper under every
    // accent family; dark keeps the translucent wash instead of a bright chip.
    val fill = if (dark) colors.accentSoft else lerp(Color.White, colors.accent, 0.10f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(fill.copy(alpha = if (dark) 1f else 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GsFonts.Product,
            color = colors.accentStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(3.dp))
        Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = colors.accentStrong.copy(alpha = 0.70f),
            modifier = Modifier.size(10.dp),
        )
    }
}

/** One of the mock's three format tiles: title, description, selection tick. */
@Composable
private fun ExportFormatTile(
    format: DataExport.Format,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GsTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val dark = LocalGsIsDark.current
    val selectedFill = if (dark) colors.accentSoft else lerp(Color.White, colors.accent, 0.10f)
    Column(
        modifier = modifier
            .testTag("export-format-${format.id}")
            .heightIn(min = 42.dp)
            .clip(shape)
            .background(
                if (selected) {
                    selectedFill
                } else {
                    Color.White.copy(alpha = if (LocalGsIsDark.current) 0.10f else 0.42f)
                }
            )
            .border(
                1.dp,
                if (selected) colors.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.62f),
                shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                format.label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                Box(
                    Modifier
                        .size(13.dp)
                        .background(colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        GsIcons.Check,
                        contentDescription = "Selected",
                        tint = colors.onAccent,
                        modifier = Modifier.size(8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            format.caption,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontFamily = GsFonts.Product,
            color = colors.textTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The warning card. It is deliberately NOT RefCard(light): the mock washes the
 * whole card rose, then nests a second paper card for the consent gate and the
 * full-width destructive action. The colour is the source art's, with a dark
 * variant that keeps the same red signal instead of a light card on black.
 */
@Composable
private fun DeleteAccountCard(
    understood: Boolean,
    busy: Boolean,
    onUnderstoodChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    val shape = RoundedCornerShape(22.dp)
    val wash = if (dark) {
        Brush.verticalGradient(listOf(colors.error.copy(alpha = 0.18f), colors.error.copy(alpha = 0.10f)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFDF1F2), Color(0xFFFBE3E8)))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(wash)
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.18f else 0.62f), shape)
            .padding(10.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.65f),
                                if (dark) colors.error.copy(alpha = 0.20f) else Color(0xFFFBE0E2),
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GsIcons.AlertTriangle,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Permanently Delete Account",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = colors.error,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    "This will remove your conversations, saved items, files and account details from this device, and sign you out. This action cannot be undone.",
                    fontSize = 8.sp,
                    lineHeight = 10.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = if (dark) 0.08f else 0.36f))
                .border(1.dp, Color.White.copy(alpha = if (dark) 0.16f else 0.55f), RoundedCornerShape(18.dp))
                .padding(10.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = understood,
                        enabled = !busy,
                        role = Role.Checkbox,
                        onValueChange = onUnderstoodChange,
                    )
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (understood) colors.error else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (understood) colors.error else colors.error.copy(alpha = 0.45f),
                            RoundedCornerShape(4.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (understood) {
                        Icon(
                            GsIcons.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "I understand that this action is permanent and I want to delete my account.",
                    fontSize = 8.sp,
                    lineHeight = 10.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (understood) colors.error else colors.error.copy(alpha = 0.55f)
                    )
                    .clickable(
                        enabled = understood && !busy,
                        role = Role.Button,
                        onClickLabel = "Delete Account",
                        onClick = onDelete,
                    )
                    .semantics {
                        stateDescription = if (understood) "Ready to delete" else "Consent required"
                    }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    GsIcons.Trash2,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Delete Account",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = Color.White.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The retention period chooser — the mock's pill opens this. */
@Composable
private fun RetentionPeriodDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Data Retention Period") },
        text = {
            Column {
                Text(
                    "Conversations untouched for longer than the period are removed when auto-delete is on.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                listOf(3, 6, 12, 24).forEach { months ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == months,
                                role = Role.RadioButton,
                                onClick = { onSelect(months) },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == months, onClick = { onSelect(months) })
                        Spacer(Modifier.width(8.dp))
                        Text("$months months", fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

