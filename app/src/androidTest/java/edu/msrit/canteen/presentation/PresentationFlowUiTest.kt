package edu.msrit.canteen.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.msrit.canteen.ui.theme.RitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PresentationFlowUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customerCanBrowseCartAndSeeAccurateUnpaidOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PresentationStore(context)
        val email = "flow-${UUID.randomUUID()}@example.test"
        store.register("Priya Sharma", email, "strongPassword42", LocalRole.CUSTOMER, null)
        store.logout()
        store.close()
        compose.setContent { RitTheme { PresentationApp() } }
        compose.onNodeWithTag("presentation_email").performTextInput(email)
        compose.onNodeWithTag("presentation_password").performTextInput("strongPassword42")
        compose.onNodeWithTag("presentation_auth_submit").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("presentation_seller_main-canteen").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("presentation_seller_main-canteen").performClick()
        compose.onNodeWithText("Masala Dosa").performClick()
        compose.onNodeWithTag("presentation_add_cart").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("presentation_view_cart").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("presentation_view_cart").performClick()
        compose.onNodeWithTag("presentation_pay_now").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Payment has not completed. This order has not been sent to the canteen.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Awaiting payment").assertExists()
    }
}
