package edu.msrit.canteen.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.BulkDraftSession
import edu.msrit.canteen.customer.BulkPolicySource
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.PickupWindow
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.QuoteDifference
import edu.msrit.canteen.customer.QuoteLineSnapshot
import edu.msrit.canteen.customer.QuoteRevision
import edu.msrit.canteen.customer.QuoteStage
import edu.msrit.canteen.customer.QuoteThread
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.checkBulkDraft
import edu.msrit.canteen.customer.compareQuotes
import edu.msrit.canteen.customer.paymentEligibility
import edu.msrit.canteen.customer.pickupWindow
import edu.msrit.canteen.customer.visibleItems
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
private fun QuoteLine(line: QuoteLineSnapshot, prefix: String = "") {
    val colors = RitThemeTokens.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        RitText("$prefix${line.title} × ${line.quantity}", RitType.body, modifier = Modifier.weight(1f))
        RitText(displayPaise(line.unitPricePaise * line.quantity), RitType.label, colors.accent)
    }
}

@Composable
private fun QuoteCard(title: String, revision: QuoteRevision) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxWidth().border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
        .background(colors.card, RoundedCornerShape(RitRadii.card)).padding(16.dp)) {
        RitText("$title · revision ${revision.version}", RitType.title)
        RitText("${revision.lines.sumOf { it.quantity }} units · ${displayPaise(revision.totalPaise)}", RitType.label, colors.accent)
        RitText(displayPickup(revision.pickup), RitType.body, colors.muted)
        if (revision.specialPickup) RitText("Special pickup needs explicit seller approval", RitType.small, colors.accent)
        Spacer(Modifier.height(7.dp))
        revision.lines.forEach { QuoteLine(it) }
        if (revision.note.isNotBlank()) RitText(revision.note, RitType.small, colors.muted)
    }
}

@Composable
fun BulkOrdersScreen(onBack: () -> Unit, onDraft: () -> Unit, onQuickReorder: () -> Unit,
                     onSampleNegotiation: () -> Unit,
                     onDemoRequests: (() -> Unit)? = null,
                     showSample: Boolean = false) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).testTag("bulk_orders")) {
        CatalogHeader("My bulk requests", onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                RitStatusIndicator(if (showSample) "Development scenario · not an actual request" else "No verified account or orders")
                Spacer(Modifier.height(14.dp))
                if (showSample) {
                    BulkInfoCard("DEMO-BULK-01 · counteroffer", "A fictional seller response demonstrates the future revision timeline. It was not sent by Main Canteen and is not payable.",
                        Modifier.testTag("bulk_orders_sample"))
                    Spacer(Modifier.height(10.dp))
                    RitSecondaryButton("Inspect demo negotiation", onSampleNegotiation,
                        Modifier.fillMaxWidth().testTag("bulk_orders_open_sample"))
                } else RitEmptyState("No bulk requests yet", "Local draft composition is available in debug, but no request can be sent without authentication and a backend.")
                Spacer(Modifier.height(16.dp))
                RitPrimaryButton("Create a local bulk draft", onDraft, Modifier.fillMaxWidth().testTag("bulk_orders_create_draft"))
                if (onDemoRequests != null) {
                    Spacer(Modifier.height(10.dp))
                    RitSecondaryButton("Open local demo scenarios", onDemoRequests,
                        Modifier.fillMaxWidth().testTag("bulk_orders_demo_requests"))
                }
                Spacer(Modifier.height(10.dp))
                RitSecondaryButton("Quick Reorder preview", onQuickReorder,
                    Modifier.fillMaxWidth().testTag("bulk_orders_quick_reorder"))
            }
        }
    }
}

