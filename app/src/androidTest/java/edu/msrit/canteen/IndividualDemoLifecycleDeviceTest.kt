package edu.msrit.canteen

import androidx.test.platform.app.InstrumentationRegistry
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import edu.msrit.canteen.customer.DebugCommerceApi
import edu.msrit.canteen.customer.IndividualLine
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

/** Local loopback check. Payment simulation is intentionally cloud_demo-only. */
class IndividualDemoLifecycleDeviceTest {
    @Test fun savedOrderIsSellerVisibleButLocalPaymentSimulationIsDenied() = runBlocking(Dispatchers.IO) {
        assumeFalse("Never mutate the hosted backend from instrumentation", BuildConfig.RIT_CLOUD_DEMO)
        DevelopmentDemoAccounts.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        DevelopmentDemoAccounts.signOut()
        try {
            val customer = DevelopmentDemoAccounts.signIn("student.arya@demo.ritcanteen.test", "RitDemo!Student01")
            assertTrue(customer.error ?: "Customer sign-in failed", customer.account != null)
            val starting = DebugCommerceApi.individualCart()
            assertTrue(starting.error ?: "Cart read failed", starting.value != null)
            val updated = DebugCommerceApi.replaceIndividualCart(starting.value!!.version,
                listOf(IndividualLine("masala-dosa", 1)))
            assertTrue(updated.error ?: "Cart update failed", updated.value != null)
            val placed = DebugCommerceApi.checkoutIndividualCart(updated.value!!.version, UUID.randomUUID().toString())
            assertTrue(placed.error ?: "Checkout failed", placed.value != null)
            val id = placed.value!!.id
            val simulated = DebugCommerceApi.simulateIndividualPayment(id, "SUCCESS")
            assertEquals(null, simulated.value)
            DevelopmentDemoAccounts.signOut()

            val otherSeller = DevelopmentDemoAccounts.signIn("owner.secondary@demo.ritcanteen.test", "RitDemo!OwnerOther01")
            assertTrue(otherSeller.error ?: "Other seller sign-in failed", otherSeller.account != null)
            val wrongQueue = DebugCommerceApi.sellerIndividualOrders("north-counter")
            assertFalse(wrongQueue.value.orEmpty().any { it.id == id })
            DevelopmentDemoAccounts.signOut()

            val owner = DevelopmentDemoAccounts.signIn("owner.main@demo.ritcanteen.test", "RitDemo!OwnerMain01")
            assertTrue(owner.error ?: "Main owner sign-in failed", owner.account != null)
            val queue = DebugCommerceApi.sellerIndividualOrders("main-canteen")
            assertTrue(queue.error ?: "Seller queue failed", queue.value.orEmpty().any { it.id == id })
            DevelopmentDemoAccounts.signOut()

            val returning = DevelopmentDemoAccounts.signIn("student.arya@demo.ritcanteen.test", "RitDemo!Student01")
            assertTrue(returning.error ?: "Customer return sign-in failed", returning.account != null)
            val history = DebugCommerceApi.individualOrders()
            assertTrue(history.error ?: "Order history failed",
                history.value.orEmpty().any { it.id == id && it.status == "DEMO_SAVED" &&
                    it.demoPaymentOutcome == "NOT_ATTEMPTED" })
        } finally { DevelopmentDemoAccounts.signOut() }
    }
}
