package edu.msrit.canteen.ui.components

import edu.msrit.canteen.customer.DailySales
import edu.msrit.canteen.customer.InsightRange
import edu.msrit.canteen.customer.TrendGrouping
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Display-only helpers for seller analytics. No sales value is calculated here, only presented. */

/**
 * Indian digit grouping (1,23,456) done by hand so JVM tests and Android devices format identically.
 * Built only from `toString()`, which always emits 0–9, so the phone's language never changes the digits.
 */
fun formatRupees(paise: Long): String {
    val sign = if (paise < 0) "-" else ""
    val whole = (abs(paise) / 100).toString()
    val fraction = abs(paise) % 100
    val grouped = if (whole.length <= 3) whole else
        whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed() + "," + whole.takeLast(3)
    return sign + "₹" + grouped + if (fraction == 0L) "" else "." + fraction.toString().padStart(2, '0')
}

fun lastDays(days: Int, today: LocalDate): InsightRange = InsightRange(today.minusDays(days - 1L), today)

/** Every day in [range] in order, with ₹0 for days that had no sales, so bar spacing is true to time. */
fun fillDays(points: List<DailySales>, range: InsightRange): List<DailySales> {
    if (!range.valid()) return emptyList()
    val byDay = points.associateBy { it.day }
    return generateSequence(range.start) { it.plusDays(1) }.takeWhile { !it.isAfter(range.end) }
        .map { byDay[it] ?: DailySales(it, 0, 0) }.toList()
}

/** Smallest round rupee value at or above [maxPaise], used as the chart's top gridline. */
fun niceAxisMax(maxPaise: Long): Long {
    val rupees = (maxPaise + 99) / 100
    if (rupees <= 0) return 10_000
    var magnitude = 1L
    while (magnitude * 10 <= rupees) magnitude *= 10
    val steps = longArrayOf(10, 15, 20, 25, 30, 40, 50, 60, 80, 100)
    val step = steps.first { it * magnitude >= rupees * 10 }
    return step * magnitude * 10
}

private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val month = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

fun shortWeekday(day: LocalDate): String = weekday.format(day)
fun shortDate(day: LocalDate): String = dayMonth.format(day)
fun weekdayDate(day: LocalDate): String = "${shortWeekday(day)} ${shortDate(day)}"
fun shortMonth(day: LocalDate): String = month.format(day)

/** How one bar's period is named in captions and summaries: "Sat 24 Oct", "week of 19 Oct", "Oct 2026". */
fun TrendGrouping.periodName(day: LocalDate): String = when (this) {
    TrendGrouping.DAILY -> weekdayDate(day)
    TrendGrouping.WEEKLY -> "week of ${shortDate(day)}"
    TrendGrouping.MONTHLY -> "${shortMonth(day)} ${day.year}"
}

private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "20–26 Oct", "28 Sep – 26 Oct", or with years when the range crosses one. */
fun formatRange(range: InsightRange): String {
    val (start, end) = range.start to range.end
    return when {
        start == end -> shortDate(end)
        start.year == end.year && start.month == end.month -> "${start.dayOfMonth}–${shortDate(end)}"
        start.year == end.year -> "${shortDate(start)} – ${shortDate(end)}"
        else -> "${dayMonthYear.format(start)} – ${dayMonthYear.format(end)}"
    }
}

/** Whole-number percentages that add up to exactly 100 (largest remainder), for legends. */
fun percentShares(values: List<Long>): List<Int> {
    val total = values.sum()
    if (total <= 0) return values.map { 0 }
    val base = values.map { (it * 100 / total).toInt() }
    val byRemainder = values.indices.sortedByDescending { (values[it] * 100) % total }
    val extra = 100 - base.sum()
    return base.mapIndexed { index, share -> share + if (index in byRemainder.take(extra)) 1 else 0 }
}

/**
 * Label every n-th bar so labels stay at least [minGapPx] apart. Daily charts longer than a week
 * step in whole weeks so labels always land on the same weekday.
 */
fun labelStep(count: Int, slotPx: Float, minGapPx: Float, alignToWeeks: Boolean): Int {
    val raw = kotlin.math.ceil(minGapPx / slotPx.coerceAtLeast(0.01f)).toInt().coerceAtLeast(1)
    return if (alignToWeeks && count > 7) ((raw + 6) / 7) * 7 else raw
}

/** Screen-reader description of a sales bar chart; [unit] names the grouping ("Daily", "Weekly"…). */
fun salesChartSummary(points: List<DailySales>, periodLabel: String, unit: String = "Daily",
                      label: (LocalDate) -> String = ::weekdayDate): String {
    if (points.isEmpty()) return "No sales in $periodLabel."
    val orders = points.sumOf { it.orders }
    val high = points.maxBy { it.netPaise }
    val low = points.minBy { it.netPaise }
    return "$unit sales, $periodLabel: ${formatRupees(points.sumOf { it.netPaise })} from $orders " +
        (if (orders == 1) "order" else "orders") +
        ". Highest ${label(high.day)}, ${formatRupees(high.netPaise)}." +
        " Lowest ${label(low.day)}, ${formatRupees(low.netPaise)}."
}
