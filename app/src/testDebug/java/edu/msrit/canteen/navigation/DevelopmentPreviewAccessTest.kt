package edu.msrit.canteen.navigation

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopmentPreviewAccessTest {
    @After fun reset() = DevelopmentPreviewAccess.reset()

    @Test fun eachMockIdentityHasOnlyItsSimulatedScope() {
        DevelopmentPreviewAccess.select(DevelopmentRole.Customer)
        assertTrue(DevelopmentPreviewAccess.allows(ProtectedArea.Customer))
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Administrator))

        DevelopmentPreviewAccess.select(DevelopmentRole.SellerOwner)
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Customer))
        assertTrue(DevelopmentPreviewAccess.allows(ProtectedArea.Seller("north-counter", "reports.export")))

        DevelopmentPreviewAccess.select(DevelopmentRole.SellerManager)
        assertTrue(DevelopmentPreviewAccess.allows(ProtectedArea.Seller("north-counter", "catalog.edit")))
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Seller("north-counter", "reports.export")))

        DevelopmentPreviewAccess.select(DevelopmentRole.SellerStaff)
        assertTrue(DevelopmentPreviewAccess.allows(ProtectedArea.Seller("north-counter", "bulk.view")))
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Seller("north-counter", "bulk.respond")))
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Seller("north-counter", "catalog.edit")))

        DevelopmentPreviewAccess.select(DevelopmentRole.PlatformAdministrator)
        assertTrue(DevelopmentPreviewAccess.allows(ProtectedArea.Administrator))
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Customer))
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Seller("north-counter", "catalog.edit")))
    }

    @Test fun resetRevokesPreviewAccess() {
        DevelopmentPreviewAccess.select(DevelopmentRole.Customer)
        DevelopmentPreviewAccess.reset()
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Customer))
    }
}
