package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.BulkRequestRecord
import edu.msrit.canteen.customer.DemoTransitionResult
import edu.msrit.canteen.customer.QuoteActor
import edu.msrit.canteen.customer.QuoteStage
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.SellerRequestFilter
import edu.msrit.canteen.customer.SellerRequestStatus
import edu.msrit.canteen.customer.compareQuotes
import edu.msrit.canteen.customer.filterSellerRequests
import edu.msrit.canteen.customer.operationalStatus
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val groupDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

@Composable
private fun SellerStatus(record: BulkRequestRecord) {
    val status = record.operationalStatus()
    val colors = RitThemeTokens.colors
    RitText(status.label, RitType.small, if (status in setOf(SellerRequestStatus.NEW,
            SellerRequestStatus.CUSTOMER_COUNTER, SellerRequestStatus.SELLER_REVIEW)) colors.accent else colors.muted,
        Modifier.testTag("seller_status_${record.reference}"))
}

@Composable
private fun SellerRequestCard(record: BulkRequestRecord, onOpen: () -> Unit) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)
        .border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
        .background(colors.card, RoundedCornerShape(RitRadii.card))
        .clickable(onClick = onOpen).testTag("seller_request_${record.reference}").padding(15.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RitText(record.reference, RitType.label)
            SellerStatus(record)
        }
        Spacer(Modifier.height(5.dp))
        RitText("${record.customerLabel} · ${record.latest.lines.sumOf { it.quantity }} units", RitType.body)
        RitText(record.latest.lines.joinToString { "${it.title} × ${it.quantity}" }, RitType.small, colors.muted,
            maxLines = 2)
        RitText("${displayPickup(record.latest.pickup)} · ${displayPaise(record.latest.totalPaise)}",
            RitType.small, colors.muted)
        if (record.latest.specialPickup) RitText(
            if (record.thread.specialAcceptedVersion == record.latest.version)
                "Special timing accepted for this revision" else "Special pickup needs explicit seller decision",
            RitType.small, colors.accent)
    }
}

