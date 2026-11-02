package edu.msrit.canteen.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndividualOrderingTest {
    private fun item(id: String) = PreviewCatalog.items.first { it.id == id }

    @Test fun onePreparedItemIsValidWithoutBulkMinimum() {
        val cart = IndividualCartSession()
        assertTrue(cart.add(item("masala-dosa")))
        val checked = checkIndividualCart(cart.draft, PreviewCatalog)
        assertTrue(checked.readyForReview)
        assertEquals(1, checked.units)
        assertEquals(5_000L, checked.subtotalPaise)
    }

    @Test fun manyItemsAndQuantityEditsUpdateTotalImmediately() {
        val cart = IndividualCartSession()
        assertTrue(cart.setQuantity(item("masala-dosa"), 2))
        assertTrue(cart.setQuantity(item("filter-coffee"), 1))
        assertEquals(3, checkIndividualCart(cart.draft, PreviewCatalog).units)
        assertEquals(13_000L, checkIndividualCart(cart.draft, PreviewCatalog).subtotalPaise)
        assertTrue(cart.setQuantity(item("masala-dosa"), 0))
        assertEquals(3_000L, checkIndividualCart(cart.draft, PreviewCatalog).subtotalPaise)
    }

    @Test fun packagedAndSecondarySellerItemsAreRejected() {
        val cart = IndividualCartSession()
        assertFalse(cart.add(item("water-bottle")))
        assertFalse(cart.add(item("north-lunch")))
        assertFalse(cart.add(item("lemon-rice")))
        assertTrue(cart.draft.lines.isEmpty())
    }

    @Test fun individualAndBulkDraftsNeverConvertOrClearEachOther() {
        val individual = IndividualCartSession()
        val bulk = BulkDraftSession()
        individual.add(item("masala-dosa"))
        assertTrue(bulk.selectSeller("north-counter"))
        assertTrue(bulk.setQuantity(item("north-lunch"), 10))
        bulk.clear()
        assertEquals(1, individual.draft.totalUnits)
        assertTrue(bulk.draft.lines.isEmpty())
    }

    @Test fun serverCartSnapshotReplacesLocalDraftAndRotatesCheckoutKeyOnVersionChange() {
        val cart = IndividualCartSession()
        cart.add(item("filter-coffee"))
        val first = IndividualServerCart(4, 10_000L, true,
            listOf(IndividualServerCartLine("masala-dosa", 2, 5_000L, true)))
        cart.applyServer(first)
        assertEquals(listOf(IndividualLine("masala-dosa", 2)), cart.draft.lines)
        assertEquals(10_000L, cart.serverCart?.subtotalPaise)
        val key = cart.checkoutIdempotencyKey()
        cart.applyServer(first)
        assertEquals(key, cart.checkoutIdempotencyKey())
        cart.applyServer(first.copy(version = 5, readyForCheckout = false))
        assertFalse(cart.serverCart!!.readyForCheckout)
        assertFalse(key == cart.checkoutIdempotencyKey())
        cart.clear()
        assertTrue(cart.draft.lines.isEmpty())
        assertEquals(null, cart.serverCart)
    }
}
