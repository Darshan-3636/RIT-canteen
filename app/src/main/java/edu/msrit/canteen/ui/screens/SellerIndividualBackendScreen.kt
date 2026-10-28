package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import edu.msrit.canteen.customer.IndividualOrderPreview
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

/** Server-owned demo queue. No state here represents verified payment or real fulfillment. */
@Composable
fun SellerIndividualBackendScreen(orders: List<IndividualOrderPreview>, canRespond: Boolean,
                                  onBack: () -> Unit, onTransition: (String, String) -> Unit,
                                  feedback: String? = null) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_individual_backend")) {
        CatalogHeader("Individual demo orders", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
            item {
                RitStatusIndicator("Demo only · payment is never verified")
                if (!feedback.isNullOrBlank()) RitText(feedback, RitType.body, colors.accent,
                    Modifier.padding(vertical = 12.dp).testTag("seller_individual_feedback"))
                Spacer(Modifier.height(10.dp))
            }
            if (orders.isEmpty()) item { RitEmptyState("No demo orders", "New Main Canteen demo checkouts appear here after refresh.") }
            items(orders, key = { it.id }) { order ->
                Column(Modifier.fillMaxWidth().padding(bottom = 18.dp).testTag("seller_individual_${order.id}")) {
                    RitText("#${order.id.take(8)} · ${order.status.removePrefix("DEMO_").replace('_', ' ')}", RitType.title)
                    RitText(order.lines.joinToString { "${it.quantity} × ${it.title}" }, RitType.body)
                    RitText("Demo total ${displayPaise(order.totalPaise)} · ${order.demoPaymentOutcome.lowercase()} simulation",
                        RitType.small, colors.muted)
                    val next = when (order.status) {
                        "DEMO_PAYMENT_SIMULATED" -> "DEMO_ACCEPTED"
                        "DEMO_ACCEPTED" -> "DEMO_READY"
                        "DEMO_READY" -> "DEMO_COMPLETED"
                        else -> null
                    }
                    if (canRespond && next != null) {
                        Spacer(Modifier.height(8.dp))
                        RitPrimaryButton("Demo: ${next.removePrefix("DEMO_").lowercase().replaceFirstChar(Char::uppercase)}",
                            { onTransition(order.id, next) }, Modifier.fillMaxWidth().testTag("seller_demo_next_${order.id}"))
                    }
                }
            }
        }
    }
}
