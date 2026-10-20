package com.grapsee.gsai.ui.chat.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.ui.theme.GsTheme

/**
 * CHART INTERACTIVITY — the one contract every chart honours.
 *
 * A chart in this product is a *measurement you can read*, not a picture of a
 * measurement. Tapping a bar, a point, a slice or a cell resolves the tap to a
 * datum and reports it in a fixed readout line under the plot. The readout is
 * a sibling of the canvas, never an overlay: an overlay that sits on top of
 * the data hides the thing the reader just tapped, and a floating tooltip on a
 * phone is a finger-sized hole in the chart.
 *
 * The probe is deliberately tiny and shared:
 *  · [ChartDatum] is what a tap resolved to — label, value, series, and an
 *    optional share for pie/donut slices.
 *  · [ChartProbe] holds the current selection. Tapping the same datum again
 *    clears it (a second tap means "never mind", not "again").
 *  · [ChartInspectBar] renders the hint before the first tap and the datum
 *    after it. Charts reserve the row permanently, so selecting never shifts
 *    the layout under the reader's thumb.
 */
internal data class ChartDatum(
    val label: String,
    val value: Float,
    val series: String? = null,
    val share: Float? = null,
    val index: Int = -1
)

internal class ChartProbe {
    var datum by mutableStateOf<ChartDatum?>(null)
        private set

    fun select(next: ChartDatum) {
        datum = if (datum == next) null else next
    }

    fun clear() {
        datum = null
    }
}

@Composable
internal fun rememberChartProbe(): ChartProbe = remember { ChartProbe() }

/**
 * The readout line. [hint] is the discoverability sentence ("Tap a bar to
 * inspect") — it disappears once a datum is selected, which is the moment the
 * reader has learned the chart is live.
 */
@Composable
internal fun ChartInspectBar(
    probe: ChartProbe,
    unit: String,
    hint: String,
    accent: Color
) {
    val colors = GsTheme.colors
    val datum = probe.datum
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = datum != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                val label = datum?.label.orEmpty()
                if (label.isNotBlank()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(96.dp)
                    )
                }
                if (!datum?.series.isNullOrBlank() && datum?.series != label) {
                    Text(
                        text = datum?.series.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(76.dp)
                    )
                }
                Text(
                    text = buildString {
                        append(axisLabel(datum?.value ?: 0f, unit))
                        datum?.share?.let { append(" · ${(it * 100).toInt()}%") }
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1
                )
            }
        }
        AnimatedVisibility(
            visible = datum == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
