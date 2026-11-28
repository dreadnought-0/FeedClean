package io.github.dreadnought0.feedclean.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.armed.ArmedLevel
import io.github.dreadnought0.feedclean.ui.common.ArmedPill
import io.github.dreadnought0.feedclean.ui.common.CardBody
import io.github.dreadnought0.feedclean.ui.common.CardHeader
import io.github.dreadnought0.feedclean.ui.common.CheckLine
import io.github.dreadnought0.feedclean.ui.common.GlassCard
import io.github.dreadnought0.feedclean.ui.common.GlassDivider
import io.github.dreadnought0.feedclean.ui.common.GlassScreen
import io.github.dreadnought0.feedclean.ui.common.SectionNote
import io.github.dreadnought0.feedclean.ui.icons.FeedCleanIcons
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import io.github.dreadnought0.feedclean.ui.theme.Glass
import io.github.dreadnought0.feedclean.ui.theme.GlassBackground

/** "What Armed means": the four badge states and what each kind of option needs. Static. */
@Composable
fun ArmedGuideScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    GlassScreen(title = stringResource(R.string.guide_title), onBack = onBack, modifier = modifier) {
        SectionNote(stringResource(R.string.guide_intro))
        GlassCard {
            LevelRow(ArmedLevel.ARMED, R.string.guide_armed)
            GlassDivider()
            LevelRow(ArmedLevel.OFF, R.string.guide_off)
            GlassDivider()
            LevelRow(ArmedLevel.BLOCKED, R.string.guide_blocked)
            GlassDivider()
            LevelRow(ArmedLevel.PAUSED, R.string.guide_paused)
        }
        GlassCard {
            Text(stringResource(R.string.guide_skip_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            listOf(R.string.guide_skip_1, R.string.guide_skip_2, R.string.guide_skip_3, R.string.guide_skip_4, R.string.guide_skip_5)
                .forEach { CheckLine(stringResource(it), met = true) }
        }
        GlassCard {
            Text(stringResource(R.string.guide_live_title), style = MaterialTheme.typography.titleMedium)
            CardBody(stringResource(R.string.guide_live_body))
        }
        GlassCard {
            CardHeader(stringResource(R.string.guide_taps_title), icon = FeedCleanIcons.TouchApp)
            CardBody(stringResource(R.string.guide_taps_body))
        }
        GlassCard {
            CardHeader(stringResource(R.string.guide_auto_ni_title), icon = FeedCleanIcons.ThumbDown)
            listOf(R.string.guide_auto_ni_1, R.string.guide_auto_ni_2, R.string.guide_auto_ni_3)
                .forEach { CheckLine(stringResource(it), met = true) }
            CardBody(stringResource(R.string.guide_auto_ni_note))
        }
    }
}

@Composable
private fun LevelRow(level: ArmedLevel, text: Int) {
    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(84.dp)) { ArmedPill(level) }
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = Glass.Ink, modifier = Modifier.weight(1f))
    }
}

@Preview(heightDp = 1500)
@Composable
private fun ArmedGuidePreview() {
    FeedCleanTheme { GlassBackground { ArmedGuideScreen(onBack = {}) } }
}
