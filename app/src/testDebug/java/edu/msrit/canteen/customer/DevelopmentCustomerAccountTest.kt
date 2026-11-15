package edu.msrit.canteen.customer

import edu.msrit.canteen.BuildConfig
import org.junit.Assume.assumeFalse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DevelopmentCustomerAccountTest {
    private val repo = DevelopmentCustomerAccount
    @Before fun before() {
        assumeFalse("Local fixture store is disabled in cloud mode", BuildConfig.RIT_CLOUD_DEMO)
        repo.reset()
    }
    @After fun after() { if (!BuildConfig.RIT_CLOUD_DEMO) repo.reset() }

    @Test fun profileAndPreferencesAreCustomerScoped() {
        val updated = CustomerProfile("Asha Rao", "demo@example.invalid", CustomerAffiliation.FACULTY, "ECE")
        assertFalse(repo.updateProfile(updated, "debug-owner"))
        assertTrue(repo.updateProfile(updated, "debug-customer"))
        assertEquals("Asha Rao", repo.profile?.fullName)
        assertFalse(repo.updatePreferences(CustomerPreferences(personalizationConsent = true), "debug-admin"))
        assertTrue(repo.updatePreferences(CustomerPreferences(personalizationConsent = true), "debug-customer"))
        assertTrue(repo.preferences.personalizationConsent)
    }

    @Test fun supportIsLocalAndAccessChecked() {
        assertNull(repo.createTicket(CustomerIssueCategory.PAYMENT, "Payment issue", "Payment was not verified",
            null, "debug-owner"))
        val ticket = repo.createTicket(CustomerIssueCategory.FOOD, "Missing item", "One item was missing",
            "DEMO-NC-PAID", "debug-customer")!!
        assertTrue(ticket.localOnly)
        assertFalse(repo.replyToTicket(ticket.id, "Please check", "debug-owner"))
        assertTrue(repo.replyToTicket(ticket.id, "Please check", "debug-customer"))
        assertTrue(repo.escalateTicket(ticket.id, "debug-customer"))
        assertTrue(repo.tickets.single().escalated)
        assertTrue(repo.tickets.single().replies.single().contains("local preview"))
    }

    @Test fun templatesAreSellerScopedAndResettable() {
        val draft = BulkDraft(sellerId = "north-counter", lines = listOf(BulkLine("north-lunch", 8)))
        assertFalse(repo.saveTemplate("Club lunch", draft, "debug-staff"))
        assertTrue(repo.saveTemplate("Club lunch", draft, "debug-customer"))
        assertEquals("north-counter", repo.templates.single().sellerId)
        assertFalse(repo.removeTemplate(repo.templates.single().id, "debug-owner"))
        repo.reset()
        assertTrue(repo.templates.isEmpty())
        assertTrue(repo.tickets.isEmpty())
        assertFalse(repo.preferences.personalizationConsent)
    }
}
