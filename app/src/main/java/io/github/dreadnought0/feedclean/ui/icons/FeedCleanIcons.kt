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

    val Add: ImageVector by lazy {
        icon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    }

    val Close: ImageVector by lazy {
        icon("Close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    }

    val InfoOutline: ImageVector by lazy {
        icon("InfoOutline", "M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z")
    }

    val Help: ImageVector by lazy {
        icon("Help", "M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z")
    }

    val FastForward: ImageVector by lazy {
        icon("FastForward", "M4,18l8.5,-6L4,6v12zM13,6v12l8.5,-6L13,6z")
    }

    val ThumbDown: ImageVector by lazy {
        icon("ThumbDown", "M15,3H6c-0.83,0 -1.54,0.5 -1.84,1.22l-3.02,7.05c-0.09,0.23 -0.14,0.47 -0.14,0.73v2c0,1.1 0.9,2 2,2h6.31l-0.95,4.57 -0.03,0.32c0,0.41 0.17,0.79 0.44,1.06L9.83,23l6.59,-6.59c0.36,-0.36 0.58,-0.86 0.58,-1.41V5c0,-1.1 -0.9,-2 -2,-2zM19,3v12h4V3h-4z")
    }

    val Block: ImageVector by lazy {
        icon("Block", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM4,12c0,-4.42 3.58,-8 8,-8 1.85,0 3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12zM12,20c-1.85,0 -3.55,-0.63 -4.9,-1.69L18.31,7.1C19.37,8.45 20,10.15 20,12c0,4.42 -3.58,8 -8,8z")
    }

    val Scan: ImageVector by lazy {
        icon("Scan", "M5,15H3v4c0,1.1 0.9,2 2,2h4v-2H5v-4zM5,5h4V3H5c-1.1,0 -2,0.9 -2,2v4h2V5zM19,3h-4v2h4v4h2V5c0,-1.1 -0.9,-2 -2,-2zM19,19h-4v2h4c1.1,0 2,-0.9 2,-2v-4h-2v4zM12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM12,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z")
    }

    val Bubble: ImageVector by lazy {
        icon("Bubble", "M12,2C6.49,2 2,6.49 2,12s4.49,10 10,10 10,-4.49 10,-10S17.51,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM15,12c0,1.66 -1.34,3 -3,3s-3,-1.34 -3,-3 1.34,-3 3,-3 3,1.34 3,3z")
    }

    val Timer: ImageVector by lazy {
        icon("Timer", "M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z")
    }

    val Speed: ImageVector by lazy {
        icon("Speed", "M20.38,8.57l-1.23,1.85a8,8 0,0 1,-0.22 7.58H5.07A8,8 0,0 1,15.58 6.85l1.85,-1.23A10,10 0,0 0,3.35 19a2,2 0,0 0,1.72 1h13.85a2,2 0,0 0,1.74 -1,10 10,0 0,0 -0.27,-10.44zM10.59,15.41a2,2 0,0 0,2.83 0l5.66,-8.49 -8.49,5.66a2,2 0,0 0,0 2.83z")
    }

    val Archive: ImageVector by lazy {
        icon("Archive", "M20.54,5.23l-1.39,-1.68C18.88,3.21 18.47,3 18,3H6c-0.47,0 -0.88,0.21 -1.16,0.55L3.46,5.23C3.17,5.57 3,6.02 3,6.5V19c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V6.5c0,-0.48 -0.17,-0.93 -0.46,-1.27zM12,17.5L6.5,12H10v-2h4v2h3.5L12,17.5zM5.12,5l0.81,-1h12l0.94,1H5.12z")
    }

    val Tune: ImageVector by lazy {
        icon("Tune", "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z")
    }

    val Checklist: ImageVector by lazy {
        icon("Checklist", "M22,7h-9v2h9V7zM22,15h-9v2h9V15zM5.54,11L2,7.46l1.41,-1.41l2.12,2.12l4.24,-4.24l1.41,1.41L5.54,11zM5.54,19L2,15.46l1.41,-1.41l2.12,2.12l4.24,-4.24l1.41,1.41L5.54,19z")
    }

    val TouchApp: ImageVector by lazy {
        icon("TouchApp", "M9,11.24V7.5C9,6.12 10.12,5 11.5,5S14,6.12 14,7.5v3.74c1.21,-0.81 2,-2.18 2,-3.74C16,5.01 13.99,3 11.5,3S7,5.01 7,7.5c0,1.56 0.79,2.93 2,3.74zM18.84,15.87l-4.54,-2.26c-0.17,-0.07 -0.35,-0.11 -0.54,-0.11H13v-6C13,6.67 12.33,6 11.5,6S10,6.67 10,7.5v10.74l-3.43,-0.72c-0.08,-0.01 -0.15,-0.03 -0.24,-0.03 -0.31,0 -0.59,0.13 -0.79,0.33l-0.79,0.8 4.94,4.94c0.27,0.27 0.65,0.44 1.06,0.44h6.79c0.75,0 1.33,-0.55 1.44,-1.28l0.75,-5.27c0.01,-0.07 0.02,-0.14 0.02,-0.2 0,-0.62 -0.38,-1.16 -0.91,-1.38z")
    }

    val Download: ImageVector by lazy {
        icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
    }

    val Upload: ImageVector by lazy {
        icon("Upload", "M9,16h6v-6h4l-7,-7 -7,7h4zM5,18h14v2H5z")
    }

    val History: ImageVector by lazy {
        icon("History", "M13,3c-4.97,0 -9,4.03 -9,9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z")
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