@Composable
fun SellerOverviewScreen(records: List<BulkRequestRecord>, sellerId: String, sellerName: String,
                         roleLabel: String, canRespond: Boolean, canHistory: Boolean,
                         onBack: () -> Unit, onInbox: () -> Unit, onSchedule: () -> Unit,
                         onHistory: () -> Unit, onRequest: (String) -> Unit,
                         onManagement: () -> Unit = {}, onAnalytics: (() -> Unit)? = null,
                         onReports: (() -> Unit)? = null, onSupport: (() -> Unit)? = null) {
    val colors = RitThemeTokens.colors
    val own = filterSellerRequests(records, sellerId)
    val needsAction = own.filter { it.operationalStatus() in setOf(SellerRequestStatus.NEW,
        SellerRequestStatus.CUSTOMER_COUNTER, SellerRequestStatus.SELLER_REVIEW) }
    val waiting = own.count { it.operationalStatus() == SellerRequestStatus.WAITING_CUSTOMER }
    val unpaid = own.count { it.operationalStatus() == SellerRequestStatus.AGREED_UNPAID }
    val scheduled = own.count { it.operationalStatus() == SellerRequestStatus.PAID_FIXTURE }
    val upcoming = own.count { it.thread.stage in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER,
        QuoteStage.AGREED_AWAITING_PAYMENT, QuoteStage.PAID_CONFIRMED) &&
        it.latest.pickup.isAfter(LocalDateTime.now(RIT_TIME_ZONE)) }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_overview")) {
        CatalogHeader("Seller overview", onBack)
        LazyColumn(modifier = Modifier.testTag("seller_overview_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
            item {
                RitStatusIndicator("Debug scenario · no live orders or seller sync")
                Spacer(Modifier.height(12.dp))
                RitText(sellerName, RitType.heading)
                RitText("$roleLabel · seller-scoped preview", RitType.small, colors.muted)
                Spacer(Modifier.height(14.dp))
                val hours = PreviewCatalog.sellers.find { it.id == sellerId }?.hours?.display
                BulkInfoCard(if (hours == null) "Current availability unverified" else "Demo weekly hours configured",
                    hours?.let { "$it · fictional schedule; no live open/closed claim or real-time update." }
                        ?: "The weekly hours and closures used here are fictional testing data. No live open/closed claim or real-time update is available.")
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BulkInfoCard("${needsAction.size} need action", "Incoming or revised requests", Modifier.weight(1f))
                    BulkInfoCard("$waiting waiting", "Customer response", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BulkInfoCard("$unpaid unpaid", "Agreed, not verified", Modifier.weight(1f))
                    BulkInfoCard("$scheduled fixture", "Paid display samples", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                BulkInfoCard("$upcoming upcoming times", "Requested pickup times, not confirmed reservations")
                Spacer(Modifier.height(20.dp))
                RitText("Needs your attention", RitType.title)
                RitText(if (canRespond) "Review the latest revision before responding."
                    else "View-only staff fixture: response capability is not granted.", RitType.small, colors.muted)
                Spacer(Modifier.height(10.dp))
            }
            items(needsAction.take(3), key = { it.reference }) { record ->
                SellerRequestCard(record) { onRequest(record.reference) }
            }
            item {
                RitSecondaryButton("Open bulk request inbox", onInbox,
                    Modifier.fillMaxWidth().testTag("seller_open_inbox"))
                Spacer(Modifier.height(10.dp))
                RitSecondaryButton("Upcoming schedule", onSchedule,
                    Modifier.fillMaxWidth().testTag("seller_open_schedule"))
                Spacer(Modifier.height(10.dp))
                RitSecondaryButton("Seller management", onManagement,
                    Modifier.fillMaxWidth().testTag("seller_open_management"))
                Spacer(Modifier.height(10.dp))
                if (onAnalytics != null) {
                    RitSecondaryButton("Analytics", onAnalytics, Modifier.fillMaxWidth().testTag("seller_open_analytics"))
                    Spacer(Modifier.height(10.dp))
                }
                if (onReports != null) {
                    RitSecondaryButton("Reports", onReports, Modifier.fillMaxWidth().testTag("seller_open_reports"))
                    Spacer(Modifier.height(10.dp))
                }
                if (onSupport != null) {
                    RitSecondaryButton("Seller support", onSupport, Modifier.fillMaxWidth().testTag("seller_open_support"))
                    Spacer(Modifier.height(10.dp))
                }
                if (canHistory) RitSecondaryButton("Search and history", onHistory,
                    Modifier.fillMaxWidth().testTag("seller_open_history"))
                else BulkInfoCard("History restricted in this staff fixture", "Owner-configurable capabilities will be built in a later prompt.")
                Spacer(Modifier.height(12.dp))
                BulkInfoCard("Individual prepared-food orders deferred", "Acceptance, preparation, readiness and handover steps are not approved. This area has no operational actions.")
            }
        }
    }
}

@Composable
fun SellerInboxScreen(records: List<BulkRequestRecord>, sellerId: String, onBack: () -> Unit,
                      onRequest: (String) -> Unit) {
    val colors = RitThemeTokens.colors
    var filter by rememberSaveable { mutableStateOf(SellerRequestFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    val visible = filterSellerRequests(records, sellerId, filter, query)
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_inbox")) {
        CatalogHeader("Bulk requests", onBack)
        LazyColumn(modifier = Modifier.testTag("seller_inbox_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
            item {
                RitStatusIndicator("Seller-scoped demo · no backend delivery")
                Spacer(Modifier.height(10.dp))
                RitTextField(query, { query = it }, "Find a request", "Reference, customer or item",
                    Modifier.fillMaxWidth(), inputTag = "seller_inbox_search")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("seller_inbox_filters")) {
                    items(SellerRequestFilter.entries) { option ->
                        RitChip(option.label, filter == option, { filter = option },
                            Modifier.testTag("seller_filter_${option.name.lowercase()}"))
                    }
                }
                Spacer(Modifier.height(14.dp))
                RitText("${visible.size} demonstration records", RitType.small, colors.muted)
                Spacer(Modifier.height(10.dp))
            }
            if (visible.isEmpty()) item { RitEmptyState("No matching requests", "Try another status or reference.") }
            items(visible, key = { it.reference }) { record -> SellerRequestCard(record) { onRequest(record.reference) } }
        }
    }
}

@Composable
fun SellerRequestDetailScreen(record: BulkRequestRecord, canRespond: Boolean, onBack: () -> Unit,
                              onCounter: () -> Unit, onApproval: () -> Unit, onSpecial: () -> Unit,
                              onCompare: () -> Unit, onReject: () -> DemoTransitionResult) {
    val colors = RitThemeTokens.colors
    var feedback by remember { mutableStateOf("") }
    val active = record.thread.stage in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER)
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_request_detail")) {
        CatalogHeader("Request details", onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
            item {
                RitStatusIndicator("Debug-only request · no network transaction")
                Spacer(Modifier.height(12.dp))
                RitText(record.reference, RitType.heading)
                SellerStatus(record)
                Spacer(Modifier.height(8.dp))
                BulkInfoCard("Customer", record.customerLabel + " · only a minimal display label is shown")
                Spacer(Modifier.height(10.dp))
                BulkInfoCard("Requested pickup", displayPickup(record.original.pickup ?: record.latest.pickup))
                if (record.original.specialPickup) {
                    Spacer(Modifier.height(10.dp))
                    BulkInfoCard("Original special pickup request", record.original.specialReason)
                }
                if (record.original.instructions.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    BulkInfoCard("Customer notes", record.original.instructions)
                }
                Spacer(Modifier.height(14.dp))
                RitText("Current revision ${record.latest.version}", RitType.title)
                RitText("${record.latest.lines.sumOf { it.quantity }} units · minimum snapshot ${record.appliedMinimum}",
                    RitType.small, colors.muted)
                Spacer(Modifier.height(8.dp))
                record.latest.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        RitText("${line.title} × ${line.quantity}", RitType.body, modifier = Modifier.weight(1f))
                        RitText(displayPaise(line.unitPricePaise * line.quantity), RitType.label)
                    }
                }
                Spacer(Modifier.height(9.dp))
                BulkInfoCard("Latest quoted total", "${displayPaise(record.latest.totalPaise)} · negotiated preview amount, not payable")
                Spacer(Modifier.height(9.dp))
                BulkInfoCard("Latest pickup", displayPickup(record.latest.pickup))
                if (record.latest.specialPickup) {
                    Spacer(Modifier.height(9.dp))
                    BulkInfoCard("Explicit special decision", if (record.thread.specialAcceptedVersion == record.latest.version)
                        "Accepted for revision ${record.latest.version} only" else "Not yet accepted for this revision")
                    Spacer(Modifier.height(9.dp))
                    RitSecondaryButton("Review exceptional pickup", onSpecial,
                        Modifier.fillMaxWidth().testTag("seller_detail_special"))
                }
                Spacer(Modifier.height(12.dp))
                BulkInfoCard("Revision approvals", "Seller: ${record.thread.sellerAcceptedVersion ?: "none"} · Customer: ${record.thread.customerAcceptedVersion ?: "none"}. Both must match the latest version before payment could be considered.")
                if (record.thread.expiresAt != null) {
                    Spacer(Modifier.height(9.dp))
                    BulkInfoCard("Demo quote expiry", displayPickup(record.thread.expiresAt))
                }
                Spacer(Modifier.height(18.dp))
                RitText("Negotiation history", RitType.title)
                Spacer(Modifier.height(8.dp))
                record.thread.revisions.sortedByDescending { it.version }.forEach { revision ->
                    BulkInfoCard("Revision ${revision.version} · ${if (revision.actor == QuoteActor.SELLER) "Seller" else "Customer"}",
                        "${revision.lines.sumOf { it.quantity }} units · ${displayPaise(revision.totalPaise)} · ${displayPickup(revision.pickup)}${if (revision.specialPickup) " · special request" else ""}")
                    Spacer(Modifier.height(8.dp))
                }
                if (record.thread.revisions.size > 1) {
                    RitSecondaryButton("Compare latest revisions", onCompare,
                        Modifier.fillMaxWidth().testTag("seller_detail_compare"))
                    Spacer(Modifier.height(9.dp))
                }
                if (active && canRespond) {
                    if (record.latest.actor == QuoteActor.CUSTOMER) {
                        RitSecondaryButton("Create seller counteroffer", onCounter,
                            Modifier.fillMaxWidth().testTag("seller_detail_counter"))
                        Spacer(Modifier.height(9.dp))
                    }
                    RitPrimaryButton("Review final seller approval", onApproval,
                        Modifier.fillMaxWidth().testTag("seller_detail_approve"))
                    Spacer(Modifier.height(9.dp))
                    RitSecondaryButton("Reject request", { feedback = onReject().explanation },
                        Modifier.fillMaxWidth().testTag("seller_detail_reject"))
                } else if (!canRespond) BulkInfoCard("View only", "This debug staff identity lacks bulk.respond. It cannot change the quote or status.")
                if (feedback.isNotBlank()) {
                    Spacer(Modifier.height(9.dp))
                    RitText(feedback, RitType.small, colors.accent)
                }
                Spacer(Modifier.height(16.dp))
                RitText("No customer message, payment or order was delivered by a server.", RitType.small, colors.muted)
            }
        }
    }
}

