package com.grapsee.gsai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.OnboardingProfileStore
import com.grapsee.gsai.data.ResponseStyle
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark

/**
 * HOW NEXLY ANSWERS — the page behind Profile's dead rows.
 *
 * Profile showed "Tone: Friendly" and "Detail Level: Standard" with no chevron
 * and no editor. Two settings, printed, unchangeable. The values had been
 * written by the setup wizard and never read by anything, so the rows were
 * honest about the value and silent about the fact that changing it was
 * impossible.
 *
 * This page makes all three rows real:
 *  - tone and detail are choices, written through [ResponseStyle], which the
 *    chat turn now reads as non-visible context;
 *  - language is stated, not offered: only English ships in this build, and a
 *    picker with one option is a lie about a choice.
 *
 * The chosen option is a lit row, not a radio button: the accent wash and the
 * tick say "you are here" in the same material as the rest of the app.
 */
@Composable
fun ResponseStyleScreen(onBack: () -> Unit) {
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current

    // Seeded from the store and re-read on every choice, so the page cannot
    // show a value the store does not hold.
    var tone by remember { mutableStateOf(OnboardingProfileStore.personality) }
    var detail by remember { mutableStateOf(OnboardingProfileStore.responseStyle) }

    RefSettingsPage(
        title = "Response Style",
        subtitle = "How Nexly talks to you — and how much it says",
        onBack = onBack,
    ) {
        RefGroupHeader("Tone", "The register, not the facts")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 4.dp)) {
            ResponseStyle.tones.forEachIndexed { index, option ->
                ChoiceRow(
                    title = option,
                    subtitle = ResponseStyle.toneBlurbs[option].orEmpty(),
                    selected = tone == option,
                    testTag = "response-tone-$option",
                    onClick = {
                        tone = option
                        ResponseStyle.setTone(option)
                    },
                )
                if (index != ResponseStyle.tones.lastIndex) RefRowDivider()
            }
        }

        RefGroupHeader("Detail Level", "How much of the reasoning you see")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 4.dp)) {
            ResponseStyle.details.forEachIndexed { index, option ->
                ChoiceRow(
                    title = option,
                    subtitle = ResponseStyle.detailBlurbs[option].orEmpty(),
                    selected = detail == option,
                    testTag = "response-detail-$option",
                    onClick = {
                        detail = option
                        ResponseStyle.setDetail(option)
                    },
                )
                if (index != ResponseStyle.details.lastIndex) RefRowDivider()
            }
        }

        RefGroupHeader("Language", "One language ships in this build")
        RefCard(role = GsGlassRole.CardLight) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RefWellTile(GsIcons.Languages, GsWellTone.Mint, 28.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "English (UK)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Text(
                        "The only language Nexly is built and tested in",
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textTertiary,
                    )
                }
                Icon(
                    GsIcons.CheckCircle2,
                    contentDescription = "In use",
                    tint = colors.accentStrong,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        // What the choice actually does. The footnote is the difference between
        // a preference and a decoration.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (dark) colors.accent.copy(alpha = 0.10f) else colors.accent.copy(alpha = 0.06f)
                )
                .border(
                    0.8.dp,
                    colors.accent.copy(alpha = 0.22f),
                    RoundedCornerShape(18.dp)
                )
                .padding(12.dp)
                .testTag("response-style-note"),
        ) {
            Icon(
                GsIcons.Sparkles,
                contentDescription = null,
                tint = colors.accentStrong,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "This is sent with every new message as hidden context: " +
                    "tone ${ResponseStyle.summary()}. It changes how an answer is " +
                    "written, never what it says — facts, sources and structure " +
                    "are untouched.",
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                fontFamily = GsFonts.Product,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        RefFootnote(
            "Interests and content preferences still live in Profile & Preferences, " +
                "and re-running setup overwrites this page's two choices."
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** One option in a group: a lit row when chosen, clear glass when not. */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = GsTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.RadioButton, onClickLabel = title, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The marker: a ring that fills and lights when chosen.
        Box(
            Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                .border(
                    1.4.dp,
                    if (selected) colors.accentStrong else colors.border.copy(alpha = 0.9f),
                    RoundedCornerShape(999.dp)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    GsIcons.Check,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontFamily = GsFonts.Product,
                color = colors.textPrimary,
            )
            Text(
                subtitle,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
            )
        }
    }
}
