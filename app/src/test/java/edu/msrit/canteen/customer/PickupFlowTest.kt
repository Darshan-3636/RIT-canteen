package edu.msrit.canteen.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class PickupFlowTest {
    private fun order(status: String, id: String = status) =
        IndividualOrderPreview(id, LocalDateTime.of(2026, 11, 13, 12, 0), emptyList(), 100, true, status)

    @Test fun onlyPaidOrdersReachTheSeller() {
        assertNull(sellerOrderTab("DEMO_SAVED"))
        assertNull(sellerOrderTab("DEMO_PAYMENT_PENDING"))
        assertNull(sellerOrderTab("DEMO_PAYMENT_FAILED"))
        assertNull(sellerOrderTab("DEMO_REJECTED"))
        assertEquals(SellerOrderTab.NEW, sellerOrderTab("DEMO_PAYMENT_SIMULATED"))
        assertEquals(SellerOrderTab.NEW, sellerOrderTab("DEMO_ACCEPTED"))
        assertEquals(SellerOrderTab.READY, sellerOrderTab("DEMO_READY"))
        assertEquals(SellerOrderTab.DONE, sellerOrderTab("DEMO_COMPLETED"))
    }

    @Test fun ordersAreGroupedByTabOldestFirst() {
        val newer = order("DEMO_PAYMENT_SIMULATED", "b").copy(placedAt = LocalDateTime.of(2026, 11, 13, 12, 5))
        val older = order("DEMO_PAYMENT_SIMULATED", "a")
        val grouped = groupSellerOrders(listOf(newer, order("DEMO_SAVED"), older, order("DEMO_READY")))
        assertEquals(listOf("a", "b"), grouped.getValue(SellerOrderTab.NEW).map { it.id })
        assertEquals(1, grouped.getValue(SellerOrderTab.READY).size)
        assertEquals(0, grouped.getValue(SellerOrderTab.DONE).size)
    }

    @Test fun scannedPickupQrYieldsPin() {
        assertEquals("0427", pickupPinFromScan(pickupQrPayload("0427")))
        assertEquals("0427", pickupPinFromScan(" 0427 "))
        assertNull(pickupPinFromScan("RITPICKUP:12"))
        assertNull(pickupPinFromScan("https://example.com"))
        assertNull(pickupPinFromScan(null))
    }

    @Test fun customerStatusUsesPlainWords() {
        assertEquals("Waiting for payment", customerOrderStatus("DEMO_SAVED"))
        assertEquals("Preparing", customerOrderStatus("DEMO_PAYMENT_SIMULATED"))
        assertEquals("Preparing", customerOrderStatus("DEMO_ACCEPTED"))
        assertEquals("Ready for pickup", customerOrderStatus("DEMO_READY"))
        assertEquals("Collected", customerOrderStatus("DEMO_COMPLETED"))
    }
}
