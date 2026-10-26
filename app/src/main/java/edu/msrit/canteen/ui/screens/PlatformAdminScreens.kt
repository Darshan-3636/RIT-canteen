package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.AdminSellerStatus
import edu.msrit.canteen.customer.DevelopmentInsights
import edu.msrit.canteen.customer.InsightPeriod
import edu.msrit.canteen.customer.InsightRange
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.TicketCategory
import edu.msrit.canteen.customer.TicketStatus
import edu.msrit.canteen.customer.range
import edu.msrit.canteen.navigation.AuthorizedIdentity
import edu.msrit.canteen.navigation.PresentationAccess
import edu.msrit.canteen.navigation.ProtectedArea
import edu.msrit.canteen.ui.components.ADMIN_MODERATION_PENDING
import edu.msrit.canteen.ui.components.AdminAttentionKind
import edu.msrit.canteen.ui.components.AdminCountTile
import edu.msrit.canteen.ui.components.AdminDemoBanner
import edu.msrit.canteen.ui.components.AdminDeniedState
import edu.msrit.canteen.ui.components.AdminDirectoryRow
import edu.msrit.canteen.ui.components.AdminHeroSummary
import edu.msrit.canteen.ui.components.AdminInfoCard
import edu.msrit.canteen.ui.components.AdminQuickAction
import edu.msrit.canteen.ui.components.AdminSectionHeader
import edu.msrit.canteen.ui.components.AdminTaskRow
import edu.msrit.canteen.ui.components.AdminTone
import edu.msrit.canteen.ui.components.AdminTwoColumn
import edu.msrit.canteen.ui.components.AdminUserGroup
import edu.msrit.canteen.ui.components.AdminStatusTile
import edu.msrit.canteen.ui.components.AdminTimelineRow
import edu.msrit.canteen.ui.components.AdminValueTile
import edu.msrit.canteen.ui.components.AdminTicketFilter
import edu.msrit.canteen.ui.components.adminAuditByDay
import edu.msrit.canteen.ui.components.adminFilterTickets
import edu.msrit.canteen.ui.components.adminSellerLabel
import edu.msrit.canteen.ui.components.adminTicketTone
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.adminAttentionQueue
import edu.msrit.canteen.ui.components.adminFilterSellers
import edu.msrit.canteen.ui.components.adminFilterUsers
import edu.msrit.canteen.ui.components.adminOverviewCounts
import edu.msrit.canteen.ui.components.adminRelativeTime
import edu.msrit.canteen.ui.components.adminUserGroup
import edu.msrit.canteen.ui.components.adminNeedsAction
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AdminPage { DASHBOARD, SELLERS, SELLER_DETAIL, SELLER_SETUP, USERS, ANALYTICS,
    SELLER_ANALYTICS, SUPPORT, TICKET_DETAIL, MODERATION, SETTINGS, AUDIT }

private fun adminMoney(paise: Long) = "₹" + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).format(paise / 100.0)

@Composable
private fun AdminPageShell(title: String, tag: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).testTag(tag)) {
        CatalogHeader(title, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            AdminDemoBanner()
            Spacer(Modifier.height(15.dp)); content(); Spacer(Modifier.height(36.dp))
        }
    }
}

private val adminNow get() = LocalDateTime.now(RIT_TIME_ZONE)
private val adminClock = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private fun sellerNames() = PreviewCatalog.sellers.associate { it.id to it.name }

/** Ordering modes come from the catalog record; a seller without one (a setup draft) has none to show. */
private fun orderingLabel(sellerId: String): String? = PreviewCatalog.sellers.find { it.id == sellerId }?.capabilities?.let {
    when {
        it.futureIndividualPrepared && it.bulk -> "Bulk · individual planned"
        it.bulk -> "Bulk only"
        else -> "No ordering mode"
    }
}

private fun sellerTone(status: AdminSellerStatus) = when (status) {
    AdminSellerStatus.ACTIVE -> AdminTone.NEUTRAL
    AdminSellerStatus.DRAFT -> AdminTone.ACTION
    AdminSellerStatus.INACTIVE -> AdminTone.DONE
}

