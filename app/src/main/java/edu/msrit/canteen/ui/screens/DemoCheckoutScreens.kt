package edu.msrit.canteen.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.DemoActionResult
import edu.msrit.canteen.customer.DemoOrder
import edu.msrit.canteen.customer.DemoOrderKind
import edu.msrit.canteen.customer.DemoOrderState
import edu.msrit.canteen.customer.DemoPaymentOutcome
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitDemoQr
import edu.msrit.canteen.ui.components.RitIcon
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitMessageCard
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

private fun DemoOrder.sellerName(): String = PreviewCatalog.sellers.find { it.id == sellerId }?.name ?: "Seller"

@Composable
fun DemoCheckoutScreen(order: DemoOrder, onBack: () -> Unit,
                       onPay: (DemoPaymentOutcome) -> DemoActionResult, onOutcome: () -> Unit) {
    val colors = RitThemeTokens.colors
    var outcome by remember { mutableStateOf(DemoPaymentOutcome.SUCCESS) }
    var feedback by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("demo_checkout")) {
        CatalogHeader("Simulated Checkout", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            RitStatusIndicator("DEMO PAYMENT — no money is charged")
            Spacer(Modifier.height(18.dp))
            RitText(order.sellerName(), RitType.heading)
            RitText(order.reference, RitType.body, colors.muted)
            order.quoteVersion?.let { RitText("Agreed quote revision $it", RitType.small, colors.muted) }
            order.pickup?.let { RitText("Requested pickup: ${displayPickup(it)}", RitType.body) }
            Spacer(Modifier.height(18.dp))
            order.lines.forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    RitText("${line.quantity} × ${line.title}", RitType.body, modifier = Modifier.weight(1f))
                    RitText(displayPaise(line.unitPricePaise * line.quantity), RitType.body)
                }
            }
            Spacer(Modifier.height(12.dp))
            RitText("Demo total ${displayPaise(order.totalPaise)}", RitType.title)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RitIcon(RitIconKind.Cart, colors.accent)
                RitText("Local demo method · no payment provider", RitType.body, colors.muted)
            }
            Spacer(Modifier.height(12.dp))
            RitMessageCard("Local simulation", "Choose an outcome to test the UI. No payment provider, seller server, or bank is contacted.")
            Spacer(Modifier.height(16.dp))
            RitText("Simulated outcome", RitType.label)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DemoPaymentOutcome.entries.forEach { choice ->
                    RitChip(choice.name.lowercase().replaceFirstChar { it.uppercase() }, outcome == choice,
                        { outcome = choice }, Modifier.testTag("demo_outcome_${choice.name.lowercase()}"))
                }
            }
            if (feedback.isNotBlank()) RitText(feedback, RitType.body, colors.accent,
                Modifier.padding(top = 12.dp).testTag("demo_checkout_feedback"))
        }
        RitPrimaryButton("Simulate payment", {
            val result = onPay(outcome)
            if (result == DemoActionResult.APPLIED) onOutcome()
            else feedback = "Cannot simulate payment: ${result.name.lowercase().replace('_', ' ')}"
        }, Modifier.fillMaxWidth().padding(20.dp).testTag("demo_simulate_payment"))
    }
}

