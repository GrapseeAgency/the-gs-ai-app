package com.grapsee.gsai.ui.agents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefIconTile
import com.grapsee.gsai.ui.components.RefPage
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.RefTextField
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsTheme

private data class TravelIdea(val title: String, val prompt: String)

private val travelIdeas = listOf(
    TravelIdea(
        "A slow week somewhere warm",
        "Plan a slow seven-day trip somewhere warm. Ask me my budget and preferred pace, then build a day-by-day plan with one good neighbourhood and two day trips."
    ),
    TravelIdea(
        "A calm trip with family",
        "Help me plan a calm family trip. Ask ages and how far we'll travel, then suggest three places with short transfers, easy food, and a rest day between activities."
    ),
    TravelIdea(
        "A work trip that leaves room to look around",
        "I have a work trip coming up. Ask me the city and the meeting hours, then build an itinerary that protects focus time and adds one good walk each evening."
    ),
    TravelIdea(
        "Compare two cities honestly",
        "Compare two cities for a four-night break. Ask me the month and budget, then use a table for cost, weather, walkability and the one thing each city does better."
    )
)

/**
 * Travel agent — a real door into chat, not a static brochure.
 *
 * The old screen used two legacy PNG orbs and four cards whose actions all
 * sent nearly the same sentence. This version is the reference language: one
 * hand-drawn scene, four distinct prompts, and a composer that sends the words
 * the person actually typed.
 */
@Composable
fun TravelAgentScreen(onBack: () -> Unit, onSendToChat: (String) -> Unit) {
    var brief by remember { mutableStateOf("") }

    RefPage(
        title = "Travel agent",
        subtitle = "Plans, routes and honest trade-offs.",
        onBack = onBack
    ) {
        RefCard(padding = androidx.compose.foundation.layout.PaddingValues(22.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RefArtwork(art = RefArt.Bloom, size = 120.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Where are we going?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = GsTheme.colors.textPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tell me the month, the budget and who's coming. I'll ask what I still need, then shape the days around how you actually travel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GsTheme.colors.textSecondary
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "Start with a brief",
            style = MaterialTheme.typography.titleSmall,
            color = GsTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        RefTextField(
            value = brief,
            onValueChange = { brief = it },
            label = "Your trip",
            placeholder = "e.g. Lisbon in April, four nights, two of us, medium budget",
            singleLine = false,
            minLines = 3,
            imeAction = ImeAction.Done,
            onDone = {
                val prompt = brief.trim().ifBlank { "Help me plan a thoughtful trip." }
                onSendToChat(prompt)
            }
        )
        Spacer(Modifier.height(10.dp))
        RefPrimaryButton(
            label = "Plan with GS",
            icon = GsIcons.ArrowUp,
            onClick = {
                val prompt = brief.trim().ifBlank {
                    "Help me plan a thoughtful trip. Ask me where, when, who's coming and the budget."
                }
                onSendToChat(prompt)
            }
        )

        Spacer(Modifier.height(22.dp))
        Text(
            text = "Or borrow a starting point",
            style = MaterialTheme.typography.titleSmall,
            color = GsTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        RefCard {
            travelIdeas.forEachIndexed { index, idea ->
                if (index > 0) RefDivider()
                IdeaRow(idea) { onSendToChat(idea.prompt) }
            }
        }

        Spacer(Modifier.height(20.dp))
        RefCard {
            CapabilityRow(GsIcons.Compass, "Itineraries that breathe", "One anchor a day, not eight stops and a sprint.")
            RefDivider()
            CapabilityRow(GsIcons.Globe, "Routes and neighbourhoods", "Walk times, transfers and where to stay for the trip you want.")
            RefDivider()
            CapabilityRow(GsIcons.Gauge, "Budgets you can read", "A table with the real trade-offs, not a single invented total.")
            RefDivider()
            CapabilityRow(GsIcons.FileText, "A plan you can keep", "Ask for a day-by-day table, a packing list, or a map with pins.")
        }
        Text(
            text = "Travel details change. Verify opening hours, visas and prices before you book.",
            style = MaterialTheme.typography.labelSmall,
            color = GsTheme.colors.textTertiary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 16.dp)
        )
    }
}

@Composable
private fun IdeaRow(idea: TravelIdea, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = idea.title,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RefIconTile(GsIcons.Compass)
        Text(
            text = idea.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = GsTheme.colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            GsIcons.ChevronRight,
            contentDescription = null,
            tint = GsTheme.colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CapabilityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    caption: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RefIconTile(icon, tint = GsTheme.colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = GsTheme.colors.textPrimary
            )
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = GsTheme.colors.textSecondary
            )
        }
    }
}
