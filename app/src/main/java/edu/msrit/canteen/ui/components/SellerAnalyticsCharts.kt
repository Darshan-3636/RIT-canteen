package edu.msrit.canteen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.DailySales
import edu.msrit.canteen.customer.FoodPhoto
import edu.msrit.canteen.customer.TrendGrouping
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.LocalDate

/** Figures in tables and charts use tabular digits so columns of numbers line up. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private val cardShape = RoundedCornerShape(RitRadii.card)

@Composable
private fun Modifier.analyticsCard(): Modifier {
    val colors = RitThemeTokens.colors
    return fillMaxWidth().clip(cardShape).border(1.dp, colors.outline, cardShape).background(colors.card)
}

/** Today | 7 Days | 30 Days. Tags `overview_range_<days>` are relied on by existing UI tests. */
@Composable
fun AnalyticsRangeSelector(selectedDays: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1 to "Today", 7 to "7 Days", 30 to "30 Days").forEach { (days, label) ->
            RitChip(label, selectedDays == days, { onSelect(days) }, Modifier.weight(1f).testTag("overview_range_$days"))
        }
    }
}

/** Revenue headline and its daily chart in one panel; [chart] is null for a single day. */
@Composable
fun RevenuePanel(revenuePaise: Long, periodLabel: String, modifier: Modifier = Modifier,
                 chart: (@Composable () -> Unit)?) {
    val colors = RitThemeTokens.colors
    Column(modifier.analyticsCard().padding(18.dp)) {
        RitText("Revenue · $periodLabel", RitType.label, colors.muted)
        Spacer(Modifier.height(4.dp))
        RitText(formatRupees(revenuePaise), RitType.display.tabular(), modifier = Modifier.testTag("sample_net_sales"))
        Spacer(Modifier.height(14.dp))
        if (chart != null) chart()
        else RitText("Choose 7 or 30 days to see the daily trend.", RitType.small, colors.muted)
    }
}

data class StatCell(val label: String, val value: String, val tag: String? = null)

/** Secondary figures side by side in one card, divided rather than boxed separately. */
@Composable
fun StatStrip(cells: List<StatCell>, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Row(modifier.analyticsCard().height(IntrinsicSize.Min)) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 14.dp).background(colors.outline))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 14.dp)
                .then(if (cell.tag == null) Modifier else Modifier.testTag(cell.tag))) {
                RitText(cell.value, RitType.title.tabular(), maxLines = 1)
                Spacer(Modifier.height(2.dp))
                RitText(cell.label, RitType.small, colors.muted, maxLines = 2)
            }
        }
    }
}

private fun TrendGrouping.axisName(day: LocalDate, count: Int): String = when (this) {
    TrendGrouping.DAILY -> if (count <= 7) shortWeekday(day) else shortDate(day)
    TrendGrouping.WEEKLY -> shortDate(day)
    TrendGrouping.MONTHLY -> shortMonth(day)
}

/**
 * Sales bars on a true time axis: [points] must already contain one entry per period (see [fillDays]).
 * The y-axis always starts at ₹0. Tapping a bar shows that period's figures above the chart.
 */
