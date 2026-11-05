package com.grapsee.gsai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsMotion
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark

/*
 * THE LAST STOCK SURFACE IN THE APP.
 *
 * Twenty-two confirmations, pickers and "here is the answer" popups across
 * twelve files were still `material3.AlertDialog`: a centred white rectangle
 * with a 28dp radius, a title, a body and two flat text buttons — the exact
 * chrome Compose gives you for free. Everything behind them is glass, so the
 * one moment the reader is asked to decide something was also the one moment
 * the material dropped out.
 *
 * These two composables replace it. One shape for both jobs:
 *
 *  - the panel is a floating slab of glass, not a sheet welded to the bottom
 *    edge and not a rectangle floating in the middle: it sits low, clears the
 *    edges, and throws a real shadow, so it reads as an object above the page;
 *  - it grows out of its own centre on a spring (0.92 → 1) instead of appearing;
 *  - the scrim is thin — a decision needs to see what it is deciding about;
 *  - the confirm action is the app's primary pill, the dismiss is a quiet
 *    text row under it, and a destructive confirm is the only red thing on
 *    screen.
 *
 * [GsDialog] is the yes/no form. [GsChoiceDialog] is the pick-one form, for the
 * places that used a dialog as a menu (the inbox filter, the category filter).
 */

private val DialogShape = RoundedCornerShape(28.dp)

/**
 * The shared modal frame: scrim, spring-in slab, and the reader's insets.
 *
 * [content] is laid out inside the glass, so callers only describe their own
 * words and controls.
 */
@Composable
private fun GsModalFrame(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val progress by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = GsMotion.snappy(),
            label = "gs-dialog-progress"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (if (dark) 0.52f else 0.26f) * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Dismiss",
                    onClick = onDismiss
                )
                .windowInsetsPadding(WindowInsets.safeDrawing),
            // Low on the page, not dead centre: a modal that covers the middle
            // hides the row that caused it.
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = modifier
                    .padding(horizontal = 18.dp, vertical = 26.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.92f + 0.08f * progress
                        scaleY = 0.92f + 0.08f * progress
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .shadow(
                        elevation = 30.dp,
                        shape = DialogShape,
                        ambientColor = colors.glassShadowAmbient,
                        spotColor = colors.glassShadowAmbient
                    )
                    .gsGlass(GsGlassRole.Modal, DialogShape)
                    .clip(DialogShape)
                    // Glass alone is too transparent to carry body copy over a
                    // busy page; the panel gets the dialog surface underneath,
                    // the same way the anchored menus do.
                    .background(colors.dialogSurface.copy(alpha = if (dark) 0.94f else 0.92f))
                    .border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = if (dark) 0.10f else 0.55f),
                        shape = DialogShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                content()
            }
        }
    }
}

/**
 * The bare slab, for the handful of modals whose body is a control rather than
 * a sentence — the quiet-hours window, the on-device download phases.
 *
 * Everything below it in this file is a specific arrangement of this frame; a
 * caller that needs its own arrangement should still sit in the same glass, so
 * it opens with the same spring and the same shadow.
 */