private fun AdminAttentionKind.chip() = when (this) {
    AdminAttentionKind.ESCALATED_TICKET -> "Escalated" to AdminTone.URGENT
    AdminAttentionKind.PLATFORM_TICKET -> "Platform support" to AdminTone.ACTION
    AdminAttentionKind.SELLER_TICKET -> "Seller issue" to AdminTone.ACTION
    AdminAttentionKind.CONTENT_REVIEW -> "Needs review" to AdminTone.ACTION
    AdminAttentionKind.SETUP_DRAFT -> "Setup draft" to AdminTone.ACTION
}

private fun AdminAttentionKind.icon() = when (this) {
    AdminAttentionKind.ESCALATED_TICKET, AdminAttentionKind.PLATFORM_TICKET, AdminAttentionKind.SELLER_TICKET -> RitIconKind.Mail
    AdminAttentionKind.CONTENT_REVIEW -> RitIconKind.Filter
    AdminAttentionKind.SETUP_DRAFT -> RitIconKind.Store
}

@Composable
private fun AdminOverview(identity: AuthorizedIdentity?, onNavigate: (AdminPage) -> Unit,
                          onSeller: (String) -> Unit, onTicket: (String) -> Unit) {
    val sellers = DevelopmentInsights.sellers(identity)
    val tickets = DevelopmentInsights.tickets(identity)
    val moderation = DevelopmentInsights.moderation(identity)
    val counts = adminOverviewCounts(sellers, tickets, moderation)
    val queue = adminAttentionQueue(tickets, moderation, sellers, sellerNames())
    var showAll by remember { mutableStateOf(false) }
    val now = adminNow

    AdminHeroSummary(queue.size,
        when (queue.size) { 0 -> "All clear"; 1 -> "1 task needs you"; else -> "${queue.size} tasks need you" },
        "${counts.activeSellers} active sellers · ${DevelopmentInsights.users(identity).size} sample accounts")
    Spacer(Modifier.height(12.dp))
    AdminTwoColumn(listOf(
        Triple(counts.openTickets, "Open tickets", "admin_count_open" to AdminPage.SUPPORT),
        Triple(counts.escalated, "Escalated", "admin_count_escalated" to AdminPage.SUPPORT),
        Triple(counts.toReview, "Content to review", "admin_count_review" to AdminPage.MODERATION),
        Triple(counts.setupDrafts, "Setup drafts", "admin_count_drafts" to AdminPage.SELLERS),
    )) { (value, label, target), modifier ->
        AdminCountTile(value, label, { onNavigate(target.second) }, modifier, tag = target.first)
    }

    AdminSectionHeader("Needs attention", actionLabel = if (queue.size > 4) (if (showAll) "Show less" else "Show all ${queue.size}") else null,
        onAction = { showAll = !showAll }, actionTag = "admin_queue_toggle")
    if (queue.isEmpty()) AdminInfoCard("Nothing waiting", "No open tickets, content reviews or setup drafts right now.", tag = "admin_queue_empty")
    else (if (showAll) queue else queue.take(4)).forEach { item ->
        val (chip, tone) = item.kind.chip()
        AdminTaskRow(item.title, item.detail, item.kind.icon(), chip, tone, meta = item.at?.let { adminRelativeTime(it, now) },
            onClick = {
                when (item.kind) {
                    AdminAttentionKind.CONTENT_REVIEW -> onNavigate(AdminPage.MODERATION)
                    AdminAttentionKind.SETUP_DRAFT -> onSeller(item.targetId)
                    else -> onTicket(item.targetId)
                }
            }, tag = "admin_queue_${item.targetId}")
    }

    AdminSectionHeader("Pending seller setup")
    val drafts = sellers.filter { it.status == AdminSellerStatus.DRAFT }
    if (drafts.isEmpty()) AdminTaskRow("No setup drafts", "Start one when a new seller joins. It stays a local draft; no account or invite is created.",
        RitIconKind.Store, "None pending", AdminTone.DONE, onClick = { onNavigate(AdminPage.SELLER_SETUP) }, tag = "admin_setup_start")
    else drafts.forEach {
        AdminTaskRow(it.name, it.ownerInviteStatus, RitIconKind.Store, "Setup draft", AdminTone.ACTION,
            onClick = { onSeller(it.id) }, tag = "admin_draft_${it.id}")
    }

    AdminSectionHeader("Support and content")
    val platformOpen = tickets.count { it.adminNeedsAction() && it.sellerId == null }
    AdminTaskRow("Unresolved support", "${counts.escalated} escalated · $platformOpen for platform support",
        RitIconKind.Mail, if (counts.openTickets == 0) "All resolved" else "${counts.openTickets} open",
        if (counts.escalated > 0) AdminTone.URGENT else if (counts.openTickets > 0) AdminTone.ACTION else AdminTone.DONE,
        onClick = { onNavigate(AdminPage.SUPPORT) }, tag = "admin_open_support")
    AdminTaskRow("Content review", "Menu text and promotions sellers published",
        RitIconKind.Filter, if (counts.toReview == 0) "Up to date" else "${counts.toReview} waiting",
        if (counts.toReview > 0) AdminTone.ACTION else AdminTone.DONE,
        onClick = { onNavigate(AdminPage.MODERATION) }, tag = "admin_open_moderation")

    AdminSectionHeader("Quick actions")
    AdminTwoColumn(listOf(
        Triple("Sellers", RitIconKind.Store, "admin_open_sellers" to AdminPage.SELLERS),
        Triple("Users", RitIconKind.Person, "admin_open_users" to AdminPage.USERS),
        Triple("New seller", RitIconKind.Bulk, "admin_open_setup" to AdminPage.SELLER_SETUP),
        Triple("Analytics", RitIconKind.Orders, "admin_open_analytics" to AdminPage.ANALYTICS),
        Triple("Health & audit", RitIconKind.Search, "admin_open_audit" to AdminPage.AUDIT),
        Triple("Settings", RitIconKind.Filter, "admin_open_settings" to AdminPage.SETTINGS),
    )) { (label, icon, target), modifier ->
        AdminQuickAction(label, icon, { onNavigate(target.second) }, modifier, tag = target.first)
    }
}

