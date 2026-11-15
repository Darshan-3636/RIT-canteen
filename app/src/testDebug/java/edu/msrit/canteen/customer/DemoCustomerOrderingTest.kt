package edu.msrit.canteen.customer

import edu.msrit.canteen.BuildConfig
import org.junit.Assume.assumeFalse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DemoCustomerOrderingTest {
    private fun item(id: String) = PreviewCatalog.items.first { it.id == id }
    @Before fun reset() { DevelopmentIndividualOrders.reset(); DevelopmentCustomerAccount.reset() }
    @After fun finish() { DevelopmentIndividualOrders.reset(); DevelopmentCustomerAccount.reset() }

    @Test fun savedIndividualPreviewIsClearlyLocalAndResettable() {
        val cart = IndividualCartSession()
        cart.add(item("masala-dosa")); cart.add(item("filter-coffee"))
        assertTrue(DevelopmentIndividualOrders.saveLocal(cart.draft))
        assertTrue(DevelopmentIndividualOrders.records.first().locallySaved)
        assertEquals(8_000L, DevelopmentIndividualOrders.records.first().totalPaise)
        DevelopmentIndividualOrders.reset()
        assertFalse(DevelopmentIndividualOrders.records.any { it.locallySaved })
    }

    @Test fun bulkTemplateCanBeCreatedAndEditedWithoutActiveCartOrMinimum() {
        assumeFalse("Cloud templates use the server API", BuildConfig.RIT_CLOUD_DEMO)
        val editor = BulkDraftSession()
        assertTrue(editor.selectSeller("north-counter"))
        assertTrue(editor.setQuantity(item("north-snack"), 2))
        assertTrue(DevelopmentCustomerAccount.saveTemplate("Club snacks", editor.draft, "debug-customer"))
        val saved = DevelopmentCustomerAccount.templates.single()
        assertEquals(2, saved.lines.sumOf { it.quantity })
        assertTrue(editor.setQuantity(item("north-snack"), 4))
        assertTrue(DevelopmentCustomerAccount.updateTemplate(saved.id, "Revised snacks", editor.draft, "debug-customer"))
        assertEquals(4, DevelopmentCustomerAccount.templates.single().lines.single().quantity)
        assertFalse(DevelopmentCustomerAccount.updateTemplate(saved.id, "Wrong account", editor.draft, "other"))
    }
}
