package edu.msrit.canteen.customer

import edu.msrit.canteen.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class CloudCustomerBoundaryTest {
    @Test fun cloudModeNeverInventsOperatingHoursOrSampleQuotes() {
        assumeTrue(BuildConfig.RIT_CLOUD_DEMO)
        DevelopmentSellerBusiness.clearServerPolicies()
        assertNull(DevelopmentBulkPreview.forSeller(PreviewCatalog.MAIN_ID))
        assertNull(DevelopmentBulkPreview.sample(QuoteStage.COUNTEROFFER))
    }

    @Test fun cloudModeNeverAcceptsFixtureOnlyCustomerWrites() {
        assumeTrue(BuildConfig.RIT_CLOUD_DEMO)
        val repo = DevelopmentCustomerAccount
        repo.activate("server-customer", CustomerProfile("Server Customer", "server@example.test", CustomerAffiliation.STUDENT))
        assertFalse(repo.updatePreferences(CustomerPreferences(promotionalAlerts = true), "server-customer"))
        assertFalse(repo.saveTemplate("Lunch", BulkDraft("main-canteen", listOf(BulkLine("plain-dosa", 8))), "server-customer"))
        assertNull(repo.createTicket(CustomerIssueCategory.FOOD, "Missing item", "An item was missing", null, "server-customer"))
        repo.deactivate()
    }
}