@Composable
private fun AdminSellerDirectory(identity: AuthorizedIdentity?, onNavigate: (AdminPage) -> Unit, onSeller: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<AdminSellerStatus?>(null) }
    val all = DevelopmentInsights.sellers(identity)
    val shown = adminFilterSellers(all, query, status)
    RitTextField(query, { query = it }, "Search sellers", "Name or ID", Modifier.fillMaxWidth(), inputTag = "admin_seller_search")
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        RitChip("All · ${all.size}", status == null, { status = null }, Modifier.testTag("admin_seller_filter_all"))
        AdminSellerStatus.entries.forEach { s ->
            RitChip("${s.label} · ${all.count { it.status == s }}", status == s, { status = s },
                Modifier.testTag("admin_seller_filter_${s.name.lowercase()}"))
        }
    }
    Spacer(Modifier.height(12.dp))
    AdminQuickAction("Set up a seller", RitIconKind.Store, { onNavigate(AdminPage.SELLER_SETUP) }, Modifier.fillMaxWidth(),
        tag = "admin_sellers_setup")
    AdminSectionHeader(if (shown.size == 1) "1 seller" else "${shown.size} sellers")
    if (shown.isEmpty()) {
        RitEmptyState("No sellers match", "Try another name, or clear the search and filter.")
        RitSecondaryButton("Clear search and filter", { query = ""; status = null }, Modifier.fillMaxWidth().testTag("admin_seller_clear"))
    } else shown.forEach {
        AdminDirectoryRow(it.name, listOfNotNull(orderingLabel(it.id), it.ownerInviteStatus).joinToString(" · "),
            listOf(it.status.label to sellerTone(it.status)), onClick = { onSeller(it.id) }, tag = "admin_seller_${it.id}")
    }
}

