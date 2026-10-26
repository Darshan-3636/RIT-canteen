package edu.msrit.canteen

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CustomerDiscoveryUiTest {
    @get:Rule val rule = createAndroidComposeRule<DebugTestingActivity>()

    @Before fun reset() {
        rule.runOnUiThread { DevelopmentPreviewAccess.reset() }
    }

    @Test fun hubRoleSwitchAndResetKeepCustomerAccessScoped() {
        rule.onNodeWithTag("debug_testing_hub").assertExists()
        listOf(
            "sellerowner" to "North Counter Owner",
            "sellermanager" to "North Counter Manager",
            "sellerstaff" to "North Counter Staff",
            "platformadministrator" to "Demo Administrator",
        ).forEach { (role, sampleName) ->
            rule.onNodeWithTag("debug_role_$role").performScrollTo().performClick()
            rule.onNodeWithTag("debug_active_identity").assertTextContains(sampleName, substring = true)
            rule.onNodeWithTag("debug_open_home").performScrollTo().assertIsNotEnabled()
        }
        rule.onNodeWithTag("debug_role_customer").performScrollTo().performClick()
        rule.onNodeWithTag("debug_active_identity").assertTextContains("Demo Customer", substring = true)
        rule.onNodeWithTag("debug_open_home").performScrollTo().assertIsEnabled()
        rule.onNodeWithTag("debug_reset").performScrollTo().performClick()
        rule.onNodeWithTag("debug_open_home").performScrollTo().assertIsNotEnabled()
    }

    @Test fun customerChoosesSellerThenFiltersOnlyItsMenuAndOpensDetails() {
        rule.onNodeWithTag("debug_role_customer").performClick()
        rule.onNodeWithTag("debug_open_home").performScrollTo().performClick()
        rule.onNodeWithTag("choose_seller").assertExists()
        rule.onNodeWithTag("choose_seller_card_main-canteen").performScrollTo().performClick()
        rule.onNodeWithTag("seller_scoped_menu").assertExists()
        rule.onNodeWithTag("seller_menu_item_north-lunch").assertDoesNotExist()

        rule.onNodeWithTag("catalog_category_rice").performClick()
        rule.onNodeWithTag("seller_menu_results_heading").assertTextContains("Rice", substring = true)
        rule.onNodeWithTag("seller_menu_item_veg-biryani").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_menu_item_masala-dosa").assertDoesNotExist()

        rule.onNodeWithTag("catalog_category_snacks").performClick()
        rule.onNodeWithTag("seller_menu_results_heading").assertTextContains("Snacks", substring = true)
        rule.onNodeWithTag("seller_menu_item_idli").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_menu_item_veg-biryani").assertDoesNotExist()

        rule.onNodeWithTag("catalog_category_all").performClick()
        rule.onNodeWithTag("seller_menu_results_heading").assertTextContains("Main Canteen menu", substring = true)
        rule.onNodeWithTag("seller_menu_item_masala-dosa").performScrollTo().performClick()
        rule.onNodeWithTag("food_details").assertExists()
    }
}
