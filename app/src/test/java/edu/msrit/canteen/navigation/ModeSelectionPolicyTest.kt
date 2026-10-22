package edu.msrit.canteen.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeSelectionPolicyTest {
    @Test fun unauthenticatedPreviewGrantsNoMode() {
        assertFalse(ModeSelectionPolicy.canEnterCustomer(null))
        assertFalse(ModeSelectionPolicy.canEnterSeller(null))
        assertFalse(ModeSelectionPolicy.canEnterAdmin(null))
    }

    @Test fun customerCannotSelfGrantSellerOrAdmin() {
        val customer = AuthorizedIdentity("user", setOf(AppMode.Customer))
        assertTrue(ModeSelectionPolicy.canEnterCustomer(customer))
        assertFalse(ModeSelectionPolicy.canEnterSeller(customer))
        assertFalse(ModeSelectionPolicy.canEnterAdmin(customer))
    }

    @Test fun sellerMembershipIsDistinctFromCustomerAndAdmin() {
        val manager = AuthorizedIdentity("manager", setOf(AppMode.SellerManager("stall-1")))
        assertTrue(ModeSelectionPolicy.canEnterSeller(manager))
        assertFalse(ModeSelectionPolicy.canEnterAdmin(manager))
    }

    @Test fun previewRoutesAreSeparateFromProductionRoutes() {
        assertTrue(Route.debugOnly.all { it.startsWith("debug/") })
        assertFalse(Route.Welcome in Route.debugOnly)
        assertFalse(Route.EmailEntry in Route.debugOnly)
        assertFalse(Route.Onboarding in Route.debugOnly)
    }
}
