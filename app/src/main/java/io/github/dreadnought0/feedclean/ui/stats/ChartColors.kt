package io.github.dreadnought0.feedclean.ui.stats

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Data-mark blue for light surfaces. The theme's teal primary is too low in chroma to read as a data color
 * (it fails the chroma floor), so charts use a dedicated blue. Checked against FeedClean's light surface,
 * card and container colors: inside the lightness band, above the chroma floor, ≥ 3:1 contrast.
 */
private val SeriesBlueLight = Color(0xFF2A78D6)

/** Data-mark blue for dark surfaces; the same hue stepped for the dark band, ≥ 3:1 on dark containers. */
private val SeriesBlueDark = Color(0xFF3987E5)

/** Colors for FeedClean's small charts and meters. Text always uses text colors, never [series]. */
@Immutable
data class ChartColors(
    /** Columns and meter fills. */
    val series: Color,
    /** Unfilled meter track: a lighter step of the same hue. */
    val track: Color,
    /** Hairline at the top of the value axis. */
    val gridline: Color,
    /** Hairline baseline. */
    val baseline: Color,
    /** Axis labels. */
    val label: Color,
    /** Direct value labels. */
    val value: Color,
    /** Wash behind the selected day's column. */
    val selection: Color,
)

/** Chart colors for the current theme; light or dark is picked from the actual surface color. */
@Composable
fun chartColors(): ChartColors {
    val scheme = MaterialTheme.colorScheme
    val series = if (scheme.surface.luminance() < 0.5f) SeriesBlueDark else SeriesBlueLight
    return ChartColors(
        series = series,
        track = series.copy(alpha = 0.22f),
        gridline = scheme.outlineVariant,
        baseline = scheme.outline,
        label = scheme.onSurfaceVariant,
        value = scheme.onSurface,
        selection = scheme.onSurface.copy(alpha = 0.07f),
    )
}