@Composable
private fun AdminUserDirectory(identity: AuthorizedIdentity?) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<AdminUserGroup?>(null) }
    val all = DevelopmentInsights.users(identity)
    val shown = adminFilterUsers(all, query, group)
    RitTextField(query, { query = it }, "Search sample accounts", "Name or role", Modifier.fillMaxWidth(), inputTag = "admin_user_search")
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        RitChip("All · ${all.size}", group == null, { group = null }, Modifier.testTag("admin_user_filter_all"))
        AdminUserGroup.entries.forEach { g ->
            RitChip("${g.label} · ${all.count { adminUserGroup(it) == g }}", group == g, { group = g },
                Modifier.testTag("admin_user_filter_${g.name.lowercase()}"))
        }
    }
    AdminSectionHeader(if (shown.size == 1) "1 account" else "${shown.size} accounts")
    if (shown.isEmpty()) {
        RitEmptyState("No accounts match", "Try another name or role, or clear the filter.")
        RitSecondaryButton("Clear search and filter", { query = ""; group = null }, Modifier.fillMaxWidth().testTag("admin_user_clear"))
    } else shown.forEach {
        val affiliation = it.affiliation ?: if (adminUserGroup(it) == AdminUserGroup.SELLER_TEAM)
            "Seller not recorded in sample" else "No affiliation claim"
        AdminDirectoryRow(it.displayName, "$affiliation · ${it.status}", listOf(it.roleLabel to AdminTone.NEUTRAL), tag = "admin_user_${it.id}")
    }
    Spacer(Modifier.height(8.dp))
    AdminInfoCard("Access changes need the backend", "Roles can't be changed here. Real account changes need authenticated, audited backend authorization. No public role self-selection.")
}

@Composable
private fun AdminSellerDetail(identity: AuthorizedIdentity?, sellerId: String?, onNavigate: (AdminPage) -> Unit,
                              onSellerAnalytics: (String) -> Unit) {
    val record = sellerId?.let { DevelopmentInsights.sellerRecord(identity, it) }
    if (record == null) { RitEmptyState("Seller unavailable", "No authorized seller record exists."); return }
    val openTickets = DevelopmentInsights.tickets(identity).count { it.sellerId == record.id && it.adminNeedsAction() }
    val toReview = DevelopmentInsights.moderation(identity).count { it.sellerId == record.id && it.state == ADMIN_MODERATION_PENDING }
    AdminDirectoryRow(record.name, orderingLabel(record.id) ?: "Not in the customer catalog yet",
        listOf(record.status.label to sellerTone(record.status)), tag = "admin_seller_header")
    // Display-only: the Support and Content review lists can't be filtered to one seller from here.
    AdminTwoColumn(listOf(
        Triple(openTickets, "Open tickets", "admin_seller_tickets"),
        Triple(toReview, "Content to review", "admin_seller_review"),
    )) { (value, label, tag), modifier -> AdminValueTile(value.toString(), label, modifier, tag) }
    AdminSectionHeader("Access and configuration")
    AdminInfoCard("Owner access", "${record.ownerInviteStatus}. Real invitation, revocation and verified email require the backend.")
    AdminInfoCard("Business configuration", "Seller-controlled minimum, hours and profile remain separate from admin oversight.")
    AdminSectionHeader("Oversight")
    if (record.status == AdminSellerStatus.ACTIVE) AdminTaskRow("Seller analytics", "Read-only synthetic metrics for this seller",
        RitIconKind.Orders, "Sample", AdminTone.NEUTRAL, onClick = { onSellerAnalytics(record.id) }, tag = "admin_seller_analytics")
    AdminTaskRow("Audit", "Local administrator actions only", RitIconKind.Search, "Local log", AdminTone.NEUTRAL,
        onClick = { onNavigate(AdminPage.AUDIT) }, tag = "admin_seller_audit")
}

