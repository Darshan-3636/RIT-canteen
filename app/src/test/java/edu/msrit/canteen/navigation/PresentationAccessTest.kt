package edu.msrit.canteen.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationAccessTest {
    @Test
    fun sellerMembershipAndCapabilityMustMatchTheSameSeller() {
        val identity = AuthorizedIdentity(
            userId = "user-1",
            modes = setOf(AppMode.Customer, AppMode.SellerManager("seller-a")),
            capabilitiesBySeller = mapOf("seller-a" to setOf("catalog.edit")),
        )

        assertTrue(PresentationAccess.allows(identity, ProtectedArea.Customer))
        assertTrue(PresentationAccess.allows(identity, ProtectedArea.Seller("seller-a", "catalog.edit")))
        assertFalse(PresentationAccess.allows(identity, ProtectedArea.Seller("seller-a", "reports.export")))
        assertFalse(PresentationAccess.allows(identity, ProtectedArea.Seller("seller-b", "catalog.edit")))
        assertFalse(PresentationAccess.allows(identity, ProtectedArea.Administrator))
    }

    @Test
    fun unauthenticatedPresentationCannotOpenProtectedAreas() {
        assertFalse(PresentationAccess.allows(null, ProtectedArea.Customer))
        assertFalse(PresentationAccess.allows(null, ProtectedArea.Seller("seller-a", "bulk.respond")))
        assertFalse(PresentationAccess.allows(null, ProtectedArea.Administrator))
    }
}
