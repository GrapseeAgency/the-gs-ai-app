package com.grapsee.gsai.ui.specimen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.GsGlassRadius
import com.grapsee.gsai.ui.components.GlassFrostedPanel
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.ReferenceBackdrop
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.gsGlass
import com.grapsee.gsai.ui.theme.GsRadius
import com.grapsee.gsai.ui.theme.GsSpacing
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * THE GLASS SPECIMEN — mandate §3, milestone one, in one place.
 *
 * Six surfaces, on the real atmosphere, so the material can be judged against
 * the reference before it is spread across thirty screens:
 *
 *   1. the atmospheric background (the existing [ReferenceBackdrop], static by
 *      design — a breathing background would tax every streaming frame, and the
 *      orb already carries the motion);
 *   2. one standard glass card;
 *   3. one elevated floating control;
 *   4. one modal sheet above a chat;
 *   5. the composer with the keyboard up;
 *   6. one readable chat surface.
 *
 * Nothing here invents colour. Every value comes from [GsTheme.colors], so
 * flipping appearance or accent in Settings re-lights the whole specimen, and a
 * screenshot taken under any accent is still a valid test of the material.
 */
@Composable
fun GlassSpecimenScreen(onBack: () -> Unit) {
    val colors = GsTheme.colors
    var sheetOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. The owned atmosphere. Glass sits on top of this and must respond to it.
        ReferenceBackdrop(modifier = Modifier.matchParentSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GsSpacing.m),
            verticalArrangement = Arrangement.spacedBy(GsSpacing.gap),
        ) {
            Spacer(modifier = Modifier.height(GsSpacing.s))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(label = "Back", onClick = onBack)
                Spacer(modifier = Modifier.width(GsSpacing.gap))
                Text(
                    text = "Glass specimen",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
            }

            // 6. A readable chat surface — the material has to hold legibility.
            ChatBubble(
                text = "Paris, first week of June. I would fly into CDG and stay " +
                    "in the 11th, where the Marais is a tram ride and the food " +
                    "is not tourist-priced.",
                mine = false,
            )
            ChatBubble(
                text = "Check the Louvre on Wednesday — it stays open until nine " +
                    "and the queues halve after six.",
                mine = true,
            )

            // 2. One standard glass card.
            RefCard(role = GsGlassRole.Card) {
                Column(modifier = Modifier.padding(GsSpacing.m)) {
                    Text(
                        text = "Standard card",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(GsSpacing.xs))
                    Text(
                        text = "Cream glass over the bloom. The rim is lit at the " +
                            "top-left and falls away toward the bottom-right, " +
                            "because the reference light sits above and left.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            // 4. The entry to the modal sheet, above the chat.
            RefCard(role = GsGlassRole.Elevated) {
                Column(modifier = Modifier.padding(GsSpacing.m)) {
                    Text(
                        text = "Elevated card",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(GsSpacing.xs))
                    Text(
                        text = "Thicker glass: more airlight added back, a brighter " +
                            "rim, and a wider warm shadow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(GsSpacing.gap))
                    TextButton(label = "Open the sheet", onClick = { sheetOpen = true })
                }
            }

            Spacer(modifier = Modifier.height(GsSpacing.xl))
        }

        // 3. One elevated floating control, clear of the scrolling content.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(GsSpacing.m)
                .size(58.dp)
                .gsGlass(GsGlassRole.FloatingControl, RoundedCornerShape(percent = 50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { sheetOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.titleLarge,
                color = colors.accent,
            )
        }

        // 5. The composer, riding the keyboard rather than fighting it.
        ComposerRow(
            value = draft,
            onChange = { draft = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding(),
        )
    }

    if (sheetOpen) {
        ModalSheet(onDismiss = { sheetOpen = false })
    }
}

/** The everyday message surface — the busiest glass in the product. */
@Composable
private fun ChatBubble(text: String, mine: Boolean) {
    val colors = GsTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = GsSpacing.xs),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .gsGlass(
                    role = if (mine) GsGlassRole.FloatingControl else GsGlassRole.Card,
                    shape = RoundedCornerShape(
                        topStart = GsRadius.card,
                        topEnd = GsRadius.card,
                        bottomStart = if (mine) GsRadius.card else GsRadius.sm,
                        bottomEnd = if (mine) GsRadius.sm else GsRadius.card,
                    ),
                )
                .padding(horizontal = GsSpacing.m, vertical = GsSpacing.gap),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
        }
    }
}

/** Field plus send affordance. The well is glass seen from the shadowed side. */
@Composable
private fun ComposerRow(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val colors = GsTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(GsSpacing.m),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .gsGlass(GsGlassRole.Sunken, RoundedCornerShape(GsRadius.card)),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GsSpacing.m, vertical = GsSpacing.gap),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Ask Nexly anything",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPlaceholder,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/** The modal: the thickest glass in the system, over a scrim that keeps the bloom. */
@Composable
private fun ModalSheet(onDismiss: () -> Unit) {
    val colors = GsTheme.colors
    Box(modifier = Modifier.fillMaxSize()) {
        // The reference never puts an opaque black scrim over the bloom.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )

        // The modal reveals the frosted bloom behind its heaviest glass: a real
        // RenderEffect blur on API 31+, the same soft bloom without the pass on
        // API 26 (mandate b6 / b9), both driven from one call.
        GlassFrostedPanel(
            shape = GsRadius.sheetShape(),
            role = GsGlassRole.Modal,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(GsSpacing.l),
            ) {
                Text(
                    text = "Travel agent",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(GsSpacing.xs))
                Text(
                    text = "The heaviest glass in the system: the most airlight added " +
                        "back, so the transcript behind it lifts rather than shows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(GsSpacing.m))
                TextButton(label = "Close", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun TextButton(label: String, onClick: () -> Unit) {
    val colors = GsTheme.colors
    Box(
        modifier = Modifier
            .gsGlass(GsGlassRole.FloatingControl, RoundedCornerShape(GsGlassRadius.control))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = GsSpacing.m, vertical = GsSpacing.s),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.accent,
            textAlign = TextAlign.Center,
        )
    }
}
