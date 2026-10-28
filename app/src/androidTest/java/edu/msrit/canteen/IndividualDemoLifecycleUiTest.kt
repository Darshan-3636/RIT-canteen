package edu.msrit.canteen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.msrit.canteen.customer.IndividualOrderPreview
import edu.msrit.canteen.customer.QuoteLineSnapshot
import edu.msrit.canteen.ui.screens.IndividualOrderDetailScreen
import edu.msrit.canteen.ui.screens.SellerIndividualBackendScreen
import edu.msrit.canteen.ui.theme.RitTheme
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IndividualDemoLifecycleUiTest {
    @get:Rule val rule = createComposeRule()
    private val order = IndividualOrderPreview("e404244b-f468-49db-b7ca-12a93e0836d0",
        LocalDateTime.of(2026,10,28,12,0),
        listOf(QuoteLineSnapshot("masala-dosa","Masala Dosa",2,5_000)),
        10_000, locallySaved = true, status = "DEMO_SAVED")

    @Test fun customerPaymentActionIsExplicitlySimulated() {
        var selected: String? = null
        rule.setContent { RitTheme { IndividualOrderDetailScreen(order, {}, { selected = it }) } }
        rule.onNodeWithText("Demo only · no money charged or verified payment.", substring = true).assertExists()
        rule.onNodeWithTag("individual_demo_pay_success").performScrollTo().performClick()
        assertEquals("SUCCESS", selected)
    }

    @Test fun paymentControlsDisableDuringRequestAndSuccessShowsPersistedStatus() {
        var current by mutableStateOf(order)
        var busy by mutableStateOf(true)
        rule.setContent { RitTheme { IndividualOrderDetailScreen(current, {}, {},
            "DEMO payment simulated successfully. No money was charged or verified.", paymentBusy = busy) } }
        rule.onNodeWithTag("individual_demo_pay_success").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("individual_demo_pay_pending").performScrollTo().assertIsNotEnabled()
        rule.runOnIdle {
            busy = false
            current = order.copy(status = "DEMO_PAYMENT_SIMULATED", demoPaymentOutcome = "SUCCESS")
        }
        rule.onNodeWithTag("individual_demo_pay_success").assertDoesNotExist()
        rule.onNodeWithTag("individual_order_status").assertExists()
        rule.onNodeWithTag("individual_demo_payment_feedback").assertExists()
    }

    @Test fun sellerActionOnlyAppearsAfterSimulatedSuccessAndRequiresCapability() {
        var changed: String? = null
        var current by mutableStateOf(order)
        var canRespond by mutableStateOf(true)
        rule.setContent { RitTheme { SellerIndividualBackendScreen(listOf(current), canRespond, {}, { _, status -> changed = status }) } }
        rule.onNodeWithTag("seller_demo_next_${order.id}").assertDoesNotExist()
        rule.runOnIdle { current = order.copy(status = "DEMO_PAYMENT_SIMULATED") }
        rule.onNodeWithTag("seller_demo_next_${order.id}").performScrollTo().performClick()
        assertEquals("DEMO_ACCEPTED", changed)
        rule.runOnIdle { canRespond = false }
        rule.onNodeWithTag("seller_demo_next_${order.id}").assertDoesNotExist()
    }
}
