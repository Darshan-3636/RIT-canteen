package edu.msrit.canteen.presentation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.msrit.canteen.customer.PreviewCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PresentationStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun databaseName() = "presentation_test_${UUID.randomUUID()}.db"

    @Test fun customerAccountsCartAndOrderPersistAcrossReopenWithoutDuplication() {
        val name = databaseName()
        try {
            val store = PresentationStore(context, name)
            val email = "customer-${UUID.randomUUID()}@example.test"
            val account = store.register("Asha Rao", email, "strongPassword42", LocalRole.CUSTOMER, null)
            assertEquals(account.id, store.activeAccount()?.id)
            store.logout()
            assertEquals(null, store.activeAccount())
            assertTrue(runCatching { store.login(email, "wrongPassword42") }.isFailure)
            assertEquals(account.id, store.login(email, "strongPassword42").id)
            store.setQuantity(account.id, "masala-dosa", 3)
            store.setQuantity(account.id, "filter-coffee", 2)
            val expected = 3 * 50 * 100L + 2 * 30 * 100L
            assertEquals(expected, store.cart(account.id).sumOf { it.item.price.rupees * it.quantity * 100L })
            assertTrue(runCatching { store.setQuantity(account.id, "veg-samosa", 1) }.isFailure)
            val key = UUID.randomUUID().toString()
            val order = store.placeUnpaidOrder(account.id, key)
            assertEquals(expected, order.totalPaise)
            assertEquals("PENDING", order.paymentState)
            assertEquals("AWAITING_PAYMENT", order.fulfillmentState)
            assertEquals(order.id, store.placeUnpaidOrder(account.id, key).id)
            assertTrue(store.cart(account.id).isEmpty())
            val seller = store.register("Main Canteen Owner", "seller-${UUID.randomUUID()}@example.test",
                "strongPassword42", LocalRole.SELLER, PreviewCatalog.MAIN_ID)
            assertTrue(store.sellerOrders(seller).any { it.id == order.id && it.fulfillmentState == "AWAITING_PAYMENT" })
            assertFalse(store.sellerOrders(seller).any { it.sellerId != PreviewCatalog.MAIN_ID })
            store.logout()
            store.close()
            val reopened = PresentationStore(context, name)
            assertEquals(account.id, reopened.login(email, "strongPassword42").id)
            assertEquals(order.id, reopened.customerOrders(account.id).single().id)
            assertEquals(2, reopened.orderLines(order.id).size)
            assertEquals(order.pickupCode, reopened.order(order.id)?.pickupCode)
            reopened.close()
        } finally { context.deleteDatabase(name) }
    }

    @Test fun sellerScopeAndPickupLifecycleRejectWrongCodeAndDuplicateTransitions() {
        val name = databaseName()
        try {
            val store = PresentationStore(context, name)
            val main = store.register("Main Canteen Owner", "main-${UUID.randomUUID()}@example.test",
                "strongPassword42", LocalRole.SELLER, PreviewCatalog.MAIN_ID)
            assertEquals(PreviewCatalog.MAIN_ID, main.sellerId)
            val mainOrders = store.sellerOrders(main)
            assertTrue(mainOrders.isNotEmpty())
            assertTrue(mainOrders.all { it.sellerId == PreviewCatalog.MAIN_ID })
            val northOwner = store.register("North Counter Owner", "north-${UUID.randomUUID()}@example.test",
                "strongPassword42", LocalRole.SELLER, "north-counter")
            val north = store.sellerOrders(northOwner)
            assertTrue(north.isNotEmpty())
            assertTrue(north.none { it.id in mainOrders.map(LocalOrder::id) })
            val notReady = mainOrders.first { it.fulfillmentState == "NOT_READY" }
            assertFalse(store.markReady(northOwner, notReady.id))
            assertTrue(store.markReady(main, notReady.id))
            assertFalse(store.markReady(main, notReady.id))
            assertFalse(store.complete(main, notReady.id, "WRONG-CODE"))
            assertEquals("READY", store.order(notReady.id)?.fulfillmentState)
            assertTrue(store.complete(main, notReady.id, notReady.pickupCode.lowercase()))
            assertFalse(store.complete(main, notReady.id, notReady.pickupCode))
            store.close()
            val reopened = PresentationStore(context, name)
            assertEquals("COMPLETED", reopened.order(notReady.id)?.fulfillmentState)
            assertNotNull(reopened.sellerOrders(main).firstOrNull { it.id == notReady.id })
            reopened.close()
        } finally { context.deleteDatabase(name) }
    }
}
