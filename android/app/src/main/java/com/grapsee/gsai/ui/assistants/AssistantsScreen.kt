package com.grapsee.gsai.ui.assistants

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.model.AssistantSample
import com.grapsee.gsai.data.model.SampleData
import com.grapsee.gsai.ui.components.GsCard
import com.grapsee.gsai.ui.components.GsChip
import com.grapsee.gsai.ui.components.GsEmptyState
import com.grapsee.gsai.ui.components.GsScreenScaffold
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsMotion

private val segmentTabs = listOf("Marketplace", "My assistants", "Favourites", "Published")

/** Static "ownership" subsets until the repository layer exists. */
private val myAssistantIds = listOf("asst-1", "asst-3", "asst-6")
private val favouriteIds = listOf("asst-2", "asst-4")

@Composable
fun AssistantsScreen(onNavigate: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }

    GsScreenScaffold(
        title = "Assistants",
        actions = {
            IconButton(onClick = { onNavigate(GsRoutes.ASSISTANT_CREATE) }) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Create assistant",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
            ) {
                segmentTabs.forEachIndexed { index, label ->
                    GsChip(text = label, selected = tab == index, onClick = { tab = index })
                }
            }

            val visible = when (tab) {
                1 -> SampleData.assistants.filter { it.id in myAssistantIds }
                2 -> SampleData.assistants.filter { it.id in favouriteIds }
                3 -> SampleData.assistants.filter { it.published }
                else -> SampleData.assistants
            }

            if (tab == 0) {
                val featured = SampleData.assistants.first()
                FeaturedCard(
                    assistant = featured,
                    onClick = { onNavigate(GsRoutes.assistant(featured.id)) }
                )
            }

            if (visible.isEmpty()) {
                GsEmptyState(
                    icon = Icons.Outlined.SmartToy,
                    title = "Nothing here yet",
                    message = "Assistants you create or save will appear in this space."
                )
            } else {
                visible.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { assistant ->
                            AssistantCard(
                                assistant = assistant,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigate(GsRoutes.assistant(assistant.id)) }
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(GsMotion.spaceL))
        }
    }
}

/** Featured marketplace card — flat surface with a 4dp aurora-teal accent bar (no gradient). */
@Composable
private fun FeaturedCard(assistant: AssistantSample, onClick: () -> Unit) {
    GsCard(onClick = onClick) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(GsMotion.spaceM))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(GsMotion.spaceXS)
            ) {
                Text(
                    text = "FEATURED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = assistant.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = assistant.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "★ ${assistant.rating} · ${assistant.uses} uses · ${assistant.category}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AssistantCard(
    assistant: AssistantSample,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GsCard(modifier = modifier, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(GsMotion.spaceS)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = assistant.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = assistant.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "★ ${assistant.rating} · ${assistant.uses} uses",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