@Composable
fun SellerRevisionCompareScreen(record: BulkRequestRecord, onBack: () -> Unit) {
    val colors = RitThemeTokens.colors
    val previous = record.thread.revisions.getOrNull(record.thread.revisions.lastIndex - 1)
    val current = record.latest
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_revision_compare")) {
        CatalogHeader("Compare revisions", onBack)
        if (previous == null) { RitEmptyState("No earlier revision", "This request has one proposal so far."); return@Column }
        val diff = compareQuotes(previous, current)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
            item {
                RitStatusIndicator("Immutable demo versions ${previous.version} → ${current.version}")
                Spacer(Modifier.height(14.dp))
                BulkInfoCard("Previous", "${displayPaise(previous.totalPaise)} · ${displayPickup(previous.pickup)}")
                Spacer(Modifier.height(9.dp))
                BulkInfoCard("Latest", "${displayPaise(current.totalPaise)} · ${displayPickup(current.pickup)}")
                Spacer(Modifier.height(16.dp))
                RitText("Changed terms", RitType.title)
                Spacer(Modifier.height(7.dp))
                diff.removed.forEach { RitText("Removed: ${it.title} × ${it.quantity}", RitType.body) }
                diff.added.forEach { RitText("Added: ${it.title} × ${it.quantity}", RitType.body, colors.accent) }
                diff.changed.forEach { (old, new) ->
                    RitText("${new.title}: ${old.quantity} → ${new.quantity}; ${displayPaise(old.unitPricePaise)} → ${displayPaise(new.unitPricePaise)} each", RitType.body)
                }
                RitText("Total: ${displayPaise(previous.totalPaise)} → ${displayPaise(current.totalPaise)}", RitType.body)
                RitText("Pickup: ${displayPickup(previous.pickup)} → ${displayPickup(current.pickup)}", RitType.body)
                RitText("Special pickup: ${previous.specialPickup} → ${current.specialPickup}", RitType.body)
                Spacer(Modifier.height(15.dp))
                BulkInfoCard("Approvals are version-bound", "A previous acceptance cannot authorize a changed item, quantity, price or pickup time.")
            }
        }
    }
}