@Composable
fun SalesBarChart(points: List<DailySales>, periodLabel: String, modifier: Modifier = Modifier,
                  grouping: TrendGrouping = TrendGrouping.DAILY) {
    val colors = RitThemeTokens.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val axisStyle = RitType.small.tabular().copy(color = colors.muted)
    val axisMax = niceAxisMax(points.maxOfOrNull { it.netPaise } ?: 0)
    val gridValues = listOf(axisMax, axisMax / 2, 0L)
    val axisLabels = remember(axisMax, axisStyle) { gridValues.map { measurer.measure(formatRupees(it), axisStyle) } }
    val gutter = (axisLabels.maxOf { it.size.width } + with(density) { 8.dp.roundToPx() }).toFloat()
    val barLabels = remember(points, grouping, axisStyle) {
        points.map { measurer.measure(grouping.axisName(it.day, points.size), axisStyle) }
    }
    val labelGap = (barLabels.maxOfOrNull { it.size.width } ?: 0) + with(density) { 12.dp.toPx() }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val unit = when (grouping) { TrendGrouping.DAILY -> "Daily"; TrendGrouping.WEEKLY -> "Weekly"; TrendGrouping.MONTHLY -> "Monthly" }

    Column(modifier.fillMaxWidth()) {
        val picked = selected?.let(points::getOrNull)
        RitText(
            picked?.let { "${grouping.periodName(it.day).replaceFirstChar(Char::uppercase)} · ${formatRupees(it.netPaise)} · " +
                "${it.orders} " + if (it.orders == 1) "order" else "orders" }
                ?: "Tap a bar to see that " + when (grouping) {
                    TrendGrouping.DAILY -> "day"; TrendGrouping.WEEKLY -> "week"; TrendGrouping.MONTHLY -> "month" },
            RitType.small.tabular(), if (picked == null) colors.muted else colors.foreground,
            modifier = Modifier.testTag("sales_chart_selection"),
        )
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(184.dp).testTag("sales_bar_chart")
            .semantics { contentDescription = salesChartSummary(points, periodLabel, unit) { grouping.periodName(it) } }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val slot = (size.width - gutter) / points.size.coerceAtLeast(1)
                    val index = ((tap.x - gutter) / slot).toInt()
                    selected = index.takeIf { tap.x >= gutter && it in points.indices && it != selected }
                }
            }) {
            if (points.isEmpty()) return@Canvas
            val labelBand = 22.dp.toPx()
            val top = axisLabels.first().size.height / 2f
            val bottom = size.height - labelBand
            val plotHeight = bottom - top
            gridValues.forEachIndexed { index, value ->
                val y = bottom - plotHeight * value / axisMax
                drawLine(colors.outline, Offset(gutter, y), Offset(size.width, y), 1.dp.toPx())
                val label = axisLabels[index]
                drawText(label, topLeft = Offset(gutter - 8.dp.toPx() - label.size.width, y - label.size.height / 2f))
            }
            val slot = (size.width - gutter) / points.size
            val step = labelStep(points.size, slot, labelGap, alignToWeeks = grouping == TrendGrouping.DAILY)
            val barWidth = if (slot < 3.dp.toPx()) slot else slot * 0.64f
            val radius = (barWidth / 2f).coerceAtMost(6.dp.toPx())
            points.forEachIndexed { index, point ->
                val x = gutter + slot * index + (slot - barWidth) / 2f
                val height = plotHeight * point.netPaise / axisMax
                val tint = when {
                    selected == null || selected == index -> colors.accent
                    else -> colors.accent.copy(alpha = 0.38f)
                }
                if (point.netPaise == 0L) {
                    drawRect(colors.outline, Offset(x, bottom - 2.dp.toPx()), Size(barWidth, 2.dp.toPx()))
                } else {
                    // One shape with a rounded top and a square base on the ₹0 line, so translucent bars shade evenly.
                    val corner = CornerRadius(minOf(radius, height))
                    drawPath(Path().apply { addRoundRect(RoundRect(Rect(x, bottom - height, x + barWidth, bottom),
                        topLeft = corner, topRight = corner, bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero)) }, tint)
                }
                // Count from the newest bar so the latest period is always labelled.
                if ((points.lastIndex - index) % step == 0) {
                    val label = barLabels[index]
                    val left = (x + barWidth / 2f - label.size.width / 2f).coerceIn(gutter, size.width - label.size.width)
                    drawText(label, topLeft = Offset(left, bottom + 6.dp.toPx()))
                }
            }
        }
    }
}

/** One best-selling item: thumbnail, name, units, share bar and value. */
@Composable
fun RankedItemRow(name: String, units: Int, netPaise: Long, share: Float, photo: FoodPhoto?,
                  localImageUri: String?, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FoodPhotoContent(photo, "$name photo", Modifier.size(48.dp).clip(RoundedCornerShape(RitRadii.small)), localImageUri)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RitText(name, RitType.label, modifier = Modifier.weight(1f), maxLines = 1)
                Spacer(Modifier.width(8.dp))
                RitText(formatRupees(netPaise), RitType.label.tabular(), colors.foreground)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(RitRadii.pill)).background(colors.accentSoft)) {
                Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(6.dp)
                    .clip(RoundedCornerShape(RitRadii.pill)).background(colors.accent))
            }
            Spacer(Modifier.height(4.dp))
            RitText("$units ${if (units == 1) "unit" else "units"} sold", RitType.small.tabular(), colors.muted)
        }
    }
}

data class InsightNavEntry(val label: String, val detail: String, val icon: RitIconKind,
                           val tag: String, val onClick: () -> Unit)

