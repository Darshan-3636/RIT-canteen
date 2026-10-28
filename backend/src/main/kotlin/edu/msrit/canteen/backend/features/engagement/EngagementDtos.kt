package edu.msrit.canteen.backend.features.engagement

import kotlinx.serialization.Serializable

@Serializable
data class PreferencesDto(
    val operationalNotifications: Boolean,
    val transactionalNotifications: Boolean,
    val promotionalNotifications: Boolean,
    val personalizationConsent: Boolean,
    val updatedAt: String? = null,
)

@Serializable
data class UpdatePreferencesRequest(
    val operationalNotifications: Boolean? = null,
    val transactionalNotifications: Boolean? = null,
    val promotionalNotifications: Boolean? = null,
    val personalizationConsent: Boolean? = null,
)

@Serializable
enum class FavoriteType { SELLER, ITEM }

@Serializable
data class FavoriteDto(val targetType: FavoriteType, val targetId: String, val sellerId: String, val createdAt: String)

@Serializable
data class FavoritesResponse(val items: List<FavoriteDto>)

@Serializable
data class TemplateLineDto(val itemId: String, val quantity: Int)

@Serializable
data class BulkTemplateDto(
    val id: String,
    val sellerId: String,
    val name: String,
    val lines: List<TemplateLineDto>,
    /** Always false here: Commerce revalidates items and quantities on actual submission. */
    val catalogValidated: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class CreateBulkTemplateRequest(val sellerId: String, val name: String, val lines: List<TemplateLineDto>)

@Serializable
data class UpdateBulkTemplateRequest(val name: String? = null, val lines: List<TemplateLineDto>? = null)

@Serializable
data class BulkTemplatesResponse(val items: List<BulkTemplateDto>)

@Serializable
data class NotificationDto(
    val id: String,
    val eventType: String,
    val title: String,
    val body: String,
    val ticketId: String? = null,
    val deliveryChannel: String = "IN_APP_ONLY",
    val read: Boolean,
    val readAt: String? = null,
    val createdAt: String,
)

@Serializable
data class NotificationsResponse(val items: List<NotificationDto>, val unreadCount: Long, val limit: Int, val offset: Int)

@Serializable
enum class SupportCategory { FOOD, SCHEDULE, PAYMENT, ACCOUNT, APP }

@Serializable
enum class TicketStatus { OPEN, ESCALATED, RESOLVED, CLOSED }

@Serializable
data class CreateTicketRequest(
    val category: SupportCategory,
    val subject: String,
    val message: String,
    val sellerId: String? = null,
    val requestReference: String? = null,
    val clientRequestId: String? = null,
)

@Serializable
data class PostMessageRequest(val body: String, val clientMessageId: String? = null)

@Serializable
data class UpdateTicketStatusRequest(val status: TicketStatus)

@Serializable
data class TicketSummaryDto(
    val id: String,
    val category: SupportCategory,
    val subject: String,
    val status: TicketStatus,
    val sellerId: String? = null,
    val requestReference: String? = null,
    /** Populated only for platform administrators. */
    val customerUserId: String? = null,
    val escalatedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class TicketMessageDto(val id: String, val authorRole: String, val mine: Boolean, val body: String, val createdAt: String)

@Serializable
data class StatusChangeDto(val fromStatus: TicketStatus? = null, val toStatus: TicketStatus, val actorRole: String, val changedAt: String)

@Serializable
data class TicketDetailDto(val ticket: TicketSummaryDto, val messages: List<TicketMessageDto>, val statusHistory: List<StatusChangeDto>)

@Serializable
data class DemoRequestCountsDto(val total: Int, val byStatus: Map<String, Int>)

@Serializable
data class SimulatedSalesDto(val orderCount: Int, val totalPaise: Long, val label: String = "DEMO_SIMULATED_NOT_VERIFIED")

@Serializable
data class SupportCountsDto(val open: Long, val escalated: Long, val resolved: Long, val closed: Long)

@Serializable
data class AnalyticsSummaryDto(
    val scope: String,
    val sellerId: String? = null,
    val from: String? = null,
    val to: String? = null,
    val timezone: String = "Asia/Kolkata",
    val dataStatus: String = "UNVERIFIED_OR_INSUFFICIENT",
    /** No verified payment source exists; this is never derived from unpaid requests. */
    val verifiedRevenuePaise: Long? = null,
    val source: String = "NO_VERIFIED_PAYMENTS",
    val demoRequestCounts: DemoRequestCountsDto? = null,
    val demoRequestCountsStatus: String,
    val simulatedSales: SimulatedSalesDto? = null,
    val forecast: String = "INSUFFICIENT_DATA",
)

@Serializable
data class DashboardSummaryDto(
    val sellerId: String,
    val insufficientData: Boolean,
    val dataStatus: String = "UNVERIFIED_OR_INSUFFICIENT",
    val verifiedRevenuePaise: Long? = null,
    val demoRequestCounts: DemoRequestCountsDto? = null,
    val demoRequestCountsStatus: String,
    val simulatedSales: SimulatedSalesDto? = null,
    val supportTickets: SupportCountsDto,
)

@Serializable
data class CreateReportSelectionRequest(
    val name: String,
    val metrics: List<String>,
    val from: String? = null,
    val to: String? = null,
)

@Serializable
data class ReportSelectionDto(
    val id: String,
    val sellerId: String,
    val name: String,
    val metrics: List<String>,
    val from: String? = null,
    val to: String? = null,
    val exportAvailable: Boolean = false,
    val createdAt: String,
)

@Serializable
data class ReportSelectionsResponse(val items: List<ReportSelectionDto>)
