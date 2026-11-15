package edu.msrit.canteen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.msrit.canteen.customer.IndividualOrderPreview
import edu.msrit.canteen.customer.QuoteLineSnapshot
import edu.msrit.canteen.customer.SandboxPayment
import edu.msrit.canteen.ui.screens.IndividualOrderDetailScreen
import edu.msrit.canteen.ui.screens.SellerOrdersPage
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

    @Test fun unpaidOrderShowsOnlyPayNowAndNoPickupCode() {
        var pays = 0
        rule.setContent { RitTheme { IndividualOrderDetailScreen(order.copy(pickupCode = "0427"), {},
            onPayNow = { pays++ }) } }
        rule.onNodeWithText("Simulate", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Demo", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("individual_pickup_code").assertDoesNotExist()
        rule.onNodeWithTag("individual_pay_now").performScrollTo().performClick()
        assertEquals(1, pays)
    }

    @Test fun paidOrderShowsPreparationTimeThenPinAndQrWhenReady() {
        var current by mutableStateOf(order.copy(status = "DEMO_PAYMENT_SIMULATED", pickupCode = "0427"))
        rule.setContent { RitTheme { IndividualOrderDetailScreen(current, {}, onPayNow = {}) } }
        rule.onNodeWithText("Ready in 10–12 min", substring = true).assertExists()
        rule.onNodeWithTag("individual_pay_now").assertDoesNotExist()
        rule.onNodeWithTag("individual_pickup_code").assertExists()
        rule.runOnIdle { current = current.copy(status = "DEMO_READY") }
        rule.onNodeWithText("Ready for pickup").assertExists()
        rule.onNodeWithTag("individual_pickup_qr").assertExists()
    }

    @Test fun sellerSeesOnlyPaidOrdersMarksReadyAndConfirmsPickupWithPin() {
        var readyCalls = 0
        var confirmedPin: String? = null
        var current by mutableStateOf(order)
        var canRespond by mutableStateOf(true)
        rule.setContent { RitTheme { SellerOrdersPage(listOf(current), emptyList(), order.sellerId, canRespond,
            onMarkReady = { _, done -> readyCalls++; done(null) },
            onConfirmPickup = { _, pin, done -> confirmedPin = pin; done(null) },
            onOpenBulkRequest = {}) } }
        rule.onNodeWithTag("seller_order_${order.id}").assertDoesNotExist() // unpaid orders stay hidden
        rule.runOnIdle { current = order.copy(status = "DEMO_PAYMENT_SIMULATED") }
        rule.onNodeWithTag("seller_mark_ready_${order.id}").performClick()
        assertEquals(1, readyCalls)
        rule.runOnIdle { current = order.copy(status = "DEMO_READY") }
        rule.onNodeWithTag("seller_orders_tab_ready").performClick()
        rule.onNodeWithTag("seller_confirm_pickup_${order.id}").performClick()
        rule.onNodeWithTag("seller_pickup_enter_pin").performClick()
        rule.onNodeWithTag("seller_pickup_pin").performTextInput("0427")
        rule.onNodeWithTag("seller_pickup_submit").performClick()
        assertEquals("0427", confirmedPin)
        rule.onNodeWithTag("seller_pickup_dialog").assertDoesNotExist()
        rule.runOnIdle { canRespond = false }
        rule.onNodeWithTag("seller_confirm_pickup_${order.id}").assertDoesNotExist()
    }

    @Test fun pendingPaymentCanBeResumedOrChecked() {
        var starts = 0
        var checks = 0
        val attempt = SandboxPayment("pay-id", "INDIVIDUAL", order.id, 10_000, "PENDING",
            "RIT_order", "provider-id", null, null)
        rule.setContent { RitTheme { IndividualOrderDetailScreen(order.copy(sandboxPaymentStatus = "PENDING"), {},
            payment = attempt, onPayNow = { starts++ }, onCheckPayment = { checks++ }) } }
        rule.onNodeWithTag("individual_pay_now").performScrollTo().performClick()
        rule.onNodeWithTag("individual_check_payment").performScrollTo().performClick()
        assertEquals(1, starts)
        assertEquals(1, checks)
    }
}
