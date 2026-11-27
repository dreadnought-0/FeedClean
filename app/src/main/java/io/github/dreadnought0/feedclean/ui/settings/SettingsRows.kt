package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.common.FeedCleanRow
import io.github.dreadnought0.feedclean.ui.icons.FeedCleanIcons
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import kotlin.math.roundToInt

// Reusable building blocks for the Settings list. Rows sit directly on the screen surface, and every row is
// fully operable with TalkBack (toggleable / selectable / clickable semantics on the whole row).

/** Group heading, e.g. "Detection". */
@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** Smaller sub-heading inside a group, e.g. "Auto-skip targets". */
@Composable
fun SettingsSubheader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

/** Helper / footnote text under a row or group (hard floors, privacy notes, dependency hints). */
@Composable
fun SettingsFootnote(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * Rows that only take effect when a master switch is on. When [active] is false they are dimmed and
 * [inactiveNote] explains the dependency, but they stay editable so the user can prepare them.
 */
@Composable
fun DependentSettings(
    active: Boolean,
    inactiveNote: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        if (!active) SettingsFootnote(inactiveNote, color = MaterialTheme.colorScheme.tertiary)
        Column(modifier = Modifier.alpha(if (active) 1f else DEPENDENT_INACTIVE_ALPHA), content = content)
    }
}

/** On/off row. The whole row is the toggle target. */
@Composable
fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    FeedCleanRow(
        title = title,
        summary = summary,
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

/**
 * Slider for a bounded integer in multiples of [step]. The thumb moves locally while dragging and
 * [onValueCommitted] fires once on release (so a drag is one settings write, not dozens).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderSettingRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    step: Int,
    valueLabel: @Composable (Int) -> String,
    onValueCommitted: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    fun snap(raw: Float): Int {
        val steps = ((raw - valueRange.first) / step).roundToInt()
        return (valueRange.first + steps * step).coerceIn(valueRange)
    }

    val sliderState = remember(valueRange, step) {
        SliderState(
            value.toFloat(),
            ((valueRange.last - valueRange.first) / step - 1).coerceAtLeast(0),
            valueRange.first.toFloat()..valueRange.last.toFloat(),
        )
    }
    // Follow external changes (another screen, sanitize clamping) when the user isn't dragging.
    LaunchedEffect(sliderState, value) {
        if (!sliderState.isDragging) sliderState.value = value.toFloat()
    }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                valueLabel(snap(sliderState.value)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            sliderState,
            { sliderState.value = it },
            Modifier.fillMaxWidth(),
            true,
            { onValueCommitted(snap(sliderState.value)) },
        )
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** − / value / + stepper for an integer range. Values outside [range] are clamped on the next tap. */
@Composable
fun StepperSettingRow(
    title: String,
    value: Int,
    range: IntRange,
    valueLabel: @Composable (Int) -> String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    step: Int = 1,
) {
    FeedCleanRow(
        title = title,
        summary = summary,
        modifier = modifier,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onValueChange((value - step).coerceIn(range)) },
                    enabled = value > range.first,
                ) {
                    Icon(FeedCleanIcons.Remove, contentDescription = stringResource(R.string.ui_settings_decrease, title))
                }
                Text(
                    valueLabel(value),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 48.dp),
                )
                IconButton(
                    onClick = { onValueChange((value + step).coerceIn(range)) },
                    enabled = value < range.last,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ui_settings_increase, title))
                }
            }
        },
    )
}

/** One choice in a [RadioGroupSettingRow]. */
data class RadioOption<T>(val value: T, val label: String, val description: String? = null)

/** Titled single-choice group. */
@Composable
fun <T> RadioGroupSettingRow(
    title: String,
    options: List<RadioOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option.value == selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option.value) },
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    RadioButton(selected = option.value == selected, onClick = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        if (option.description != null) {
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tappable action row with a chevron (navigation or one-shot action). */
@Composable
fun ActionSettingRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val titleColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    FeedCleanRow(
        title = title,
        summary = summary,
        titleColor = titleColor,
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else titleColor,
            )
        },
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

/** Read-only label/value row (e.g. the current selector source). */
@Composable
fun ValueSettingRow(title: String, value: String, modifier: Modifier = Modifier) {
    FeedCleanRow(title = title, summary = value, modifier = modifier)
}

private const val DEPENDENT_INACTIVE_ALPHA = 0.6f
private const val DISABLED_ALPHA = 0.38f

@Preview(showBackground = true)
@Composable
private fun SettingsRowsPreview() {
    FeedCleanTheme(dynamicColor = false) {
        Column {
            SettingsSectionHeader("Curate feed")
            SwitchSettingRow("Curate feed", checked = true, onCheckedChange = {}, summary = "Master switch")
            DependentSettings(active = false, inactiveNote = "Takes effect when Curate feed is on.") {
                SliderSettingRow("Skip settle delay", 300, 0..2000, 50, { "$it ms" }, {})
                StepperSettingRow("Consecutive-skip cap", 8, 1..50, { "$it" }, {})
            }
            RadioGroupSettingRow(
                "Cover style",
                listOf(RadioOption(1, "Blank"), RadioOption(2, "Blur"), RadioOption(3, "Labelled")),
                selected = 1,
                onSelect = {},
            )
            ActionSettingRow("Clear all data", onClick = {}, destructive = true)
            SettingsFootnote("Minimum 3 s.")
        }
    }
}
