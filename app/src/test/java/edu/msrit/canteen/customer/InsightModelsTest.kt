package edu.msrit.canteen.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightModelsTest {
    private val day = LocalDate.of(2026, 10, 22)
    private val rows = listOf(
        SampleSale("a", "seller-a", "buyer-1", day.atTime(10, 0), listOf(
            SampleSaleLine("item-1", "cat-1", 2, 20000, 1000))),
        SampleSale("b", "seller-a", "buyer-1", day.minusDays(1).atTime(12, 0), listOf(
            SampleSaleLine("item-1", "cat-1", 1, 10000, 0))),
        SampleSale("c", "seller-b", "buyer-2", day.atTime(13, 0), listOf(
            SampleSaleLine("item-2", "cat-2", 1, 40000, 0))),
    )
    private fun summary(seller: String? = "seller-a", range: InsightRange = InsightRange(day.minusDays(2), day)) =
        calculateSales(rows, seller, range, mapOf("item-1" to "Lunch", "item-2" to "Tea"),
            mapOf("cat-1" to "Meals", "cat-2" to "Drinks"))

    @Test fun sellerMetricsExcludeOtherSellerAndCalculateNet() {
        val value = summary()
        assertEquals(2, value.orders); assertEquals(1, value.customers)
        assertEquals(30000L, value.grossPaise); assertEquals(1000L, value.discountPaise)
        assertEquals(29000L, value.netPaise); assertEquals(14500L, value.averagePaise)
    }
    @Test fun aggregateMetricsCountEverySampleOrderExactlyOnce() {
        val value = calculateSales(rows + rows.first(), null, InsightRange(day.minusDays(2), day), emptyMap(), emptyMap())
        assertEquals(3, value.orders); assertEquals(69000L, value.netPaise)
    }
    @Test fun productCategoryAndReturningMetricsAreDeterministic() {
        val value = summary()
        assertEquals(MetricRow("item-1", "Lunch", 3, 29000), value.products.single())
        assertEquals(MetricRow("cat-1", "Meals", 3, 29000), value.categories.single())
        assertEquals(1, value.returningCustomers)
    }
    @Test fun customRangeAndEmptyDataDoNotInventSales() {
        assertEquals(1, summary(range = InsightRange(day, day)).orders)
        assertEquals(SalesSummary.EMPTY, summary(range = InsightRange(day.plusDays(1), day.plusDays(2))))
        assertEquals(SalesSummary.EMPTY, summary(range = InsightRange(day, day.minusDays(1))))
    }
    @Test fun periodBoundariesUseLocalCalendar() {
        assertEquals(day, InsightPeriod.TODAY.range(day).start)
        assertEquals(day.minusDays(1), InsightPeriod.YESTERDAY.range(day).end)
        assertEquals(day.withDayOfMonth(1), InsightPeriod.MONTH.range(day).start)
        assertEquals(day.withDayOfYear(1), InsightPeriod.YEAR.range(day).start)
    }
    @Test fun reportValidationRejectsCrossSellerItemsAndReverseDates() {
        assertEquals("Select only this seller's items", validateReport(
            ReportSelection("seller-a", ReportType.ITEMS, InsightRange(day, day), setOf("foreign")), setOf("item-1")))
        assertEquals("End date must be on or after start date", validateReport(
            ReportSelection("seller-a", ReportType.SALES, InsightRange(day, day.minusDays(1))), setOf("item-1")))
        assertEquals(null, validateReport(ReportSelection("seller-a", ReportType.SALES,
            InsightRange(day, day), setOf("item-1")), setOf("item-1")))
    }
    @Test fun ticketsFilterBySellerAndStatus() {
        val tickets = listOf(
            SupportTicket("1", "seller-a", "A", TicketCategory.FOOD, "Food", "", TicketStatus.OPEN, false, day.atStartOfDay()),
            SupportTicket("2", "seller-b", "B", TicketCategory.FOOD, "Food", "", TicketStatus.OPEN, false, day.atStartOfDay()),
            SupportTicket("3", "seller-a", "C", TicketCategory.SCHEDULE, "Time", "", TicketStatus.RESOLVED, false, day.atStartOfDay()))
        assertEquals(listOf("1"), visibleTickets(tickets, "seller-a", false, TicketStatus.OPEN).map { it.id })
        assertEquals(3, visibleTickets(tickets, null, true).size)
    }
    @Test fun trendGroupingPreservesOrdersAndRevenue() {
        val points = listOf(DailySales(day, 1, 1000), DailySales(day.minusDays(1), 2, 3000),
            DailySales(day.minusDays(20), 1, 500))
        assertEquals(3, groupSales(points, TrendGrouping.DAILY).size)
        assertEquals(4, groupSales(points, TrendGrouping.WEEKLY).sumOf { it.orders })
        assertEquals(4500L, groupSales(points, TrendGrouping.MONTHLY).sumOf { it.netPaise })
    }
}
