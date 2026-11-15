package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.BulkRequestRecord
import edu.msrit.canteen.customer.QuoteStage
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.SandboxPayment
import edu.msrit.canteen.customer.customerBulkStatus
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitPickupQr
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.Instant
import java.time.LocalDateTime

/**
 * A customer's bulk order: the canteen accepts it or sends changes, the customer accepts and pays, then it
 * is prepared for the agreed pickup time and collected with the pickup QR code or PIN.
 *
 * [onAcceptChanges] and [onCancel] report back a message to show (an error, or a confirmation).
 */
@Composable
fun CustomerBulkOrderScreen(
    record: BulkRequestRecord,
    onBack: () -> Unit,
    onAcceptChanges: ((String) -> Unit) -> Unit,
    onCancel: ((String) -> Unit) -> Unit,
    payment: SandboxPayment? = null,
    paymentBusy: Boolean = false,
    paymentFeedback: String? = null,
    onPayNow: (() -> Unit)? = null,
    onCheckPayment: (() -> Unit)? = null,
    now: LocalDateTime = LocalDateTime.now(RIT_TIME_ZONE),
) {
    val colors = RitThemeTokens.colors
    val latest = record.latest
    val stage = record.thread.stage
    // A message belongs to the step it was shown for; a new status clears it.
    var feedback by remember(stage) { mutableStateOf<String?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val offerOpen = record.thread.expiresAt?.isAfter(now) != false
    val canteenAccepted = record.thread.sellerAcceptedVersion == latest.version &&
        (!latest.specialPickup || record.thread.specialAcceptedVersion == latest.version)
    val canAcceptChanges = stage == QuoteStage.COUNTEROFFER && canteenAccepted && offerOpen &&
        record.thread.customerAcceptedVersion != latest.version
    val canPay = stage == QuoteStage.AGREED_AWAITING_PAYMENT && offerOpen
    val canCancel = stage in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER) && offerOpen
    val pin = record.pickupCode

    Column(Modifier.fillMaxSize().background(colors.background).testTag("customer_bulk_order")) {
        CatalogHeader("Bulk order", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            RitText(customerBulkStatus(record), RitType.heading, modifier = Modifier.testTag("bulk_order_status"))
            Spacer(Modifier.height(6.dp))
            RitText(when (stage) {
                QuoteStage.SUBMITTED -> "The canteen is checking your request. You can pay once they accept it."
                QuoteStage.COUNTEROFFER -> if (canteenAccepted)
                    "The canteen changed the items, price or pickup time. Check the order below, then accept the changes to pay."
                    else "The canteen is checking your request. You can pay once they accept it."
                QuoteStage.AGREED_AWAITING_PAYMENT -> if (offerOpen)
                    "The canteen accepted your order. Pay${record.thread.expiresAt?.let { " by ${displayPickup(it)}" }.orEmpty()} to confirm it."
                    else "This order wasn't paid in time. Send a new request if you still need it."
                QuoteStage.PAID -> "Your order is being prepared for pickup. Show this at the counter when you collect it."
                QuoteStage.READY -> "Your order is ready. Show this at the counter."
                QuoteStage.COMPLETED -> record.handedOverAt?.let { "Collected ${instantLabel(it)}." } ?: "Collected."
                QuoteStage.EXPIRED -> "This request expired before it was confirmed. Send a new request if you still need it."
                QuoteStage.REJECTED -> "The canteen can't take this order. You can send a new request with different items or a different time."
                QuoteStage.WITHDRAWN -> "You cancelled this request."
            }, RitType.body, modifier = Modifier.testTag("bulk_order_next_step"))

            if (pin != null && stage in setOf(QuoteStage.PAID, QuoteStage.READY)) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    RitPickupQr(pin, Modifier.testTag("bulk_pickup_qr"))
                    Spacer(Modifier.height(10.dp))
                    RitText("Pickup PIN", RitType.label, colors.muted)
                    RitText(pin, RitType.display, colors.accent, Modifier.testTag("bulk_pickup_code"))
                }
            }

            Spacer(Modifier.height(18.dp))
            BulkInfoCard("Pickup", displayPickup(latest.pickup) +
                (if (latest.specialPickup && latest.specialReason.isNotBlank()) "\nOutside usual hours: ${latest.specialReason}" else "") +
                (if (record.original.instructions.isNotBlank()) "\n${record.original.instructions}" else ""))
            Spacer(Modifier.height(14.dp))
            RitText("Items", RitType.title)
            latest.lines.forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    RitText("${line.quantity} × ${line.title}", RitType.body, modifier = Modifier.weight(1f))
                    RitText(displayPaise(line.unitPricePaise * line.quantity), RitType.body)
                }
            }
            Spacer(Modifier.height(8.dp))
            RitText("Total  ${displayPaise(latest.totalPaise)}", RitType.title, modifier = Modifier.testTag("bulk_order_total"))

            if (canAcceptChanges) {
                Spacer(Modifier.height(18.dp))
                RitPrimaryButton(if (busy) "Saving…" else "Accept changes", {
                    busy = true; feedback = null
                    onAcceptChanges { busy = false; feedback = it }
                }, Modifier.fillMaxWidth().testTag("bulk_accept_changes"), enabled = !busy)
            }
            if (canPay) {
                Spacer(Modifier.height(18.dp))
                if (onPayNow == null) RitText("Payment isn't available right now. Please try again later.",
                    RitType.body, colors.muted)
                else if (payment?.status !in setOf("SANDBOX_SUCCESS", "CANCELLED")) RitPrimaryButton(
                    if (paymentBusy) "Opening PhonePe…" else "Pay Now", onPayNow,
                    Modifier.fillMaxWidth().testTag("bulk_pay_now"), enabled = !paymentBusy)
                if (payment != null && payment.status in setOf("PENDING", "INITIATED", "CANCELLED") && onCheckPayment != null) {
                    Spacer(Modifier.height(8.dp))
                    RitSecondaryButton("Check payment status", onCheckPayment,
                        Modifier.fillMaxWidth().testTag("bulk_check_payment"), enabled = !paymentBusy)
                }
            }
            if (canCancel) {
                Spacer(Modifier.height(10.dp))
                RitSecondaryButton("Cancel request", { confirmCancel = true },
                    Modifier.fillMaxWidth().testTag("bulk_cancel_request"), enabled = !busy)
            }
            (feedback ?: paymentFeedback)?.takeIf { it.isNotBlank() }?.let {
                RitText(it, RitType.body, colors.accent, Modifier.padding(top = 10.dp).testTag("bulk_order_feedback"))
            }
        }
    }
    if (confirmCancel) RitConfirmDialog("Cancel this request?",
        "The canteen won't prepare it. You can send a new request later.", { confirmCancel = false }, {
            confirmCancel = false; busy = true; feedback = null
            onCancel { busy = false; feedback = it }
        }, "Cancel request")
}

private fun instantLabel(iso: String): String = runCatching {
    displayPickup(LocalDateTime.ofInstant(Instant.parse(iso), RIT_TIME_ZONE))
}.getOrDefault("")