@Composable
fun BulkNegotiationScreen(thread: QuoteThread?, onBack: () -> Unit, onCompare: () -> Unit,
                          onCounterDraft: () -> Unit, onFinal: () -> Unit) {
    val colors = RitThemeTokens.colors
    if (thread == null) { RitEmptyState("No negotiation data", "This demo is available only in debug builds."); return }
    val latest = thread.revisions.maxByOrNull { it.version } ?: return
    Column(Modifier.fillMaxSize().background(colors.background).testTag("bulk_negotiation")) {
        CatalogHeader("Bulk negotiation", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                RitStatusIndicator("DEMO timeline · no seller message was received")
                Spacer(Modifier.height(14.dp))
                RitText(thread.reference, RitType.small, colors.muted)
                RitText(when (thread.stage) {
                    QuoteStage.SUBMITTED -> "Awaiting seller · demo"
                    QuoteStage.COUNTEROFFER -> "Seller counteroffer · demo"
                    QuoteStage.AGREED_AWAITING_PAYMENT -> "Same revision agreed · demo only"
                    QuoteStage.PAID_CONFIRMED -> "Verified-paid fixture · demo only"
                    QuoteStage.EXPIRED -> "Quote expired · demo"
                    QuoteStage.REJECTED -> "Request rejected · demo"
                    QuoteStage.WITHDRAWN -> "Request withdrawn · demo"
                }, RitType.heading)
                Spacer(Modifier.height(10.dp))
                BulkInfoCard("Every version is separate", "An actual seller could accept, reject or change items, quantities, pickup and price. Neither side may accept an obsolete quote. This preview cannot commit a response.")
                Spacer(Modifier.height(16.dp))
                RitText("Revision timeline", RitType.title)
                Spacer(Modifier.height(8.dp))
            }
            items(thread.revisions.sortedByDescending { it.version }, key = { it.version }) { revision ->
                Column(Modifier.padding(bottom = 10.dp).testTag("bulk_revision_${revision.version}")) {
                    QuoteCard(if (revision.actor == edu.msrit.canteen.customer.QuoteActor.SELLER) "Seller proposal · demo" else "Customer request · demo", revision)
                }
            }
            item {
                if (thread.revisions.size >= 2) {
                    RitSecondaryButton("Compare revisions", onCompare,
                        Modifier.fillMaxWidth().testTag("bulk_compare_open"))
                    Spacer(Modifier.height(9.dp))
                }
                if (thread.stage == QuoteStage.COUNTEROFFER) {
                    RitSecondaryButton("Draft a counterproposal locally", onCounterDraft,
                        Modifier.fillMaxWidth().testTag("bulk_counter_open"))
                    Spacer(Modifier.height(9.dp))
                    RitPrimaryButton("Accept / decline unavailable · no server", {},
                        Modifier.fillMaxWidth().testTag("bulk_accept_disabled"), enabled = false)
                }
                if (thread.stage == QuoteStage.AGREED_AWAITING_PAYMENT) {
                    RitSecondaryButton("Review demo agreed quote", onFinal,
                        Modifier.fillMaxWidth().testTag("bulk_final_open"))
                }
                if (thread.stage == QuoteStage.EXPIRED) BulkInfoCard("Expired", "A real expired quote must receive fresh seller approval. No payment request is possible.")
            }
        }
    }
}

