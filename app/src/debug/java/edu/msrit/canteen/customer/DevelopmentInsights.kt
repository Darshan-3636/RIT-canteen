package edu.msrit.canteen.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.msrit.canteen.BuildConfig
import edu.msrit.canteen.navigation.AuthorizedIdentity
import edu.msrit.canteen.navigation.PresentationAccess
import edu.msrit.canteen.navigation.ProtectedArea
import java.time.LocalDate
import java.time.LocalDateTime

/** Isolated synthetic fixtures. No operation or paid-looking quote enters this data source. */
object DevelopmentInsights {
    private val today get() = LocalDate.now(RIT_TIME_ZONE)
    private fun at(daysAgo: Long, hour: Int) = today.minusDays(daysAgo).atTime(hour, 15)
    private val fixtureSales get() = (0L..29L).flatMap { day ->
        val lunchUnits = 2 + (day % 4).toInt()
        val dosaUnits = 1 + (day % 3).toInt()
        buildList {
            add(SampleSale("SAMPLE-NC-L-$day", "north-counter", "sample-buyer-${day % 9}", at(day, 12), listOf(
                SampleSaleLine("north-lunch", "combos", lunchUnits, lunchUnits * 9_000L,
                    if (day % 7L == 0L) 3_000L else 0L))))
            if (day % 2L == 0L) {
                val snackUnits = 1 + (day % 3).toInt()
                add(SampleSale("SAMPLE-NC-S-$day", "north-counter", "sample-buyer-${(day + 3) % 9}", at(day, 16), listOf(
                    SampleSaleLine("north-snack", "snacks", snackUnits, snackUnits * 6_500L, 0))))
            }
            add(SampleSale("SAMPLE-MAIN-D-$day", PreviewCatalog.MAIN_ID, "sample-main-${day % 12}", at(day, 10), listOf(
                SampleSaleLine("masala-dosa", "dosa", dosaUnits, dosaUnits * 5_000L, 0))))
            if (day % 3L == 0L) add(SampleSale("SAMPLE-MAIN-C-$day", PreviewCatalog.MAIN_ID,
                "sample-main-${(day + 5) % 12}", at(day, 15), listOf(
                    SampleSaleLine("filter-coffee", "drinks", 2, 6_000L, 0))))
            if (day % 4L == 0L) add(SampleSale("SAMPLE-CC-$day", "coffee-corner",
                "sample-corner-${day % 5}", at(day, 17), listOf(
                    SampleSaleLine("corner-coffee", "drinks", 4, 14_000L, 0))))
            if (day % 5L == 0L) add(SampleSale("SAMPLE-PANTRY-$day", "campus-pantry",
                "sample-pantry-${day % 4}", at(day, 13), listOf(
                    SampleSaleLine("pantry-juice", "drinks", 6, 18_000L, 0))))
        }
    }
    private val ticketFixtures get() = listOf(
        SupportTicket("SAMPLE-T-101", "north-counter", "Sample customer A", TicketCategory.FOOD,
            "Missing item in group request", "Customer says one item was missing; not a real complaint.",
            TicketStatus.OPEN, false, at(0, 14)),
        SupportTicket("SAMPLE-T-102", "north-counter", "Sample customer B", TicketCategory.SCHEDULE,
            "Pickup time question", "Sample scheduling issue for layout testing.",
            TicketStatus.IN_PROGRESS, true, at(1, 16)),
        SupportTicket("SAMPLE-T-103", null, "Sample customer C", TicketCategory.PAYMENT,
            "Payment status dispute", "No provider transaction is associated with this example.",
            TicketStatus.OPEN, false, at(0, 9)),
    )
    private var ticketsState by mutableStateOf<List<SupportTicket>>(ticketFixtures)
    private var sellerDrafts by mutableStateOf<List<AdminSellerRecord>>(emptyList())
    private var moderationState by mutableStateOf(listOf(
        ModerationRecord("SAMPLE-M-1", "north-counter", "Sample menu description", "Catalog", "Needs review"),
        ModerationRecord("SAMPLE-M-2", PreviewCatalog.MAIN_ID, "Sample promotional copy", "Promotion", "Needs review")))
    private val initialAudit get() = listOf(AdminAuditEvent("SAMPLE-AUDIT-1", "demo-admin",
        "Sample seller review", "north-counter", "Draft example", "Reviewed example", at(1, 10)))
    private val initialReports get() = listOf(ReportHistoryRow("north-counter:sample-saved",
        "Weekly sales · sample configuration", InsightRange(today.minusDays(6), today), at(0, 9),
        "Saved preview · no XLSX generated", true))
    private var auditState by mutableStateOf<List<AdminAuditEvent>>(initialAudit)
    private var reportRows by mutableStateOf<List<ReportHistoryRow>>(initialReports)
    private var notificationPrefs by mutableStateOf<Map<String, Set<String>>>(emptyMap())