/** Drill-down destinations as one grouped list rather than a grid of identical tiles. */
@Composable
fun InsightNavList(entries: List<InsightNavEntry>, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Column(modifier.analyticsCard()) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) Box(Modifier.fillMaxWidth().padding(start = 60.dp).height(1.dp).background(colors.outline))
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = entry.onClick)
                .testTag(entry.tag).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(RitRadii.small)).background(colors.accentSoft),
                    contentAlignment = Alignment.Center) { RitIcon(entry.icon, colors.accent, size = 18.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    RitText(entry.label, RitType.label)
                    RitText(entry.detail, RitType.small, colors.muted, maxLines = 2)
                }
                RitIcon(RitIconKind.Arrow, colors.muted, size = 16.dp)
            }
        }
    }
}

/** Plain outlined analytics card; one level only, never nested. */
@Composable
fun AnalyticsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.analyticsCard().padding(18.dp), content = content)
}

/**
 * Series colours for share charts. The theme has a single accent, so slices are accent tints plus the
 * neutral tokens; all derive from theme tokens and so follow light/dark mode.
 */
@Composable
fun chartPalette(): List<Color> {
    val colors = RitThemeTokens.colors
    return listOf(colors.accent, colors.accent.copy(alpha = 0.62f), colors.accent.copy(alpha = 0.34f),
        colors.foreground.copy(alpha = 0.55f), colors.foreground.copy(alpha = 0.22f))
}

data class ShareSlice(val label: String, val value: Long, val valueText: String)

/** Ring chart for parts of a whole; [description] is read by screen readers instead of the drawing. */
@Composable
fun DonutChart(slices: List<ShareSlice>, centreValue: String, centreLabel: String, description: String,
               modifier: Modifier = Modifier) {
    val palette = chartPalette()
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    Box(modifier.size(156.dp).semantics { contentDescription = description }.testTag("share_donut"),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2f
            val gap = if (slices.count { it.value > 0 } > 1) 2.5f else 0f
            var start = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = 360f * slice.value / total
                if (sweep > 0f) drawArc(palette[index % palette.size], start + gap / 2f, (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false, topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RitText(centreValue, RitType.title.tabular(), maxLines = 1)
            RitText(centreLabel, RitType.small, RitThemeTokens.colors.muted, maxLines = 1)
        }
    }
}

/** A single horizontal bar split into parts, for two- or three-way comparisons. */
@Composable
fun ShareBar(slices: List<ShareSlice>, description: String, modifier: Modifier = Modifier) {
    val palette = chartPalette()
    val total = slices.sumOf { it.value }
    Row(modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(RitRadii.pill))
        .background(RitThemeTokens.colors.outline).semantics { contentDescription = description }.testTag("share_bar")) {
        if (total > 0) slices.forEachIndexed { index, slice ->
            if (slice.value > 0) Box(Modifier.weight(slice.value.toFloat()).fillMaxHeight().background(palette[index % palette.size]))
        }
    }
}

/** Legend rows matching [DonutChart] / [ShareBar] colours: swatch, name, value and whole-number share. */
@Composable
fun ShareLegend(slices: List<ShareSlice>, modifier: Modifier = Modifier) {
    val palette = chartPalette()
    val colors = RitThemeTokens.colors
    val shares = percentShares(slices.map { it.value })
    Column(modifier.fillMaxWidth()) {
        slices.forEachIndexed { index, slice ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(palette[index % palette.size]))
                Spacer(Modifier.width(10.dp))
                RitText(slice.label, RitType.label, modifier = Modifier.weight(1f), maxLines = 1)
                RitText(slice.valueText, RitType.label.tabular())
                Spacer(Modifier.width(10.dp))
                RitText("${shares[index]}%", RitType.small.tabular(), colors.muted, modifier = Modifier.width(36.dp))
            }
        }
    }
}

/** Legend for line charts: a drawn solid or dashed sample beside each label. */
@Composable
fun LineLegend(entries: List<Pair<String, Boolean>>, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        entries.forEach { (label, dashed) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(22.dp).height(10.dp)) {
                    drawLine(if (dashed) colors.muted else colors.accent, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                        2.5.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null)
                }
                Spacer(Modifier.width(6.dp))
                RitText(label, RitType.small, colors.muted)
            }
        }
    }
}

/** Two or three mutually exclusive choices shown as equal-width chips. */
@Composable
fun <T> AnalyticsToggle(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit,
                        modifier: Modifier = Modifier, tagPrefix: String) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            RitChip(label, value == selected, { onSelect(value) }, Modifier.weight(1f).testTag("${tagPrefix}_${label.lowercase()}"))
        }
    }
}
