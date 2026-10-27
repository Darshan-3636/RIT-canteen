package edu.msrit.canteen.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.BulkPolicySource
import edu.msrit.canteen.customer.BulkProposal
import edu.msrit.canteen.customer.BulkRequestRecord
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.DemoTransitionResult
import edu.msrit.canteen.customer.ItemEligibility
import edu.msrit.canteen.customer.MenuItem
import edu.msrit.canteen.customer.PickupWindow
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.QuoteActor
import edu.msrit.canteen.customer.QuoteLineSnapshot
import edu.msrit.canteen.customer.QuoteRevision
import edu.msrit.canteen.customer.QuoteStage
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.compareQuotes
import edu.msrit.canteen.customer.itemEligibility
import edu.msrit.canteen.customer.pickupWindow
import edu.msrit.canteen.customer.validateBulkProposal
import edu.msrit.canteen.customer.visibleItems
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private fun SnapshotStateMap<String, Int>.setBounded(itemId: String, value: Int) {
    this[itemId] = value.coerceIn(0, 999)
}

/** Shared editor for seller and customer demo revisions. Sending only mutates the local debug repository. */
@Composable
fun DemoQuoteEditorScreen(record: BulkRequestRecord, isSeller: Boolean, policies: BulkPolicySource,
                          onBack: () -> Unit, onSubmit: (BulkProposal, (Boolean, String) -> Unit) -> Unit,
                          onApplied: () -> Unit, catalog: CustomerCatalogSource = PreviewCatalog) {
    val colors = RitThemeTokens.colors
    val context = LocalContext.current
    val latest = record.latest
    val seller = catalog.sellers.firstOrNull { it.id == record.sellerId }
    val menu = catalog.visibleItems(record.sellerId).filter {
        seller != null && itemEligibility(seller, it) != ItemEligibility.UNAVAILABLE
    }
    val quantities = remember(record.reference, latest.version) {
        mutableStateMapOf<String, Int>().apply {
            menu.forEach { item -> put(item.id, latest.lines.find { it.itemId == item.id }?.quantity ?: 0) }
        }
    }
    val prices = remember(record.reference, latest.version) {
        mutableStateMapOf<String, String>().apply {
            menu.forEach { item -> put(item.id, item.price.rupees.toString()) }
        }
    }
    var pickup by remember(record.reference, latest.version) { mutableStateOf(latest.pickup) }
    var special by remember(record.reference, latest.version) { mutableStateOf(latest.specialPickup) }
    var specialReason by remember(record.reference, latest.version) { mutableStateOf(latest.specialReason) }
    var note by remember(record.reference, latest.version) { mutableStateOf("") }
    var feedback by remember(record.reference, latest.version) { mutableStateOf("") }
    var showingPreview by remember(record.reference, latest.version) { mutableStateOf(false) }
    val selected = menu.filter { (quantities[it.id] ?: 0) > 0 }
    val pricesValid = selected.all { prices[it.id]?.toLongOrNull() != null }
    val lines = selected.mapNotNull { item ->
        val rupees = prices[item.id]?.toLongOrNull() ?: return@mapNotNull null
        QuoteLineSnapshot(item.id, item.name, quantities[item.id] ?: 0, rupees * 100L)
    }
    val proposal = BulkProposal(lines, pickup, special, specialReason, note)
    val hasChange = proposal.lines != latest.lines || proposal.pickup != latest.pickup ||
        proposal.specialPickup != latest.specialPickup || proposal.specialReason != latest.specialReason ||
        proposal.note.isNotBlank()
    val now = LocalDateTime.now(RIT_TIME_ZONE)
    val issues = if (pricesValid) validateBulkProposal(proposal, record.sellerId, record.appliedMinimum,
        catalog, policies.forSeller(record.sellerId), now).filterNot {
        // Backend owns hours/closure decisions; the local editor has no read-back endpoint.
        it in setOf(edu.msrit.canteen.customer.ProposalIssue.INVALID_PICKUP,
            edu.msrit.canteen.customer.ProposalIssue.SPECIAL_FLAG_REQUIRED,
            edu.msrit.canteen.customer.ProposalIssue.SPECIAL_REASON_REQUIRED)
    }.toSet() else setOf(edu.msrit.canteen.customer.ProposalIssue.INVALID_LINE)
    val window = pickupWindow(policies.forSeller(record.sellerId), pickup, now)
    val canSubmit = isSeller && hasChange && issues.isEmpty() && pickup.isAfter(now) &&
        (!special || specialReason.isNotBlank()) &&
        record.thread.stage in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER) &&
        latest.actor != if (isSeller) QuoteActor.SELLER else QuoteActor.CUSTOMER
    val preview = if (pricesValid && lines.isNotEmpty()) QuoteRevision(latest.version + 1,
        if (isSeller) QuoteActor.SELLER else QuoteActor.CUSTOMER, lines, pickup, special,
        proposal.totalPaise, note, now, specialReason) else null
    val diff = preview?.let { compareQuotes(latest, it) }

    if (isSeller && showingPreview && preview != null) {
        SellerCounterofferPreviewPresentation(
            record = record, proposal = preview, canRespond = true,
            onBack = { showingPreview = false },
            onContinueReview = { showingPreview = false },
            onReviewSpecialPickup = { showingPreview = false },
            proposalIsDraft = true,
        )
        return
    }

    Column(Modifier.fillMaxSize().background(colors.background).testTag("demo_quote_editor")) {
        CatalogHeader(if (isSeller) "Seller counteroffer" else "Customer revision", onBack)
        LazyColumn(Modifier.weight(1f).testTag("demo_quote_editor_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 25.dp)) {
            item {
                RitStatusIndicator("Revision ${latest.version + 1} · local backend demo")
                Spacer(Modifier.height(12.dp))
                BulkInfoCard("One seller: ${seller?.name ?: record.sellerId}",
                    "Only this seller's available items appear. Minimum snapshot: ${record.appliedMinimum} combined units.")
                Spacer(Modifier.height(14.dp))
                RitText("Items, quantities and unit prices", RitType.title)
                RitText("Server catalog prices are authoritative; this editor cannot set custom prices.", RitType.small, colors.muted)
                Spacer(Modifier.height(9.dp))
            }
            items(menu, key = { it.id }) { item: MenuItem ->
                Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).testTag("demo_quote_item_${item.id}")) {
                    RitText(item.name, RitType.label)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RitChip("−", false, { quantities.setBounded(item.id, (quantities[item.id] ?: 0) - 1) },
                            Modifier.testTag("demo_quote_minus_${item.id}"))
                        RitText("${quantities[item.id] ?: 0}", RitType.label,
                            modifier = Modifier.padding(top = 9.dp).testTag("demo_quote_qty_${item.id}"))
                        RitChip("+", false, { quantities.setBounded(item.id, (quantities[item.id] ?: 0) + 1) },
                            Modifier.testTag("demo_quote_plus_${item.id}"))
                    }
                    RitText("Current server price: ${item.price.display}", RitType.small, colors.muted)
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                RitText("Proposed pickup", RitType.title)
                RitText(displayPickup(pickup), RitType.body, colors.muted)
                Spacer(Modifier.height(8.dp))
                RitSecondaryButton("Edit date", {
                    val initial = pickup.toLocalDate()
                    DatePickerDialog(context, { _, year, month, day ->
                        pickup = LocalDateTime.of(LocalDate.of(year, month + 1, day), pickup.toLocalTime())
                    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                }, Modifier.fillMaxWidth().testTag("demo_quote_date"))
                Spacer(Modifier.height(8.dp))
                RitSecondaryButton("Edit time", {
                    val initial = pickup.toLocalTime()
                    TimePickerDialog(context, { _, hour, minute ->
                        pickup = LocalDateTime.of(pickup.toLocalDate(), LocalTime.of(hour, minute))
                    }, initial.hour, initial.minute, false).show()
                }, Modifier.fillMaxWidth().testTag("demo_quote_time"))
                Spacer(Modifier.height(9.dp))
                BulkInfoCard("Demo hours check", when (window) {
                    PickupWindow.REGULAR -> "Inside fictional regular hours; still not a reserved slot."
                    PickupWindow.SPECIAL_REQUIRED -> "Outside hours, holiday or closure: special flag and reason required."
                    PickupWindow.HOURS_UNVERIFIED -> "Hours unverified; cannot propose ordinary pickup."
                    PickupWindow.PAST -> "Choose a future pickup time."
                })
                Spacer(Modifier.height(9.dp))
                RitChip("Special pickup request", special, { special = !special },
                    Modifier.testTag("demo_quote_special_toggle"))
                if (special || window == PickupWindow.SPECIAL_REQUIRED) {
                    Spacer(Modifier.height(8.dp))
                    BulkTextBox("Reason for exceptional pickup", specialReason, { specialReason = it.take(500) },
                        "Explain the exception…", "demo_quote_special_reason")
                    RitText("Seller acceptance of special timing is a separate, revision-bound decision.",
                        RitType.small, colors.muted)
                }
                Spacer(Modifier.height(14.dp))
                BulkTextBox("Message about this revision", note, { note = it.take(500) },
                    "Explain changed terms…", "demo_quote_note")
                Spacer(Modifier.height(14.dp))
                RitText("Proposed total: ${displayPaise(proposal.totalPaise)}", RitType.title,
                    modifier = Modifier.testTag("demo_quote_total"))
                RitText("${lines.sumOf { it.quantity }} units · ${issues.size} validation issue(s)",
                    RitType.small, if (issues.isEmpty()) colors.muted else colors.accent)
                Spacer(Modifier.height(16.dp))
                RitText("Compared with revision ${latest.version}", RitType.title)
                Spacer(Modifier.height(7.dp))
                if (diff != null) {
                    diff.removed.forEach { RitText("Removed: ${it.title} × ${it.quantity}", RitType.body) }
                    diff.added.forEach { RitText("Added: ${it.title} × ${it.quantity}", RitType.body, colors.accent) }
                    diff.changed.forEach { (old, new) ->
                        RitText("${new.title}: ${old.quantity} → ${new.quantity}; ${displayPaise(old.unitPricePaise)} → ${displayPaise(new.unitPricePaise)} each", RitType.body)
                    }
                    if (diff.pickupChanged) RitText("Pickup time changed", RitType.body)
                    if (diff.specialChanged) RitText("Special pickup flag changed", RitType.body)
                    if (diff.amountChanged) RitText("Total ${displayPaise(latest.totalPaise)} → ${displayPaise(proposal.totalPaise)}", RitType.body)
                    if (!diff.pickupChanged && !diff.specialChanged && !diff.amountChanged &&
                        diff.added.isEmpty() && diff.removed.isEmpty() && diff.changed.isEmpty())
                        RitText("Change at least one term or add a message before sending a revision.", RitType.small, colors.muted)
                }
                Spacer(Modifier.height(10.dp))
                BulkInfoCard("Revision integrity", "Sending creates a new immutable demo version and invalidates approvals on older versions. It does not create a real seller/customer message.")
                if (feedback.isNotBlank()) RitText(feedback, RitType.small, colors.accent)
            }
        }
        if (isSeller && canSubmit) RitSecondaryButton("Preview changed terms", { showingPreview = true },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("demo_quote_preview"))
        RitPrimaryButton(if (isSeller) "Send seller counteroffer" else "Customer counteroffer unavailable", {
            onSubmit(proposal) { applied, message ->
                feedback = message
                if (applied) onApplied()
            }
        }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("demo_quote_submit"),
            enabled = canSubmit)
    }
}
