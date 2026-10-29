package edu.msrit.canteen.customer

import edu.msrit.canteen.BuildConfig
import edu.msrit.canteen.navigation.AppMode
import edu.msrit.canteen.navigation.AuthorizedIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class CloudCheckoutBoundaryTest {
    @Test fun cloudBuildCannotSimulatePaymentOrIssuePickupCode() {
        assumeTrue(BuildConfig.RIT_CLOUD_DEMO)
        val customer = AuthorizedIdentity("customer", setOf(AppMode.Customer))
        assertNull(DevelopmentDemoCheckout.previewBulk("some-request", customer))
        assertEquals(DemoActionResult.INVALID_STATE,
            DevelopmentDemoCheckout.simulatePayment("some-request", DemoPaymentOutcome.SUCCESS, customer))
        assertEquals(DemoActionResult.INVALID_STATE, DevelopmentDemoCheckout.verifyCode("AAAA-BBBB-CCCC-DDDD", customer).first)
        assertEquals(DemoActionResult.INVALID_STATE, DevelopmentDemoCheckout.collect("AAAA-BBBB-CCCC-DDDD", customer))
    }
}
