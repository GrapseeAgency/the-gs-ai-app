package com.grapsee.gsai.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.OnboardingProfileStore
import com.grapsee.gsai.ui.auth.capabilityOptions
import com.grapsee.gsai.ui.auth.interestOptions
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefChip
import com.grapsee.gsai.ui.components.RefChoiceRow
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * THE TWO PROFILE ROWS THAT HAD NO EDITOR.
 *
 * "Interests" and "Content Preferences" were static rows: they printed what
 * the wizard had collected and offered nothing. The only way to change either
 * was to re-run the whole onboarding flow from step one — which also re-asks
 * for your name, your tone and your notification choice, and overwrites all
 * four on the way out.
 *
 * This page edits the two collections in place. Each toggle writes its own
 * key, so changing an interest cannot reset the tone picked three screens
 * earlier in the wizard.
 *
 * The copy is careful about what these actually do: they choose the first
 * suggestions and the quick actions on Home. They are not a hidden system
 * prompt, and saying so is cheaper than a reader wondering why "Travel" did
 * not change an answer about tax law.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePersonalizationScreen(
    onBack: () -> Unit,
    /** Render-harness entry points: the real state, driven from outside. */
    initialInterests: Set<String>? = null,
    initialCapabilities: Set<String>? = null,
) {
    val colors = GsTheme.colors
    var interests by remember {
        mutableStateOf(initialInterests ?: OnboardingProfileStore.interests)
    }
    var capabilities by remember {
        mutableStateOf(initialCapabilities ?: OnboardingProfileStore.capabilities)
    }

    RefSettingsPage(
        title = "Personalization",
        subtitle = "What Nexly leads with, and what it does first",
        onBack = onBack,
    ) {
        RefGroupHeader(
            "Interests",
            if (interests.isEmpty()) "Nothing picked" else "${interests.size} picked",
        )
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(14.dp)) {
            Text(
                "I'm mostly here for",
                fontSize = 12.sp,
                fontFamily = GsFonts.Product,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                interestOptions.forEach { topic ->
                    RefChip(
                        text = topic,
                        selected = topic in interests,
                        onClick = {
                            val next = if (topic in interests) interests - topic else interests + topic
                            interests = next
                            OnboardingProfileStore.setInterests(next)
                        },
                    )
                }
            }
            if (interests.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "With nothing picked, Home leads with the six starting tools rather than a guess.",
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = GsFonts.Product,
                    color = colors.textTertiary,
                )
            }
        }

        RefGroupHeader(
            "What Nexly does first",
            if (capabilities.isEmpty()) "Nothing pinned" else "${capabilities.size} pinned",
        )
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(0.dp)) {
            capabilityOptions.forEachIndexed { index, option ->
                if (index > 0) RefDivider(startIndent = 66.dp)
                RefChoiceRow(
                    title = option.title,
                    subtitle = option.caption,
                    icon = option.icon,
                    selected = option.title in capabilities,
                    onClick = {
                        val next = if (option.title in capabilities) {
                            capabilities - option.title
                        } else {
                            capabilities + option.title
                        }
                        capabilities = next
                        OnboardingProfileStore.setCapabilities(next)
                    },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        RefFootnote(
            "These shape the first suggestions and the quick actions on Home. They are not a " +
                "hidden instruction to the model, and they never override an answer.",
        )
        Spacer(Modifier.height(16.dp))
    }
}
