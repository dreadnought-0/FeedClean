package io.github.dreadnought0.feedclean.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import java.time.LocalDate
import kotlin.math.min

private val PlotHeight = 88.dp
private val MinGutter = 24.dp
private val MaxBarWidth = 16.dp
private val BarCorner = 4.dp

/** Style of axis labels (y maximum, 0, dates). */
@Composable
private fun axisLabelStyle(): TextStyle = MaterialTheme.typography.labelSmall

/** Style of the selected day's value above its column. */
@Composable
private fun valueLabelStyle(): TextStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)

/**
 * Width of the value-axis gutter: room for the widest axis label among [axisMaxima] at the current font
 * scale. Pass the same value to every chart in a stack so their columns line up day by day.
 */
@Composable
fun rememberChartGutter(axisMaxima: List<Int>): Dp {
    val measurer = rememberTextMeasurer()
    val style = axisLabelStyle()
    val density = LocalDensity.current
    val widest = (axisMaxima.maxOrNull() ?: 0).coerceAtLeast(0).toString()
    return remember(widest, style, density) {
        val widthPx = measurer.measure(widest, style).size.width
        maxOf(MinGutter, with(density) { widthPx.toDp() } + 8.dp)
    }
}

/** Line height of [style] in dp (font-scaled), falling back to 1.5 × font size. */
@Composable
private fun lineHeightDp(style: TextStyle): Dp = with(LocalDensity.current) {
    if (style.lineHeight.isSpecified) style.lineHeight.toDp() else (style.fontSize * 1.5f).toDp()
}

/**
 * One measure as a small column chart over [dates]: one hue, thin columns with rounded data ends, a hairline
 * baseline and top gridline (labelled 0 and the axis maximum), dates under the first, middle and last
 * column, and the selected day's value printed above its column. A zero day draws no column.
 *
 * Tapping a column calls [onSelect] with its index; selection is shared by all charts on the screen. The
 * chart's content description summarises it, and the screen offers a table view with every value.
 */
@Composable
fun DailyColumnChart(
    series: DailySeries,
    dates: List<LocalDate>,
    today: LocalDate,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    gutter: Dp = rememberChartGutter(listOf(series.axisMax)),
) {
    val colors = chartColors()
    val locale = currentLocale()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = axisLabelStyle()
    val valueStyle = valueLabelStyle()
    // Bands above and below the plot grow with the font scale, so labels are never clipped.
    val topPadding = lineHeightDp(valueStyle) + 4.dp
    val axisBand = lineHeightDp(labelStyle) + 6.dp
    val todayLabel = stringResource(R.string.screens_time_today)
    val tickIndices = axisTickIndices(dates.size)
    val tickLabels = tickIndices.map { i -> if (dates[i] == today) todayLabel else shortDateLabel(dates[i], today, locale) }
    val title = stringResource(series.metric.titleRes)
    val description = chartDescription(series, dates, today, title)
    val currentOnSelect by rememberUpdatedState(onSelect)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.screens_chart_total, series.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(topPadding + PlotHeight + axisBand)
                .pointerInput(dates.size, gutter) {
                    detectTapGestures { offset ->
                        val gutterPx = gutter.toPx()
                        val slot = (size.width - gutterPx) / dates.size.coerceAtLeast(1)
                        if (dates.isNotEmpty() && slot > 0f) {
                            currentOnSelect(((offset.x - gutterPx) / slot).toInt().coerceIn(0, dates.size - 1))
                        }
                    }
                }
                .semantics { contentDescription = description },
        ) {
            if (dates.isEmpty()) return@Canvas
            val geometry = ChartGeometry(
                gutter = gutter.toPx(),
                top = topPadding.toPx(),
                plotHeight = PlotHeight.toPx(),
                slot = (size.width - gutter.toPx()) / dates.size,
            )
            drawSelection(geometry, selectedIndex, dates.size, colors.selection)
            drawAxes(geometry, series.axisMax, textMeasurer, labelStyle, colors)
            drawColumns(geometry, series, colors.series)
            drawSelectedValue(geometry, series, selectedIndex, textMeasurer, valueStyle, colors.value)
            drawDateTicks(geometry, tickIndices, tickLabels, textMeasurer, labelStyle, colors.label)
        }
    }
}

/** Pixel layout shared by the draw steps. */
private class ChartGeometry(val gutter: Float, val top: Float, val plotHeight: Float, val slot: Float) {
    val baseline: Float get() = top + plotHeight

    fun slotLeft(index: Int): Float = gutter + slot * index

    fun slotCenter(index: Int): Float = slotLeft(index) + slot / 2f
}

private fun DrawScope.drawSelection(g: ChartGeometry, index: Int, count: Int, color: Color) {
    if (index !in 0 until count) return
    val inset = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(g.slotLeft(index) + inset, g.top - 4.dp.toPx()),
        size = Size(g.slot - 2 * inset, g.plotHeight + 4.dp.toPx()),
        cornerRadius = CornerRadius(BarCorner.toPx()),
    )
}