@Composable
fun PlatformAdminScreen(page: AdminPage, identity: AuthorizedIdentity?, sample: Boolean,
                        sellerId: String?, ticketId: String?, onBack: () -> Unit,
                        onNavigate: (AdminPage) -> Unit, onSeller: (String) -> Unit,
                        onSellerAnalytics: (String) -> Unit, onTicket: (String) -> Unit) {
    val title = when (page) {
        AdminPage.DASHBOARD -> "Platform overview"; AdminPage.SELLERS -> "Participating sellers"
        AdminPage.SELLER_DETAIL -> "Seller oversight"; AdminPage.SELLER_SETUP -> "Seller setup"
        AdminPage.USERS -> "Users & access"; AdminPage.ANALYTICS -> "Platform analytics"
        AdminPage.SELLER_ANALYTICS -> "Seller-level analytics"; AdminPage.SUPPORT -> "Support escalations"
        AdminPage.TICKET_DETAIL -> "Ticket detail"; AdminPage.MODERATION -> "Content review"
        AdminPage.SETTINGS -> "Platform settings"; AdminPage.AUDIT -> "Audit & system health"
    }
    AdminPageShell(title, "admin_${page.name.lowercase()}", onBack) {
        if (!PresentationAccess.allows(identity, ProtectedArea.Administrator)) { AdminDeniedState(onBack); return@AdminPageShell }
        when (page) {
            AdminPage.DASHBOARD -> AdminOverview(identity, onNavigate, onSeller, onTicket)
            AdminPage.SELLERS -> AdminSellerDirectory(identity, onNavigate, onSeller)
            AdminPage.SELLER_DETAIL -> AdminSellerDetail(identity, sellerId, onNavigate, onSellerAnalytics)
            AdminPage.SELLER_SETUP -> AdminSellerSetup(identity, onNavigate)
            AdminPage.USERS -> AdminUserDirectory(identity)
            AdminPage.ANALYTICS, AdminPage.SELLER_ANALYTICS -> AdminAnalytics(identity, page, sellerId, sample, onSellerAnalytics)
            AdminPage.SUPPORT -> AdminSupport(identity, onTicket)
            AdminPage.TICKET_DETAIL -> AdminTicketDetail(identity, ticketId)
            AdminPage.MODERATION -> AdminModeration(identity)
            AdminPage.SETTINGS -> AdminSettings()
            AdminPage.AUDIT -> AdminAudit(identity)
        }
    }
}

