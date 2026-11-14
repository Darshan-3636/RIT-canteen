package edu.msrit.canteen.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import edu.msrit.canteen.customer.BulkRequestRecord
import edu.msrit.canteen.customer.IndividualOrderPreview
import edu.msrit.canteen.customer.SellerOrderTab
import edu.msrit.canteen.customer.SellerRequestFilter
import edu.msrit.canteen.customer.filterSellerRequests
import edu.msrit.canteen.customer.groupSellerOrders
import edu.msrit.canteen.customer.pickupPinFromScan
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockTime = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** Which page section is open: one of the individual-order tabs, or bulk requests. */
private enum class OrdersSection { NEW, READY, DONE, BULK }

/**
 * Seller "Orders" page. Paid orders flow New → Ready → (Confirm Pickup) → Completed, each card showing
 * exactly one next action. Bulk requests keep their own tab.
 *
 * [onMarkReady] and [onConfirmPickup] call back with an error message, or null on success.
 */
@Composable
fun SellerOrdersPage(
    orders: List<IndividualOrderPreview>,
    bulkRecords: List<BulkRequestRecord>,
    sellerId: String,
    canRespond: Boolean,
    onMarkReady: (orderId: String, onResult: (String?) -> Unit) -> Unit,
    onConfirmPickup: (orderId: String, pin: String, onResult: (String?) -> Unit) -> Unit,
    onOpenBulkRequest: (String) -> Unit,
    loadError: String? = null,
) {
    val colors = RitThemeTokens.colors
    val grouped = remember(orders) { groupSellerOrders(orders) }
    var section by rememberSaveable { mutableStateOf(OrdersSection.NEW) }
    var busyOrderId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pickupFor by remember { mutableStateOf<IndividualOrderPreview?>(null) }
    val newBulk = remember(bulkRecords, sellerId) {
        filterSellerRequests(bulkRecords, sellerId, SellerRequestFilter.NEEDS_ACTION).size
    }

    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_orders")) {
        CatalogHeader("Orders", null)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTab("New", grouped.getValue(SellerOrderTab.NEW).size, section == OrdersSection.NEW,
                Modifier.weight(1f).testTag("seller_orders_tab_new")) { section = OrdersSection.NEW }
            SectionTab("Ready", grouped.getValue(SellerOrderTab.READY).size, section == OrdersSection.READY,
                Modifier.weight(1f).testTag("seller_orders_tab_ready")) { section = OrdersSection.READY }
            SectionTab("Done", grouped.getValue(SellerOrderTab.DONE).size, section == OrdersSection.DONE,
                Modifier.weight(1f).testTag("seller_orders_tab_done")) { section = OrdersSection.DONE }
            SectionTab("Bulk", newBulk, section == OrdersSection.BULK,
                Modifier.weight(1f).testTag("seller_orders_tab_bulk")) { section = OrdersSection.BULK }
        }
        loadError?.let {
            RitText("Couldn't refresh orders: $it", RitType.small, colors.muted,
                Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp))
        }
        message?.let {
            RitText(it, RitType.label, colors.accent,
                Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).testTag("seller_orders_message"))
        }
        if (section == OrdersSection.BULK) {
            Box(Modifier.weight(1f)) { SellerBulkRequestsList(bulkRecords, sellerId, onOpenBulkRequest) }
        } else {
            val tab = when (section) {
                OrdersSection.READY -> SellerOrderTab.READY
                OrdersSection.DONE -> SellerOrderTab.DONE
                else -> SellerOrderTab.NEW
            }
            val visible = grouped.getValue(tab)
            LazyColumn(Modifier.weight(1f).testTag("seller_orders_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (visible.isEmpty()) item {
                    when (tab) {
                        SellerOrderTab.NEW -> RitEmptyState("No new orders", "Paid orders appear here as soon as customers place them.")
                        SellerOrderTab.READY -> RitEmptyState("Nothing waiting for pickup", "Orders you mark ready appear here.")
                        SellerOrderTab.DONE -> RitEmptyState("No completed orders yet", "Collected orders appear here.")
                    }
                }
                items(visible, key = { it.id }) { order ->
                    SellerOrderCard(order, tab, canRespond, busy = busyOrderId == order.id,
                        onMarkReady = {
                            busyOrderId = order.id; message = null
                            onMarkReady(order.id) { error ->
                                busyOrderId = null
                                message = error ?: "${orderLabel(order)} is ready for pickup."
                            }
                        },
                        onConfirmPickup = { message = null; pickupFor = order })
                }
            }
        }
    }

    pickupFor?.let { order ->
        PickupConfirmDialog(order,
            onDismiss = { pickupFor = null },
            onSubmit = { pin, onError ->
                onConfirmPickup(order.id, pin) { error ->
                    if (error == null) {
                        pickupFor = null
                        message = "${orderLabel(order)} collected. Hand over the food."
                    } else onError(error)
                }
            })
    }
}

private fun orderLabel(order: IndividualOrderPreview) =
    if (order.pickupReference.isNotBlank()) "Order ${order.pickupReference}" else "Order"

