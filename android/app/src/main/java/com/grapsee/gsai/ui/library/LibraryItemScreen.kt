package com.grapsee.gsai.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.local.SavedItemEntity
import com.grapsee.gsai.di.ServiceLocator
import com.grapsee.gsai.ui.components.GsConfirmDialog
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefInfoCard
import com.grapsee.gsai.ui.components.RefInlinePill
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.launch

/**
 * ONE SAVED ITEM — the page the list's 72-character rows point at.
 *
 * The list can only show a fragment, and a fragment is useless for the thing
 * people save: a whole answer, a whole draft. So the item page is the text at
 * full length, selectable, with the two things you can do to a saved thing —
 * copy it, or let it go.
 *
 * Delete is the only destructive action in the library and it asks first. It
 * removes the row and returns to the list, which is already observing the
 * table, so the day group shrinks without a refresh.
 */
@Composable
fun LibraryItemScreen(
    itemId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit = onBack,
) {
    val colors = GsTheme.colors
    val scope = rememberCoroutineScope()
    val dao = ServiceLocator.db.savedItemDao()
    val clipboard = LocalClipboardManager.current

    var item by remember(itemId) { mutableStateOf<SavedItemEntity?>(null) }
    var loaded by remember(itemId) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        item = dao.getById(itemId)
        loaded = true
    }

    val saved = item
    val savedOn = saved?.let { LibraryModel.shortDate(it.createdAt) }.orEmpty()

    RefSettingsPage(
        title = if (saved != null) LibraryModel.kindLabel(saved.kind) else "Saved item",
        subtitle = when {
            saved == null -> null
            savedOn.isBlank() -> "Kept on this device"
            else -> "Saved $savedOn · on this device"
        },
        onBack = onBack,
    ) {
        when {
            !loaded -> Unit
            saved == null -> missingCard(onBack = onBack)
            else -> {
                RefCard(role = GsGlassRole.CardLight) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KindBadge(saved.kind)
                            Spacer(Modifier.weight(1f))
                            // The header already carries "Saved 5 Nov"; the
                            // card repeats the date only when it is missing
                            // up there, which happens for an unreadable stamp.
                            if (savedOn.isBlank()) {
                                Text(
                                    "Date unreadable",
                                    fontSize = 10.5.sp,
                                    fontFamily = GsFonts.Product,
                                    color = colors.textTertiary,
                                )
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(
                            saved.title.ifBlank { LibraryModel.kindLabel(saved.kind) },
                            fontSize = 15.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GsFonts.Product,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.divider),
                        )
                        Spacer(Modifier.height(11.dp))
                        // Selectable: the reason to open this page is to take
                        // the text somewhere else, and a long-press that
                        // copies beats a button for the common case.
                        SelectionContainer {
                            Text(
                                saved.content,
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                fontFamily = GsFonts.Product,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                RefGroupHeader("Actions")
                RefCard(role = GsGlassRole.CardLight) {
                    RefNavRow(
                        title = if (copied) "Copied" else "Copy text",
                        subtitle = if (copied) "It's on your clipboard" else "Take the whole thing with you",
                        onClick = {
                            clipboard.setText(AnnotatedString(saved.content))
                            copied = true
                        },
                        leading = {
                            RefWellTile(
                                if (copied) GsIcons.Check else GsIcons.Copy,
                                if (copied) GsWellTone.Mint else GsWellTone.Blue,
                                28.dp,
                            )
                        },
                    )
                    RefRowDivider()
                    RefNavRow(
                        title = "Delete from library",
                        subtitle = "Remove it from this device for good",
                        onClick = { confirmDelete = true },
                        leading = { RefWellTile(GsIcons.Trash2, GsWellTone.Rose, 28.dp) },
                        danger = true,
                    )
                }

                Spacer(Modifier.height(14.dp))
                RefInfoCard(
                    text = "Deleting here removes the saved copy only. The conversation " +
                        "it came from is untouched.",
                    icon = GsIcons.Bookmark,
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (confirmDelete && saved != null) {
        GsConfirmDialog(
            title = "Delete this saved item?",
            message = "\u201C" + LibraryModel.snippet(saved.content, 60) + "\u201D leaves this " +
                "device. It cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    dao.delete(saved.id)
                    onDeleted()
                }
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
            footnote = "The conversation it came from stays exactly where it is.",
        )
    }
}

@Composable
private fun KindBadge(kind: String) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.accentSoft.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            libraryGlyph(kind),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            LibraryModel.kindLabel(kind),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = GsFonts.Product,
            color = colors.accent,
        )
    }
}

/** The row was deleted on another screen, or the link is stale. Say so —
 *  a blank page under a header is the abnormal state, not a rare one. */
@Composable
private fun missingCard(onBack: () -> Unit) {
    val colors = GsTheme.colors
    RefCard(role = GsGlassRole.CardLight) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RefWellTile(GsIcons.BookmarkX, GsWellTone.Rose, 46.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                "This item is gone",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "It was deleted from the library, or it was saved on another device. " +
                    "Saved items live on the device that kept them.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            RefInlinePill(label = "Back to library", icon = GsIcons.ArrowLeft) { onBack() }
        }
    }
}
