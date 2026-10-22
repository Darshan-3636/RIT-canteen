package edu.msrit.canteen.customer

import java.time.LocalDate
import java.time.LocalDateTime

/** Presentation contract only. Synthetic rows are never provider-verified transactions. */
data class SampleSaleLine(val itemId: String, val categoryId: String, val units: Int,
                          val grossPaise: Long, val discountPaise: Long)
data class SampleSale(val orderId: String, val sellerId: String, val customerRef: String,
                      val paidAt: LocalDateTime, val lines: List<SampleSaleLine>) {
    val netPaise: Long get() = lines.sumOf { it.grossPaise - it.discountPaise }
}
data class InsightRange(val start: LocalDate, val end: LocalDate) {
    fun valid() = !end.isBefore(start)
    fun contains(date: LocalDate) = valid() && !date.isBefore(start) && !date.isAfter(end)
}
enum class InsightPeriod(val label: String) { TODAY("Today"), YESTERDAY("Yesterday"), WEEK("This week"),
    MONTH("This month"), YEAR("This year"), CUSTOM("Custom") }
fun InsightPeriod.range(today: LocalDate, custom: InsightRange? = null): InsightRange = when (this) {
    InsightPeriod.TODAY -> InsightRange(today, today)
    InsightPeriod.YESTERDAY -> InsightRange(today.minusDays(1), today.minusDays(1))
    InsightPeriod.WEEK -> InsightRange(today.with(java.time.DayOfWeek.MONDAY), today)
    InsightPeriod.MONTH -> InsightRange(today.withDayOfMonth(1), today)
    InsightPeriod.YEAR -> InsightRange(today.withDayOfYear(1), today)
    InsightPeriod.CUSTOM -> custom ?: InsightRange(today, today)
}
data class MetricRow(val id: String, val label: String, val units: Int, val netPaise: Long)
data class DailySales(val day: LocalDate, val orders: Int, val netPaise: Long)
enum class TrendGrouping(val label: String) { DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly") }
fun groupSales(points: List<DailySales>, grouping: TrendGrouping): List<DailySales> = points.groupBy { point ->
    when (grouping) {
        TrendGrouping.DAILY -> point.day
        TrendGrouping.WEEKLY -> point.day.with(java.time.DayOfWeek.MONDAY)
        TrendGrouping.MONTHLY -> point.day.withDayOfMonth(1)
    }
}.map { (day, rows) -> DailySales(day, rows.sumOf { it.orders }, rows.sumOf { it.netPaise }) }.sortedBy { it.day }
data class SalesSummary(val orders: Int, val customers: Int, val grossPaise: Long,
                        val discountPaise: Long, val netPaise: Long, val averagePaise: Long,
                        val products: List<MetricRow>, val categories: List<MetricRow>,
                        val daily: List<DailySales>, val returningCustomers: Int) {
    companion object { val EMPTY = SalesSummary(0, 0, 0, 0, 0, 0, emptyList(), emptyList(), emptyList(), 0) }
}

/** Input is exclusively the explicitly synthetic sample-sales fixture; never bulk request states. */
fun calculateSales(rows: List<SampleSale>, sellerId: String?, range: InsightRange,
                   itemNames: Map<String, String>, categoryNames: Map<String, String>): SalesSummary {
    if (!range.valid()) return SalesSummary.EMPTY
    val selected = rows.distinctBy { it.sellerId to it.orderId }.filter { (sellerId == null || it.sellerId == sellerId) &&
        range.contains(it.paidAt.toLocalDate()) }
    if (selected.isEmpty()) return SalesSummary.EMPTY
    val lines = selected.flatMap { it.lines }
    val gross = lines.sumOf { it.grossPaise }
    val discount = lines.sumOf { it.discountPaise }
    fun grouped(key: (SampleSaleLine) -> String, names: Map<String, String>) = lines.groupBy(key).map { (id, part) ->
        MetricRow(id, names[id] ?: id, part.sumOf { it.units }, part.sumOf { it.grossPaise - it.discountPaise })
    }.sortedWith(compareByDescending<MetricRow> { it.netPaise }.thenBy { it.label })
    val customerCounts = selected.groupingBy { it.customerRef }.eachCount()
    return SalesSummary(selected.size, customerCounts.size, gross, discount, gross - discount,
        (gross - discount) / selected.size,
        grouped({ it.itemId }, itemNames), grouped({ it.categoryId }, categoryNames),
        selected.groupBy { it.paidAt.toLocalDate() }.map { (date, sales) -> DailySales(date, sales.size,
            sales.sumOf { it.netPaise }) }.sortedBy { it.day }, customerCounts.values.count { it > 1 })
}

enum class ReportType(val label: String) { SALES("Sales summary"), ITEMS("Item-wise sales"),
    CATEGORIES("Category-wise sales"), ORDERS("Order details") }
data class ReportSelection(val sellerId: String, val type: ReportType, val range: InsightRange,
                           val itemIds: Set<String> = emptySet())
fun validateReport(selection: ReportSelection, sellerItems: Set<String>): String? = when {
    !selection.range.valid() -> "End date must be on or after start date"
    selection.itemIds.any { it !in sellerItems } -> "Select only this seller's items"
    else -> null
}
data class ReportHistoryRow(val id: String, val title: String, val range: InsightRange,
                            val generatedAt: LocalDateTime, val status: String, val sampleOnly: Boolean)

enum class TicketCategory(val label: String) { FOOD("Food or fulfillment"), SCHEDULE("Bulk scheduling"),
    PAYMENT("Payment issue"), ACCOUNT("Account access"), APP("App problem") }
enum class TicketStatus(val label: String) { OPEN("Open"), IN_PROGRESS("In progress"),
    RESOLVED("Resolved"), CLOSED("Closed") }
data class SupportTicket(val id: String, val sellerId: String?, val customerRef: String,
                         val category: TicketCategory, val subject: String, val detail: String,
                         val status: TicketStatus, val escalated: Boolean, val updatedAt: LocalDateTime,
                         val internalNote: String = "")
fun visibleTickets(tickets: List<SupportTicket>, sellerId: String?, admin: Boolean,
                   status: TicketStatus? = null): List<SupportTicket> = tickets.filter {
    (admin || it.sellerId == sellerId) && (status == null || it.status == status)
}.sortedByDescending { it.updatedAt }

enum class AdminSellerStatus(val label: String) { ACTIVE("Active"), DRAFT("Setup draft"), INACTIVE("Inactive") }
data class AdminSellerRecord(val id: String, val name: String, val status: AdminSellerStatus,
                             val ownerInviteStatus: String)
data class AdminUserRecord(val id: String, val displayName: String, val roleLabel: String,
                           val status: String, val affiliation: String? = null)
data class ModerationRecord(val id: String, val sellerId: String, val subject: String,
                            val kind: String, val state: String)
data class AdminAuditEvent(val id: String, val actor: String, val action: String,
                           val entity: String, val before: String, val after: String,
                           val at: LocalDateTime)