@Composable
private fun SectionTab(label: String, count: Int?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.small)
    Column(modifier.clip(shape)
        .background(if (selected) colors.accent else colors.card)
        .border(1.dp, if (selected) colors.accent else colors.outline, shape)
        .clickable(role = Role.Tab, onClick = onClick)
        .padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RitText(label, RitType.label, if (selected) colors.onAccent else colors.foreground)
        if (count != null) RitText(count.toString(), RitType.title, if (selected) colors.onAccent else colors.accent)
    }
}

@Composable
private fun SellerOrderCard(order: IndividualOrderPreview, tab: SellerOrderTab, canRespond: Boolean, busy: Boolean,
                            onMarkReady: () -> Unit, onConfirmPickup: () -> Unit) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.warmCard).border(1.dp, colors.outline, shape)
        .padding(16.dp).testTag("seller_order_${order.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RitText(order.customerName ?: "Customer", RitType.title, modifier = Modifier.weight(1f), maxLines = 1)
            RitText(displayPaise(order.totalPaise), RitType.title, colors.accent)
        }
        RitText(listOf(order.pickupReference.ifBlank { null }, placedLabel(order.placedAt)).filterNotNull()
            .joinToString(" · "), RitType.small, colors.muted)
        Spacer(Modifier.height(2.dp))
        order.lines.forEach { line ->
            RitText("${line.quantity} × ${line.title}", RitType.body)
        }
        Spacer(Modifier.height(4.dp))
        when (tab) {
            SellerOrderTab.NEW -> if (canRespond) RitPrimaryButton(if (busy) "Saving…" else "Mark Ready", onMarkReady,
                Modifier.fillMaxWidth().testTag("seller_mark_ready_${order.id}"), enabled = !busy)
            SellerOrderTab.READY -> {
                RitText("Waiting for the customer to collect", RitType.small, colors.muted)
                if (canRespond) RitPrimaryButton("Confirm Pickup", onConfirmPickup,
                    Modifier.fillMaxWidth().testTag("seller_confirm_pickup_${order.id}"))
            }
            SellerOrderTab.DONE -> RitText(order.handedOverAt?.let { "Collected at ${instantClock(it)}" } ?: "Collected",
                RitType.label, colors.muted)
        }
    }
}

private fun placedLabel(placedAt: LocalDateTime): String {
    val minutes = Duration.between(placedAt, LocalDateTime.now()).toMinutes()
    val ago = when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> null
    }
    return "Placed ${clockTime.format(placedAt)}" + (ago?.let { " · $it" } ?: "")
}

private fun instantClock(iso: String): String = runCatching {
    clockTime.format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault(iso)

@Composable
private fun PickupConfirmDialog(order: IndividualOrderPreview, onDismiss: () -> Unit,
                                onSubmit: (pin: String, onError: (String) -> Unit) -> Unit) {
    val colors = RitThemeTokens.colors
    var enteringPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun submit(value: String) {
        busy = true; error = null
        onSubmit(value) { busy = false; error = it }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents == null) return@rememberLauncherForActivityResult
        val scanned = pickupPinFromScan(result.contents)
        if (scanned == null) error = "That QR code is not an RIT Canteen pickup code." else submit(scanned)
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        val shape = RoundedCornerShape(RitRadii.card)
        Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).padding(20.dp)
            .testTag("seller_pickup_dialog"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RitText("Confirm pickup", RitType.heading)
            RitText("${order.customerName ?: "Customer"} · ${orderLabel(order)}", RitType.body, colors.muted)
            if (!enteringPin) {
                RitText("Scan the customer's QR code or ask for their 4-digit PIN.", RitType.body)
                RitPrimaryButton("Scan QR code", {
                    scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Scan the customer's pickup QR code").setBeepEnabled(false)
                        .setOrientationLocked(false))
                }, Modifier.fillMaxWidth().testTag("seller_pickup_scan"), enabled = !busy)
                RitSecondaryButton("Enter 4-digit PIN", { enteringPin = true; error = null },
                    Modifier.fillMaxWidth().testTag("seller_pickup_enter_pin"), enabled = !busy)
            } else {
                RitTextField(pin, { value -> pin = value.filter(Char::isDigit).take(4); error = null },
                    "Pickup PIN", "4 digits", Modifier.fillMaxWidth(), keyboardType = KeyboardType.NumberPassword,
                    inputTag = "seller_pickup_pin")
                RitPrimaryButton(if (busy) "Checking…" else "Confirm pickup", { submit(pin) },
                    Modifier.fillMaxWidth().testTag("seller_pickup_submit"), enabled = pin.length == 4 && !busy)
                RitSecondaryButton("Scan QR code instead", { enteringPin = false; pin = ""; error = null },
                    Modifier.fillMaxWidth(), enabled = !busy)
            }
            error?.let { RitText(it, RitType.label, colors.accent, Modifier.testTag("seller_pickup_error")) }
            RitSecondaryButton("Cancel", onDismiss, Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
}
