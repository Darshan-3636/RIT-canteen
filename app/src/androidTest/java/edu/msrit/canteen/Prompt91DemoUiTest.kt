package edu.msrit.canteen

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import edu.msrit.canteen.customer.DevelopmentCustomerAccount
import edu.msrit.canteen.customer.DevelopmentIndividualOrders
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Prompt91DemoUiTest {
    @get:Rule val rule = createAndroidComposeRule<DebugTestingActivity>()
    @Before fun reset() = rule.runOnUiThread {
        DevelopmentPreviewAccess.reset(); DevelopmentIndividualOrders.reset(); DevelopmentCustomerAccount.reset()
    }
    private fun hub(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun preparedFoodCanBuildAndSaveMultiItemDemoOrder() {
        hub("debug_role_customer")
        hub("debug_open_food_details")
        rule.onNodeWithTag("food_add_individual").performClick()
        rule.onNodeWithTag("food_added_feedback").assertExists()
        rule.onNodeWithTag("food_view_cart").performClick()
        rule.onNodeWithTag("customer_cart").assertExists()
        rule.onNodeWithTag("individual_masala-dosa_plus").performClick()
        rule.onNodeWithText("Subtotal  ₹100").assertExists()
        rule.onNodeWithTag("cart_review_individual").assertIsEnabled().performClick()
        rule.onNodeWithTag("individual_save_demo").performClick()
        rule.onNodeWithTag("customer_orders").assertExists()
        rule.onNodeWithTag("orders_individual_LOCAL-FOOD-1").assertExists()
        rule.onNodeWithTag("orders_list").performScrollToNode(hasTestTag("orders_bulk_DEMO-NC-NEW"))
        rule.onNodeWithTag("orders_bulk_DEMO-NC-NEW").assertExists()
    }

    @Test fun cartTabsStaySeparateAndTemplateStartsWithoutBulkCart() {
        hub("debug_template_create")
        rule.onNodeWithTag("template_seller_north-counter").performClick()
        rule.onNodeWithTag("template_plus_north-snack").performClick()
        rule.onNodeWithTag("template_editor_name").performTextInput("Club snacks")
        rule.onNodeWithTag("template_editor_save").performScrollTo().performClick()
        hub("debug_customer_templates")
        rule.onNodeWithText("Club snacks").assertExists()
        rule.onNodeWithTag("template_edit_LOCAL-T-1").performScrollTo().performClick()
        rule.onNodeWithTag("customer_template_editor").assertExists()
        back(); back()
        hub("debug_unified_cart")
        rule.onNodeWithTag("cart_tab_individual").assertExists()
        rule.onNodeWithTag("cart_tab_bulk").performClick()
        rule.onNodeWithTag("cart_bulk_minimum").assertExists()
    }

    @Test fun choosingAnotherSellerOnlyChangesTheBulkCartAfterConfirmation() {
        hub("debug_seed_carts")
        rule.onNodeWithTag("customer_cart").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag("debug_testing_hub").assertExists()
        hub("debug_open_home")
        rule.onNodeWithTag("choose_seller_card_main-canteen").performScrollTo().performClick()
        rule.onNodeWithTag("seller_menu_item_masala-dosa").performScrollTo().performClick()
        rule.onNodeWithTag("food_add_bulk").performClick()
        rule.onNodeWithTag("cart_confirmation").assertExists()
        rule.onNodeWithTag("cart_keep").performClick()
        rule.onNodeWithTag("food_details").assertExists()
        rule.onNodeWithTag("food_add_bulk").performClick()
        rule.onNodeWithTag("cart_clear_confirm").performClick()
        rule.onNodeWithTag("customer_cart").assertExists()
        rule.onNodeWithTag("cart_line_bulk_masala-dosa").assertExists()
        rule.onNodeWithTag("cart_line_bulk_north-lunch").assertDoesNotExist()
        rule.onNodeWithTag("cart_tab_individual").performClick()
        rule.onNodeWithTag("cart_line_individual_masala-dosa").assertExists()
    }

    @Test fun analyticsDateRangeAndForecastAreInteractiveDemoOnly() {
        hub("debug_role_sellerowner")
        hub("debug_seller_analytics")
        rule.onNodeWithTag("sample_net_sales").assertExists()
        rule.onNodeWithTag("overview_range_1").performClick()
        rule.onNodeWithTag("overview_range_7").performClick()
        rule.onNodeWithTag("overview_range_30").performClick()
        rule.onNodeWithTag("overview_open_forecast").performScrollTo().performClick()
        rule.onNodeWithText("Illustrative Forecast — Demo Only").assertExists()
    }

    @Test fun offerCardsAndSellerPromotionPreviewShowBenefits() {
        hub("debug_open_promotions")
        rule.onNodeWithTag("offers_list").performScrollToNode(hasTestTag("promotion_dosa-spotlight"))
        rule.onNodeWithTag("offer_benefit_dosa-spotlight", useUnmergedTree = true).assertExists()
        back()
        hub("debug_role_sellerowner")
        hub("debug_seller_promotions")
        rule.onNodeWithTag("seller_promotion_preview_north-lunch-demo").performScrollTo().performClick()
        rule.onNodeWithTag("seller_offer_preview").assertExists()
    }
}
