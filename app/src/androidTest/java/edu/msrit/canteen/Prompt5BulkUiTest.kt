package edu.msrit.canteen

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.DevelopmentBulkPreview
import edu.msrit.canteen.customer.PickupWindow
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.pickupWindow
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class Prompt5BulkUiTest {
    @get:Rule val rule = createAndroidComposeRule<DebugTestingActivity>()

    @Before fun resetRole() = rule.runOnUiThread { DevelopmentPreviewAccess.reset() }

    private fun openHubLink(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun combinedMinimumPickupAndReviewStayLocal() {
        openHubLink("debug_bulk_cart")
        rule.onNodeWithTag("bulk_cart").assertExists()
        rule.onNodeWithTag("bulk_continue_pickup").assertIsNotEnabled()
        repeat(3) {
            rule.onNodeWithTag("bulk_cart_list").performScrollToNode(hasTestTag("bulk_cart_item_masala-dosa"))
            rule.onNodeWithTag("bulk_plus_masala-dosa").performClick()
        }
        repeat(5) {
            rule.onNodeWithTag("bulk_cart_list").performScrollToNode(hasTestTag("bulk_cart_item_water-bottle"))
            rule.onNodeWithTag("bulk_plus_water-bottle").performClick()
        }
        rule.onNodeWithTag("bulk_continue_pickup").assertIsEnabled().performClick()
        rule.onNodeWithTag("bulk_pickup").assertExists()
        val now = LocalDateTime.now(RIT_TIME_ZONE)
        val date = listOf(1L, 2L, 4L).map { now.toLocalDate().plusDays(it) }.first {
            pickupWindow(DevelopmentBulkPreview.forSeller(PreviewCatalog.MAIN_ID),
                LocalDateTime.of(it, LocalTime.of(11, 30)), now) == PickupWindow.REGULAR
        }
        rule.onNodeWithTag("bulk_pickup_list").performScrollToNode(hasTestTag("bulk_date_choices"))
        rule.onNodeWithTag("bulk_date_choices").performScrollToNode(hasTestTag("bulk_date_$date"))
        rule.onNodeWithTag("bulk_date_$date").performClick()
        rule.onNodeWithTag("bulk_continue_review").assertIsEnabled().performClick()
        rule.onNodeWithTag("bulk_review").assertExists()
        rule.onNodeWithTag("bulk_submit_disabled").assertIsNotEnabled()
    }

    @Test fun sellerOverrideAndCrossSellerSwitchRequireClearingDraft() {
        openHubLink("debug_bulk_override")
        repeat(8) {
            rule.onNodeWithTag("bulk_cart_list").performScrollToNode(hasTestTag("bulk_cart_item_north-lunch"))
            rule.onNodeWithTag("bulk_plus_north-lunch").performClick()
        }
        rule.onNodeWithTag("bulk_continue_pickup").assertIsNotEnabled()
        rule.onNodeWithTag("bulk_switch_seller").performScrollTo().performClick()
        rule.onNodeWithTag("bulk_seller_list").performScrollToNode(hasTestTag("bulk_choose_campus-pantry"))
        rule.onNodeWithTag("bulk_choose_campus-pantry").performClick()
        rule.onNodeWithTag("cart_confirmation").assertExists()
        rule.onNodeWithTag("cart_keep").performClick()
        rule.onNodeWithTag("cart_confirmation").assertDoesNotExist()
        rule.onNodeWithTag("bulk_choose_campus-pantry").performClick()
        rule.onNodeWithTag("cart_clear_confirm").performClick()
        rule.onNodeWithTag("bulk_cart").assertExists()
        rule.onNodeWithTag("bulk_continue_pickup").assertIsNotEnabled()
    }

    @Test fun closedDateNeedsExplicitSpecialRequestAndQuotePaymentRemainsBlocked() {
        openHubLink("debug_bulk_pickup")
        val holiday = LocalDate.now(RIT_TIME_ZONE).plusDays(4)
        rule.onNodeWithTag("bulk_pickup_list").performScrollToNode(hasTestTag("bulk_date_choices"))
        rule.onNodeWithTag("bulk_date_choices").performScrollToNode(hasTestTag("bulk_date_$holiday"))
        rule.onNodeWithTag("bulk_date_$holiday").performClick()
        rule.onNodeWithTag("bulk_continue_review").assertIsNotEnabled()
        rule.onNodeWithTag("bulk_pickup_list").performScrollToNode(hasTestTag("bulk_special_toggle"))
        rule.onNodeWithTag("bulk_special_toggle").performClick()
        rule.onNodeWithTag("bulk_continue_review").assertIsNotEnabled()
        rule.onNodeWithTag("bulk_special_reason").performTextInput("Campus event after normal service")
        rule.onNodeWithTag("bulk_continue_review").assertIsEnabled()
        back()
        back()
        openHubLink("debug_bulk_negotiation")
        rule.onNodeWithTag("bulk_negotiation").assertExists()
        rule.onNodeWithTag("bulk_accept_disabled").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("bulk_compare_open").performScrollTo().performClick()
        rule.onNodeWithTag("bulk_compare").assertExists()
        back()
        back()
        openHubLink("debug_bulk_payment")
        rule.onNodeWithTag("bulk_payment_eligibility").assertExists()
        rule.onNodeWithTag("bulk_payment_disabled").assertIsNotEnabled()
        back()
        openHubLink("debug_bulk_expired")
        rule.onNodeWithTag("bulk_negotiation").assertExists()
        rule.onNodeWithTag("bulk_accept_disabled").assertDoesNotExist()
    }
}
