package edu.msrit.canteen.ui.components

import edu.msrit.canteen.customer.DailySales
import edu.msrit.canteen.customer.InsightRange
import edu.msrit.canteen.customer.TrendGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class SellerAnalyticsFormatTest {
    private val monday = LocalDate.of(2026, 10, 26)

    @Test fun rupeesUseIndianGroupingAndOnlyShowPaiseWhenPresent() {
        assertEquals("₹0", formatRupees(0))
        assertEquals("₹90", formatRupees(9_000))
        assertEquals("₹1,234", formatRupees(123_400))
        assertEquals("₹1,234.50", formatRupees(123_450))
        assertEquals("₹1,23,456", formatRupees(12_345_600))
        assertEquals("₹12,34,56,789.05", formatRupees(123_456_789_05))
        assertEquals("-₹30", formatRupees(-3_000))
    }

    @Test fun rupeesKeepAsciiDigitsWhateverThePhoneLanguage() {
        val original = Locale.getDefault()
        try {
            listOf("mr-IN", "ar-EG", "bn-IN").forEach { tag ->
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals(tag, "₹1,234.50", formatRupees(123_450))
                assertEquals(tag, "₹0.05", formatRupees(5))
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun lastDaysEndsTodayAndIncludesToday() {
        assertEquals(InsightRange(monday, monday), lastDays(1, monday))
        assertEquals(InsightRange(monday.minusDays(6), monday), lastDays(7, monday))
        assertEquals(InsightRange(monday.minusDays(29), monday), lastDays(30, monday))
    }

    @Test fun fillDaysAddsZeroDaysWithoutChangingTotals() {
        val points = listOf(DailySales(monday.minusDays(4), 2, 18_000), DailySales(monday, 1, 6_500))
        val filled = fillDays(points, lastDays(7, monday))
        assertEquals(7, filled.size)
        assertEquals((6L downTo 0L).map { monday.minusDays(it) }, filled.map { it.day })
        assertEquals(points.sumOf { it.netPaise }, filled.sumOf { it.netPaise })
        assertEquals(points.sumOf { it.orders }, filled.sumOf { it.orders })
        assertEquals(0L, filled.first().netPaise)
    }

    @Test fun fillDaysReturnsNothingForInvalidRange() {
        assertTrue(fillDays(emptyList(), InsightRange(monday, monday.minusDays(1))).isEmpty())
    }

    @Test fun axisMaxIsARoundRupeeValueAtOrAboveTheLargestBar() {
        assertEquals(10_000L, niceAxisMax(0))
        assertEquals(150_000L, niceAxisMax(124_000))
        assertEquals(100_000L, niceAxisMax(100_000))
        assertEquals(40_000L, niceAxisMax(36_000))
        assertEquals(1_000_000L, niceAxisMax(810_000))
    }

    @Test fun dayLabelsAreShortAndReadable() {
        assertEquals("Mon", shortWeekday(monday))
        assertEquals("26 Oct", shortDate(monday))
        assertEquals("Mon 26 Oct", weekdayDate(monday))
        assertEquals("Oct", shortMonth(monday))
        assertEquals("Mon 26 Oct", TrendGrouping.DAILY.periodName(monday))
        assertEquals("week of 26 Oct", TrendGrouping.WEEKLY.periodName(monday))
        assertEquals("Oct 2026", TrendGrouping.MONTHLY.periodName(monday))
    }

    @Test fun percentSharesAlwaysTotalOneHundred() {
        assertEquals(listOf(33, 33, 34), percentShares(listOf(1L, 1L, 1L)).sorted())
        assertEquals(listOf(82, 18), percentShares(listOf(9_120L, 1_950L)))
        assertEquals(100, percentShares(listOf(7L, 5L, 3L, 3L, 1L)).sum())
        assertEquals(listOf(0, 0), percentShares(listOf(0L, 0L)))
    }

    @Test fun labelStepKeepsAxisLabelsApartAndWeeklyAligned() {
        assertEquals(1, labelStep(count = 7, slotPx = 80f, minGapPx = 60f, alignToWeeks = true))
        assertEquals(7, labelStep(count = 30, slotPx = 20f, minGapPx = 60f, alignToWeeks = true))
        assertEquals(14, labelStep(count = 120, slotPx = 5f, minGapPx = 60f, alignToWeeks = true))
        assertEquals(3, labelStep(count = 10, slotPx = 25f, minGapPx = 60f, alignToWeeks = false))
    }

    @Test fun dateRangesReadNaturally() {
        assertEquals("26 Oct", formatRange(InsightRange(monday, monday)))
        assertEquals("20–26 Oct", formatRange(InsightRange(monday.minusDays(6), monday)))
        assertEquals("28 Sep – 26 Oct", formatRange(InsightRange(monday.minusDays(28), monday)))
        assertEquals("28 Dec 2025 – 3 Jan 2026",
            formatRange(InsightRange(LocalDate.of(2025, 12, 28), LocalDate.of(2026, 1, 3))))
    }

    @Test fun groupedChartSummaryUsesTheGroupingAndItsLabels() {
        val weeks = listOf(DailySales(monday.minusDays(7), 9, 300_000), DailySales(monday, 2, 21_500))
        assertEquals(
            "Weekly sales, this month: ₹3,215 from 11 orders. Highest week of 19 Oct, ₹3,000. Lowest week of 26 Oct, ₹215.",
            salesChartSummary(weeks, "this month", "Weekly") { "week of ${shortDate(it)}" },
        )
    }

    @Test fun chartSummaryNamesTotalBestAndLowestDays() {
        val points = listOf(DailySales(monday.minusDays(1), 3, 24_000), DailySales(monday, 0, 0))
        assertEquals(
            "Daily sales, last 2 days: ₹240 from 3 orders. Highest Sun 25 Oct, ₹240. Lowest Mon 26 Oct, ₹0.",
            salesChartSummary(points, "last 2 days"),
        )
    }
}
