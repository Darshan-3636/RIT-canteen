package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.BulkRequestRecord
import edu.msrit.canteen.customer.IndividualOrderPreview
import edu.msrit.canteen.customer.DemoOrder
import edu.msrit.canteen.customer.DemoOrderKind
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.QuoteStage
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.CatalogPill
import edu.msrit.canteen.ui.components.FoodPhotoContent
import edu.msrit.canteen.ui.components.RitBottomItem
import edu.msrit.canteen.ui.components.RitBottomNavigation
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.format.DateTimeFormatter

private enum class OrdersTab { ALL, INDIVIDUAL, BULK }
private val orderDate = DateTimeFormatter.ofPattern("d MMM · h:mm a")

private fun bulkPrimaryState(record: BulkRequestRecord): String = when (record.thread.stage) {
    QuoteStage.SUBMITTED -> "Waiting for seller review"
    QuoteStage.COUNTEROFFER -> if (record.latest.actor == edu.msrit.canteen.customer.QuoteActor.SELLER)
        "Review seller's updated offer" else "Waiting for seller's response"
    QuoteStage.AGREED_AWAITING_PAYMENT -> "Agreed · payment unverified"
    QuoteStage.PAID_CONFIRMED -> "Demo state · no actual payment"
    QuoteStage.EXPIRED -> "Offer expired"
    QuoteStage.REJECTED -> "Seller declined"
    QuoteStage.WITHDRAWN -> "Request withdrawn"
}

@Composable
fun CustomerOrdersScreen(individual: List<IndividualOrderPreview>, bulk: List<BulkRequestRecord>,
                         onBack: () -> Unit, onIndividual: (String) -> Unit, onBulk: (String) -> Unit,
                         onCart: () -> Unit, onBottom: (RitBottomItem) -> Unit, cartCount: Int = 0,
                         customerUserId: String = "debug-customer",
                         demoOrders: List<DemoOrder> = emptyList(), onDemoOrder: (String) -> Unit = {}) {
    val colors = RitThemeTokens.colors
    var tab by remember { mutableStateOf(OrdersTab.ALL) }
    val bulkVisible = bulk.filter { it.customerUserId == customerUserId }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("customer_orders")) {
        CatalogHeader("Orders", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrdersTab.entries.forEach { choice ->
                RitChip(choice.name.lowercase().replaceFirstChar { it.uppercase() }, tab == choice,
                    { tab = choice }, Modifier.weight(1f).testTag("orders_tab_${choice.name.lowercase()}"))
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("orders_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
            item {
                RitText("Your activity", RitType.heading)
                RitText("Stored on demo backend · no actual charges or fulfillment", RitType.small, colors.muted,
                    Modifier.testTag("orders_demo_disclosure"))
                Spacer(Modifier.height(14.dp))
            }
            if (tab != OrdersTab.BULK) {
                item { RitText("Individual food", RitType.title); Spacer(Modifier.height(7.dp)) }
                items(demoOrders.filter { it.customerId == customerUserId && it.kind == DemoOrderKind.INDIVIDUAL },
                    key = { "demo_${it.reference}" }) { order ->
                    OrderCard("Individual · Main Canteen", order.state.name.lowercase().replace('_', ' '),
                        order.lines.joinToString { "${it.quantity}× ${it.title}" },
                        "${order.reference} · ${displayPaise(order.totalPaise)} · local simulation",
                        { onDemoOrder(order.reference) }, "orders_demo_${order.reference}", order.lines.firstOrNull()?.itemId)
                }
                if (individual.isEmpty() && demoOrders.none {
                        it.customerId == customerUserId && it.kind == DemoOrderKind.INDIVIDUAL })
                    item { RitEmptyState("No individual previews", "Select prepared food from Main Canteen to build a demo order.") }
                items(individual, key = { it.id }) { record ->
                    OrderCard("Individual · Main Canteen", if (record.locallySaved)
                        record.status.removePrefix("DEMO_").lowercase().replace('_', ' ') + " · demo only"
                        else "Sample history · not a real purchase",
                        record.lines.joinToString { "${it.quantity}× ${it.title}" },
                        "${record.placedAt.format(orderDate)} · ${displayPaise(record.totalPaise)}",
                        { onIndividual(record.id) }, "orders_individual_${record.id}", record.lines.firstOrNull()?.itemId)
                }
            }
            if (tab != OrdersTab.INDIVIDUAL) {
                item { Spacer(Modifier.height(12.dp)); RitText("Bulk requests", RitType.title); Spacer(Modifier.height(7.dp)) }
                items(demoOrders.filter { it.customerId == customerUserId && it.kind == DemoOrderKind.BULK },
                    key = { "demo_${it.reference}" }) { order ->
                    OrderCard("Bulk · ${PreviewCatalog.sellers.find { it.id == order.sellerId }?.name ?: "Seller"}",
                        order.state.name.lowercase().replace('_', ' '),
                        order.lines.joinToString { "${it.quantity}× ${it.title}" },
                        "${order.reference} · ${displayPaise(order.totalPaise)} · local simulation",
                        { onDemoOrder(order.reference) }, "orders_demo_${order.reference}", order.lines.firstOrNull()?.itemId)
                }
                if (bulkVisible.isEmpty() && demoOrders.none {
                        it.customerId == customerUserId && it.kind == DemoOrderKind.BULK })
                    item { RitEmptyState("No bulk requests", "Group requests and seller offers will appear here.") }
                items(bulkVisible, key = { it.reference }) { record ->
                    val seller = PreviewCatalog.sellers.find { it.id == record.sellerId }?.name ?: "Seller"
                    val simulated = demoOrders.find { it.reference == record.reference && it.customerId == customerUserId }
                    OrderCard("Bulk quote · $seller", simulated?.state?.name?.lowercase()?.replace('_', ' ')
                        ?: bulkPrimaryState(record),
                        "${record.latest.lines.sumOf { it.quantity }} items · pickup ${record.latest.pickup.format(orderDate)}",
                        "${record.reference} · ${displayPaise(record.latest.totalPaise)} · Demo data",
                        { onBulk(record.reference) }, "orders_bulk_${record.reference}", record.latest.lines.firstOrNull()?.itemId)
                }
            }
            item { Spacer(Modifier.height(10.dp)); RitSecondaryButton("Open Cart", onCart, Modifier.fillMaxWidth()) }
        }
        RitBottomNavigation(RitBottomItem.Orders, onBottom, cartCount = cartCount)
    }
}

@Composable
private fun OrderCard(title: String, status: String, detail: String, foot: String,
                      onClick: () -> Unit, tag: String, previewItemId: String?) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).border(1.dp, colors.outline,
        RoundedCornerShape(RitRadii.card)).background(colors.card, RoundedCornerShape(RitRadii.card))
        .clickable(onClick = onClick).testTag(tag).padding(16.dp)) {
        PreviewCatalog.items.find { it.id == previewItemId }?.let { item ->
            FoodPhotoContent(item.photo, "${item.name} order preview", Modifier.fillMaxWidth().height(110.dp), item.localImageUri)
            Spacer(Modifier.height(10.dp))
        }
        RitText(title, RitType.label)
        Spacer(Modifier.height(6.dp))
        CatalogPill(status)
        Spacer(Modifier.height(8.dp))
        RitText(detail, RitType.body)
        Spacer(Modifier.height(5.dp))
        RitText(foot, RitType.small, colors.muted)
    }
}