    private fun admin(identity: AuthorizedIdentity?) = PresentationAccess.allows(identity, ProtectedArea.Administrator)
    private fun seller(identity: AuthorizedIdentity?, sellerId: String, capability: SellerCapability) =
        PresentationAccess.allows(identity, ProtectedArea.Seller(sellerId, capability.wire))

    fun sales(identity: AuthorizedIdentity?, sellerId: String?, range: InsightRange, sample: Boolean): SalesSummary {
        val authorized = if (sellerId == null) admin(identity) else
            admin(identity) || seller(identity, sellerId, SellerCapability.ANALYTICS)
        if (BuildConfig.RIT_CLOUD_DEMO || !authorized || !sample) return SalesSummary.EMPTY
        return calculateSales(fixtureSales, sellerId, range,
            PreviewCatalog.items.associate { it.id to it.name }, PreviewCatalog.categories.associate { it.id to it.name })
    }

    fun tickets(identity: AuthorizedIdentity?, sellerId: String? = null,
                status: TicketStatus? = null): List<SupportTicket> {
        if (BuildConfig.RIT_CLOUD_DEMO || !admin(identity) && (sellerId == null || !seller(identity, sellerId, SellerCapability.VIEW_REQUESTS))) return emptyList()
        return visibleTickets(ticketsState, sellerId, admin(identity), status)
    }
    fun ticket(identity: AuthorizedIdentity?, id: String, sellerId: String? = null): SupportTicket? =
        tickets(identity, sellerId).find { it.id == id }
    fun createSellerTicket(identity: AuthorizedIdentity?, sellerId: String, category: TicketCategory,
                           subject: String, detail: String): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO || !seller(identity, sellerId, SellerCapability.VIEW_REQUESTS) || subject.isBlank() ||
            detail.isBlank() || subject.length > 100 || detail.length > 1000) return false
        val id = "SAMPLE-T-${200 + ticketsState.size}"
        ticketsState = listOf(SupportTicket(id, sellerId, "Seller demo", category, subject.trim(),
            detail.trim(), TicketStatus.OPEN, false, LocalDateTime.now(RIT_TIME_ZONE))) + ticketsState
        return true
    }

    fun setTicketStatus(identity: AuthorizedIdentity?, id: String, status: TicketStatus): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO || !admin(identity) || ticketsState.none { it.id == id }) return false
        val old = ticketsState.first { it.id == id }
        ticketsState = ticketsState.map { if (it.id == id) it.copy(status = status, updatedAt = LocalDateTime.now(RIT_TIME_ZONE)) else it }
        log(identity, "Ticket status", id, old.status.label, status.label)
        return true
    }
    fun saveInternalNote(identity: AuthorizedIdentity?, id: String, note: String): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO || !admin(identity) || ticketsState.none { it.id == id } || note.length > 500) return false
        ticketsState = ticketsState.map { if (it.id == id) it.copy(internalNote = note.trim()) else it }
        log(identity, "Internal note", id, "Previous local note", "Local note updated")
        return true
    }

    fun sellers(identity: AuthorizedIdentity?): List<AdminSellerRecord> = if (BuildConfig.RIT_CLOUD_DEMO || !admin(identity)) emptyList() else
        PreviewCatalog.sellers.map { AdminSellerRecord(it.id, it.name, AdminSellerStatus.ACTIVE,
            if (it.id == "north-counter") "Sample owner membership" else "No verified invitation") } + sellerDrafts
    fun sellerRecord(identity: AuthorizedIdentity?, id: String) = sellers(identity).find { it.id == id }
    fun createSellerDraft(identity: AuthorizedIdentity?, name: String): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO || !admin(identity) || name.isBlank() || name.length > 80 || sellers(identity).any { it.name.equals(name.trim(), true) }) return false
        val id = "sample-seller-${sellerDrafts.size + 1}"
        sellerDrafts = sellerDrafts + AdminSellerRecord(id, name.trim(), AdminSellerStatus.DRAFT,
            "No account or invitation created")
        log(identity, "Seller setup draft", id, "Absent", "Local draft")
        return true
    }
    fun users(identity: AuthorizedIdentity?): List<AdminUserRecord> = if (BuildConfig.RIT_CLOUD_DEMO || !admin(identity)) emptyList() else listOf(
        AdminUserRecord("sample-customer", "Sample customer", "Customer", "Demo active", "Student · self-declared"),
        AdminUserRecord("debug-owner", "North Counter Owner", "Seller owner", "Demo active"),
        AdminUserRecord("debug-manager", "North Counter Manager", "Seller manager", "Demo active"),
        AdminUserRecord("debug-staff", "North Counter Staff", "Operational staff", "Demo active"),
        AdminUserRecord("debug-admin", "Demo Administrator", "Platform administrator", "Demo active"))
    fun moderation(identity: AuthorizedIdentity?): List<ModerationRecord> = if (!BuildConfig.RIT_CLOUD_DEMO && admin(identity)) moderationState else emptyList()
    fun markModerationReviewed(identity: AuthorizedIdentity?, id: String): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO || !admin(identity) || moderationState.none { it.id == id }) return false
        moderationState = moderationState.map { if (it.id == id) it.copy(state = "Reviewed locally") else it }
        log(identity, "Content review", id, "Needs review", "Reviewed locally")
        return true
    }
    fun audit(identity: AuthorizedIdentity?): List<AdminAuditEvent> = if (!BuildConfig.RIT_CLOUD_DEMO && admin(identity)) auditState else emptyList()
    private fun log(identity: AuthorizedIdentity?, action: String, entity: String, before: String, after: String) {
        auditState = listOf(AdminAuditEvent("sample-audit-${auditState.size + 1}", identity?.userId ?: "unknown",
            action, entity, before, after, LocalDateTime.now(RIT_TIME_ZONE))) + auditState
    }

    fun reports(identity: AuthorizedIdentity?, sellerId: String): List<ReportHistoryRow> =
        if (!BuildConfig.RIT_CLOUD_DEMO && seller(identity, sellerId, SellerCapability.REPORTS)) reportRows.filter { it.id.startsWith("$sellerId:") } else emptyList()
    fun saveReportPreview(identity: AuthorizedIdentity?, selection: ReportSelection): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO || !seller(identity, selection.sellerId, SellerCapability.REPORTS) ||
            validateReport(selection, PreviewCatalog.items.filter { it.sellerId == selection.sellerId }.map { it.id }.toSet()) != null) return false
        reportRows = listOf(ReportHistoryRow("${selection.sellerId}:sample-${reportRows.size + 1}",
            "${selection.type.label} · sample configuration", selection.range, LocalDateTime.now(RIT_TIME_ZONE),
            "Preview only · no XLSX generated", true)) + reportRows
        return true
    }
    fun notificationChoices(identity: AuthorizedIdentity?, sellerId: String): Set<String> =
        if (!BuildConfig.RIT_CLOUD_DEMO && seller(identity, sellerId, SellerCapability.VIEW_REQUESTS)) notificationPrefs[sellerId]
            ?: setOf("Requests", "Schedule", "Support") else emptySet()
    fun setNotificationChoice(identity: AuthorizedIdentity?, sellerId: String, key: String, enabled: Boolean): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO || !seller(identity, sellerId, SellerCapability.PROFILE) || key !in setOf("Requests", "Schedule", "Support", "Promotions")) return false
        val current = notificationChoices(identity, sellerId)
        notificationPrefs = notificationPrefs + (sellerId to if (enabled) current + key else current - key)
        return true
    }
    fun reset() {
        ticketsState = ticketFixtures; sellerDrafts = emptyList(); moderationState = listOf(
            ModerationRecord("SAMPLE-M-1", "north-counter", "Sample menu description", "Catalog", "Needs review"),
            ModerationRecord("SAMPLE-M-2", PreviewCatalog.MAIN_ID, "Sample promotional copy", "Promotion", "Needs review"))
        auditState = initialAudit; reportRows = initialReports; notificationPrefs = emptyMap()
    }
}
