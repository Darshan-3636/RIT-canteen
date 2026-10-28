package edu.msrit.canteen.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.BulkDraft
import edu.msrit.canteen.customer.BulkDraftIssue
import edu.msrit.canteen.customer.BulkDraftSession
import edu.msrit.canteen.customer.BulkPolicySource
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.ItemEligibility
import edu.msrit.canteen.customer.MenuItem
import edu.msrit.canteen.customer.PickupWindow
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.Seller
import edu.msrit.canteen.customer.checkBulkDraft
import edu.msrit.canteen.customer.itemEligibility
import edu.msrit.canteen.customer.pickupWindow
import edu.msrit.canteen.customer.visibleItems
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.CatalogSellerCard
import edu.msrit.canteen.ui.components.FoodPhotoContent
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
import java.time.format.DateTimeFormatter
import java.util.Locale

private val pickupDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val pickupTimeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun displayPickup(value: LocalDateTime): String =
    "${value.format(pickupDateFormat)} · ${value.format(pickupTimeFormat)} IST"

fun displayPaise(paise: Long): String = "₹${paise / 100}${if (paise % 100L == 0L) "" else ".%02d".format(paise % 100L)}"

@Composable
internal fun BulkInfoCard(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Column(modifier.fillMaxWidth().background(colors.warmCard, RoundedCornerShape(RitRadii.card)).padding(16.dp)) {
        RitText(title, RitType.label)
        Spacer(Modifier.height(4.dp))
        RitText(body, RitType.body, colors.muted)
    }
}

@Composable
internal fun BulkTextBox(label: String, value: String, onChange: (String) -> Unit, hint: String, tag: String) {
    val colors = RitThemeTokens.colors
    RitText(label, RitType.label)
    Spacer(Modifier.height(7.dp))
    BasicTextField(value, onChange, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
        .border(1.dp, colors.outline, RoundedCornerShape(16.dp)).padding(14.dp).testTag(tag),
        textStyle = RitType.body.copy(color = colors.foreground), cursorBrush = SolidColor(colors.accent),
        decorationBox = { inner ->
            if (value.isEmpty()) RitText(hint, RitType.body, colors.muted)
            inner()
        })
}

@Composable
fun BulkSellerPickerScreen(
    session: BulkDraftSession,
    policies: BulkPolicySource,
    onBack: () -> Unit,
    onSelected: () -> Unit,
    catalog: CustomerCatalogSource = PreviewCatalog,
) {
    val colors = RitThemeTokens.colors
    var blockedSeller by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("bulk_seller_picker")) {
        CatalogHeader("Choose a seller", onBack)
        LazyColumn(Modifier.testTag("bulk_seller_list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                RitStatusIndicator("Development catalog · no request will be sent")
                Spacer(Modifier.height(12.dp))
                BulkInfoCard("One seller per request", "Prepared and packaged items can be combined only when they belong to the same seller. Each seller has its own total-unit minimum.")
                Spacer(Modifier.height(18.dp))
            }
            items(catalog.sellers.filter { it.capabilities.bulk }, key = { it.id }) { seller ->
                Column(Modifier.padding(bottom = 14.dp).testTag("bulk_choose_${seller.id}")) {
                    CatalogSellerCard(seller, "Minimum ${policies.forSeller(seller.id)?.minimum ?: 8} combined units · demo policy",
                        onClick = {
                            if (session.selectSeller(seller.id)) onSelected() else blockedSeller = seller.id
                        })
                }
            }
        }
    }
    blockedSeller?.let { target -> RitConfirmDialog("Switch seller?",
        "Your bulk cart contains items from another seller. Clear only the bulk cart to start with this seller?",
        onDismiss = { blockedSeller = null }, onConfirm = {
            session.clear(); session.selectSeller(target); blockedSeller = null; onSelected()
        }) }
}

