package com.grapsee.gsai.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.GsWellTone
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefGroupHeader
import com.grapsee.gsai.ui.components.RefNavRow
import com.grapsee.gsai.ui.components.RefRowDivider
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefSheetTile
import com.grapsee.gsai.ui.components.RefTagPill
import com.grapsee.gsai.ui.components.RefWellTile
import com.grapsee.gsai.ui.components.TileCorner
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.navigation.GsRoutes
import com.grapsee.gsai.ui.theme.GsFonts
import com.grapsee.gsai.ui.theme.GsTheme
import com.grapsee.gsai.ui.theme.LocalGsIsDark

/**
 * ONE HELP ARTICLE, READ PROPERLY.
 *
 * A help topic used to open a `material3.AlertDialog`: one paragraph in a
 * rectangle, clipped at the dialog's maximum height, with the two ways of
 * reaching a person demoted to flat text buttons. That is a tooltip, not an
 * article.
 *
 * This page gives the answer room:
 *  - the question, its category and the one-line summary at the top;
 *  - "The short answer", at reading size;
 *  - the same answer split into numbered steps, because a paragraph describing
 *    a four-tap flow is a puzzle;
 *  - "Read next", which is the article's own sub-page: related topics open
 *    another article, so help is a graph rather than a leaf;
 *  - two real ways out — email support, or ask Nexly.
 */
@Composable
fun HelpArticleScreen(
    topicId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = GsTheme.colors
    val dark = LocalGsIsDark.current
    val article = HelpCatalog.byId(topicId)

    if (article == null) {
        // A stale deep link, or a topic that was removed. Say it, and give the
        // reader the index rather than a blank page.
        RefSettingsPage(
            title = "Help",
            subtitle = "That article is no longer in this build",
            onBack = onBack,
        ) {
            RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(18.dp)) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RefWellTile(GsIcons.BookOpen, GsWellTone.Peach, 40.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Help topics change between versions.",
                        fontSize = 12.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.TextButton(onClick = { onNavigate(GsRoutes.SETTINGS_HELP) }) {
                        Text("Open Help & Support", fontFamily = GsFonts.Product, fontSize = 12.sp)
                    }
                }
            }
        }
        return
    }

    RefSettingsPage(
        title = "Help",
        subtitle = article.category.label,
        onBack = onBack,
    ) {
        // ---- The question ------------------------------------------------
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                RefWellTile(article.icon, article.tone, 38.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = article.question,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GsFonts.Product,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(5.dp))
                    RefTagPill(article.kicker, article.tone.ink)
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = article.summary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = GsFonts.Product,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        // ---- The short answer --------------------------------------------
        RefGroupHeader("The short answer")
        RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(15.dp)) {
            Text(
                text = article.answer,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                fontFamily = GsFonts.Product,
                color = colors.textSecondary,
            )
        }

        // ---- Steps --------------------------------------------------------
        if (article.steps.isNotEmpty()) {
            RefGroupHeader("Step by step", "In the order you do them")
            RefCard(role = GsGlassRole.CardLight, padding = PaddingValues(vertical = 6.dp)) {
                article.steps.forEachIndexed { index, step ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .background(
                                    if (dark) {
                                        article.tone.ink.copy(alpha = 0.18f)
                                    } else {
                                        article.tone.wash
                                    },
                                    CircleShape,
                                )
                                .border(0.8.dp, article.tone.ink.copy(alpha = 0.28f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GsFonts.Product,
                                color = article.tone.ink,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = step,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = GsFonts.Product,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (index != article.steps.lastIndex) RefRowDivider()
                }
            }
        }

        // ---- Read next ----------------------------------------------------
        val related = HelpCatalog.related(article)
        if (related.isNotEmpty()) {
            RefGroupHeader("Read next", "Other answers that touch this one")
            RefCard(role = GsGlassRole.CardLight) {
                related.forEachIndexed { index, next ->
                    RefNavRow(
                        title = next.question,
                        subtitle = next.summary,
                        onClick = { onNavigate(GsRoutes.helpArticle(next.id)) },
                        leading = { RefWellTile(next.icon, next.tone, 28.dp) },
                    )
                    if (index != related.lastIndex) RefRowDivider()
                }
            }
        }

        // ---- Still need a person ------------------------------------------
        RefGroupHeader("Still need help?", "A human answers this address")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RefSheetTile(
                title = "Email support",
                caption = "About this article",
                icon = GsIcons.Mail,
                tone = GsWellTone.Peach,
                corner = TileCorner.BottomChevron,
                centered = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    mailSupport(
                        context,
                        "Help: ${article.question}",
                        "I read \"${article.question}\" and still need help.\n\nWhat I tried:\n",
                    )
                },
            )
            RefSheetTile(
                title = "Ask Nexly",
                caption = "Opens a new chat",
                icon = GsIcons.MessageCircle,
                tone = GsWellTone.Blue,
                corner = TileCorner.BottomChevron,
                centered = true,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(GsRoutes.chat(null, article.question)) },
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .testTag("help-article-footer"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                GsIcons.Heart,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "Written for this build, not a generic FAQ.",
                fontSize = 10.5.sp,
                fontFamily = GsFonts.Product,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
