package com.grapsee.gsai.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.refMotion
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.launch

/**
 * WELCOME — the three-page introduction.
 *
 * The reference's first impression is not a form. It is a held breath: one
 * illustration, one sentence, one weighty button. Everything else (the skip,
 * the page marks) is deliberately quiet so the button is the only thing that
 * competes for the thumb.
 *
 * Structure per page: illustration → eyebrow → title → supporting line. The
 * pages share geometry exactly, so swiping reads as the same page changing
 * its mind, not as three different screens. Pages are swipeable *and* the
 * button advances, because a consumer app should never make someone find the
 * gesture before they can continue.
 */

private data class WelcomePage(
    val art: RefArt,
    val eyebrow: String,
    val title: String,
    val body: String,
)

private val WELCOME_PAGES = listOf(
    WelcomePage(
        art = RefArt.Bloom,
        eyebrow = "Welcome to GS",
        title = "A calmer way to think.",
        body = "Ask in your own words. GS listens, reasons, and answers in whatever shape fits the question."
    ),
    WelcomePage(
        art = RefArt.Conversation,
        eyebrow = "Answers you can touch",
        title = "Not just text.",
        body = "Charts, tables, timelines, maps and working tools — rendered inside the conversation, and interactive down to the last data point."
    ),
    WelcomePage(
        art = RefArt.Privacy,
        eyebrow = "Yours alone",
        title = "Private by default.",
        body = "Choose a cloud model, or run one entirely on this device. Your conversations stay yours, and nothing leaves without asking."
    )
)

@Composable
fun ReferenceWelcomeScreen(onExplore: () -> Unit, onSkip: () -> Unit) {
    val colors = GsTheme.colors
    val pager = rememberPagerState(pageCount = { WELCOME_PAGES.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == WELCOME_PAGES.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // The skip sits alone in the top-right, in tertiary ink: present for
        // the returning reader, invisible to the first-timer.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Skip",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textTertiary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "Skip introduction",
                        onClick = onSkip
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) { index ->
            WelcomePageBody(WELCOME_PAGES[index])
        }

        PageMarks(count = WELCOME_PAGES.size, selected = pager.currentPage)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 18.dp, bottom = 20.dp)
        ) {
            RefPrimaryButton(
                label = if (last) "Get started" else "Continue",
                onClick = {
                    if (last) {
                        onExplore()
                    } else {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                }
            )
            if (last) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "You can change every choice later in Settings.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun WelcomePageBody(page: WelcomePage) {
    val colors = GsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The illustration sits on its own halo rather than in a card — a
        // framed illustration would read as a screenshot, not as a companion.
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(250.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                colors.aurora.first().copy(alpha = 0.55f),
                                colors.aurora.first().copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .size(206.dp)
                    .clip(CircleShape)
                    .border(1.dp, colors.glassBorder.copy(alpha = 0.7f), CircleShape)
                    .background(colors.glassSurface.copy(alpha = 0.35f))
            )
            RefArtwork(
                art = page.art,
                size = 170.dp,
                contentDescription = page.title
            )
        }

        Spacer(Modifier.height(22.dp))

        Text(
            text = page.eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp
            ),
            color = colors.accentStrong
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.displaySmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                // Announced as a heading when the page settles.
                contentDescription = "${page.title}. ${page.body}"
            }
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/** Three marks: the current one stretches into a short warm bar. */
@Composable
private fun PageMarks(count: Int, selected: Int) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val active = index == selected
            val width by animateFloatAsState(
                targetValue = if (active) 26f else 7f,
                animationSpec = if (refMotion()) tween(260) else tween(0),
                label = "pageMarkWidth"
            )
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .width(width.dp)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(if (active) colors.accent else colors.textTertiary.copy(alpha = 0.28f))
            )
        }
    }
}