@Composable
fun BulkCompareScreen(thread: QuoteThread?, onBack: () -> Unit, onCounterDraft: () -> Unit) {
    val colors = RitThemeTokens.colors
    if (thread == null || thread.revisions.size < 2) { RitEmptyState("Nothing to compare", "Two demo revisions are needed."); return }
    val previous = thread.revisions[thread.revisions.lastIndex - 1]
    val current = thread.revisions.last()
    val difference = compareQuotes(previous, current)
    Column(Modifier.fillMaxSize().background(colors.background).testTag("bulk_compare")) {
        CatalogHeader("Compare offers", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                RitStatusIndicator("Demo revisions · immutable snapshots")
                Spacer(Modifier.height(14.dp))
                QuoteCard("Previous", previous)
                Spacer(Modifier.height(10.dp))
                QuoteCard("Latest", current)
                Spacer(Modifier.height(18.dp))
                RitText("Exactly what changed", RitType.title)
                Spacer(Modifier.height(8.dp))
                if (difference.added.isEmpty() && difference.removed.isEmpty() && difference.changed.isEmpty())
                    RitText("Items and quantities unchanged", RitType.body, colors.muted)
                difference.removed.forEach { RitText("Removed: ${it.title} × ${it.quantity}", RitType.body, colors.muted) }
                difference.added.forEach { RitText("Added: ${it.title} × ${it.quantity}", RitType.body, colors.accent) }
                difference.changed.forEach { (old, new) ->
                    RitText("${new.title}: ${old.quantity} → ${new.quantity}; ${displayPaise(old.unitPricePaise)} → ${displayPaise(new.unitPricePaise)} each", RitType.body)
                }
                RitText("Amount: ${displayPaise(previous.totalPaise)} → ${displayPaise(current.totalPaise)}", RitType.body)
                RitText("Pickup: ${displayPickup(previous.pickup)} → ${displayPickup(current.pickup)}", RitType.body)
                RitText("Special approval flag: ${if (previous.specialPickup) "Yes" else "No"} → ${if (current.specialPickup) "Yes" else "No"}", RitType.body)
                Spacer(Modifier.height(14.dp))
                BulkInfoCard("No silent change", "A future acceptance must specify the exact latest revision and be confirmed by the backend. Comparison alone does not approve a quote.")
                if (thread.stage == QuoteStage.COUNTEROFFER) {
                    Spacer(Modifier.height(10.dp))
                    RitSecondaryButton("Draft a counterproposal locally", onCounterDraft,
                        Modifier.fillMaxWidth().testTag("bulk_compare_counter"))
                }
            }
        }
    }
}

