package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.AdminSellerStatus
import edu.msrit.canteen.customer.DevelopmentInsights
import edu.msrit.canteen.customer.InsightPeriod
import edu.msrit.canteen.customer.InsightRange
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.TicketStatus
import edu.msrit.canteen.customer.range
import edu.msrit.canteen.navigation.AuthorizedIdentity
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.text.NumberFormat
import java.time.LocalDate
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
            RitText("PLATFORM ADMIN · SYNTHETIC SAMPLE · no production authority", RitType.small, colors.accent)
            Spacer(Modifier.height(15.dp)); content(); Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun AdminCard(title: String, detail: String, onClick: (() -> Unit)? = null, tag: String? = null) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 9.dp)
        .border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
        .background(colors.card, RoundedCornerShape(RitRadii.card))
        .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
        .then(if (tag == null) Modifier else Modifier.testTag(tag)).padding(16.dp)) {
        RitText(title, RitType.label); RitText(detail, RitType.small, colors.muted)
    }
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
        when (page) {
            AdminPage.DASHBOARD -> {
                RitText("RIT Canteen · administration", RitType.heading)
                AdminCard("Participating seller previews", DevelopmentInsights.sellers(identity).size.toString(),
                    { onNavigate(AdminPage.SELLERS) }, "admin_open_sellers")
                AdminCard("Sample support tickets", DevelopmentInsights.tickets(identity).size.toString(),
                    { onNavigate(AdminPage.SUPPORT) }, "admin_open_support")
                AdminCard("Accounts", "Sample customer, owner, manager, staff and admin identities", { onNavigate(AdminPage.USERS) })
                AdminCard("Analytics", "Only synthetic paid-sample rows; no real revenue", { onNavigate(AdminPage.ANALYTICS) })
                AdminCard("Content review", "Resettable sample moderation queue", { onNavigate(AdminPage.MODERATION) })
                AdminCard("Platform settings", "Defaults and integration boundaries", { onNavigate(AdminPage.SETTINGS) })
                AdminCard("Audit & health", "Local action history; backend status unknown", { onNavigate(AdminPage.AUDIT) })
            }
            AdminPage.SELLERS -> {
                var query by remember { mutableStateOf("") }
                var status by remember { mutableStateOf<AdminSellerStatus?>(null) }
                RitTextField(query, { query = it }, "Search sellers", "Name or identifier", Modifier.fillMaxWidth(), inputTag = "admin_seller_search")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    RitChip("All", status == null, { status = null })
                    AdminSellerStatus.entries.forEach { RitChip(it.label, status == it, { status = it }) }
                }
                AdminCard("Set up a seller", "Save a local draft; no account is provisioned", { onNavigate(AdminPage.SELLER_SETUP) })
                DevelopmentInsights.sellers(identity).filter { (status == null || it.status == status) &&
                    (it.name.contains(query, true) || it.id.contains(query, true)) }.forEach {
                    AdminCard(it.name, "${it.status.label} · ${it.ownerInviteStatus}", { onSeller(it.id) })
                }
            }
            AdminPage.SELLER_DETAIL -> {
                val record = sellerId?.let { DevelopmentInsights.sellerRecord(identity, it) }
                if (record == null) RitEmptyState("Seller unavailable", "No authorized seller record exists.")
                else {
                    AdminCard(record.name, "${record.status.label} · ${record.ownerInviteStatus}")
                    AdminCard("Owner access", "Real invitation, revocation and verified email require the backend.")
                    AdminCard("Business configuration", "Seller-controlled minimum, hours and profile remain separate from admin oversight.")
                    AdminCard("Seller analytics", "Read-only synthetic metrics for this seller", { onSellerAnalytics(record.id) })
                    AdminCard("Audit", "Local administrator actions only", { onNavigate(AdminPage.AUDIT) })
                }
            }
            AdminPage.SELLER_SETUP -> {
                var name by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }
                RitText("Admin-controlled onboarding · local draft only", RitType.title)
                RitTextField(name, { name = it }, "Seller display name", "Required", Modifier.fillMaxWidth(), inputTag = "admin_new_seller_name")
                AdminCard("Owner invitation", "Disabled until verified email, authorization and seller approval are implemented.")
                RitPrimaryButton("Save sample setup draft", {
                    message = if (DevelopmentInsights.createSellerDraft(identity, name))
                        "Local seller draft saved. No owner access granted." else "Enter a unique seller name."
                }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
                RitText(message, RitType.small, RitThemeTokens.colors.accent)
            }
            AdminPage.USERS -> {
                var query by remember { mutableStateOf("") }
                RitTextField(query, { query = it }, "Search sample accounts", "Name or role", Modifier.fillMaxWidth())
                DevelopmentInsights.users(identity).filter { it.displayName.contains(query, true) ||
                    it.roleLabel.contains(query, true) }.forEach {
                    AdminCard(it.displayName, "${it.roleLabel} · ${it.affiliation ?: "No affiliation claim"} · ${it.status}")
                }
                AdminCard("Access restrictions", "Real account changes need authenticated, audited backend authorization. No public role self-selection.")
            }
            AdminPage.ANALYTICS, AdminPage.SELLER_ANALYTICS -> {
                val currentSeller = if (page == AdminPage.SELLER_ANALYTICS) sellerId else null
                var period by remember(page) { mutableStateOf(InsightPeriod.MONTH) }
                var custom by remember { mutableStateOf<InsightRange?>(null) }
                var start by remember { mutableStateOf("") }; var end by remember { mutableStateOf("") }
                val today = LocalDate.now(RIT_TIME_ZONE)
                val range = period.range(today, custom)
                val summary = DevelopmentInsights.sales(identity, currentSeller, range, sample)
                RitText(if (currentSeller == null) "All sellers · sample" else
                    "${DevelopmentInsights.sellerRecord(identity, currentSeller)?.name ?: "Seller"} · sample", RitType.title)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    InsightPeriod.entries.forEach { RitChip(it.label, period == it, { period = it }) }
                }
                if (period == InsightPeriod.CUSTOM) {
                    RitTextField(start, { start = it }, "From", "YYYY-MM-DD", Modifier.fillMaxWidth())
                    RitTextField(end, { end = it }, "Through", "YYYY-MM-DD", Modifier.fillMaxWidth())
                    val parsed = runCatching { InsightRange(LocalDate.parse(start), LocalDate.parse(end)) }.getOrNull()
                    if (parsed?.valid() == true) RitSecondaryButton("Apply range", { custom = parsed }, Modifier.fillMaxWidth())
                }
                if (summary.orders == 0) RitEmptyState("No verified app sales", "No eligible production transactions exist. Unpaid requests are excluded.")
                else {
                    AdminCard("Sample net collected", adminMoney(summary.netPaise), tag = "admin_sample_revenue")
                    AdminCard("Sample paid orders", summary.orders.toString())
                    AdminCard("Sample unique buyers", summary.customers.toString())
                    AdminCard("Sample gross and discounts", "${adminMoney(summary.grossPaise)} gross · ${adminMoney(summary.discountPaise)} discounts")
                }
                if (currentSeller == null) DevelopmentInsights.sellers(identity).filter { it.status == AdminSellerStatus.ACTIVE }.forEach {
                    val scoped = DevelopmentInsights.sales(identity, it.id, range, sample)
                    AdminCard(it.name, "${scoped.orders} sample paid orders · ${adminMoney(scoped.netPaise)}", { onSellerAnalytics(it.id) })
                }
                AdminCard("Scope", "Only synthetic in-app sale fixtures are shown. Walk-in sales and unpaid quotes are never counted.")
            }
            AdminPage.SUPPORT -> {
                var status by remember { mutableStateOf<TicketStatus?>(null) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    RitChip("All", status == null, { status = null })
                    TicketStatus.entries.forEach { RitChip(it.label, status == it, { status = it }) }
                }
                val tickets = DevelopmentInsights.tickets(identity, status = status)
                if (tickets.isEmpty()) RitEmptyState("No support tickets", "No matching sample escalations exist.")
                else tickets.forEach { AdminCard(it.subject, "${it.id} · ${it.category.label} · ${it.status.label}${if (it.escalated) " · escalated" else ""}", { onTicket(it.id) }) }
            }
            AdminPage.TICKET_DETAIL -> {
                val ticket = ticketId?.let { DevelopmentInsights.ticket(identity, it) }
                if (ticket == null) RitEmptyState("Ticket unavailable", "No authorized ticket was found.")
                else {
                    var note by remember(ticket.id) { mutableStateOf(ticket.internalNote) }
                    var message by remember { mutableStateOf("") }
                    AdminCard(ticket.subject, "${ticket.id} · ${ticket.category.label} · ${ticket.status.label}")
                    AdminCard("Sample report", ticket.detail)
                    AdminCard("Routing", ticket.sellerId ?: "Platform support · payment/account/app")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        TicketStatus.entries.forEach { RitChip(it.label, ticket.status == it, {
                            DevelopmentInsights.setTicketStatus(identity, ticket.id, it)
                        }) }
                    }
                    RitTextField(note, { note = it }, "Internal local note", "Visible only in this demo", Modifier.fillMaxWidth(), singleLine = false)
                    RitPrimaryButton("Save local note", {
                        message = if (DevelopmentInsights.saveInternalNote(identity, ticket.id, note))
                            "Local note saved; no participant notified." else "Could not save note."
                    }, Modifier.fillMaxWidth())
                    RitText(message, RitType.small, RitThemeTokens.colors.accent)
                    AdminCard("Payment truth protected", "A ticket cannot change provider verification or issue a refund.")
                }
            }
            AdminPage.MODERATION -> {
                val records = DevelopmentInsights.moderation(identity)
                if (records.isEmpty()) RitEmptyState("Nothing to review", "No sample content is queued.")
                else records.forEach { record -> AdminCard(record.subject,
                    "${record.kind} · ${record.sellerId} · ${record.state}",
                    if (record.state == "Needs review") ({ DevelopmentInsights.markModerationReviewed(identity, record.id) }) else null) }
                AdminCard("Local review only", "This action does not suspend a seller or alter real published content.")
            }
            AdminPage.SETTINGS -> {
                AdminCard("Default bulk minimum", "8 combined units per seller unless that seller has an authorized override")
                AdminCard("Quote expiry", "Backend-enforced value pending product decision")
                AdminCard("Seller onboarding", "Administrator approval and verified-email owner invitation required")
                AdminCard("Notification routing", "Durable backend inbox, push and transactional email not connected")
                AdminCard("Payment provider", "PhonePe integration, merchant eligibility and settlement remain unverified")
                AdminCard("Global changes", "No live settings can be changed or negotiated quotes rewritten from this preview.")
            }
            AdminPage.AUDIT -> {
                AdminCard("System health", "Backend, real-time sync, provider and notification services not connected in this local build")
                val events = DevelopmentInsights.audit(identity)
                if (events.isEmpty()) RitEmptyState("No local admin actions", "Try a sample draft, ticket status or content review.")
                else events.forEach { AdminCard(it.action, "${it.actor} · ${it.entity} · ${it.before} → ${it.after} · ${it.at}") }
                AdminCard("Audit boundary", "This resettable on-device log is not a durable or tamper-resistant audit trail.")
            }
        }
    }
}