private fun DrawScope.drawAxes(
    g: ChartGeometry,
    axisMax: Int,
    measurer: TextMeasurer,
    style: TextStyle,
    colors: ChartColors,
) {
    val hairline = 1.dp.toPx()
    drawLine(colors.gridline, Offset(g.gutter, g.top), Offset(size.width, g.top), hairline)
    drawLine(colors.baseline, Offset(g.gutter, g.baseline), Offset(size.width, g.baseline), hairline)
    val labelRight = g.gutter - 6.dp.toPx()
    for ((text, y) in listOf(axisMax.toString() to g.top, "0" to g.baseline)) {
        val layout = measurer.measure(text, style)
        drawText(
            textLayoutResult = layout,
            color = colors.label,
            topLeft = Offset(labelRight - layout.size.width, y - layout.size.height / 2f),
        )
    }
}

private fun DrawScope.drawColumns(g: ChartGeometry, series: DailySeries, color: Color) {
    val barWidth = min(MaxBarWidth.toPx(), g.slot * 0.6f)
    series.values.forEachIndexed { index, value ->
        if (value <= 0) return@forEachIndexed
        val height = value.toFloat() / series.axisMax * g.plotHeight
        val left = g.slotCenter(index) - barWidth / 2f
        val radius = CornerRadius(min(BarCorner.toPx(), min(height, barWidth / 2f)))
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = g.baseline - height,
                    right = left + barWidth,
                    bottom = g.baseline,
                    topLeftCornerRadius = radius,
                    topRightCornerRadius = radius,
                    bottomRightCornerRadius = CornerRadius.Zero,
                    bottomLeftCornerRadius = CornerRadius.Zero,
                ),
            )
        }
        drawPath(path, color)
    }
}

private fun DrawScope.drawSelectedValue(
    g: ChartGeometry,
    series: DailySeries,
    index: Int,
    measurer: TextMeasurer,
    style: TextStyle,
    color: Color,
) {
    val value = series.values.getOrNull(index) ?: return
    val layout = measurer.measure(value.toString(), style)
    val barTop = g.baseline - value.toFloat() / series.axisMax * g.plotHeight
    val x = (g.slotCenter(index) - layout.size.width / 2f).coerceIn(g.gutter, size.width - layout.size.width)
    val y = (barTop - 2.dp.toPx() - layout.size.height).coerceAtLeast(0f)
    drawText(textLayoutResult = layout, color = color, topLeft = Offset(x, y))
}

private fun DrawScope.drawDateTicks(
    g: ChartGeometry,
    indices: List<Int>,
    labels: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
    color: Color,
) {
    indices.forEachIndexed { i, index ->
        val layout = measurer.measure(labels[i], style)
        val x = (g.slotCenter(index) - layout.size.width / 2f).coerceIn(g.gutter, size.width - layout.size.width)
        drawText(textLayoutResult = layout, color = color, topLeft = Offset(x, g.baseline + 4.dp.toPx()))
    }
}

/** TalkBack summary: total, the highest day and today's value. */
@Composable
private fun chartDescription(series: DailySeries, dates: List<LocalDate>, today: LocalDate, title: String): String {
    val locale = currentLocale()
    val peak = series.peakIndex
    return if (peak == null || dates.isEmpty()) {
        stringResource(R.string.screens_chart_desc_empty, title, dates.size)
    } else {
        stringResource(
            R.string.screens_chart_desc,
            title,
            dates.size,
            series.total,
            shortDateLabel(dates[peak], today, locale),
            series.values[peak],
            series.values.last(),
        )
    }
}

/** "2 blocks" etc. for [metric]. */
@Composable
fun metricCountText(metric: DailyMetric, count: Int): String = pluralStringResource(metric.unitPluralRes, count, count)

@Preview(showBackground = true)
@Composable
private fun DailyColumnChartPreview() {
    val today = LocalDate.of(2026, 11, 22)
    val model = buildDailyChartModel(
        stats = emptyList(),
        today = today,
    ).let { empty ->
        empty.copy(
            series = listOf(
                DailySeries(DailyMetric.AUTO_SKIPS, listOf(3, 8, 0, 12, 5, 7, 14, 9, 0, 0, 6, 11, 4, 9)),
                DailySeries(DailyMetric.BLOCKS, List(14) { 0 }),
            ),
        )
    }
    FeedCleanTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val gutter = rememberChartGutter(model.series.map { it.axisMax })
            model.series.forEach { series ->
                DailyColumnChart(
                    series = series,
                    dates = model.dates,
                    today = today,
                    selectedIndex = 13,
                    onSelect = {},
                    gutter = gutter,
                )
            }
        }
    }
}