@Composable
fun BulkCounterproposalScreen(thread: QuoteThread?, policies: BulkPolicySource, onBack: () -> Unit,
                              catalog: CustomerCatalogSource = PreviewCatalog) {
    val colors = RitThemeTokens.colors
    val context = LocalContext.current
    if (thread == null || thread.stage != QuoteStage.COUNTEROFFER) {
        RitEmptyState("No current counteroffer", "Only the latest active demo counteroffer can be edited locally."); return
    }
    val latest = thread.revisions.maxByOrNull { it.version } ?: return
    val draft = remember(thread.reference, latest.version) {
        BulkDraftSession().apply {
            selectSeller(thread.sellerId)
            latest.lines.forEach { line -> catalog.items.find { it.id == line.itemId }?.let { setQuantity(it, line.quantity) } }
            setPickup(latest.pickup)
            setSpecial(latest.specialPickup)
        }
    }
    var proposedRupees by rememberSaveable(thread.reference) { mutableStateOf((latest.totalPaise / 100).toString()) }
    var message by rememberSaveable(thread.reference) { mutableStateOf("") }
    var saved by rememberSaveable(thread.reference) { mutableStateOf(false) }
    val check = checkBulkDraft(draft.draft, catalog, policies, LocalDateTime.now(RIT_TIME_ZONE))
    val total = proposedRupees.toLongOrNull()
    Column(Modifier.fillMaxSize().background(colors.background).testTag("bulk_counter_draft")) {
        CatalogHeader("Counterproposal draft", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                RitStatusIndicator("Local editing only · not sent to the seller")
                Spacer(Modifier.height(12.dp))
                BulkInfoCard("Based on demo revision ${latest.version}", "Change quantities, proposed pickup or amount. This is not a submitted quote revision; real negotiation needs an authenticated server.")
                Spacer(Modifier.height(16.dp))
                RitText("Items and quantities", RitType.title)
            }
            items(catalog.visibleItems(thread.sellerId), key = { it.id }) { item ->
                if (edu.msrit.canteen.customer.itemEligibility(catalog.sellers.first { it.id == thread.sellerId }, item) != edu.msrit.canteen.customer.ItemEligibility.UNAVAILABLE) {
                    val quantity = draft.draft.lines.find { it.itemId == item.id }?.quantity ?: 0
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            RitText(item.name, RitType.label)
                            RitText("Current demo catalog: ${item.price.display}", RitType.small, colors.muted)
                        }
                        RitChip("−", false, { draft.setQuantity(item, (quantity - 1).coerceAtLeast(0)); saved = false }, Modifier.testTag("bulk_counter_minus_${item.id}"))
                        RitText("$quantity", RitType.label, modifier = Modifier.padding(horizontal = 8.dp).testTag("bulk_counter_qty_${item.id}"))
                        RitChip("+", false, { draft.setQuantity(item, quantity + 1); saved = false }, Modifier.testTag("bulk_counter_plus_${item.id}"))
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                RitText("${check.totalUnits} units · minimum ${check.minimum} for this seller", RitType.body)
                Spacer(Modifier.height(14.dp))
                RitText("Proposed pickup", RitType.title)
                RitText(draft.draft.pickup?.let(::displayPickup) ?: "Choose a time", RitType.body, colors.muted)
                Spacer(Modifier.height(8.dp))
                RitSecondaryButton("Edit pickup date", {
                    val initial = draft.draft.pickup?.toLocalDate() ?: LocalDate.now(RIT_TIME_ZONE).plusDays(1)
                    DatePickerDialog(context, { _, year, month, day ->
                        draft.setPickup(LocalDateTime.of(LocalDate.of(year, month + 1, day), draft.draft.pickup?.toLocalTime() ?: LocalTime.NOON)); saved = false
                    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                }, Modifier.fillMaxWidth().testTag("bulk_counter_pick_date"))
                Spacer(Modifier.height(7.dp))
                RitSecondaryButton("Edit pickup time", {
                    val initial = draft.draft.pickup?.toLocalTime() ?: LocalTime.NOON
                    TimePickerDialog(context, { _, hour, minute ->
                        draft.setPickup(LocalDateTime.of(draft.draft.pickup?.toLocalDate() ?: LocalDate.now(RIT_TIME_ZONE).plusDays(1), LocalTime.of(hour, minute))); saved = false
                    }, initial.hour, initial.minute, false).show()
                }, Modifier.fillMaxWidth().testTag("bulk_counter_pick_time"))
                val window = draft.draft.pickup?.let { pickupWindow(policies.forSeller(thread.sellerId), it, LocalDateTime.now(RIT_TIME_ZONE)) }
                if (window == PickupWindow.SPECIAL_REQUIRED) {
                    Spacer(Modifier.height(8.dp))
                    RitChip("Special pickup request", draft.draft.specialPickup,
                        { draft.setSpecial(!draft.draft.specialPickup); saved = false }, Modifier.testTag("bulk_counter_special"))
                    Spacer(Modifier.height(8.dp))
                    BulkTextBox("Special request reason", draft.draft.specialReason, { draft.setSpecialReason(it); saved = false },
                        "Why is an exception needed?", "bulk_counter_special_reason")
                }
                Spacer(Modifier.height(14.dp))
                RitText("Proposed total · illustrative rupees", RitType.label)
                BasicTextField(proposedRupees, { proposedRupees = it.filter(Char::isDigit).take(7); saved = false },
                    Modifier.fillMaxWidth().border(1.dp, colors.outline, RoundedCornerShape(16.dp)).padding(14.dp)
                        .testTag("bulk_counter_total"),
                    textStyle = RitType.title.copy(color = colors.foreground), cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(14.dp))
                BulkTextBox("Message (optional)", message, { message = it.take(500); saved = false },
                    "Explain the proposed change…", "bulk_counter_message")
                Spacer(Modifier.height(10.dp))
                if (saved) BulkInfoCard("Local counterproposal saved", "Nothing was sent. No seller has accepted this proposal and no payment can follow from it.",
                    Modifier.testTag("bulk_counter_saved"))
            }
        }
        RitPrimaryButton("Save local counterproposal", { saved = true },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("bulk_counter_save"),
            enabled = check.readyForLocalReview && total != null && total > 0)
    }
}

@Composable
fun BulkFinalQuoteScreen(thread: QuoteThread?, onBack: () -> Unit, onPayment: () -> Unit) {
    val colors = RitThemeTokens.colors
    if (thread == null) { RitEmptyState("No agreed quote", "No verified quote exists."); return }
    val latest = thread.revisions.maxByOrNull { it.version } ?: return
    val sameRevision = thread.sellerAcceptedVersion == latest.version && thread.customerAcceptedVersion == latest.version
    Column(Modifier.fillMaxSize().background(colors.background).testTag("bulk_final_quote")) {
        CatalogHeader("Final quote · demo", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                RitStatusIndicator("Illustrative agreement · not server confirmed")
                Spacer(Modifier.height(12.dp))
                BulkInfoCard(if (sameRevision) "Both sides marked demo revision ${latest.version}" else "No exact matching approval",
                    "Real approval must be recorded and checked by the server against the same latest immutable version. This sample does not create a payment request.")
                Spacer(Modifier.height(14.dp))
                QuoteCard("Agreed snapshot · demo", latest)
                Spacer(Modifier.height(10.dp))
                BulkInfoCard("Quote expiry", thread.expiresAt?.let(::displayPickup) ?: "No valid expiry. A real seller-approved quote needs a server-enforced deadline.")
                Spacer(Modifier.height(10.dp))
                RitSecondaryButton("Inspect payment eligibility", onPayment,
                    Modifier.fillMaxWidth().testTag("bulk_payment_open"))
            }
        }
        RitPrimaryButton("Approve / pay unavailable · no server", {},
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("bulk_final_approve_disabled"), enabled = false)
    }
}

