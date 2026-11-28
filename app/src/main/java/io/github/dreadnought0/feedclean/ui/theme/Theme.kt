package io.github.dreadnought0.feedclean.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * One light "glass" scheme for the whole app (no dynamic color, no dark variant: the look is locked). Material
 * components pick it up automatically: `primary` is the teal accent (switches, buttons, selection), plain `Card`s
 * read `surfaceContainerHighest` (translucent white), and `background` is transparent so [GlassBackground] shows
 * through every Scaffold.
 */
private val GlassColorScheme = lightColorScheme(
    primary = Glass.Accent,
    onPrimary = Color.White,
    primaryContainer = Glass.AccentWash,
    onPrimaryContainer = Glass.Accent,
    inversePrimary = Color(0xFF9FD0DA),
    secondary = Glass.Accent,
    onSecondary = Color.White,
    secondaryContainer = Glass.Accent,
    onSecondaryContainer = Color.White,
    tertiary = Glass.Teal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0x332F7F8F),
    onTertiaryContainer = Glass.Ink,
    background = Color.Transparent,
    onBackground = Glass.Ink,
    surface = Glass.Sheet,
    onSurface = Glass.Ink,
    surfaceVariant = Glass.CardStrong,
    onSurfaceVariant = Glass.Muted,
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF2B3437),
    inverseOnSurface = Color(0xFFEEF3F4),
    error = Glass.Red,
    onError = Color.White,
    errorContainer = Color(0x339A4A44),
    onErrorContainer = Glass.Red,
    outline = Color(0x5A1D2326),
    outlineVariant = Glass.Divider,
    scrim = Color(0x80283237),
    surfaceBright = Glass.Sheet,
    surfaceDim = Color(0xFFD9E1E3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Glass.Sheet,
    surfaceContainer = Glass.Sheet,
    surfaceContainerHigh = Glass.Sheet,
    surfaceContainerHighest = Glass.Card,
)

private val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Extra semantic colors that Material 3's scheme does not cover. Read via [FeedCleanTheme.extraColors]. */
@Immutable
data class FeedCleanExtraColors(
    /** Tint for a passing ("✓") status item. Failing items use amber ("Needs attention"). */
    val statusOk: Color,
)

private val LocalFeedCleanExtraColors = staticCompositionLocalOf { FeedCleanExtraColors(statusOk = Glass.Ok) }

/** FeedClean's theme: the locked glass look (always light, never dynamic). */
@Composable
fun FeedCleanTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFeedCleanExtraColors provides FeedCleanExtraColors(statusOk = Glass.Ok)) {
        MaterialTheme(
            colorScheme = GlassColorScheme,
            typography = Typography,
            shapes = GlassShapes,
            content = content,
        )
    }
}

/** Accessors for FeedClean-specific theme values. */
object FeedCleanTheme {
    /** Semantic colors beyond the Material 3 scheme. */
    val extraColors: FeedCleanExtraColors
        @Composable get() = LocalFeedCleanExtraColors.current
}

/** The grey-blue gradient with soft light blobs that sits behind every screen. */
fun Modifier.glassBackground(): Modifier = this
    .background(Brush.linearGradient(listOf(Glass.BgTop, Glass.BgMid, Glass.BgBottom)))
    .drawBehind {
        val r = max(size.width, size.height)
        fun blob(color: Color, x: Float, y: Float, radius: Float) = drawRect(
            Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = radius),
        )
        blob(Glass.BlobWhite, size.width * 0.12f, size.height * 0.08f, r * 0.38f)
        blob(Glass.BlobTeal, size.width * 0.98f, size.height * 0.36f, r * 0.42f)
        blob(Glass.BlobWhite.copy(alpha = 0.34f), 0f, size.height * 0.66f, r * 0.40f)
        blob(Glass.BlobSage, size.width * 0.8f, size.height * 0.98f, r * 0.45f)
    }

/** Fills its bounds with [glassBackground]. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize().glassBackground(), content = content)
}