@Composable
fun SellerFinalApprovalScreen(record: BulkRequestRecord, canRespond: Boolean, onBack: () -> Unit,
                              onSpecial: () -> Unit, onApprove: () -> DemoTransitionResult) {
    val colors = RitThemeTokens.colors
    var feedback by remember { mutableStateOf("") }
    val latest = record.latest
    val specialReady = !latest.specialPickup || record.thread.specialAcceptedVersion == latest.version
    val canApprove = canRespond && specialReady && record.thread.stage in setOf(
        QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER) && record.thread.sellerAcceptedVersion != latest.version
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_final_approval")) {
        CatalogHeader("Final quote review", onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
            item {
                RitStatusIndicator("Seller decision · local demo only")
                Spacer(Modifier.height(14.dp))
                RitText("${record.reference} · revision ${latest.version}", RitType.heading)
                Spacer(Modifier.height(10.dp))
                latest.lines.forEach { line ->
                    RitText("${line.title} × ${line.quantity} · ${displayPaise(line.unitPricePaise * line.quantity)}", RitType.body)
                }
                Spacer(Modifier.height(12.dp))
                BulkInfoCard("Final quoted amount", displayPaise(latest.totalPaise))
                Spacer(Modifier.height(9.dp))
                BulkInfoCard("Proposed pickup", displayPickup(latest.pickup))
                if (latest.specialPickup) {
                    Spacer(Modifier.height(9.dp))
                    BulkInfoCard("Special timing", "${latest.specialReason}\n${if (specialReady) "Explicitly accepted for this revision" else "Not explicitly accepted"}")
                    Spacer(Modifier.height(9.dp))
                    RitSecondaryButton("Review special pickup", onSpecial, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                BulkInfoCard("Customer response", "Customer approval of revision ${latest.version}: ${if (record.thread.customerAcceptedVersion == latest.version) "recorded in demo" else "not recorded"}. Seller approval alone cannot trigger payment.")
                Spacer(Modifier.height(12.dp))
                RitPrimaryButton("Approve exact revision in demo", { feedback = onApprove().explanation },
                    Modifier.fillMaxWidth().testTag("seller_final_approve_action"), enabled = canApprove)
                if (!specialReady) RitText("Accept exceptional pickup separately before approving the quote.",
                    RitType.small, colors.accent)
                if (feedback.isNotBlank()) RitText(feedback, RitType.small, colors.accent)
                Spacer(Modifier.height(12.dp))
                RitText("This is not a server acknowledgement or a payment request.", RitType.small, colors.muted)
            }
        }
    }
}

@Composable
fun SellerSpecialPickupScreen(record: BulkRequestRecord, canRespond: Boolean, onBack: () -> Unit,
                              onAccept: () -> DemoTransitionResult, onReject: () -> DemoTransitionResult) {
    val colors = RitThemeTokens.colors
    var feedback by remember { mutableStateOf("") }
    val latest = record.latest
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_special_pickup")) {
        CatalogHeader("Special pickup request", onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
            item {
                RitStatusIndicator("Exception is never automatic")
                Spacer(Modifier.height(14.dp))
                RitText("${record.reference} · revision ${latest.version}", RitType.heading)
                Spacer(Modifier.height(13.dp))
                BulkInfoCard("Proposed exceptional time", displayPickup(latest.pickup))
                Spacer(Modifier.height(9.dp))
                BulkInfoCard("Customer justification", latest.specialReason.ifBlank { "No reason recorded" })
                if (record.original.instructions.isNotBlank()) {
                    Spacer(Modifier.height(9.dp))
                    BulkInfoCard("Customer notes", record.original.instructions)
                }
                Spacer(Modifier.height(9.dp))
                BulkInfoCard("Demo policy conflict", "This fixture may fall outside weekly hours, on a holiday or during a temporary closure. Actual seller hours are not verified.")
                Spacer(Modifier.height(13.dp))
                RitText(if (record.thread.specialAcceptedVersion == latest.version) "Accepted for this exact revision"
                    else "Not accepted for this revision", RitType.label)
                Spacer(Modifier.height(12.dp))
                RitPrimaryButton("Explicitly accept special timing", { feedback = onAccept().explanation },
                    Modifier.fillMaxWidth().testTag("seller_special_accept"),
                    enabled = canRespond && latest.specialPickup && record.thread.specialAcceptedVersion != latest.version &&
                        record.thread.stage in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER))
                Spacer(Modifier.height(9.dp))
                if (canRespond && record.thread.stage in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER)) {
                    RitSecondaryButton("Reject this special request", { feedback = onReject().explanation },
                        Modifier.fillMaxWidth().testTag("seller_special_reject"))
                }
                if (!canRespond) RitText("View only: this mock role cannot decide exceptional pickup.", RitType.small, colors.muted)
                if (feedback.isNotBlank()) RitText(feedback, RitType.small, colors.accent)
            }
        }
    }
}