@Composable
private fun AdminSellerSetup(identity: AuthorizedIdentity?, onNavigate: (AdminPage) -> Unit) {
    val colors = RitThemeTokens.colors
    var name by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    RitText("Admin-controlled onboarding · local draft only", RitType.title)
    AdminSectionHeader("1 · Business name")
    RitTextField(name, { name = it; saved = false; message = "" }, "Seller display name", "Required", Modifier.fillMaxWidth(), inputTag = "admin_new_seller_name")
    AdminSectionHeader("2 · Owner invitation")
    AdminTaskRow("Invite the owner by verified email", "Disabled until verified email, authorization and seller approval are implemented.",
        RitIconKind.Mail, "Needs backend", AdminTone.DONE)
    AdminSectionHeader("3 · Approval and go-live")
    AdminTaskRow("Approve ordering modes", "Bulk-only unless the platform approves individual prepared-food orders. Not available in this preview.",
        RitIconKind.Bulk, "Needs backend", AdminTone.DONE)
    Spacer(Modifier.height(12.dp))
    RitPrimaryButton("Save sample setup draft", {
        val trimmed = name.trim()
        saved = DevelopmentInsights.createSellerDraft(identity, name)
        message = when {
            saved -> "Local seller draft saved. No owner access granted."
            trimmed.length > 80 -> "Seller names can be up to 80 characters."
            DevelopmentInsights.sellers(identity).any { it.name.equals(trimmed, true) } -> "A seller named \"$trimmed\" already exists."
            else -> "Could not save this draft."
        }
    }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
    if (message.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        RitText(message, RitType.small, if (saved) colors.accent else colors.foreground, Modifier.testTag("admin_setup_message"))
    }
    if (saved) {
        Spacer(Modifier.height(8.dp))
        RitSecondaryButton("View in seller directory", { onNavigate(AdminPage.SELLERS) }, Modifier.fillMaxWidth().testTag("admin_setup_view"))
    }
}

@Composable
private fun AdminAnalytics(identity: AuthorizedIdentity?, page: AdminPage, sellerId: String?, sample: Boolean,
                           onSellerAnalytics: (String) -> Unit) {
    val currentSeller = if (page == AdminPage.SELLER_ANALYTICS) sellerId else null
    var period by remember(page) { mutableStateOf(InsightPeriod.MONTH) }
    var custom by remember { mutableStateOf<InsightRange?>(null) }
    var start by remember { mutableStateOf("") }; var end by remember { mutableStateOf("") }
    val today = LocalDate.now(RIT_TIME_ZONE)
    val range = period.range(today, custom)
    val summary = DevelopmentInsights.sales(identity, currentSeller, range, sample)
    RitText(if (currentSeller == null) "All sellers · sample" else
        "${DevelopmentInsights.sellerRecord(identity, currentSeller)?.name ?: "Seller"} · sample", RitType.title)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        InsightPeriod.entries.forEach { RitChip(it.label, period == it, { period = it }) }
    }
    if (period == InsightPeriod.CUSTOM) {
        Spacer(Modifier.height(10.dp))
        RitTextField(start, { start = it }, "From", "YYYY-MM-DD", Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        RitTextField(end, { end = it }, "Through", "YYYY-MM-DD", Modifier.fillMaxWidth())
        val parsed = runCatching { InsightRange(LocalDate.parse(start), LocalDate.parse(end)) }.getOrNull()
        if (parsed?.valid() == true) { Spacer(Modifier.height(8.dp)); RitSecondaryButton("Apply range", { custom = parsed }, Modifier.fillMaxWidth()) }
    }
    Spacer(Modifier.height(12.dp))
    if (summary.orders == 0) RitEmptyState("No verified app sales", "No eligible production transactions exist. Unpaid requests are excluded.")
    else AdminTwoColumn(listOf(
        Triple(adminMoney(summary.netPaise), "Sample net collected", "admin_sample_revenue"),
        Triple(adminMoney(summary.grossPaise), "Sample gross before discounts", null),
        Triple(adminMoney(summary.discountPaise), "Sample discounts", null),
        Triple(summary.orders.toString(), "Sample paid orders", null),
        Triple(summary.customers.toString(), "Sample unique buyers", null),
    )) { (value, label, tag), modifier -> AdminValueTile(value, label, modifier, tag) }
    if (currentSeller == null) {
        val active = DevelopmentInsights.sellers(identity).filter { it.status == AdminSellerStatus.ACTIVE }
        if (active.isNotEmpty()) AdminSectionHeader("By seller")
        active.forEach {
            val scoped = DevelopmentInsights.sales(identity, it.id, range, sample)
            AdminDirectoryRow(it.name, "${scoped.orders} sample paid orders · ${adminMoney(scoped.netPaise)}", emptyList(),
                onClick = { onSellerAnalytics(it.id) }, tag = "admin_analytics_${it.id}")
        }
    }
    Spacer(Modifier.height(8.dp))
    AdminInfoCard("Scope", "Only synthetic in-app sale fixtures are shown. Walk-in sales and unpaid quotes are never counted.")
}

@Composable
private fun AdminSupport(identity: AuthorizedIdentity?, onTicket: (String) -> Unit) {
    var filter by remember { mutableStateOf(AdminTicketFilter.NEEDS_ACTION) }
    val all = DevelopmentInsights.tickets(identity)
    val shown = adminFilterTickets(all, filter)
    val names = sellerNames(); val now = adminNow
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        AdminTicketFilter.entries.forEach { f ->
            RitChip("${f.label} · ${adminFilterTickets(all, f).size}", filter == f, { filter = f },
                Modifier.testTag("admin_ticket_filter_${f.name.lowercase()}"))
        }
    }
    AdminSectionHeader(if (shown.size == 1) "1 ticket" else "${shown.size} tickets")
    if (shown.isEmpty()) {
        RitEmptyState(if (all.isEmpty()) "No support tickets" else "Nothing here",
            if (all.isEmpty()) "No matching sample escalations exist." else "No tickets match this filter.")
        if (all.isNotEmpty()) RitSecondaryButton("Show all tickets", { filter = AdminTicketFilter.ALL }, Modifier.fillMaxWidth().testTag("admin_ticket_show_all"))
    } else shown.forEach {
        val tone = adminTicketTone(it)
        AdminTaskRow(it.subject, "${adminSellerLabel(it.sellerId, names)} · ${it.category.label}", RitIconKind.Mail,
            if (tone == AdminTone.URGENT) "Escalated · ${it.status.label}" else it.status.label, tone,
            meta = adminRelativeTime(it.updatedAt, now), onClick = { onTicket(it.id) }, tag = "admin_ticket_${it.id}")
    }
    Spacer(Modifier.height(8.dp))
    AdminInfoCard("How tickets are routed", "Food and bulk-scheduling issues go to the seller first and can be escalated here. Payment, account and app issues come straight to platform support.")
}