@Composable
fun IndividualOrderDetailScreen(record: IndividualOrderPreview?, onBack: () -> Unit,
                                onDemoPayment: ((String) -> Unit)? = null, feedback: String? = null,
                                paymentBusy: Boolean = false) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).testTag("individual_order_detail")) {
        CatalogHeader("Demo order", onBack)
        if (record == null) RitEmptyState("Preview unavailable", "Refresh Orders to load this server record.")
        else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            RitText(if (record.locallySaved) "Demo order · ${record.status.removePrefix("DEMO_").replace('_', ' ')}" else "Sample history", RitType.heading,
                modifier = Modifier.testTag("individual_order_status"))
            RitText("Backend ID: ${record.id}", RitType.small, colors.muted)
            RitText("Demo only · no money charged or verified payment. Seller actions are simulated.", RitType.body, colors.muted)
            Spacer(Modifier.height(18.dp))
            record.lines.forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    PreviewCatalog.items.find { it.id == line.itemId }?.let { item ->
                        FoodPhotoContent(item.photo, "${item.name} order item", Modifier.padding(end = 10.dp).size(48.dp), item.localImageUri)
                    }
                    RitText("${line.quantity} × ${line.title}", RitType.body, modifier = Modifier.weight(1f))
                    RitText(displayPaise(line.unitPricePaise * line.quantity), RitType.body)
                }
            }
            Spacer(Modifier.height(12.dp))
            RitText("Demo total  ${displayPaise(record.totalPaise)}", RitType.title)
            if (record.locallySaved && record.status in setOf("DEMO_SAVED", "DEMO_PAYMENT_PENDING", "DEMO_PAYMENT_FAILED") && onDemoPayment != null) {
                Spacer(Modifier.height(18.dp))
                RitText("Simulate a payment outcome", RitType.label)
                RitText("This is a test action, not PhonePe or a bank transaction.", RitType.small, colors.muted)
                Spacer(Modifier.height(8.dp))
                RitPrimaryButton(if (paymentBusy) "Saving DEMO outcome…" else "Simulate Payment", { onDemoPayment("SUCCESS") },
                    Modifier.fillMaxWidth().testTag("individual_demo_pay_success"), enabled = !paymentBusy)
                Spacer(Modifier.height(8.dp))
                RitSecondaryButton("Simulate pending", { onDemoPayment("PENDING") },
                    Modifier.fillMaxWidth().testTag("individual_demo_pay_pending"), enabled = !paymentBusy)
                Spacer(Modifier.height(8.dp))
                RitSecondaryButton("Simulate failure", { onDemoPayment("FAILURE") },
                    Modifier.fillMaxWidth().testTag("individual_demo_pay_failure"), enabled = !paymentBusy)
            }
            if (!feedback.isNullOrBlank()) RitText(feedback, RitType.body, colors.accent,
                Modifier.padding(top = 10.dp).testTag("individual_demo_payment_feedback"))
        }
    }
}
