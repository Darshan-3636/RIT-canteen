package edu.msrit.canteen

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import edu.msrit.canteen.customer.DevelopmentSellerOperations
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Prompt6SellerUiTest {
    @get:Rule val rule = createAndroidComposeRule<DebugTestingActivity>()

    @Before fun resetPreview() = rule.runOnUiThread {
        DevelopmentPreviewAccess.reset()
        DevelopmentSellerOperations.reset()
    }

    private fun hub(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun ownerCanBrowseInboxDetailCounterAndCompare() {
        hub("debug_role_sellerowner")
        hub("debug_seller_dashboard")
        rule.onNodeWithTag("seller_overview").assertExists()
        rule.onNodeWithTag("seller_overview_list").performScrollToNode(hasTestTag("seller_open_inbox"))
        rule.onNodeWithTag("seller_open_inbox").performClick()
        rule.onNodeWithTag("seller_inbox").assertExists()
        rule.onNodeWithTag("seller_inbox_list").performScrollToNode(hasTestTag("seller_request_DEMO-NC-CUSTOMER"))
        rule.onNodeWithTag("seller_request_DEMO-NC-CUSTOMER").performClick()
        rule.onNodeWithTag("seller_request_detail").assertExists()
        rule.onNodeWithTag("seller_detail_compare").performScrollTo().performClick()
        rule.onNodeWithTag("seller_revision_compare").assertExists()
        back()
        rule.onNodeWithTag("seller_detail_counter").performScrollTo().performClick()
        rule.onNodeWithTag("demo_quote_editor").assertExists()
        rule.onNodeWithTag("demo_quote_submit").assertIsNotEnabled()
        rule.onNodeWithTag("demo_quote_editor_list").performScrollToNode(hasTestTag("demo_quote_plus_north-lunch"))
        rule.onNodeWithTag("demo_quote_plus_north-lunch").performClick()
        rule.onNodeWithTag("demo_quote_submit").assertIsEnabled()
        rule.onNodeWithTag("demo_quote_submit").performClick()
        rule.onNodeWithTag("seller_request_detail").assertExists()
        back()
        back()
        back()
        hub("debug_customer_scenarios")
        rule.onNodeWithTag("customer_demo_requests_list").performScrollToNode(hasTestTag("seller_request_DEMO-NC-CUSTOMER"))
        rule.onNodeWithTag("seller_request_DEMO-NC-CUSTOMER").performClick()
        rule.onNodeWithTag("customer_demo_request").assertExists()
    }

    @Test fun staffIsViewOnlyAndSpecialNeedsExplicitApproval() {
        hub("debug_role_sellerstaff")
        hub("debug_seller_request")
        rule.onNodeWithTag("seller_request_detail").assertExists()
        rule.onNodeWithTag("seller_detail_counter").assertDoesNotExist()
        rule.onNodeWithTag("seller_detail_approve").assertDoesNotExist()
        back()
        hub("debug_role_sellermanager")
        hub("debug_seller_special")
        rule.onNodeWithTag("seller_special_pickup").assertExists()
        rule.onNodeWithTag("seller_special_accept").assertIsEnabled().performClick()
        back()
        hub("debug_seller_approval")
        rule.onNodeWithTag("seller_final_approval").assertExists()
    }

    @Test fun scheduleHistoryAndCustomerScenarioRoutesOpen() {
        hub("debug_role_sellerowner")
        hub("debug_seller_schedule")
        rule.onNodeWithTag("seller_schedule").assertExists()
        rule.onNodeWithTag("seller_schedule_filters").performScrollToNode(hasTestTag("seller_schedule_filter_paid"))
        rule.onNodeWithTag("seller_schedule_filter_paid").performClick()
        rule.onNodeWithTag("seller_request_DEMO-NC-PAID").assertExists()
        rule.onNodeWithTag("seller_request_DEMO-NC-NEW").assertDoesNotExist()
        back()
        hub("debug_seller_history")
        rule.onNodeWithTag("seller_history").assertExists()
        back()
        hub("debug_customer_scenarios")
        rule.onNodeWithTag("customer_demo_requests").assertExists()
        rule.onNodeWithTag("customer_demo_requests_list").performScrollToNode(hasTestTag("seller_request_DEMO-NC-WAITING"))
        rule.onNodeWithTag("seller_request_DEMO-NC-WAITING").performClick()
        rule.onNodeWithTag("customer_demo_request").assertExists()
        rule.onNodeWithTag("customer_demo_approve").performScrollTo().assertIsEnabled()
    }
}
