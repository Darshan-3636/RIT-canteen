package edu.msrit.canteen.customer

import edu.msrit.canteen.navigation.AuthorizedIdentity

/** Deliberately inert: no mock analytics, administration or privileged mutation in release. */
object DevelopmentInsights {
    fun sales(identity: AuthorizedIdentity?, sellerId: String?, range: InsightRange, sample: Boolean) = SalesSummary.EMPTY
    fun tickets(identity: AuthorizedIdentity?, sellerId: String? = null, status: TicketStatus? = null): List<SupportTicket> = emptyList()
    fun ticket(identity: AuthorizedIdentity?, id: String, sellerId: String? = null): SupportTicket? = null
    fun createSellerTicket(identity: AuthorizedIdentity?, sellerId: String, category: TicketCategory,
                           subject: String, detail: String) = false
    fun setTicketStatus(identity: AuthorizedIdentity?, id: String, status: TicketStatus) = false
    fun saveInternalNote(identity: AuthorizedIdentity?, id: String, note: String) = false
    fun sellers(identity: AuthorizedIdentity?): List<AdminSellerRecord> = emptyList()
    fun sellerRecord(identity: AuthorizedIdentity?, id: String): AdminSellerRecord? = null
    fun createSellerDraft(identity: AuthorizedIdentity?, name: String) = false
    fun users(identity: AuthorizedIdentity?): List<AdminUserRecord> = emptyList()
    fun moderation(identity: AuthorizedIdentity?): List<ModerationRecord> = emptyList()
    fun markModerationReviewed(identity: AuthorizedIdentity?, id: String) = false
    fun audit(identity: AuthorizedIdentity?): List<AdminAuditEvent> = emptyList()
    fun reports(identity: AuthorizedIdentity?, sellerId: String): List<ReportHistoryRow> = emptyList()
    fun saveReportPreview(identity: AuthorizedIdentity?, selection: ReportSelection) = false
    fun notificationChoices(identity: AuthorizedIdentity?, sellerId: String): Set<String> = emptySet()
    fun setNotificationChoice(identity: AuthorizedIdentity?, sellerId: String, key: String, enabled: Boolean) = false
    fun reset() = Unit
}
