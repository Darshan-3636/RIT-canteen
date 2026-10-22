package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.CurrentItemState
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.EmptyOrderHistory
import edu.msrit.canteen.customer.OrderHistorySource
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.currentItemState
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitMessageCard
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyDate = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())

@Composable
fun CustomerQuickReorderScreen(onBack: () -> Unit, onFood: (String) -> Unit, onSeller: (String) -> Unit,
                               history: OrderHistorySource = EmptyOrderHistory,
                               catalog: CustomerCatalogSource = PreviewCatalog) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).testTag("quick_reorder_screen")) {
        CatalogHeader("Quick Reorder", onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            RitText("Revisit past food", RitType.heading)
            RitText("A future shortcut, not an order submission.", RitType.body, colors.muted)
            Spacer(Modifier.height(10.dp))
            RitStatusIndicator(if (history.orders.isEmpty()) "No verified account or order history" else "Development-only sample history · not your purchases")
            Spacer(Modifier.height(14.dp))
        }
        if (history.orders.isEmpty()) {
            Column(Modifier.testTag("quick_reorder_empty")) {
                RitEmptyState("No orders to reorder", "Once real orders exist, eligible items can be reviewed against the current menu.")
            }
        } else {
            LazyColumn(Modifier.testTag("quick_reorder_sample"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                items(history.orders, key = { it.id }) { order ->
                    Column(Modifier.padding(vertical = 10.dp)) {
                        RitMessageCard("Sample history · ${historyDate.format(order.completedAt)}",
                            "Snapshot ${order.id}. Historic titles and prices are illustrative; current catalog rules always apply.")
                        Spacer(Modifier.height(12.dp))
                        order.lines.forEach { line ->
                            val state = currentItemState(catalog, line)
                            val current = catalog.items.find { it.id == line.itemId && it.sellerId == line.sellerId }
                            RitText(line.titleSnapshot, RitType.title)
                            RitText("Historic sample price ${line.priceSnapshot.display}" +
                                (if (current == null) "" else " · current preview price ${current.price.display}"),
                                RitType.small, colors.muted)
                            RitText(when (state) {
                                CurrentItemState.AVAILABLE_FOR_REVIEW -> "Current item may be reviewed; ordering is still unavailable."
                                CurrentItemState.UNAVAILABLE -> "Current item is unavailable for a new request."
                                CurrentItemState.REMOVED -> "Item is no longer in the current preview catalog."
                            }, RitType.body, colors.muted)
                            Spacer(Modifier.height(8.dp))
                            if (state == CurrentItemState.AVAILABLE_FOR_REVIEW) {
                                RitSecondaryButton("View current item", { onFood(line.itemId) },
                                    Modifier.fillMaxWidth().testTag("reorder_open_item_${line.itemId}"))
                            } else if (catalog.sellers.any { it.id == line.sellerId }) {
                                RitSecondaryButton("View seller", { onSeller(line.sellerId) }, Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }
                item {
                    RitPrimaryButton("Reorder unavailable in preview", {}, Modifier.fillMaxWidth().testTag("reorder_submit"), enabled = false)
                    Spacer(Modifier.height(12.dp))
                    RitText("A future reorder must revalidate seller, item, availability, current price and ordering eligibility before a new request.",
                        RitType.small, colors.muted)
                }
            }
        }
    }
}
