package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.dreadnought0.feedclean.ui.theme.Glass
import kotlin.math.roundToInt

/**
 * Glass slider for a bounded integer in multiples of [step]. The thumb moves locally while dragging and
 * [onValueCommitted] fires once on release (one settings write per drag). [label] receives the live value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassIntSlider(
    value: Int,
    valueRange: IntRange,
    step: Int,
    onValueCommitted: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (Int) -> Unit = {},
) {
    fun snap(raw: Float): Int {
        val steps = ((raw - valueRange.first) / step).roundToInt()
        return (valueRange.first + steps * step).coerceIn(valueRange)
    }

    val sliderState = remember(valueRange, step) {
        SliderState(
            value.toFloat(),
            0,
            valueRange.first.toFloat()..valueRange.last.toFloat(),
        )
    }
    LaunchedEffect(sliderState, value) {
        if (!sliderState.isDragging) sliderState.value = value.toFloat()
    }
    label(snap(sliderState.value))
    Slider(
        sliderState,
        { sliderState.value = it },
        modifier.fillMaxWidth(),
        true,
        { onValueCommitted(snap(sliderState.value)) },
        SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Glass.Teal,
            inactiveTrackColor = Color.White.copy(alpha = 0.7f),
        ),
    )
}
