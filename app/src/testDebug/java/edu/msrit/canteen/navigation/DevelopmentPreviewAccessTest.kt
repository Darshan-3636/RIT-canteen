package edu.msrit.canteen.navigation

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevelopmentPreviewAccessTest {
    @After fun reset() = DevelopmentPreviewAccess.reset()

    @Test fun localRoleSelectionAloneNeverGrantsRuntimeAccess() {
        DevelopmentRole.entries.forEach { role ->
            DevelopmentPreviewAccess.select(role)
            assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Customer))
            assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Administrator))
            assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Seller("north-counter", "catalog.edit")))
        }
    }

    @Test fun resetRevokesPreviewAccess() {
        DevelopmentPreviewAccess.select(DevelopmentRole.Customer)
        DevelopmentPreviewAccess.reset()
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Customer))
    }
}
