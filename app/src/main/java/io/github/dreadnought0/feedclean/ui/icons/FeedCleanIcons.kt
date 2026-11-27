package io.github.dreadnought0.feedclean.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few icons FeedClean needs that are not in `material-icons-core` (the app avoids the large
 * icons-extended artifact). Paths follow the 24×24 Material grid. Tinted by `Icon` like any other icon.
 */
object FeedCleanIcons {
    /** Funnel / filter glyph: the app's mark (also used in the launcher icon). */
    val Filter: ImageVector by lazy {
        icon(
            "Filter",
            "M4.25,5.61C6.27,8.2 10,13 10,13v6c0,0.55 0.45,1 1,1h2c0.55,0 1,-0.45 1,-1v-6c0,0 3.72,-4.8 " +
                "5.74,-7.39C20.25,4.95 19.78,4 18.95,4H5.04C4.21,4 3.74,4.95 4.25,5.61z",
        )
    }

    /** Pause (two bars): the automation kill-switch. */
    val Pause: ImageVector by lazy {
        icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    }

    /** Bar chart: the Stats tab. */
    val BarChart: ImageVector by lazy {
        icon("BarChart", "M5,9.2h3V19H5V9.2zM10.6,5h2.8v14h-2.8V5zM16.2,13H19v6h-2.8V13z")
    }

    /** Code brackets: the Debug screen. */
    val Code: ImageVector by lazy {
        icon(
            "Code",
            "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 " +
                "-1.4,-1.4z",
        )
    }

    /** Minus sign, for steppers. */
    val Remove: ImageVector by lazy {
        icon("Remove", "M19,13H5v-2h14v2z")
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = "FeedClean.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
        ).build()
}
