package com.grapsee.gsai.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * GsIcons — the single icon family for GS AI (design phase 2).
 *
 * Source: Lucide (ISC license), pinned lucide-static 0.469.0. Geometry is vendored
 * at generation time by scripts/gen_gs_icons.py and committed here, so the
 * app has NO runtime dependency on the package. Every icon is a 24x24 stroke
 * drawing with width 2 and round caps/joins — one optical weight across the
 * app, per the icon-system rule (one family, one weight, one container spec).
 *
 * The set is CURATED: only icons rendered by shipped screens are included.
 * Add to scripts/gen_gs_icons.py ICONS and regenerate; never hand-edit paths.
 *
 * Lucide ISC license: https://lucide.dev/license
 */

public object GsIcons {

    public val PanelLeft: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.PanelLeft",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M5,3 H19 A2,2 0 0 1 21,5 V19 A2,2 0 0 1 19,21 H5 A2,2 0 0 1 3,19 V5 A2,2 0 0 1 5,3 Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M9 3v18"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3,11 a8,8 0 1,0 16,0 a8,8 0 1,0 -16,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m21 21-4.3-4.3"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Plus",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M5 12h14"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 5v14"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val MessageCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.MessageCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M7.9 20A9 9 0 1 0 4 16.1L2 22Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Inbox: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Inbox",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M22,12 L16,12 L14,15 L10,15 L8,12 L2,12"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m9 18 6-6-6-6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val ChevronDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.ChevronDown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m6 9 6 6 6-6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val ChevronUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.ChevronUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m18 15-6-6-6 6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val ChevronLeft: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.ChevronLeft",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m15 18-6-6 6-6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val UserRound: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.UserRound",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M7,8 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M20 21a8 8 0 0 0-16 0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M9,12 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Bell: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Bell",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M10.268 21a2 2 0 0 0 3.464 0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val CreditCard: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.CreditCard",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M4,5 H20 A2,2 0 0 1 22,7 V17 A2,2 0 0 1 20,19 H4 A2,2 0 0 1 2,17 V7 A2,2 0 0 1 4,5 Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Folder: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Bookmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Bookmark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Compass: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Compass",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2,12 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val SquarePen: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.SquarePen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Layers: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Layers",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val BookOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.BookOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 7v14"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val UsersRound: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.UsersRound",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M18 21a8 8 0 0 0-16 0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M5,8 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M22 20c0-3.37-2-6.5-4-8a5 5 0 0 0-.45-8.3"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val AudioLines: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.AudioLines",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2 10v3"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M6 6v11"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M10 3v18"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M14 8v7"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M18 5v13"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M22 10v3"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val EllipsisVertical: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.EllipsisVertical",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M11,12 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M11,5 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M11,19 a1,1 0 1,0 2,0 a1,1 0 1,0 -2,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val History: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.History",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3 3v5h5"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 7v5l4 2"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val ArrowUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.ArrowUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m5 12 7-7 7 7"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 19V5"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Mic: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Mic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M19 10v2a7 7 0 0 1-14 0v-2"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Camera: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Camera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M9,13 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val ImagePlus: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.ImagePlus",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M16 5h6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M19 2v6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M7,9 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Paperclip: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Paperclip",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M13.234 20.252 21 12.3"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m16 6-8.414 8.586a2 2 0 0 0 0 2.828 2 2 0 0 0 2.828 0l8.414-8.586a4 4 0 0 0 0-5.656 4 4 0 0 0-5.656 0l-8.415 8.585a6 6 0 1 0 8.486 8.486"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Copy: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Copy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M10,8 H20 A2,2 0 0 1 22,10 V20 A2,2 0 0 1 20,22 H10 A2,2 0 0 1 8,20 V10 A2,2 0 0 1 10,8 Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M20 6 9 17l-5-5"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Trash2: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Trash2",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3 6h18"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Archive: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Archive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3,3 H21 A1,1 0 0 1 22,4 V7 A1,1 0 0 1 21,8 H3 A1,1 0 0 1 2,7 V4 A1,1 0 0 1 3,3 Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M10 12h4"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Pin: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Pin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 17v5"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val X: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.X",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M18 6 6 18"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m6 6 12 12"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val RefreshCw: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.RefreshCw",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M21 3v5h-5"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M8 16H3v5"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Share: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Share",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M16,6 L12,2 L8,6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Globe: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Globe",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2,12 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2 12h20"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val FileText: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.FileText",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M14 2v4a2 2 0 0 0 2 2h4"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M10 9H8"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M16 13H8"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M16 17H8"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Code: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Code",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M16,18 L22,12 L16,6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M8,6 L2,12 L8,18"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Star: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Star",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Clock: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Clock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2,12 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12,6 L12,12 L16,14"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val TextSearch: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.TextSearch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M21 6H3"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M10 12H3"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M10 18H3"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M14,15 a3,3 0 1,0 6,0 a3,3 0 1,0 -6,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m21 19-1.9-1.9"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Quote: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Quote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M16 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v2a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M5 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v2a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Table: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Table",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 3v18"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M5,3 H19 A2,2 0 0 1 21,5 V19 A2,2 0 0 1 19,21 H5 A2,2 0 0 1 3,19 V5 A2,2 0 0 1 5,3 Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3 9h18"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3 15h18"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val List: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.List",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3 12h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3 18h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3 6h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M8 12h13"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M8 18h13"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M8 6h13"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Zap: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Zap",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Image: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Image",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M5,3 H19 A2,2 0 0 1 21,5 V19 A2,2 0 0 1 19,21 H5 A2,2 0 0 1 3,19 V5 A2,2 0 0 1 5,3 Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M7,9 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val PenLine: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.PenLine",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 20h9"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M16.376 3.622a1 1 0 0 1 3.002 3.002L7.368 18.635a2 2 0 0 1-.855.506l-2.872.838a.5.5 0 0 1-.62-.62l.838-2.872a2 2 0 0 1 .506-.854z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Brain: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Brain",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M15 13a4.5 4.5 0 0 1-3-4 4.5 4.5 0 0 1-3 4"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M17.599 6.5a3 3 0 0 0 .399-1.375"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M6.003 5.125A3 3 0 0 0 6.401 6.5"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M3.477 10.896a4 4 0 0 1 .585-.396"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M19.938 10.5a4 4 0 0 1 .585.396"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M6 18a4 4 0 0 1-1.967-.516"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M19.967 17.484A4 4 0 0 1 18 18"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Languages: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Languages",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m5 8 6 6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m4 14 6-6 2-3"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2 5h12"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M7 2h1"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m22 22-5-10-5 10"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M14 18h6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Telescope: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Telescope",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m10.065 12.493-6.18 1.318a.934.934 0 0 1-1.108-.702l-.537-2.15a1.07 1.07 0 0 1 .691-1.265l13.504-4.44"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m13.56 11.747 4.332-.924"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m16 21-3.105-6.21"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M16.485 5.94a2 2 0 0 1 1.455-2.425l1.09-.272a1 1 0 0 1 1.212.727l1.515 6.06a1 1 0 0 1-.727 1.213l-1.09.272a2 2 0 0 1-2.425-1.455z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m6.158 8.633 1.114 4.456"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m8 21 3.105-6.21"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M10,13 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val SlidersHorizontal: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.SlidersHorizontal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val LayoutGrid: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.LayoutGrid",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M4,3 H9 A1,1 0 0 1 10,4 V9 A1,1 0 0 1 9,10 H4 A1,1 0 0 1 3,9 V4 A1,1 0 0 1 4,3 Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M15,3 H20 A1,1 0 0 1 21,4 V9 A1,1 0 0 1 20,10 H15 A1,1 0 0 1 14,9 V4 A1,1 0 0 1 15,3 Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M15,14 H20 A1,1 0 0 1 21,15 V20 A1,1 0 0 1 20,21 H15 A1,1 0 0 1 14,20 V15 A1,1 0 0 1 15,14 Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M4,14 H9 A1,1 0 0 1 10,15 V20 A1,1 0 0 1 9,21 H4 A1,1 0 0 1 3,20 V15 A1,1 0 0 1 4,14 Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Shield: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Shield",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Info",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2,12 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 16v-4"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 8h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val LogOut: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.LogOut",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M16,17 L21,12 L16,7"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Flag: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Flag",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M13,6.5 a0.5,0.5 0 1,0 1,0 a0.5,0.5 0 1,0 -1,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M17,10.5 a0.5,0.5 0 1,0 1,0 a0.5,0.5 0 1,0 -1,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M8,7.5 a0.5,0.5 0 1,0 1,0 a0.5,0.5 0 1,0 -1,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M6,12.5 a0.5,0.5 0 1,0 1,0 a0.5,0.5 0 1,0 -1,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Volume2: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Volume2",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M16 9a5 5 0 0 1 0 6"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M19.364 18.364a9 9 0 0 0 0-12.728"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val VolumeX: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.VolumeX",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Download",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M7,10 L12,15 L17,10"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val CircleAlert: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.CircleAlert",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2,12 a10,10 0 1,0 20,0 a10,10 0 1,0 -20,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M0,0 L0,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val WifiOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.WifiOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 20h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M8.5 16.429a5 5 0 0 1 7 0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M5 12.859a10 10 0 0 1 5.17-2.69"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M19 12.859a10 10 0 0 0-2.007-1.523"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2 8.82a15 15 0 0 1 4.177-2.643"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M22 8.82a15 15 0 0 0-11.288-3.764"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m2 2 20 20"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val LoaderCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.LoaderCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M21 12a9 9 0 1 1-6.219-8.56"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Keyboard: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Keyboard",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M10 8h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 12h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M14 8h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M16 12h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M18 8h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M6 8h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M7 16h10"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M8 12h.01"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M4,4 H20 A2,2 0 0 1 22,6 V18 A2,2 0 0 1 20,20 H4 A2,2 0 0 1 2,18 V6 A2,2 0 0 1 4,4 Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Moon: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Moon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }

    public val Sun: ImageVector by lazy {
        ImageVector.Builder(
            name = "GsIcons.Sun",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            defaultStrokeLineWidth = 2f,
            defaultStrokeLineCap = StrokeCap.Round,
            defaultStrokeLineJoin = StrokeJoin.Round,
        ).apply {
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M8,12 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 2v2"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M12 20v2"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m4.93 4.93 1.41 1.41"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m17.66 17.66 1.41 1.41"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M2 12h2"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "M20 12h2"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m6.34 17.66-1.41 1.41"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathDataString(
                    "m19.07 4.93-1.41 1.41"
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
            )
        }.build()
    }
}
