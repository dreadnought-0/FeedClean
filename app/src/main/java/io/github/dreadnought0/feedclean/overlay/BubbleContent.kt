package io.github.dreadnought0.feedclean.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.service.BlockedMatch

/** Bubble size in dp (window size). */
const val BUBBLE_SIZE_DP = 44

/** Bubble colours per state. */
internal object BubbleColors {
    val NEUTRAL = Color(0xFF8E8E93)
    val AD = Color(0xFFE53935)
    val PROMO = Color(0xFFFFB300)
    val SHOP = Color(0xFFFB8C00)
    val LIVE = Color(0xFF8E24AA)
    val WARNING_STRONG = Color(0xFFD50000)
    val WARNING_POSSIBLE = Color(0xFFFFD54F)
    val GLYPH_BG = Color(0xE6000000)

    fun of(kind: BubbleKind): Color = when (kind) {
        BubbleKind.NEUTRAL -> NEUTRAL
        BubbleKind.AD -> AD
        BubbleKind.PROMO -> PROMO
        BubbleKind.SHOP -> SHOP
        BubbleKind.LIVE -> LIVE
    }
}

/**
 * The bubble (drawn only; gestures are handled by the window's touch listener). A coloured dot with a short
 * label, a skip glyph when auto-skip is armed, a warning badge for "seen after block", a count badge for
 * several signals, a paused look (kill-switch or loop guard), and a progress ring while an action runs.
 */
@Composable
fun BubbleContent(look: BubbleLook, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.overlay_bubble_cd)
    Box(
        modifier = modifier
            .size(BUBBLE_SIZE_DP.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .alpha(if (look.paused) 0.55f else 0.92f)
                .background(BubbleColors.of(look.kind), CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val label = when {
                look.paused -> stringResource(R.string.overlay_bubble_paused)
                look.kind == BubbleKind.AD -> stringResource(R.string.overlay_bubble_ad)
                look.kind == BubbleKind.PROMO -> stringResource(R.string.overlay_bubble_promo)
                look.kind == BubbleKind.SHOP -> stringResource(R.string.overlay_bubble_shop)
                look.kind == BubbleKind.LIVE -> stringResource(R.string.overlay_bubble_live)
                else -> null
            }
            if (label != null) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = if (label.length > 4) 7.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        if (look.busy) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize().padding(1.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        }
        if (look.warning != BlockedMatch.NONE) {
            Badge(
                text = stringResource(R.string.overlay_bubble_warning_glyph),
                background = if (look.warning == BlockedMatch.STRONG) BubbleColors.WARNING_STRONG else BubbleColors.WARNING_POSSIBLE,
                textColor = if (look.warning == BlockedMatch.STRONG) Color.White else Color.Black,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        if (look.signalCount >= 2) {
            Badge(
                text = look.signalCount.toString(),
                background = BubbleColors.GLYPH_BG,
                textColor = Color.White,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        if (look.skipArmed && !look.paused) {
            Badge(
                text = stringResource(R.string.overlay_bubble_skip_glyph),
                background = BubbleColors.GLYPH_BG,
                textColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun Badge(text: String, background: Color, textColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(15.dp)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = textColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The hint pill shown next to the bubble (kill-switch confirmation, loop-guard nudge, results). */
@Composable
fun HintContent(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xEE202124),
        contentColor = Color.White,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            maxLines = 3,
        )
    }
}

@Preview
@Composable
private fun BubblePreview() {
    BubbleContent(BubbleLook(kind = BubbleKind.SHOP, signalCount = 2, skipArmed = true, warning = BlockedMatch.POSSIBLE))
}
