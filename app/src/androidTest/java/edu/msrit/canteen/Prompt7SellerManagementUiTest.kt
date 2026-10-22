package edu.msrit.canteen

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import edu.msrit.canteen.customer.DevelopmentBulkPreview
import edu.msrit.canteen.customer.DevelopmentSellerBusiness
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Prompt7SellerManagementUiTest {
    @get:Rule val rule = createAndroidComposeRule<DebugTestingActivity>()

    @Before fun reset() = rule.runOnUiThread {
        DevelopmentPreviewAccess.reset()
        DevelopmentSellerBusiness.reset()
    }

    private fun hub(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun ownerCreatesItemAndCustomerCatalogSharesIt() {
        hub("debug_role_sellerowner")
        hub("debug_seller_manage_menu")
        rule.onNodeWithTag("seller_menu_management").assertExists()
        rule.onNodeWithTag("seller_add_item").performClick()
        rule.onNodeWithTag("seller_item_editor").assertExists()
        rule.onNodeWithTag("seller_item_name").performTextInput("Campus group rice")
        rule.onNodeWithTag("seller_item_price").performTextInput("75")
        rule.onNodeWithTag("seller_save_item").performScrollTo().performClick()
        rule.onNodeWithTag("seller_menu_management").assertExists()
        rule.runOnIdle { assert(PreviewCatalog.items.any { it.sellerId == "north-counter" && it.name == "Campus group rice" }) }
    }

    @Test fun sellerMinimumUpdatesBulkPolicyAndHoursRouteOpens() {
        hub("debug_role_sellerowner")
        hub("debug_seller_minimum")
        rule.onNodeWithTag("seller_bulk_minimum").assertExists()
        rule.onNodeWithTag("seller_minimum_input").performTextClearance()
        rule.onNodeWithTag("seller_minimum_input").performTextInput("12")
        rule.onNodeWithTag("seller_save_minimum").performScrollTo().performClick()
        rule.runOnIdle { assert(DevelopmentBulkPreview.forSeller("north-counter")?.minimum == 12) }
        back()
        hub("debug_seller_hours")
        rule.onNodeWithTag("seller_hours").assertExists()
    }

    @Test fun staffRestrictionsAndDelegatedAvailabilityAreVisible() {
        hub("debug_role_sellerstaff")
        rule.onNodeWithTag("debug_seller_manage_menu").performScrollTo().assertIsNotEnabled()
        hub("debug_role_sellerstaffoperations")
        hub("debug_seller_manage_menu")
        rule.onNodeWithTag("seller_menu_management").assertExists()
        rule.onNodeWithTag("seller_add_item").assertDoesNotExist()
        rule.onNodeWithTag("seller_item_north-lunch").performScrollTo().performClick()
        rule.onNodeWithTag("seller_availability").assertExists()
    }

    @Test fun ownerOpensTeamPromotionsAndSettings() {
        hub("debug_role_sellerowner")
        hub("debug_seller_employees")
        rule.onNodeWithTag("seller_employees").assertExists()
        rule.onNodeWithTag("seller_employee_debug-manager").performScrollTo().performClick()
        rule.onNodeWithTag("seller_employee_editor").assertExists()
        back(); back()
        hub("debug_seller_promotions")
        rule.onNodeWithTag("seller_promotions").assertExists()
        rule.onNodeWithTag("seller_add_promotion").performScrollTo().performClick()
        rule.onNodeWithTag("seller_promotion_editor").assertExists()
        back(); back()
        hub("debug_seller_settings")
        rule.onNodeWithTag("seller_business_settings").assertExists()
    }
}