@Composable
private fun AdminTicketDetail(identity: AuthorizedIdentity?, ticketId: String?) {
    val ticket = ticketId?.let { DevelopmentInsights.ticket(identity, it) }
    if (ticket == null) { RitEmptyState("Ticket unavailable", "No authorized ticket was found."); return }
    var note by remember(ticket.id) { mutableStateOf(ticket.internalNote) }
    var message by remember { mutableStateOf("") }
    val tone = adminTicketTone(ticket)
    AdminTaskRow(ticket.subject, "${ticket.id} · ${ticket.category.label}", RitIconKind.Mail,
        if (tone == AdminTone.URGENT) "Escalated · ${ticket.status.label}" else ticket.status.label, tone,
        meta = adminRelativeTime(ticket.updatedAt, adminNow), tag = "admin_ticket_header")
    AdminInfoCard("Sample report", ticket.detail)
    AdminInfoCard("Routing", if (ticket.sellerId == null) "Platform support · payment, account or app"
        else "${adminSellerLabel(ticket.sellerId, sellerNames())} · raised by ${ticket.customerRef}${if (ticket.escalated) " · escalated to platform" else ""}")
    if (ticket.category == TicketCategory.PAYMENT) AdminTaskRow("Payment truth protected",
        "A ticket cannot change provider verification or issue a refund.", RitIconKind.Orders, "Read-only", AdminTone.NEUTRAL)
    AdminSectionHeader("Status")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        TicketStatus.entries.forEach { s -> RitChip(s.label, ticket.status == s, {
            if (ticket.status != s && DevelopmentInsights.setTicketStatus(identity, ticket.id, s))
                message = "Status set to ${s.label} locally; no participant notified."
        }, Modifier.testTag("admin_ticket_status_${s.name.lowercase()}")) }
    }
    AdminSectionHeader("Internal note")
    RitTextField(note, { note = it }, "Internal local note", "Visible only in this demo", Modifier.fillMaxWidth(),
        inputTag = "admin_ticket_note", singleLine = false)
    RitText("${note.length}/500", RitType.small, if (note.length > 500) RitThemeTokens.colors.accent else RitThemeTokens.colors.muted,
        Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(8.dp))
    RitPrimaryButton("Save local note", {
        message = when {
            note.length > 500 -> "Notes can be up to 500 characters. Shorten it and save again."
            DevelopmentInsights.saveInternalNote(identity, ticket.id, note) -> "Local note saved; no participant notified."
            else -> "Could not save note."
        }
    }, Modifier.fillMaxWidth())
    if (message.isNotEmpty()) RitText(message, RitType.small, RitThemeTokens.colors.accent, Modifier.padding(top = 8.dp).testTag("admin_ticket_message"))
    if (ticket.category != TicketCategory.PAYMENT) { Spacer(Modifier.height(12.dp))
        AdminInfoCard("Payment truth protected", "A ticket cannot change provider verification or issue a refund.") }
}

