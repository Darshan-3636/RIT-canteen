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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.IndividualOrderPreview
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSearchField
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sellerOrderTime = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
private val orderFilters = listOf("All Orders", "New", "Preparing", "Ready", "Completed", "Cancelled")

/** Server-owned demo queue. No state here represents verified payment or real fulfillment. */
@Composable
fun SellerIndividualBackendScreen(orders: List<IndividualOrderPreview>, canRespond: Boolean,
                                  onBack: () -> Unit, onTransition: (String, String, String?) -> Unit,
                                  feedback: String? = null) {
    val colors = RitThemeTokens.colors
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All Orders") }
    val visibleOrders = remember(orders, query, filter) {
        orders.filter { order ->
            val statusMatches = when (filter) {
                "New" -> order.status in setOf("DEMO_SAVED", "DEMO_PAYMENT_PENDING", "DEMO_PAYMENT_FAILED", "DEMO_PAYMENT_SIMULATED")
                "Preparing" -> order.status == "DEMO_ACCEPTED"
                "Ready" -> order.status == "DEMO_READY"
                "Completed" -> order.status == "DEMO_COMPLETED"
                "Cancelled" -> order.status == "DEMO_REJECTED"
                else -> true
            }
            val needle = query.trim()
            val searchMatches = needle.isEmpty() || order.pickupReference.contains(needle, ignoreCase = true) ||
                order.customerName.orEmpty().contains(needle, ignoreCase = true) ||
                order.lines.any { it.title.contains(needle, ignoreCase = true) }
            statusMatches && searchMatches
        }
    }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_individual_backend")) {
        CatalogHeader("Individual orders", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            item {
                if (!feedback.isNullOrBlank()) RitText(feedback, RitType.body, colors.accent,
                    Modifier.padding(vertical = 12.dp).testTag("seller_individual_feedback"))
                Spacer(Modifier.height(8.dp))
                RitSearchField(query, { query = it }, hint = "Search customer or item")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    orderFilters.forEach { value -> RitChip(value, filter == value, { filter = value }) }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (visibleOrders.isEmpty()) item {
                if (orders.isEmpty()) RitEmptyState("No orders found", "New orders will appear here after refresh.")
                else RitEmptyState("No matching orders", "Try another status or search term.")
            }
            items(visibleOrders, key = { it.id }) { order ->
                var expanded by rememberSaveable(order.id) { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().padding(bottom = 18.dp).testTag("seller_individual_${order.id}")) {
                    RitText(order.customerName ?: "Customer order", RitType.title, maxLines = 2)
                    RitText(orderStatus(order.status), RitType.label, colors.accent)
                    RitText("Customer: ${order.customerName ?: "Not provided"}", RitType.body)
                    RitText("Phone: ${order.customerPhone ?: "Not provided"}", RitType.body)
                    RitText("Ordered: ${order.lines.joinToString { "${it.quantity} ${it.title}" }}", RitType.body)
                    RitText("Total: ${displayPaise(order.totalPaise)}", RitType.body)
                    RitText("Time: ${sellerOrderTime.format(order.placedAt)}", RitType.small, colors.muted)
                    RitText("Order type: Individual", RitType.small, colors.muted)
                    RitText("Payment: ${paymentLabel(order)}", RitType.small, colors.muted)
                    if (expanded) {
                        Spacer(Modifier.height(6.dp))
                        order.lines.forEach { line ->
                            RitText("${line.title} · ${line.quantity} × ${displayPaise(line.unitPricePaise)} each",
                                RitType.body)
                        }
                        RitText("Pickup instructions: Not provided", RitType.body)
                        order.handedOverAt?.let { RitText("Handover recorded: $it", RitType.small, colors.muted) }
                    }
                    Spacer(Modifier.height(5.dp))
                    RitSecondaryButton(if (expanded) "Hide details" else "View details", { expanded = !expanded },
                        Modifier.fillMaxWidth().testTag("seller_order_details_${order.id}"))
                    val next = when (order.status) {
                        "DEMO_PAYMENT_SIMULATED" -> "DEMO_ACCEPTED"
                        "DEMO_ACCEPTED" -> "DEMO_READY"
                        "DEMO_READY" -> "DEMO_COMPLETED"
                        else -> null
                    }
                    if (canRespond && next != null) {
                        Spacer(Modifier.height(8.dp))
                        var code by rememberSaveable(order.id) { mutableStateOf("") }
                        if (next == "DEMO_COMPLETED") RitTextField(code, { code = it.uppercase().take(12) },
                            "Customer handover code", "Ask the customer for their code")
                        RitPrimaryButton(if (next == "DEMO_COMPLETED") "Confirm demo handover" else
                            "Demo: ${next.removePrefix("DEMO_").lowercase().replaceFirstChar(Char::uppercase)}",
                            { onTransition(order.id, next, if (next == "DEMO_COMPLETED") code else null) },
                            Modifier.fillMaxWidth().testTag("seller_demo_next_${order.id}"),
                            enabled = next != "DEMO_COMPLETED" || code.length == 12)
                    }
                }
            }
        }
    }
}

private fun orderStatus(status: String): String = when (status) {
    "DEMO_SAVED" -> "New · payment not confirmed"
    "DEMO_PAYMENT_PENDING" -> "New · payment pending"
    "DEMO_PAYMENT_FAILED" -> "New · payment failed"
    "DEMO_PAYMENT_SIMULATED" -> "New · test payment"
    "DEMO_ACCEPTED" -> "Preparing"
    "DEMO_READY" -> "Ready"
    "DEMO_COMPLETED" -> "Completed · test handover"
    "DEMO_REJECTED" -> "Cancelled"
    else -> status.removePrefix("DEMO_").replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
}

private fun paymentLabel(order: IndividualOrderPreview): String = buildString {
    append(if (order.sandboxPaymentStatus == null) "Not verified" else "PhonePe UAT sandbox · not verified")
    if (order.demoPaymentOutcome != "NOT_ATTEMPTED") append(" · ${order.demoPaymentOutcome.lowercase()}")
}