@Composable
fun GsDialogShell(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = GsModalFrame(onDismiss = onDismiss, modifier = modifier, content = content)

/**
 * Yes/no, in glass.
 *
 * [icon] and [tone] paint the well above the title — an icon is what makes a
 * destructive confirmation legible before a single word is read. [destructive]
 * turns the confirm pill into the error role; it is the only sanctioned red.
 */
@Composable
fun GsDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = "Cancel",
    destructive: Boolean = false,
    icon: ImageVector? = null,
    tone: GsWellTone = GsWellTone.Peach,
    /** One line under the buttons — a consequence the title cannot carry. */
    footnote: String? = null,
) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current

    GsModalFrame(onDismiss = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                RefGlyphWell(glyph = "", tone = tone, size = 46.dp, icon = icon)
                Spacer(Modifier.height(14.dp))
            }
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = message,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                fontFamily = GsFonts.Product,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))

            // Confirm — the app's primary pill, at the dialog's own scale.
            val pillShape = RoundedCornerShape(999.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .then(
                        if (!destructive) {
                            Modifier.shadow(
                                elevation = 10.dp,
                                shape = pillShape,
                                ambientColor = colors.accent.copy(alpha = 0.40f),
                                spotColor = colors.accent.copy(alpha = 0.50f)
                            )
                        } else {
                            Modifier.shadow(
                                elevation = 10.dp,
                                shape = pillShape,
                                ambientColor = colors.error.copy(alpha = 0.34f),
                                spotColor = colors.error.copy(alpha = 0.44f)
                            )
                        }
                    )
                    .clip(pillShape)
                    .background(
                        if (destructive) {
                            Brush.verticalGradient(
                                listOf(colors.error, colors.error.copy(alpha = 0.86f))
                            )
                        } else {
                            Brush.verticalGradient(listOf(colors.accentStrong, colors.accent))
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.30f),
                        shape = pillShape
                    )
                    .clickable(role = Role.Button, onClick = { onConfirm(); onDismiss() }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = confirmLabel,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GsFonts.Product,
                    color = Color.White
                )
            }

            if (dismissLabel != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dismissLabel,
                    fontSize = 12.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 11.dp)
                )
            }

            if (footnote != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = footnote,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * A read-only sheet of label/value facts — what this device is, what a session
 * was, what a file weighed.
 *
 * A prose [GsDialog] would flatten a table into a sentence and lose the
 * alignment that makes it scannable, so the pairs keep their own two-column
 * layout inside the same slab.
 */
@Composable
fun GsFactDialog(
    title: String,
    facts: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Done",
    icon: ImageVector? = null,
    tone: GsWellTone = GsWellTone.Blue,
    footnote: String? = null,
) {
    val colors = GsTheme.colors

    GsModalFrame(onDismiss = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            if (icon != null) {
                RefGlyphWell(glyph = "", tone = tone, size = 44.dp, icon = icon)
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                facts.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = label,
                            modifier = Modifier.width(112.dp),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = value,
                            modifier = Modifier.weight(1f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = dismissLabel,
                // Compose has no vertical text alignment; padding the line to
                // the pill's height keeps it optically centred.
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.textPrimary.copy(alpha = 0.06f))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(vertical = 13.dp),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            if (footnote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = footnote,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The ask-for-one-line form: a rename, a name, a subject.
 *
 * Same slab as [GsDialog], with the field inside it. The keyboard's done action
 * submits, so a reader who types and presses enter is finished — the old
 * dialogs made them find a button afterwards.
 */
@Composable
fun GsPromptDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    placeholder: String = "",
    confirmLabel: String = "Save",
) {
    val colors = GsTheme.colors
    var draft by remember { mutableStateOf(initial) }
    val submit: () -> Unit = {
        if (draft.isNotBlank()) {
            onConfirm(draft.trim())
            onDismiss()
        }
    }

    GsModalFrame(onDismiss = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary
            )
            if (message != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = message,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary
                )
            }
            Spacer(Modifier.height(12.dp))
            RefTextField(
                value = draft,
                onValueChange = { draft = it },
                label = label,
                placeholder = placeholder,
                fieldTestTag = "gs-prompt-field",
                onDone = submit,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cancel",
                    fontSize = 12.5.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.verticalGradient(listOf(colors.accentStrong, colors.accent))
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
                        .clickable(role = Role.Button, onClick = submit),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = confirmLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/** One line in [GsChoiceDialog]. */
data class GsChoice(
    val id: String,
    val label: String,
    val detail: String? = null,
    val glyph: String? = null,
    val tone: GsWellTone = GsWellTone.Peach,
    val selected: Boolean = false,
)

/**
 * Pick-one, in glass.
 *
 * This is the form the inbox filter and the help category filter needed: they
 * were menus wearing a dialog's clothes. Each option is a row with its own
 * well, the current one is washed with the accent and carries a tick, and the
 * panel closes the moment a choice is made — no "Close" button to press after
 * the work is already done.
 */
@Composable
fun GsChoiceDialog(
    title: String,
    options: List<GsChoice>,
    onDismiss: () -> Unit,
    onSelect: (GsChoice) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = GsTheme.colors

    GsModalFrame(onDismiss = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = 10.5.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textTertiary
                        )
                    }
                }
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        GsIcons.X,
                        contentDescription = "Close",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                options.forEach { option ->
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(
                                if (option.selected) {
                                    colors.accent.copy(alpha = 0.10f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable(role = Role.Button) { onSelect(option) }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (option.glyph != null) {
                            RefGlyphWell(glyph = option.glyph, tone = option.tone, size = 30.dp)
                        } else {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.accent.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    GsIcons.Check,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                fontSize = 12.sp,
                                fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Medium,
                                fontFamily = GsFonts.Product,
                                color = colors.textPrimary
                            )
                            if (option.detail != null) {
                                Text(
                                    text = option.detail,
                                    fontSize = 10.5.sp,
                                    fontFamily = GsFonts.Product,
                                    color = colors.textTertiary
                                )
                            }
                        }
                        if (option.selected) {
                            Icon(
                                GsIcons.CheckCircle2,
                                contentDescription = "Selected",
                                tint = colors.accentStrong,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The old name, kept because twelve files already call it: a confirmation is
 * [GsDialog] with a destructive flag, nothing more.
 */
@Composable
fun GsConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel",
    footnote: String? = null,
) {
    GsDialog(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        dismissLabel = dismissLabel,
        destructive = destructive,
        icon = if (destructive) GsIcons.Trash else GsIcons.CheckCircle2,
        tone = if (destructive) GsWellTone.Rose else GsWellTone.Mint,
        footnote = footnote,
    )
}

/** Kept for the compact menu surface that already ships. */
@Composable
fun GsMenuSurface(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = GsTheme.colors.elevatedSurface,
        tonalElevation = 3.dp,
        content = content,
    )
}