@Composable
private fun AdminModeration(identity: AuthorizedIdentity?) {
    val records = DevelopmentInsights.moderation(identity)
    val names = sellerNames()
    val (pending, reviewed) = records.partition { it.state == ADMIN_MODERATION_PENDING }
    if (records.isEmpty()) { RitEmptyState("Nothing to review", "No sample content is queued."); return }
    AdminSectionHeader(if (pending.isEmpty()) "All caught up" else "Needs review · ${pending.size}")
    if (pending.isEmpty()) AdminInfoCard("Nothing waiting", "Every queued item has been reviewed.", tag = "admin_moderation_clear")
    pending.forEach { record ->
        AdminTaskRow(record.subject, "${record.kind} · ${adminSellerLabel(record.sellerId, names)}", RitIconKind.Filter,
            record.state, AdminTone.ACTION, tag = "admin_moderation_${record.id}")
        RitSecondaryButton("Mark as reviewed", { DevelopmentInsights.markModerationReviewed(identity, record.id) },
            Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("admin_review_${record.id}")
                .semantics { contentDescription = "Mark ${record.subject} as reviewed" })
    }
    if (reviewed.isNotEmpty()) {
        AdminSectionHeader("Reviewed · ${reviewed.size}")
        reviewed.forEach { record ->
            AdminTaskRow(record.subject, "${record.kind} · ${adminSellerLabel(record.sellerId, names)}", RitIconKind.Filter,
                record.state, AdminTone.DONE, tag = "admin_moderation_${record.id}")
        }
    }
    Spacer(Modifier.height(8.dp))
    AdminInfoCard("Local review only", "This action does not suspend a seller or alter real published content.")
}

@Composable
private fun AdminSettings() {
    AdminSectionHeader("Ordering defaults")
    AdminTaskRow("Default bulk minimum", "8 combined units per seller unless that seller has an authorized override",
        RitIconKind.Bulk, "Platform default", AdminTone.NEUTRAL)
    AdminTaskRow("Quote expiry", "Backend-enforced value pending product decision", RitIconKind.Orders, "Pending decision", AdminTone.ACTION)
    AdminSectionHeader("Onboarding and access")
    AdminTaskRow("Seller onboarding", "Administrator approval and verified-email owner invitation required",
        RitIconKind.Store, "Admin only", AdminTone.NEUTRAL)
    AdminSectionHeader("Integrations")
    AdminTaskRow("Notification routing", "Durable backend inbox, push and transactional email not connected",
        RitIconKind.Mail, "Not connected", AdminTone.DONE)
    AdminTaskRow("Payment provider", "PhonePe integration, merchant eligibility and settlement remain unverified",
        RitIconKind.Cart, "Unverified", AdminTone.DONE)
    Spacer(Modifier.height(8.dp))
    AdminInfoCard("Global changes", "No live settings can be changed or negotiated quotes rewritten from this preview.")
}

@Composable
private fun AdminAudit(identity: AuthorizedIdentity?) {
    AdminSectionHeader("System health")
    AdminTwoColumn(listOf("Backend" to "Not connected", "Real-time sync" to "Not connected",
        "Payment provider" to "Unverified", "Notifications" to "Not connected")) { (label, status), modifier ->
        AdminStatusTile(label, status, AdminTone.DONE, modifier)
    }
    Spacer(Modifier.height(8.dp))
    AdminInfoCard("System health", "Backend, real-time sync, provider and notification services not connected in this local build")
    val groups = adminAuditByDay(DevelopmentInsights.audit(identity), LocalDate.now(RIT_TIME_ZONE))
    AdminSectionHeader("Recent admin actions")
    if (groups.isEmpty()) RitEmptyState("No local admin actions", "Try a sample draft, ticket status or content review.")
    else groups.forEach { (day, events) ->
        RitText(day, RitType.label, RitThemeTokens.colors.muted, Modifier.padding(top = 8.dp))
        events.forEach {
            AdminTimelineRow(it.at.format(adminClock), it.action, "${it.entity} · ${it.before} → ${it.after} · by ${it.actor}", tag = "admin_audit_${it.id}")
        }
    }
    Spacer(Modifier.height(8.dp))
    AdminInfoCard("Audit boundary", "This resettable on-device log is not a durable or tamper-resistant audit trail.")
}
