package edu.msrit.canteen.ui.components

import edu.msrit.canteen.customer.AdminAuditEvent
import edu.msrit.canteen.customer.AdminSellerRecord
import edu.msrit.canteen.customer.AdminSellerStatus
import edu.msrit.canteen.customer.AdminUserRecord
import edu.msrit.canteen.customer.ModerationRecord
import edu.msrit.canteen.customer.SupportTicket
import edu.msrit.canteen.customer.TicketStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display-only helpers for platform admin. They sort and count authorized records; they never change them. */

/** The moderation fixture stores state as text; this is the only state that still needs an admin. */
const val ADMIN_MODERATION_PENDING = "Needs review"

enum class AdminTone { URGENT, ACTION, NEUTRAL, DONE }

fun SupportTicket.adminNeedsAction() = status == TicketStatus.OPEN || status == TicketStatus.IN_PROGRESS

fun adminTicketTone(ticket: SupportTicket): AdminTone = when {
    !ticket.adminNeedsAction() -> AdminTone.DONE
    ticket.escalated -> AdminTone.URGENT
    else -> AdminTone.ACTION
}

data class AdminOverviewCounts(val activeSellers: Int, val setupDrafts: Int, val openTickets: Int,
                               val escalated: Int, val toReview: Int)

fun adminOverviewCounts(sellers: List<AdminSellerRecord>, tickets: List<SupportTicket>,
                        moderation: List<ModerationRecord>): AdminOverviewCounts {
    val open = tickets.filter { it.adminNeedsAction() }
    return AdminOverviewCounts(sellers.count { it.status == AdminSellerStatus.ACTIVE },
        sellers.count { it.status == AdminSellerStatus.DRAFT }, open.size, open.count { it.escalated },
        moderation.count { it.state == ADMIN_MODERATION_PENDING })
}

/** Ordered by urgency: escalations, then platform-routed (payment/account/app), seller tickets, content, setup. */
enum class AdminAttentionKind { ESCALATED_TICKET, PLATFORM_TICKET, SELLER_TICKET, CONTENT_REVIEW, SETUP_DRAFT }

data class AdminAttentionItem(val kind: AdminAttentionKind, val targetId: String, val title: String,
                              val detail: String, val at: LocalDateTime? = null)

/** A seller id without a known name is shown as-is rather than guessed. */
fun adminSellerLabel(sellerId: String?, sellerNames: Map<String, String>) =
    if (sellerId == null) "Platform support" else sellerNames[sellerId] ?: sellerId

fun adminAttentionQueue(tickets: List<SupportTicket>, moderation: List<ModerationRecord>,
                        sellers: List<AdminSellerRecord>, sellerNames: Map<String, String>): List<AdminAttentionItem> {
    val ticketItems = tickets.filter { it.adminNeedsAction() }.map {
        val kind = when {
            it.escalated -> AdminAttentionKind.ESCALATED_TICKET
            it.sellerId == null -> AdminAttentionKind.PLATFORM_TICKET
            else -> AdminAttentionKind.SELLER_TICKET
        }
        AdminAttentionItem(kind, it.id, it.subject, "${adminSellerLabel(it.sellerId, sellerNames)} · ${it.category.label}", it.updatedAt)
    }.sortedWith(compareBy<AdminAttentionItem> { it.kind.ordinal }.thenByDescending { it.at })
    val reviewItems = moderation.filter { it.state == ADMIN_MODERATION_PENDING }.map {
        AdminAttentionItem(AdminAttentionKind.CONTENT_REVIEW, it.id, it.subject, "${it.kind} · ${adminSellerLabel(it.sellerId, sellerNames)}")
    }
    val draftItems = sellers.filter { it.status == AdminSellerStatus.DRAFT }.map {
        AdminAttentionItem(AdminAttentionKind.SETUP_DRAFT, it.id, it.name, "Setup draft · ${it.ownerInviteStatus}")
    }
    return ticketItems + reviewItems + draftItems
}

fun adminSupportOrder(tickets: List<SupportTicket>): List<SupportTicket> =
    tickets.sortedWith(compareBy<SupportTicket> { adminTicketTone(it).ordinal }.thenByDescending { it.updatedAt })

enum class AdminTicketFilter(val label: String) { ALL("All"), NEEDS_ACTION("Needs action"), ESCALATED("Escalated"), DONE("Resolved or closed") }

fun adminFilterTickets(tickets: List<SupportTicket>, filter: AdminTicketFilter): List<SupportTicket> = adminSupportOrder(tickets.filter {
    when (filter) {
        AdminTicketFilter.ALL -> true
        AdminTicketFilter.NEEDS_ACTION -> it.adminNeedsAction()
        AdminTicketFilter.ESCALATED -> it.escalated
        AdminTicketFilter.DONE -> !it.adminNeedsAction()
    }
})

fun adminFilterSellers(sellers: List<AdminSellerRecord>, query: String, status: AdminSellerStatus?): List<AdminSellerRecord> {
    val q = query.trim()
    return sellers.filter { (status == null || it.status == status) && (it.name.contains(q, true) || it.id.contains(q, true)) }
}

enum class AdminUserGroup(val label: String) { CUSTOMERS("Customers"), SELLER_TEAM("Seller team"), PLATFORM("Platform admin") }

/** Grouped from the recorded role label only; an unrecognised role stays ungrouped instead of being guessed. */
fun adminUserGroup(user: AdminUserRecord): AdminUserGroup? = when (user.roleLabel) {
    "Customer" -> AdminUserGroup.CUSTOMERS
    "Seller owner", "Seller manager", "Operational staff" -> AdminUserGroup.SELLER_TEAM
    "Platform administrator" -> AdminUserGroup.PLATFORM
    else -> null
}

fun adminFilterUsers(users: List<AdminUserRecord>, query: String, group: AdminUserGroup?): List<AdminUserRecord> {
    val q = query.trim()
    return users.filter { (group == null || adminUserGroup(it) == group) &&
        (it.displayName.contains(q, true) || it.roleLabel.contains(q, true)) }
}

/** Audit events grouped under Today / Yesterday / "d MMM", newest day and newest event first. */
fun adminAuditByDay(events: List<AdminAuditEvent>, today: LocalDate): List<Pair<String, List<AdminAuditEvent>>> =
    events.sortedByDescending { it.at }.groupBy { it.at.toLocalDate() }.map { (day, rows) ->
        when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> day.format(shortDate)
        } to rows
    }

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun adminRelativeTime(at: LocalDateTime, now: LocalDateTime): String {
    val elapsed = Duration.between(at, now)
    return when {
        elapsed.toMinutes() < 1 -> "Just now"
        elapsed.toHours() < 1 -> "${elapsed.toMinutes()} min ago"
        elapsed.toHours() < 24 -> "${elapsed.toHours()} h ago"
        at.toLocalDate() == now.toLocalDate().minusDays(1) -> "Yesterday"
        else -> at.format(shortDate)
    }
}