@Composable
fun SellerScheduleScreen(records: List<BulkRequestRecord>, sellerId: String, onBack: () -> Unit,
                         onRequest: (String) -> Unit) {
    val colors = RitThemeTokens.colors
    var filter by rememberSaveable { mutableStateOf(SellerRequestFilter.ALL) }
    val scheduleFilters = listOf(SellerRequestFilter.ALL, SellerRequestFilter.NEEDS_ACTION,
        SellerRequestFilter.WAITING_CUSTOMER, SellerRequestFilter.UNPAID, SellerRequestFilter.PAID)
    val upcoming = filterSellerRequests(records, sellerId, filter).filter { it.thread.stage in setOf(
        QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER, QuoteStage.AGREED_AWAITING_PAYMENT,
        QuoteStage.PAID_CONFIRMED) && it.latest.pickup.isAfter(LocalDateTime.now(RIT_TIME_ZONE)) }
        .sortedBy { it.latest.pickup }
    val groups = upcoming.groupBy { it.latest.pickup.toLocalDate() }.toSortedMap()
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_schedule")) {
        CatalogHeader("Upcoming pickups", onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
            item {
                RitStatusIndicator("Demo schedule · requested times are not reservations")
                Spacer(Modifier.height(12.dp))
                BulkInfoCard("Payment truth", "Only the verified-paid fixture is shown as a confirmed order. New requests, counters and agreed-unpaid quotes are not paid orders.")
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("seller_schedule_filters")) {
                    items(scheduleFilters) { option ->
                        RitChip(option.label, filter == option, { filter = option },
                            Modifier.testTag("seller_schedule_filter_${option.name.lowercase()}"))
                    }
                }
                Spacer(Modifier.height(12.dp))
                RitText("${upcoming.size} upcoming demonstration records", RitType.small, colors.muted)
                Spacer(Modifier.height(10.dp))
            }
            groups.forEach { (date, dayRecords) ->
                item(key = "date_$date") {
                    RitText(date.format(groupDateFormat), RitType.title)
                    Spacer(Modifier.height(8.dp))
                }
                items(dayRecords, key = { it.reference }) { record ->
                    SellerRequestCard(record) { onRequest(record.reference) }
                }
            }
            if (groups.isEmpty()) item { RitEmptyState("No upcoming demo records", "No confirmed pickup is inferred from a request alone.") }
        }
    }
}