@Composable
fun BulkPaymentEligibilityScreen(thread: QuoteThread?, onBack: () -> Unit,
                                 now: LocalDateTime = LocalDateTime.now(RIT_TIME_ZONE)) {
    val colors = RitThemeTokens.colors
    val latest = thread?.revisions?.maxByOrNull { it.version }
    val structurallyReady = thread != null && latest != null && thread.stage == QuoteStage.AGREED_AWAITING_PAYMENT &&
        thread.sellerAcceptedVersion == latest.version && thread.customerAcceptedVersion == latest.version &&
        thread.expiresAt?.isAfter(now) == true
    val payable = thread?.let { paymentEligibility(it, now, serverConfirmed = false) } == true
    Column(Modifier.fillMaxSize().background(colors.background).testTag("bulk_payment_eligibility")) {
        CatalogHeader("Payment eligibility", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                RitStatusIndicator("No payment provider or verified request")
                Spacer(Modifier.height(16.dp))
                RitText(if (structurallyReady) "Demo quote is structurally agreed" else "Quote cannot be paid", RitType.heading)
                Spacer(Modifier.height(10.dp))
                BulkInfoCard("Payment remains unavailable", if (structurallyReady)
                    "The demo has matching revision numbers and an unexpired example window, but no backend has confirmed the request, seller merchant or payable intent."
                    else "A quote must be the latest exact revision, agreed by both parties, unexpired and validated by the backend before any payment request.")
                Spacer(Modifier.height(14.dp))
                if (latest != null) {
                    QuoteCard("Illustrative amount · not payable", latest)
                    Spacer(Modifier.height(10.dp))
                }
                BulkInfoCard("PhonePe is not connected", "Merchant eligibility, seller-specific settlement, gateway credentials and provider verification remain unresolved. No checkout, success state or paid order can be created here.")
                Spacer(Modifier.height(10.dp))
                RitText("Server-confirmed eligibility: ${if (payable) "yes" else "no"}", RitType.label,
                    modifier = Modifier.testTag("bulk_payment_server_gate"))
            }
        }
        RitPrimaryButton("Payment unavailable", {},
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("bulk_payment_disabled"), enabled = false)
    }
}
