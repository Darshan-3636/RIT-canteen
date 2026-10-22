package edu.msrit.canteen.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CustomerAccountModelsTest {
    @Test fun profileRequiresNameButNotDepartment() {
        assertNotNull(validateCustomerProfile(CustomerProfile(" ", "", CustomerAffiliation.STUDENT)))
        assertNull(validateCustomerProfile(CustomerProfile("Asha Rao", "", CustomerAffiliation.FACULTY)))
    }

    @Test fun optionalPersonalizationStartsOff() {
        val prefs = CustomerPreferences()
        assertEquals(false, prefs.personalizationConsent)
        assertEquals(false, prefs.promotionalAlerts)
        assertEquals(true, prefs.operationalAlerts)
    }

    @Test fun ticketValidationRequiresCategoryAndMeaningfulContent() {
        assertNotNull(validateCustomerTicket(null, "Order issue", "An item was missing"))
        assertNotNull(validateCustomerTicket(CustomerIssueCategory.FOOD, "Hi", "An item was missing"))
        assertNotNull(validateCustomerTicket(CustomerIssueCategory.FOOD, "Order issue", "short"))
        assertNull(validateCustomerTicket(CustomerIssueCategory.FOOD, "Order issue", "An item was missing"))
        assertEquals("Platform support", CustomerIssueCategory.PAYMENT.routedTo)
    }

    @Test fun templateRequiresSingleSellerDraftAndItems() {
        assertNotNull(validateBulkTemplate("Club snacks", null, listOf(BulkLine("dosa", 8))))
        assertNotNull(validateBulkTemplate("Club snacks", "seller-a", emptyList()))
        assertNotNull(validateBulkTemplate("Club snacks", "seller-a", listOf(BulkLine("dosa", 0))))
        assertNull(validateBulkTemplate("Club snacks", "seller-a", listOf(BulkLine("dosa", 8))))
    }
}