@Composable
fun SellerHistoryScreen(records: List<BulkRequestRecord>, sellerId: String, onBack: () -> Unit,
                        onRequest: (String) -> Unit) {
    val colors = RitThemeTokens.colors
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SellerRequestFilter.ALL) }
    val visible = filterSellerRequests(records, sellerId, filter, query)
    val groups = visible.groupBy { it.createdAt.toLocalDate() }.toSortedMap(reverseOrder())
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_history")) {
        CatalogHeader("Search and history", onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
            item {
                RitStatusIndicator("Fictional request records · no sales history")
                Spacer(Modifier.height(12.dp))
                RitTextField(query, { query = it }, "Find a record", "Reference, customer or item",
                    Modifier.fillMaxWidth(), inputTag = "seller_history_search")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("seller_history_filters")) {
                    items(SellerRequestFilter.entries) { option ->
                        RitChip(option.label, filter == option, { filter = option },
                            Modifier.testTag("seller_history_filter_${option.name.lowercase()}"))
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            groups.forEach { (date, dayRecords) ->
                item(key = "history_$date") {
                    RitText(date.format(groupDateFormat), RitType.title)
                    Spacer(Modifier.height(8.dp))
                }
                items(dayRecords, key = { it.reference }) { record ->
                    SellerRequestCard(record) { onRequest(record.reference) }
                }
            }
            if (groups.isEmpty()) item { RitEmptyState("No matching records", "Try another reference or status filter.") }
        }
    }
}

@Composable
fun CustomerDemoRequestsScreen(records: List<BulkRequestRecord>, customerId: String,
                               onBack: () -> Unit, onRequest: (String) -> Unit) {
    val colors = RitThemeTokens.colors
    val mine = records.filter { it.customerUserId == customerId }.sortedByDescending { it.createdAt }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("customer_demo_requests")) {
        CatalogHeader("Local demo requests", onBack)
        LazyColumn(modifier = Modifier.testTag("customer_demo_requests_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
            item {
                RitStatusIndicator("Single-device scenarios · no actual seller received these")
                Spacer(Modifier.height(12.dp))
                RitText("These records are separate from real orders and disappear on reset.", RitType.body, colors.muted)
                Spacer(Modifier.height(15.dp))
            }
            items(mine, key = { it.reference }) { record -> SellerRequestCard(record) { onRequest(record.reference) } }
        }
    }
}