@Composable
private fun BulkQuantityControl(item: MenuItem, quantity: Int, onQuantity: (Int) -> Unit) {
    val colors = RitThemeTokens.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RitText("−", RitType.title, colors.accent, Modifier.heightIn(min = 48.dp).width(48.dp)
            .clickable(enabled = quantity > 0, role = Role.Button) { onQuantity(quantity - 1) }
            .semantics { contentDescription = "Remove one ${item.name}" }.testTag("bulk_minus_${item.id}")
            .padding(horizontal = 18.dp, vertical = 10.dp))
        RitText("$quantity", RitType.label, modifier = Modifier.testTag("bulk_quantity_${item.id}"))
        RitText("+", RitType.title, colors.accent, Modifier.heightIn(min = 48.dp).width(48.dp)
            .clickable(enabled = quantity < 999, role = Role.Button) { onQuantity(quantity + 1) }
            .semantics { contentDescription = "Add one ${item.name}" }.testTag("bulk_plus_${item.id}")
            .padding(horizontal = 17.dp, vertical = 10.dp))
    }
}

@Composable
fun BulkCartScreen(
    session: BulkDraftSession,
    policies: BulkPolicySource,
    onBack: () -> Unit,
    onChooseSeller: () -> Unit,
    onPickup: () -> Unit,
    onViewCart: (() -> Unit)? = null,
    catalog: CustomerCatalogSource = PreviewCatalog,
) {
    val colors = RitThemeTokens.colors
    val draft = session.draft
    val seller = catalog.sellers.find { it.id == draft.sellerId }
    if (seller == null) {
        Column(Modifier.fillMaxSize().background(colors.background).testTag("bulk_cart_empty")) {
            CatalogHeader("Add bulk items", onBack)
            RitEmptyState("Choose one seller", "A bulk draft contains products from exactly one seller.")
            RitPrimaryButton("Choose a seller", onChooseSeller,
                Modifier.fillMaxWidth().padding(20.dp).testTag("bulk_cart_choose_seller"))
        }
        return
    }
    val now = LocalDateTime.now(RIT_TIME_ZONE)
    val check = checkBulkDraft(draft, catalog, policies, now)
    val menu = catalog.visibleItems(seller.id)
    Column(Modifier.fillMaxSize().background(colors.background).testTag("bulk_cart")) {
        CatalogHeader("Add bulk items", onBack)
        LazyColumn(Modifier.weight(1f).testTag("bulk_cart_list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                RitStatusIndicator("Local draft only · not sent to ${seller.name}")
                Spacer(Modifier.height(10.dp))
                BulkInfoCard(seller.name, "One seller per request · ${check.minimum} combined units minimum. Seller hours and prices shown here are demonstration data.")
                Spacer(Modifier.height(10.dp))
                RitSecondaryButton("Choose another seller", onChooseSeller, Modifier.fillMaxWidth().testTag("bulk_switch_seller"))
                Spacer(Modifier.height(16.dp))
                RitText("Items from this seller", RitType.title)
                RitText("Prepared and packaged products both count toward this seller's minimum.", RitType.small, colors.muted)
                Spacer(Modifier.height(8.dp))
            }
            items(menu, key = { it.id }) { item ->
                val eligibility = itemEligibility(seller, item)
                val quantity = draft.lines.find { it.itemId == item.id }?.quantity ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)
                    .border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
                    .background(colors.card, RoundedCornerShape(RitRadii.card)).padding(12.dp)
                    .testTag("bulk_cart_item_${item.id}"), verticalAlignment = Alignment.CenterVertically) {
                    FoodPhotoContent(item.photo, "${item.name} illustrative image", Modifier.width(72.dp).height(72.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        RitText(item.name, RitType.label, maxLines = 1)
                        RitText("${item.price.display} each · ${if (item.kind == edu.msrit.canteen.customer.FoodKind.PACKAGED) "Packaged" else "Prepared"}", RitType.small, colors.muted)
                        if (eligibility == ItemEligibility.UNAVAILABLE) RitText("Unavailable for new requests", RitType.small, colors.accent)
                        else BulkQuantityControl(item, quantity) { session.setQuantity(item, it) }
                    }
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                BulkInfoCard("${check.totalUnits} of ${check.minimum} units", if (check.totalUnits < check.minimum)
                    "Add ${check.minimum - check.totalUnits} more units from ${seller.name}. Quantities combine across this seller's items."
                    else "Minimum met. This is still only a local request draft.", Modifier.testTag("bulk_minimum_status"))
                Spacer(Modifier.height(14.dp))
                RitText("Provisional catalog estimate: ${displayPaise(check.provisionalPaise)}", RitType.title,
                    modifier = Modifier.testTag("bulk_estimate"))
                RitText("The seller may change items, quantities, time and price in a later quote. No payment is due now.", RitType.small, colors.muted)
            }
        }
        RitPrimaryButton("Continue to pickup details", onPickup,
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("bulk_continue_pickup"),
            enabled = check.totalUnits >= check.minimum && check.issues.none { it in setOf(BulkDraftIssue.NO_ITEMS,
                BulkDraftIssue.INVALID_QUANTITY, BulkDraftIssue.CROSS_SELLER_ITEM, BulkDraftIssue.ITEM_UNAVAILABLE, BulkDraftIssue.SELLER_NOT_BULK) })
        if (onViewCart != null) RitSecondaryButton("View Bulk Cart", onViewCart,
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).testTag("bulk_view_cart"))
    }
}