@Composable
fun DemoOrderDetailScreen(order: DemoOrder, onBack: () -> Unit, onCheckout: () -> Unit,
                          onOrders: () -> Unit, onHome: () -> Unit) {
    val colors = RitThemeTokens.colors
    val context = LocalContext.current
    val state = when (order.state) {
        DemoOrderState.SUBMITTED -> "Request submitted · waiting for seller"
        DemoOrderState.SELLER_ACCEPTED -> "Seller accepted · awaiting demo payment"
        DemoOrderState.SELLER_REJECTED -> "Seller declined this demo request"
        DemoOrderState.PAYMENT_FAILED -> "Demo payment failed · not confirmed"
        DemoOrderState.PAYMENT_PENDING -> "Demo payment pending · not confirmed"
        DemoOrderState.DEMO_CONFIRMED -> "Demo confirmed · pickup code available"
        DemoOrderState.DEMO_COLLECTED -> "Collected (demo)"
    }
    val icon = when (order.state) {
        DemoOrderState.PAYMENT_FAILED, DemoOrderState.SELLER_REJECTED -> RitIconKind.Close
        DemoOrderState.DEMO_CONFIRMED, DemoOrderState.DEMO_COLLECTED -> RitIconKind.Orders
        else -> RitIconKind.Bulk
    }
    val explanation = when (order.state) {
        DemoOrderState.PAYMENT_FAILED -> "The local payment simulation failed. No pickup code was issued; you can retry."
        DemoOrderState.PAYMENT_PENDING -> "The outcome is unresolved. This is not paid or confirmed; no pickup code exists."
        DemoOrderState.DEMO_CONFIRMED -> "Your local demo outcome succeeded. No real order was placed or money charged."
        DemoOrderState.DEMO_COLLECTED -> "The single-use local code was redeemed by this seller."
        DemoOrderState.SELLER_REJECTED -> "The seller declined this local demonstration request."
        DemoOrderState.SELLER_ACCEPTED -> "The seller accepted this demo request. Checkout remains a simulation."
        DemoOrderState.SUBMITTED -> "The local request is waiting for a seller response on this device."
    }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("demo_order_detail")) {
        CatalogHeader("Order tracking", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            RitStatusIndicator("Demo simulation — no actual payment or order")
            Spacer(Modifier.height(18.dp))
            Box(Modifier.size(72.dp).background(colors.accentSoft, CircleShape),
                contentAlignment = Alignment.Center) {
                RitIcon(icon, colors.accent, size = 36.dp)
            }
            Spacer(Modifier.height(14.dp))
            RitText(state, RitType.heading, modifier = Modifier.testTag("demo_order_state"))
            RitText(explanation, RitType.body, colors.muted)
            RitText("${order.reference} · ${order.sellerName()}", RitType.body, colors.muted)
            order.quoteVersion?.let { RitText("Agreed revision $it", RitType.small, colors.muted) }
            order.pickup?.let { RitText("Requested pickup: ${displayPickup(it)}", RitType.body) }
            Spacer(Modifier.height(18.dp))
            order.lines.forEach { RitText("${it.quantity} × ${it.title}", RitType.body) }
            Spacer(Modifier.height(10.dp))
            RitText("Demo total ${displayPaise(order.totalPaise)}", RitType.title)
            Spacer(Modifier.height(18.dp))
            val timeline = listOf("Request submitted", "Seller response", "Quote agreed / seller accepted",
                "Demo payment success", "Pickup code available", "Demo collected")
            RitText("Tracking", RitType.title)
            timeline.forEachIndexed { index, label ->
                val reached = when (order.state) {
                    DemoOrderState.SUBMITTED -> index == 0
                    DemoOrderState.SELLER_ACCEPTED, DemoOrderState.PAYMENT_FAILED,
                    DemoOrderState.PAYMENT_PENDING -> index <= 2
                    DemoOrderState.DEMO_CONFIRMED -> index <= 4
                    DemoOrderState.DEMO_COLLECTED -> true
                    DemoOrderState.SELLER_REJECTED -> index <= 1
                }
                RitText("${if (reached) "●" else "○"} $label", RitType.body,
                    if (reached) colors.foreground else colors.muted, Modifier.padding(vertical = 3.dp))
            }
            if (order.pickupCode != null && order.state == DemoOrderState.DEMO_CONFIRMED) {
                Spacer(Modifier.height(18.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    RitDemoQr(order.pickupCode, Modifier.testTag("demo_pickup_qr"))
                }
                Spacer(Modifier.height(10.dp))
                RitMessageCard("Demo pickup code", "${order.pickupCode}\nShow this at ${order.sellerName()}. Manual entry is supported. One use only.",
                    Modifier.testTag("demo_pickup_code"))
                Spacer(Modifier.height(10.dp))
                RitSecondaryButton("Copy demo code", {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Demo pickup code", order.pickupCode))
                }, Modifier.fillMaxWidth().testTag("demo_copy_code"))
            }
            if (order.state == DemoOrderState.DEMO_COLLECTED)
                RitMessageCard("Collected (demo)", "The local one-use code was redeemed. This is not a real collection record.")
        }
        if (order.state in setOf(DemoOrderState.SELLER_ACCEPTED, DemoOrderState.PAYMENT_FAILED,
                DemoOrderState.PAYMENT_PENDING)) {
            RitPrimaryButton(if (order.state == DemoOrderState.SELLER_ACCEPTED) "Open demo checkout" else "Retry demo checkout",
                onCheckout, Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("demo_open_checkout"))
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RitSecondaryButton("Orders", onOrders, Modifier.weight(1f).testTag("demo_detail_orders"))
            RitSecondaryButton("Home", onHome, Modifier.weight(1f).testTag("demo_detail_home"))
        }
    }
}

@Composable
fun SellerDemoIndividualQueue(orders: List<DemoOrder>, onBack: () -> Unit,
                              onRespond: (String, Boolean) -> DemoActionResult,
                              onVerify: () -> Unit, canRespond: Boolean = true,
                              onOrder: ((String) -> Unit)? = null) {
    val colors = RitThemeTokens.colors
    var feedback by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_individual_queue")) {
        CatalogHeader("Individual orders", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            RitStatusIndicator("Demo requests · no kitchen submission")
            Spacer(Modifier.height(14.dp))
            RitText("New individual orders", RitType.heading)
            val visible = orders.filter { it.kind == DemoOrderKind.INDIVIDUAL }
            if (visible.isEmpty()) RitEmptyState("No demo requests", "Customer submissions for this seller will appear here.")
            visible.sortedBy { it.reference }.reversed().forEach { order ->
                Spacer(Modifier.height(12.dp))
                RitMessageCard(order.reference, "${order.lines.joinToString { "${it.quantity}× ${it.title}" }}\n${order.state.name.lowercase().replace('_', ' ')}")
                if (onOrder != null) RitSecondaryButton("View details", { onOrder(order.reference) },
                    Modifier.fillMaxWidth().testTag("seller_individual_detail_${order.reference}"))
                if (order.state == DemoOrderState.SUBMITTED && canRespond) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RitSecondaryButton("Reject", { feedback = onRespond(order.reference, false).name },
                            Modifier.weight(1f).testTag("seller_reject_${order.reference}"))
                        RitPrimaryButton("Accept", { feedback = onRespond(order.reference, true).name },
                            Modifier.weight(1f).testTag("seller_accept_${order.reference}"))
                    }
                }
            }
            if (feedback.isNotBlank()) RitText("Demo result: $feedback", RitType.small, colors.accent)
        }
        if (canRespond) RitSecondaryButton("Verify demo pickup code", onVerify,
            Modifier.fillMaxWidth().padding(20.dp).testTag("seller_open_verify"))
    }
}

