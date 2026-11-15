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

    private fun bulk(stage: QuoteStage, id: String, pickup: LocalDateTime, sellerAccepted: Boolean = false) =
        BulkRequestRecord(QuoteThread("main-canteen", id, listOf(QuoteRevision(1, QuoteActor.CUSTOMER,
            listOf(QuoteLineSnapshot("idli", "Idli", 10, 3500)), pickup, false, 35000, "",
            LocalDateTime.of(2026, 11, 13, 9, 0))), stage, sellerAcceptedVersion = if (sellerAccepted) 1 else null),
            "c", "Group lead", BulkDraft("main-canteen"), 8, LocalDateTime.of(2026, 11, 13, 9, 0))

    @Test fun ordersAreGroupedByTabOldestFirst() {
        val newer = order("DEMO_PAYMENT_SIMULATED", "b").copy(placedAt = LocalDateTime.of(2026, 11, 13, 12, 5))
        val older = order("DEMO_PAYMENT_SIMULATED", "a")
        val grouped = groupSellerPickups(listOf(newer, order("DEMO_SAVED"), older, order("DEMO_READY")), emptyList())
        assertEquals(listOf("a", "b"), grouped.getValue(SellerOrderTab.NEW).map { it.id })
        assertEquals(1, grouped.getValue(SellerOrderTab.READY).size)
        assertEquals(0, grouped.getValue(SellerOrderTab.DONE).size)
    }

    @Test fun paidBulkOrdersJoinTheSameTabsInPickupTimeOrder() {
        val individual = order("DEMO_PAYMENT_SIMULATED", "ind")  // placed 12:00, due about 12:11
        val soon = bulk(QuoteStage.PAID, "bulk-soon", LocalDateTime.of(2026, 11, 13, 11, 30))
        val later = bulk(QuoteStage.PAID, "bulk-later", LocalDateTime.of(2026, 11, 14, 10, 0))
        val grouped = groupSellerPickups(listOf(individual), listOf(later, soon,
            bulk(QuoteStage.AGREED_AWAITING_PAYMENT, "unpaid", LocalDateTime.of(2026, 11, 13, 11, 0)),
            bulk(QuoteStage.READY, "ready", LocalDateTime.of(2026, 11, 13, 13, 0)),
            bulk(QuoteStage.COMPLETED, "done", LocalDateTime.of(2026, 11, 13, 8, 0))))
        assertEquals(listOf("bulk-soon", "ind", "bulk-later"), grouped.getValue(SellerOrderTab.NEW).map { it.id })
        assertEquals(PickupKind.BULK, grouped.getValue(SellerOrderTab.NEW).first().kind)
        assertEquals(listOf("ready"), grouped.getValue(SellerOrderTab.READY).map { it.id })
        assertEquals(listOf("done"), grouped.getValue(SellerOrderTab.DONE).map { it.id })
    }

    @Test fun customerBulkStatusSaysWhatHappensNext() {
        val pickup = LocalDateTime.of(2026, 11, 14, 10, 0)
        assertEquals("Waiting for the canteen", customerBulkStatus(bulk(QuoteStage.SUBMITTED, "a", pickup)))
        assertEquals("Canteen changed your order", customerBulkStatus(bulk(QuoteStage.COUNTEROFFER, "a", pickup, sellerAccepted = true)))
        assertEquals("Accepted · pay to confirm", customerBulkStatus(bulk(QuoteStage.AGREED_AWAITING_PAYMENT, "a", pickup)))
        assertEquals("Paid · being prepared", customerBulkStatus(bulk(QuoteStage.PAID, "a", pickup)))
        assertEquals("Ready for pickup", customerBulkStatus(bulk(QuoteStage.READY, "a", pickup)))
        assertEquals("Collected", customerBulkStatus(bulk(QuoteStage.COMPLETED, "a", pickup)))
        assertEquals("Declined by the canteen", customerBulkStatus(bulk(QuoteStage.REJECTED, "a", pickup)))
        assertEquals("Cancelled", customerBulkStatus(bulk(QuoteStage.WITHDRAWN, "a", pickup)))
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
