package edu.msrit.canteen.ui.components

import edu.msrit.canteen.customer.AdminAuditEvent
import edu.msrit.canteen.customer.AdminSellerRecord
import edu.msrit.canteen.customer.AdminSellerStatus
import edu.msrit.canteen.customer.AdminUserRecord
import edu.msrit.canteen.customer.ModerationRecord
import edu.msrit.canteen.customer.SupportTicket
import edu.msrit.canteen.customer.TicketCategory
import edu.msrit.canteen.customer.TicketStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AdminPresentationTest {
    private val now = LocalDateTime.of(2026, 10, 26, 15, 0)
    private fun ticket(id: String, status: TicketStatus, escalated: Boolean = false, sellerId: String? = "north-counter",
                       hoursAgo: Long = 1, category: TicketCategory = TicketCategory.FOOD) =
        SupportTicket(id, sellerId, "Sample customer", category, "Subject $id", "Detail", status, escalated, now.minusHours(hoursAgo))
    private val sellers = listOf(
        AdminSellerRecord("main-canteen", "Main Canteen", AdminSellerStatus.ACTIVE, "No verified invitation"),
        AdminSellerRecord("north-counter", "North Counter · demo", AdminSellerStatus.ACTIVE, "Sample owner membership"),
        AdminSellerRecord("sample-seller-1", "Juice Stall", AdminSellerStatus.DRAFT, "No account or invitation created"),
        AdminSellerRecord("old-stall", "Old Stall", AdminSellerStatus.INACTIVE, "No verified invitation"))
    private val moderation = listOf(
        ModerationRecord("M-1", "north-counter", "Menu text", "Catalog", ADMIN_MODERATION_PENDING),
        ModerationRecord("M-2", "main-canteen", "Promo copy", "Promotion", "Reviewed locally"))
    private val names = mapOf("north-counter" to "North Counter · demo", "main-canteen" to "Main Canteen")

    @Test fun countsIgnoreResolvedTicketsAndReviewedContent() {
        val tickets = listOf(ticket("A", TicketStatus.OPEN), ticket("B", TicketStatus.IN_PROGRESS, escalated = true),
            ticket("C", TicketStatus.RESOLVED, escalated = true), ticket("D", TicketStatus.CLOSED))
        assertEquals(AdminOverviewCounts(activeSellers = 2, setupDrafts = 1, openTickets = 2, escalated = 1, toReview = 1),
            adminOverviewCounts(sellers, tickets, moderation))
    }

    @Test fun countsAreZeroForEmptyData() {
        assertEquals(AdminOverviewCounts(0, 0, 0, 0, 0), adminOverviewCounts(emptyList(), emptyList(), emptyList()))
    }

    @Test fun attentionQueueOrdersEscalatedThenPlatformThenSellerThenReviewThenDrafts() {
        val tickets = listOf(
            ticket("seller-old", TicketStatus.OPEN, hoursAgo = 5),
            ticket("seller-new", TicketStatus.OPEN, hoursAgo = 1),
            ticket("platform", TicketStatus.OPEN, sellerId = null, category = TicketCategory.PAYMENT, hoursAgo = 9),
            ticket("escalated", TicketStatus.IN_PROGRESS, escalated = true, hoursAgo = 20),
            ticket("done", TicketStatus.RESOLVED, escalated = true))
        val queue = adminAttentionQueue(tickets, moderation, sellers, names)
        assertEquals(listOf("escalated", "platform", "seller-new", "seller-old", "M-1", "sample-seller-1"), queue.map { it.targetId })
        assertEquals(listOf(AdminAttentionKind.ESCALATED_TICKET, AdminAttentionKind.PLATFORM_TICKET,
            AdminAttentionKind.SELLER_TICKET, AdminAttentionKind.SELLER_TICKET, AdminAttentionKind.CONTENT_REVIEW,
            AdminAttentionKind.SETUP_DRAFT), queue.map { it.kind })
    }

    @Test fun attentionItemsNameTheSellerOrPlatformRouting() {
        val queue = adminAttentionQueue(listOf(ticket("s", TicketStatus.OPEN),
            ticket("p", TicketStatus.OPEN, sellerId = null, category = TicketCategory.PAYMENT)), emptyList(), emptyList(), names)
        assertEquals("Platform support · Payment issue", queue[0].detail)
        assertEquals("North Counter · demo · Food or fulfillment", queue[1].detail)
        assertEquals("Subject p", queue[0].title)
    }

    @Test fun attentionQueueIsEmptyWhenNothingNeedsAction() {
        assertTrue(adminAttentionQueue(listOf(ticket("x", TicketStatus.CLOSED)),
            moderation.filterNot { it.state == ADMIN_MODERATION_PENDING }, sellers.filterNot { it.status == AdminSellerStatus.DRAFT },
            names).isEmpty())
    }

    @Test fun unknownSellerIdFallsBackToTheIdNotAnInventedName() {
        val queue = adminAttentionQueue(listOf(ticket("x", TicketStatus.OPEN, sellerId = "mystery")), emptyList(), emptyList(), names)
        assertEquals("mystery · Food or fulfillment", queue.single().detail)
    }

    @Test fun supportOrderPutsUnresolvedEscalationsFirstThenNewest() {
        val tickets = listOf(ticket("resolved", TicketStatus.RESOLVED, hoursAgo = 0),
            ticket("open-old", TicketStatus.OPEN, hoursAgo = 8), ticket("open-new", TicketStatus.OPEN, hoursAgo = 2),
            ticket("esc", TicketStatus.OPEN, escalated = true, hoursAgo = 30))
        assertEquals(listOf("esc", "open-new", "open-old", "resolved"), adminSupportOrder(tickets).map { it.id })
    }

    @Test fun ticketToneReflectsStatusAndEscalation() {
        assertEquals(AdminTone.URGENT, adminTicketTone(ticket("a", TicketStatus.OPEN, escalated = true)))
        assertEquals(AdminTone.ACTION, adminTicketTone(ticket("b", TicketStatus.IN_PROGRESS)))
        assertEquals(AdminTone.DONE, adminTicketTone(ticket("c", TicketStatus.RESOLVED, escalated = true)))
        assertEquals(AdminTone.DONE, adminTicketTone(ticket("d", TicketStatus.CLOSED)))
    }

    @Test fun sellerFilterMatchesNameOrIdAndStatusTogether() {
        assertEquals(listOf("north-counter"), adminFilterSellers(sellers, "  north ", null).map { it.id })
        assertEquals(listOf("sample-seller-1"), adminFilterSellers(sellers, "sample-seller", null).map { it.id })
        assertEquals(listOf("main-canteen", "north-counter"), adminFilterSellers(sellers, "", AdminSellerStatus.ACTIVE).map { it.id })
        assertTrue(adminFilterSellers(sellers, "juice", AdminSellerStatus.ACTIVE).isEmpty())
        assertEquals(sellers, adminFilterSellers(sellers, "", null))
    }

    private val users = listOf(
        AdminUserRecord("c", "Sample customer", "Customer", "Demo active", "Student · self-declared"),
        AdminUserRecord("o", "North Counter Owner", "Seller owner", "Demo active"),
        AdminUserRecord("m", "North Counter Manager", "Seller manager", "Demo active"),
        AdminUserRecord("s", "North Counter Staff", "Operational staff", "Demo active"),
        AdminUserRecord("a", "Demo Administrator", "Platform administrator", "Demo active"),
        AdminUserRecord("x", "Unknown Role", "Auditor", "Demo active"))

    @Test fun userGroupsComeFromTheRecordedRoleOnly() {
        assertEquals(listOf(AdminUserGroup.CUSTOMERS, AdminUserGroup.SELLER_TEAM, AdminUserGroup.SELLER_TEAM,
            AdminUserGroup.SELLER_TEAM, AdminUserGroup.PLATFORM, null), users.map { adminUserGroup(it) })
    }

    @Test fun userFilterCombinesSearchAndGroup() {
        assertEquals(listOf("o", "m", "s"), adminFilterUsers(users, "", AdminUserGroup.SELLER_TEAM).map { it.id })
        assertEquals(listOf("m"), adminFilterUsers(users, "manager", AdminUserGroup.SELLER_TEAM).map { it.id })
        assertEquals(listOf("c"), adminFilterUsers(users, "customer", null).map { it.id })
        assertTrue(adminFilterUsers(users, "owner", AdminUserGroup.CUSTOMERS).isEmpty())
        assertEquals(users, adminFilterUsers(users, " ", null))
    }

    @Test fun relativeTimeIsShortAndHonest() {
        assertEquals("Just now", adminRelativeTime(now.minusSeconds(30), now))
        assertEquals("12 min ago", adminRelativeTime(now.minusMinutes(12), now))
        assertEquals("3 h ago", adminRelativeTime(now.minusHours(3), now))
        assertEquals("Yesterday", adminRelativeTime(now.minusDays(1).withHour(9), now))
        assertEquals("21 Oct", adminRelativeTime(now.minusDays(5), now))
        assertEquals("Just now", adminRelativeTime(now.plusMinutes(5), now))
    }

    @Test fun ticketFilterCombinesEscalationAndStatusThenUsesSupportOrder() {
        val tickets = listOf(ticket("open", TicketStatus.OPEN, hoursAgo = 1),
            ticket("esc-open", TicketStatus.OPEN, escalated = true, hoursAgo = 9),
            ticket("esc-done", TicketStatus.RESOLVED, escalated = true, hoursAgo = 0),
            ticket("progress", TicketStatus.IN_PROGRESS, hoursAgo = 3))
        assertEquals(listOf("esc-open", "open", "progress", "esc-done"), adminFilterTickets(tickets, AdminTicketFilter.ALL).map { it.id })
        assertEquals(listOf("esc-open", "open", "progress"), adminFilterTickets(tickets, AdminTicketFilter.NEEDS_ACTION).map { it.id })
        assertEquals(listOf("esc-open", "esc-done"), adminFilterTickets(tickets, AdminTicketFilter.ESCALATED).map { it.id })
        assertEquals(listOf("esc-done"), adminFilterTickets(tickets, AdminTicketFilter.DONE).map { it.id })
        assertTrue(adminFilterTickets(emptyList(), AdminTicketFilter.ESCALATED).isEmpty())
    }

    @Test fun auditGroupsByDayNewestFirstWithReadableLabels() {
        fun event(id: String, at: LocalDateTime) = AdminAuditEvent(id, "demo-admin", "Action", "entity", "a", "b", at)
        val groups = adminAuditByDay(listOf(event("old", now.minusDays(4)), event("today-early", now.minusHours(5)),
            event("yesterday", now.minusDays(1)), event("today-late", now.minusMinutes(2))), now.toLocalDate())
        assertEquals(listOf("Today", "Yesterday", "22 Oct"), groups.map { it.first })
        assertEquals(listOf("today-late", "today-early"), groups.first().second.map { it.id })
        assertTrue(adminAuditByDay(emptyList(), now.toLocalDate()).isEmpty())
    }
}