@Composable
fun CustomerDemoRequestScreen(record: BulkRequestRecord, onBack: () -> Unit,
                              onCounter: () -> Unit, onCompare: () -> Unit,
                              onApprove: () -> DemoTransitionResult, onWithdraw: () -> DemoTransitionResult) {
    val colors = RitThemeTokens.colors
    var feedback by remember { mutableStateOf("") }
    val latest = record.latest
    val canApprove = record.thread.stage == QuoteStage.COUNTEROFFER &&
        record.thread.sellerAcceptedVersion == latest.version &&
        (!latest.specialPickup || record.thread.specialAcceptedVersion == latest.version)
    val canWithdraw = record.thread.stage in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER,
        QuoteStage.AGREED_AWAITING_PAYMENT)
    Column(Modifier.fillMaxSize().background(colors.background).testTag("customer_demo_request")) {
        CatalogHeader("Demo bulk request", onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
            item {
                RitStatusIndicator("Local scenario · no seller notification or payment")
                Spacer(Modifier.height(12.dp))
                RitText(record.reference, RitType.heading)
                SellerStatus(record)
                Spacer(Modifier.height(13.dp))
                BulkInfoCard("Current quote · revision ${latest.version}",
                    "${latest.lines.joinToString { "${it.title} × ${it.quantity}" }}\n${displayPaise(latest.totalPaise)} · ${displayPickup(latest.pickup)}")
                if (latest.specialPickup) {
                    Spacer(Modifier.height(9.dp))
                    BulkInfoCard("Special pickup", "${latest.specialReason}\nSeller acceptance: ${if (record.thread.specialAcceptedVersion == latest.version) "explicit on this version" else "pending"}")
                }
                Spacer(Modifier.height(10.dp))
                BulkInfoCard("Exact-version approval", "Seller: ${record.thread.sellerAcceptedVersion ?: "none"} · Customer: ${record.thread.customerAcceptedVersion ?: "none"}. Both must match revision ${latest.version}; payment remains disabled without a server.")
                if (record.thread.expiresAt != null) {
                    Spacer(Modifier.height(9.dp))
                    BulkInfoCard("Demo expiry", displayPickup(record.thread.expiresAt))
                }
                Spacer(Modifier.height(16.dp))
                RitText("Revision timeline", RitType.title)
                Spacer(Modifier.height(8.dp))
                record.thread.revisions.sortedByDescending { it.version }.forEach { revision ->
                    BulkInfoCard("Revision ${revision.version} · ${revision.actor}",
                        "${displayPaise(revision.totalPaise)} · ${displayPickup(revision.pickup)}")
                    Spacer(Modifier.height(8.dp))
                }
                if (record.thread.revisions.size > 1) RitSecondaryButton("Compare revisions", onCompare,
                    Modifier.fillMaxWidth().testTag("customer_demo_compare"))
                if (record.thread.stage == QuoteStage.COUNTEROFFER && latest.actor == QuoteActor.SELLER) {
                    Spacer(Modifier.height(9.dp))
                    RitSecondaryButton("Propose a revision in demo", onCounter,
                        Modifier.fillMaxWidth().testTag("customer_demo_counter"))
                }
                Spacer(Modifier.height(10.dp))
                RitPrimaryButton("Approve exact revision in demo", { feedback = onApprove().explanation },
                    Modifier.fillMaxWidth().testTag("customer_demo_approve"), enabled = canApprove)
                if (canWithdraw) {
                    Spacer(Modifier.height(9.dp))
                    RitSecondaryButton("Withdraw unpaid demo request", { feedback = onWithdraw().explanation },
                        Modifier.fillMaxWidth().testTag("customer_demo_withdraw"))
                }
                if (feedback.isNotBlank()) RitText(feedback, RitType.small, colors.accent)
                Spacer(Modifier.height(12.dp))
                BulkInfoCard("No checkout", "Even a mutually agreed demo quote cannot create a payable intent or a verified order without authoritative backend and payment-provider confirmation.")
            }
        }
    }
}
