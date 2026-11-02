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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import edu.msrit.canteen.customer.IndividualOrderPreview
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

/** Server-owned demo queue. No state here represents verified payment or real fulfillment. */
@Composable
fun SellerIndividualBackendScreen(orders: List<IndividualOrderPreview>, canRespond: Boolean,
                                  onBack: () -> Unit, onTransition: (String, String, String?) -> Unit,
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
            if (orders.isEmpty()) item { RitEmptyState("No demo orders", "New M.S.R.I.T Canteen demo checkouts appear here after refresh.") }
            items(orders, key = { it.id }) { order ->
                Column(Modifier.fillMaxWidth().padding(bottom = 18.dp).testTag("seller_individual_${order.id}")) {
                    RitText("${order.pickupReference} · ${order.status.removePrefix("DEMO_").replace('_', ' ')}", RitType.title)
                    RitText(order.lines.joinToString { "${it.quantity} × ${it.title}" }, RitType.body)
                    RitText("Demo total ${displayPaise(order.totalPaise)} · ${order.demoPaymentOutcome.lowercase()} simulation",
                        RitType.small, colors.muted)
                    order.sandboxPaymentStatus?.let { status ->
                        RitText("PhonePe UAT: ${status.replace('_', ' ')} · SANDBOX ONLY · paymentVerified=false",
                            RitType.small, colors.muted, Modifier.testTag("seller_individual_uat_${order.id}"))
                    }
                    val next = when (order.status) {
                        "DEMO_PAYMENT_SIMULATED" -> "DEMO_ACCEPTED"
                        "DEMO_ACCEPTED" -> "DEMO_READY"
                        "DEMO_READY" -> "DEMO_COMPLETED"
                        else -> null
                    }
                    if (canRespond && next != null) {
                        Spacer(Modifier.height(8.dp))
                        var code by remember(order.id) { mutableStateOf("") }
                        if(next=="DEMO_COMPLETED") RitTextField(code,{ code=it.uppercase().take(12) },
                            "Customer handover code","Ask the customer for their code")
                        RitPrimaryButton(if(next=="DEMO_COMPLETED") "Confirm demo handover" else
                            "Demo: ${next.removePrefix("DEMO_").lowercase().replaceFirstChar(Char::uppercase)}",
                            { onTransition(order.id,next,if(next=="DEMO_COMPLETED") code else null) },
                            Modifier.fillMaxWidth().testTag("seller_demo_next_${order.id}"),
                            enabled=next!="DEMO_COMPLETED" || code.length==12)
                    }
                }
            }
        }
    }
}
