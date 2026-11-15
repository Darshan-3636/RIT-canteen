package edu.msrit.canteen

import androidx.test.platform.app.InstrumentationRegistry
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import edu.msrit.canteen.customer.DevelopmentSellerBusiness
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import org.junit.rules.ExternalResource

/** Clears only the debug account session before MainActivity is created; test data remains intact. */
class ResetDemoBeforeLaunchRule : ExternalResource() {
    override fun before() {
        DevelopmentDemoAccounts.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        DevelopmentPreviewAccess.reset()
        DevelopmentSellerBusiness.reset()
    }
}
