package edu.msrit.canteen.customer

import edu.msrit.canteen.navigation.AppMode
import edu.msrit.canteen.navigation.AuthorizedIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DevelopmentDemoCheckoutTest {
    private val customer = AuthorizedIdentity("customer-a", setOf(AppMode.Customer))
    private val otherCustomer = AuthorizedIdentity("customer-b", setOf(AppMode.Customer))
    private val mainOwner = AuthorizedIdentity("main-owner", setOf(AppMode.SellerOwner(PreviewCatalog.MAIN_ID)),
        mapOf(PreviewCatalog.MAIN_ID to setOf("bulk.respond")))
    private val northOwner = AuthorizedIdentity("north-owner", setOf(AppMode.SellerOwner("north-counter")),
        mapOf("north-counter" to setOf("bulk.respond")))
    private val northStaff = AuthorizedIdentity("north-staff", setOf(AppMode.OperationalStaff("north-counter")),
        mapOf("north-counter" to setOf("bulk.view")))

    @Before fun reset() { DevelopmentDemoCheckout.reset(); DevelopmentSellerOperations.reset() }
    @After fun cleanup() { DevelopmentDemoCheckout.reset(); DevelopmentSellerOperations.reset() }

    private fun submitIndividual(): String {
        val cart = IndividualCartSession()
        assertTrue(cart.add(PreviewCatalog.items.first { it.id == "masala-dosa" }))
        val (result, reference) = DevelopmentDemoCheckout.submitIndividual(cart.draft, customer)
        assertEquals(DemoActionResult.APPLIED, result)
        return requireNotNull(reference)
    }

    @Test fun individualRequiresSellerApprovalAndCustomerScope() {
        val reference = submitIndividual()
        assertEquals(DemoActionResult.INVALID_STATE,
            DevelopmentDemoCheckout.simulatePayment(reference, DemoPaymentOutcome.SUCCESS, customer))
        assertEquals(DemoActionResult.UNAUTHORIZED,
            DevelopmentDemoCheckout.respondIndividual(reference, true, northOwner))
        assertEquals(DemoActionResult.APPLIED,
            DevelopmentDemoCheckout.respondIndividual(reference, true, mainOwner))
        assertEquals(DemoActionResult.UNAUTHORIZED,
            DevelopmentDemoCheckout.simulatePayment(reference, DemoPaymentOutcome.SUCCESS, otherCustomer))
        assertEquals(DemoActionResult.APPLIED,
            DevelopmentDemoCheckout.simulatePayment(reference, DemoPaymentOutcome.SUCCESS, customer))
        assertNotNull(DevelopmentDemoCheckout.orders.single().pickupCode)
    }

    @Test fun failedAndPendingDoNotCreateCodeAndRetryCreatesOnlyOne() {
        val reference = submitIndividual()
        DevelopmentDemoCheckout.respondIndividual(reference, true, mainOwner)
        assertEquals(DemoActionResult.APPLIED,
            DevelopmentDemoCheckout.simulatePayment(reference, DemoPaymentOutcome.FAILURE, customer))
        assertNull(DevelopmentDemoCheckout.orders.single().pickupCode)
        assertEquals(DemoActionResult.APPLIED,
            DevelopmentDemoCheckout.simulatePayment(reference, DemoPaymentOutcome.PENDING, customer))
        assertNull(DevelopmentDemoCheckout.orders.single().pickupCode)
        assertEquals(DemoActionResult.APPLIED,
            DevelopmentDemoCheckout.simulatePayment(reference, DemoPaymentOutcome.SUCCESS, customer))
        val code = DevelopmentDemoCheckout.orders.single().pickupCode
        assertEquals(DemoActionResult.ALREADY_USED,
            DevelopmentDemoCheckout.simulatePayment(reference, DemoPaymentOutcome.SUCCESS, customer))
        assertEquals(code, DevelopmentDemoCheckout.orders.single().pickupCode)
        assertEquals(1, DevelopmentDemoCheckout.orders.size)
    }

    @Test fun pickupCodeIsSellerScopedAndSingleUse() {
        val reference = submitIndividual()
        DevelopmentDemoCheckout.respondIndividual(reference, true, mainOwner)
        DevelopmentDemoCheckout.simulatePayment(reference, DemoPaymentOutcome.SUCCESS, customer)
        val code = requireNotNull(DevelopmentDemoCheckout.orders.single().pickupCode)
        assertEquals(DemoActionResult.NOT_FOUND, DevelopmentDemoCheckout.verifyCode("WRONG", mainOwner).first)
        assertEquals(DemoActionResult.UNAUTHORIZED, DevelopmentDemoCheckout.verifyCode(code, northOwner).first)
        assertEquals(DemoActionResult.APPLIED, DevelopmentDemoCheckout.verifyCode(code, mainOwner).first)
        assertEquals(DemoActionResult.APPLIED,
            DevelopmentDemoCheckout.verifyCode(code.replace("-", ""), mainOwner).first)
        assertEquals(DemoActionResult.APPLIED, DevelopmentDemoCheckout.collect(code, mainOwner))
        assertEquals(DemoActionResult.ALREADY_USED, DevelopmentDemoCheckout.collect(code, mainOwner))
        assertEquals(DemoOrderState.DEMO_COLLECTED, DevelopmentDemoCheckout.orders.single().state)
        assertEquals("main-owner", DevelopmentDemoCheckout.orders.single().redeemedByUserId)
        assertNotNull(DevelopmentDemoCheckout.orders.single().redeemedAt)
    }

    @Test fun bulkCheckoutRequiresAgreedCurrentQuoteAndSellerScope() {
        val fixtureCustomer = AuthorizedIdentity("debug-customer", setOf(AppMode.Customer))
        val pending = DevelopmentDemoCheckout.previewBulk("DEMO-NC-WAITING", fixtureCustomer)
        assertNull(pending)
        val preview = DevelopmentDemoCheckout.previewBulk("DEMO-NC-UNPAID", fixtureCustomer)
        assertNotNull(preview)
        assertEquals(DemoActionResult.NOT_FOUND,
            DevelopmentDemoCheckout.simulatePayment("DEMO-NC-UNPAID", DemoPaymentOutcome.SUCCESS, otherCustomer))
        assertEquals(DemoActionResult.APPLIED,
            DevelopmentDemoCheckout.simulatePayment("DEMO-NC-UNPAID", DemoPaymentOutcome.SUCCESS, fixtureCustomer))
        val code = requireNotNull(DevelopmentDemoCheckout.orders.single().pickupCode)
        assertEquals(DemoActionResult.UNAUTHORIZED, DevelopmentDemoCheckout.verifyCode(code, mainOwner).first)
        assertEquals(DemoActionResult.UNAUTHORIZED, DevelopmentDemoCheckout.verifyCode(code, northStaff).first)
        assertEquals(DemoActionResult.APPLIED, DevelopmentDemoCheckout.collect(code, northOwner))
    }

    @Test fun withdrawnOrAlreadyConfirmedBulkQuoteCannotBeSimulatedAgain() {
        val fixtureCustomer = AuthorizedIdentity("debug-customer", setOf(AppMode.Customer))
        assertEquals(DemoTransitionResult.INVALID_STATE,
            DevelopmentSellerOperations.withdraw("DEMO-NC-UNPAID", fixtureCustomer))
        assertEquals(DemoActionResult.APPLIED,
            DevelopmentDemoCheckout.simulatePayment("DEMO-NC-UNPAID", DemoPaymentOutcome.SUCCESS, fixtureCustomer))
        assertEquals(DemoTransitionResult.INVALID_STATE,
            DevelopmentSellerOperations.withdraw("DEMO-NC-UNPAID", fixtureCustomer))
        assertEquals(DemoActionResult.ALREADY_USED,
            DevelopmentDemoCheckout.simulatePayment("DEMO-NC-UNPAID", DemoPaymentOutcome.SUCCESS, fixtureCustomer))
    }
}
