package edu.msrit.canteen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.msrit.canteen.customer.DemoActionResult
import edu.msrit.canteen.customer.DemoOrder
import edu.msrit.canteen.customer.DemoOrderKind
import edu.msrit.canteen.customer.DemoOrderState
import edu.msrit.canteen.customer.DemoPaymentOutcome
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.QuoteLineSnapshot
import edu.msrit.canteen.ui.screens.DemoCheckoutScreen
import edu.msrit.canteen.ui.screens.DemoOrderDetailScreen
import edu.msrit.canteen.ui.screens.SellerDemoPickupScreen
import edu.msrit.canteen.ui.theme.RitTheme
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DemoCheckoutUiTest {
    @get:Rule val rule = createComposeRule()

    private fun order(state: DemoOrderState = DemoOrderState.SELLER_ACCEPTED,
                      code: String? = null) = DemoOrder("DEMO-IND-0001", DemoOrderKind.INDIVIDUAL,
        "customer-a", PreviewCatalog.MAIN_ID,
        listOf(QuoteLineSnapshot("masala-dosa", "Masala Dosa", 2, 5_000)),
        10_000, LocalDateTime.of(2026, 11, 1, 12, 0), null, state, code)

    @Test fun checkoutOutcomeIsExplicitAndNeverImplicitOnBack() {
        val chosen = AtomicReference<DemoPaymentOutcome?>(null)
        val finished = AtomicBoolean(false)
        rule.setContent { RitTheme {
            DemoCheckoutScreen(order(), {}, { chosen.set(it); DemoActionResult.APPLIED }, { finished.set(true) })
        } }
        assertFalse(finished.get())
        rule.onNodeWithText("DEMO PAYMENT — no money is charged").assertExists()
        rule.onNodeWithTag("demo_outcome_failure").performClick()
        rule.onNodeWithTag("demo_simulate_payment").performClick()
        assertEquals(DemoPaymentOutcome.FAILURE, chosen.get())
        assertTrue(finished.get())
    }

    @Test fun pendingHasNoCredentialButConfirmedHasQrAndTextCode() {
        var state by mutableStateOf(DemoOrderState.PAYMENT_PENDING)
        rule.setContent { RitTheme { DemoOrderDetailScreen(order(state,
            if (state == DemoOrderState.DEMO_CONFIRMED) "ABCD-EFGH-JKLM-NPQR" else null), {}, {}, {}, {}) } }
        rule.onNodeWithTag("demo_pickup_code").assertDoesNotExist()
        rule.onNodeWithTag("demo_pickup_qr").assertDoesNotExist()
        rule.onNodeWithTag("demo_open_checkout").assertExists()
        rule.runOnIdle { state = DemoOrderState.DEMO_CONFIRMED }
        rule.onNodeWithTag("demo_pickup_code").assertExists()
        rule.onNodeWithTag("demo_pickup_qr").assertExists()
    }

    @Test fun sellerMustVerifyThenDeliberatelyConfirmCollection() {
        val confirmed = AtomicBoolean(false)
        rule.setContent { RitTheme { SellerDemoPickupScreen({},
            { code -> if (code == "ABCD-EFGH-JKLM-NPQR") DemoActionResult.APPLIED to
                order(DemoOrderState.DEMO_CONFIRMED, code) else DemoActionResult.NOT_FOUND to null },
            { confirmed.set(true); DemoActionResult.APPLIED }) } }
        rule.onNodeWithTag("seller_confirm_collection").assertDoesNotExist()
        rule.onNodeWithTag("seller_demo_code").performTextInput("ABCD-EFGH-JKLM-NPQR")
        rule.onNodeWithTag("seller_verify_code").performClick()
        rule.onNodeWithTag("seller_confirm_collection").assertExists().performClick()
        assertTrue(confirmed.get())
        rule.onNodeWithText("Collected (demo). This code can no longer be used.", substring = true).assertExists()
    }
}