@Composable
fun SellerDemoPickupScreen(onBack: () -> Unit,
                           onVerify: (String) -> Pair<DemoActionResult, DemoOrder?>,
                           onCollect: (String) -> DemoActionResult) {
    val colors = RitThemeTokens.colors
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var match by remember { mutableStateOf<DemoOrder?>(null) }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_demo_pickup")) {
        CatalogHeader("Verify demo pickup", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            RitStatusIndicator("Local demo only · not a real fulfillment check")
            Spacer(Modifier.height(16.dp))
            RitText("Enter pickup code", RitType.heading)
            RitTextField(code, { code = it.uppercase().take(19); match = null; result = "" },
                "Demo code", "XXXX-XXXX-XXXX-XXXX", Modifier.fillMaxWidth(),
                KeyboardType.Ascii, "seller_demo_code")
            Spacer(Modifier.height(12.dp))
            RitPrimaryButton("Verify code", {
                val checked = onVerify(code)
                match = checked.second
                result = when (checked.first) {
                    DemoActionResult.APPLIED -> "Code matches this seller and a confirmed demo order."
                    DemoActionResult.ALREADY_USED -> "This demo code has already been redeemed."
                    else -> "Code unavailable: ${checked.first.name.lowercase().replace('_', ' ')}"
                }
            }, Modifier.fillMaxWidth().testTag("seller_verify_code"))
            if (result.isNotBlank()) RitMessageCard("Verification", result,
                Modifier.padding(top = 14.dp).testTag("seller_verify_result"))
            match?.let { order ->
                Spacer(Modifier.height(14.dp))
                RitMessageCard("${order.reference} · ${order.sellerName()}",
                    "${order.lines.joinToString { "${it.quantity}× ${it.title}" }}\nDemo confirmed · ${displayPaise(order.totalPaise)}")
                Spacer(Modifier.height(10.dp))
                RitPrimaryButton("Confirm demo collection", {
                    val applied = onCollect(code)
                    match = null
                    result = if (applied == DemoActionResult.APPLIED) "Collected (demo). This code can no longer be used."
                        else "Collection denied: ${applied.name.lowercase().replace('_', ' ')}"
                }, Modifier.fillMaxWidth().testTag("seller_confirm_collection"))
            }
        }
    }
}

@Composable
fun SellerDemoOrderDetailScreen(order: DemoOrder, onBack: () -> Unit, onVerify: () -> Unit,
                                canVerify: Boolean = true) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_demo_order_detail")) {
        CatalogHeader("Demo order", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            RitStatusIndicator("Local simulation · no verified payment or fulfillment")
            Spacer(Modifier.height(16.dp))
            RitText(order.reference, RitType.heading)
            RitText("${order.sellerName()} · ${order.kind.name.lowercase()}", RitType.body, colors.muted)
            Spacer(Modifier.height(12.dp))
            RitMessageCard("Current state", order.state.name.lowercase().replace('_', ' '))
            Spacer(Modifier.height(12.dp))
            order.lines.forEach { RitText("${it.quantity} × ${it.title}", RitType.body) }
            Spacer(Modifier.height(8.dp))
            RitText("Demo total ${displayPaise(order.totalPaise)}", RitType.title)
            order.quoteVersion?.let { RitText("Bulk quote revision $it", RitType.small, colors.muted) }
            order.pickup?.let { RitText("Requested pickup ${displayPickup(it)}", RitType.body) }
            Spacer(Modifier.height(14.dp))
            RitText("Created ${order.createdAt}", RitType.small, colors.muted)
            RitText("Last local update ${order.updatedAt}", RitType.small, colors.muted)
            order.redeemedAt?.let { RitText("Demo collected at $it by ${order.redeemedByUserId ?: "seller"}",
                RitType.small, colors.muted) }
        }
        if (order.state == DemoOrderState.DEMO_CONFIRMED && canVerify)
            RitPrimaryButton("Verify customer pickup code", onVerify,
                Modifier.fillMaxWidth().padding(20.dp).testTag("seller_demo_order_verify"))
    }
}