@Composable
fun BulkPickupScreen(
    session: BulkDraftSession,
    policies: BulkPolicySource,
    onBack: () -> Unit,
    onReview: () -> Unit,
    catalog: CustomerCatalogSource = PreviewCatalog,
    now: LocalDateTime = LocalDateTime.now(RIT_TIME_ZONE),
    connectedMode: Boolean = false,
) {
    val colors = RitThemeTokens.colors
    val context = LocalContext.current
    val draft = session.draft
    val policy = draft.sellerId?.let(policies::forSeller)
    val seller = catalog.sellers.find { it.id == draft.sellerId }
    val selected = draft.pickup
    val window = selected?.let { pickupWindow(policy, it, now) }
    val check = checkBulkDraft(draft, catalog, policies, now)
    Column(Modifier.fillMaxSize().background(colors.background).testTag("bulk_pickup")) {
        CatalogHeader("Schedule pickup", onBack)
        LazyColumn(Modifier.weight(1f).testTag("bulk_pickup_list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                RitStatusIndicator("Proposed pickup · Asia/Kolkata (IST)")
                Spacer(Modifier.height(12.dp))
                BulkInfoCard(seller?.name ?: "Seller", if (connectedMode)
                    if (policy == null) "Seller hours could not be loaded. This is only a proposed time; the server will validate it on submission."
                    else "Server policy loaded: minimum ${policy.minimum} units. Pickup remains a request until the seller confirms."
                    else "Demo weekly hours: Mon–Fri 9:00–17:00, Sat 10:00–14:00, Sun closed. Demo holiday and temporary closure exceptions apply. These are not verified live hours.")
                Spacer(Modifier.height(18.dp))
                RitText("Preferred date", RitType.title)
                Spacer(Modifier.height(8.dp))
            }
            item {
                LazyRow(modifier = Modifier.testTag("bulk_date_choices"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val dates = listOf(now.toLocalDate().plusDays(1), now.toLocalDate().plusDays(2), now.toLocalDate().plusDays(4))
                    items(dates) { date ->
                        RitChip(date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
                            selected?.toLocalDate() == date, {
                                session.setPickup(LocalDateTime.of(date, selected?.toLocalTime() ?: LocalTime.of(11, 30)))
                            }, Modifier.testTag("bulk_date_${date}"))
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                RitSecondaryButton("Choose another date", {
                    val initial = selected?.toLocalDate() ?: now.toLocalDate().plusDays(1)
                    DatePickerDialog(context, { _, year, month, day ->
                        session.setPickup(LocalDateTime.of(LocalDate.of(year, month + 1, day),
                            selected?.toLocalTime() ?: LocalTime.of(11, 30)))
                    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
                }, Modifier.fillMaxWidth().testTag("bulk_pick_date"))
                Spacer(Modifier.height(16.dp))
                RitText("Preferred time", RitType.title)
                RitText("A proposed time is not a reserved slot.", RitType.small, colors.muted)
                Spacer(Modifier.height(8.dp))
            }
            item {
                LazyRow(modifier = Modifier.testTag("bulk_time_choices"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(LocalTime.of(11, 30), LocalTime.of(15, 0), LocalTime.of(19, 0))) { time ->
                        RitChip(time.format(pickupTimeFormat), selected?.toLocalTime() == time, {
                            session.setPickup(LocalDateTime.of(selected?.toLocalDate() ?: now.toLocalDate().plusDays(1), time))
                        }, Modifier.testTag("bulk_time_${time.hour}"))
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                RitSecondaryButton("Choose another time", {
                    val initial = selected?.toLocalTime() ?: LocalTime.of(11, 30)
                    TimePickerDialog(context, { _, hour, minute ->
                        session.setPickup(LocalDateTime.of(selected?.toLocalDate() ?: now.toLocalDate().plusDays(1),
                            LocalTime.of(hour, minute)))
                    }, initial.hour, initial.minute, false).show()
                }, Modifier.fillMaxWidth().testTag("bulk_pick_time"))
                Spacer(Modifier.height(16.dp))
                RitText(selected?.let(::displayPickup) ?: "No pickup chosen", RitType.title, modifier = Modifier.testTag("bulk_pickup_selected"))
                val status = if (connectedMode) {
                    if (selected?.isAfter(now) == true) when (window) {
                        PickupWindow.REGULAR -> "Within the seller's published hours. Seller confirmation is still required."
                        PickupWindow.SPECIAL_REQUIRED -> if (policy?.specialPickupEnabled == false)
                            "Seller does not accept special pickup requests. Choose a time within published hours."
                            else "Outside published hours or on a closure. Request a special pickup; the seller may decline."
                        else -> "Seller hours are unverified. The server will validate this proposed time on submission."
                    }
                    else "Choose a future pickup time."
                } else when (window) {
                    null -> "Choose a future date and time."
                    PickupWindow.REGULAR -> "Within the demo operating window. Seller confirmation is still required."
                    PickupWindow.SPECIAL_REQUIRED -> "Closed or outside demo hours. An explicit special pickup request and reason are required; the seller may decline."
                    PickupWindow.HOURS_UNVERIFIED -> "Seller hours are unverified. Ordinary pickup cannot be assumed."
                    PickupWindow.PAST -> "Choose a future pickup time."
                }
                BulkInfoCard("Pickup check", status, Modifier.testTag("bulk_pickup_status"))
                if ((connectedMode || window == PickupWindow.SPECIAL_REQUIRED) && policy?.specialPickupEnabled != false) {
                    Spacer(Modifier.height(12.dp))
                    RitChip("Special pickup request", draft.specialPickup,
                        { session.setSpecial(!draft.specialPickup) }, Modifier.testTag("bulk_special_toggle"))
                    Spacer(Modifier.height(10.dp))
                    BulkTextBox("Reason for special request", draft.specialReason, session::setSpecialReason,
                        "Explain why this exception is requested…", "bulk_special_reason")
                    RitText("Special pickup is never guaranteed without explicit seller approval.", RitType.small, colors.muted)
                }
                Spacer(Modifier.height(16.dp))
                BulkTextBox("Instructions (optional)", draft.instructions, session::setInstructions,
                    "Helpful pickup details for the seller…", "bulk_instructions")
            }
        }
        RitPrimaryButton("Review local request draft", onReview,
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("bulk_continue_review"),
            enabled = if (connectedMode) selected?.isAfter(now) == true &&
                (window != PickupWindow.SPECIAL_REQUIRED || policy?.specialPickupEnabled != false &&
                    draft.specialPickup && draft.specialReason.isNotBlank()) &&
                draft.lines.isNotEmpty() && draft.lines.sumOf { it.quantity } >= (policy?.minimum ?: seller?.bulkMinimumUnits ?: check.minimum)
                else check.readyForLocalReview)
    }
}

@Composable
fun BulkReviewScreen(
    session: BulkDraftSession,
    policies: BulkPolicySource,
    onBack: () -> Unit,
    onEditItems: () -> Unit,
    onEditPickup: () -> Unit,
    onDemoNegotiation: () -> Unit,
    onSubmitLocalDemo: (() -> Unit)? = null,
    catalog: CustomerCatalogSource = PreviewCatalog,
    errorMessage: String? = null,
) {
    val colors = RitThemeTokens.colors
    val draft = session.draft
    val check = checkBulkDraft(draft, catalog, policies, LocalDateTime.now(RIT_TIME_ZONE))
    val seller = catalog.sellers.find { it.id == draft.sellerId }
    val policy = draft.sellerId?.let(policies::forSeller)
    val window = draft.pickup?.let { pickupWindow(policy, it, LocalDateTime.now(RIT_TIME_ZONE)) }
    val canTryServer = seller != null && draft.pickup?.isAfter(LocalDateTime.now(RIT_TIME_ZONE)) == true &&
        draft.lines.isNotEmpty() && draft.lines.sumOf { it.quantity } >= (policy?.minimum ?: seller.bulkMinimumUnits) &&
        draft.lines.all { line -> line.quantity in 1..999 &&
            catalog.items.any { it.id == line.itemId && it.sellerId == seller.id } } &&
        (!draft.specialPickup || draft.specialReason.isNotBlank()) &&
        (window != PickupWindow.SPECIAL_REQUIRED || policy?.specialPickupEnabled != false && draft.specialPickup && draft.specialReason.isNotBlank())
    Column(Modifier.fillMaxSize().background(colors.background).testTag("bulk_review")) {
        CatalogHeader("Review bulk request", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                RitStatusIndicator("Unsubmitted local draft · no seller has seen this")
                Spacer(Modifier.height(14.dp))
                RitText(seller?.name ?: "Choose a seller", RitType.heading)
                RitText("One seller · minimum ${policy?.minimum ?: seller?.bulkMinimumUnits ?: check.minimum} combined units", RitType.small, colors.muted)
                Spacer(Modifier.height(14.dp))
                BulkInfoCard("${check.totalUnits} units · ${displayPaise(check.provisionalPaise)} provisional", "Current demo catalog prices only. This is not a final quote or payable amount.")
                Spacer(Modifier.height(12.dp))
            }
            items(draft.lines, key = { it.itemId }) { line ->
                val food = catalog.items.find { it.id == line.itemId }
                RitText("${food?.name ?: "Unavailable item"} × ${line.quantity} · ${food?.let { displayPaise(it.price.rupees.toLong() * line.quantity * 100) } ?: "Price unavailable"}",
                    RitType.body, modifier = Modifier.padding(vertical = 5.dp))
            }
            item {
                Spacer(Modifier.height(10.dp))
                RitSecondaryButton("Edit items", onEditItems, Modifier.fillMaxWidth().testTag("bulk_review_edit_items"))
                Spacer(Modifier.height(16.dp))
                BulkInfoCard("Requested pickup", draft.pickup?.let(::displayPickup) ?: "Not selected")
                if (draft.specialPickup) {
                    Spacer(Modifier.height(8.dp))
                    BulkInfoCard("Special pickup request · seller approval required", draft.specialReason)
                }
                if (draft.instructions.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    BulkInfoCard("Instructions", draft.instructions)
                }
                Spacer(Modifier.height(8.dp))
                RitSecondaryButton("Edit pickup", onEditPickup, Modifier.fillMaxWidth().testTag("bulk_review_edit_pickup"))
                Spacer(Modifier.height(16.dp))
                BulkInfoCard("Server validation", "The local backend checks seller ownership, item availability, minimum units, configured hours and closures. A draft is not a confirmed order.")
                Spacer(Modifier.height(12.dp))
                RitSecondaryButton("View separate negotiation demo", onDemoNegotiation,
                    Modifier.fillMaxWidth().testTag("bulk_review_demo_negotiation"))
                RitText("The demonstration is not a response to this draft.", RitType.small, colors.muted)
                if (errorMessage != null) RitText(errorMessage, RitType.body, colors.accent)
            }
        }
        RitPrimaryButton("Submit bulk request", { onSubmitLocalDemo?.invoke() },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("bulk_review_submit_demo"), enabled = onSubmitLocalDemo != null && canTryServer)
    }
}
