package edu.msrit.canteen

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import edu.msrit.canteen.customer.DevelopmentSellerBusiness
import edu.msrit.canteen.customer.ItemAvailability
import edu.msrit.canteen.customer.PreviewCatalog
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test

/** All paths begin at the normal launcher; no direct preview hub or role-selection UI is involved. */
class NormalCatalogFlowUiTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    @get:Rule val launchRules = RuleChain.outerRule(ResetDemoBeforeLaunchRule()).around(composeRule)
    private val rule get() = composeRule

    @Before fun reset() {
        rule.runOnUiThread { DevelopmentSellerBusiness.reset() }
    }
    @After fun cleanup() = rule.runOnUiThread {
        DevelopmentDemoAccounts.signOut(); DevelopmentSellerBusiness.reset()
    }

    private fun login(email: String, password: String) {
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("welcome_screen").fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() }
        if (rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("Skip").performClick()
        rule.onNodeWithTag("welcome_login").performScrollTo().performClick()
        rule.onNodeWithTag("demo_login_email").performTextInput(email)
        rule.onNodeWithTag("demo_login_password").performTextInput(password)
        rule.onNodeWithTag("demo_login_submit").performScrollTo().performClick()
        val destination = if (email.startsWith("owner.") || email.startsWith("staff."))
            "seller_daily_overview" else "seller_scoped_menu"
        rule.waitUntil(25_000) { rule.onAllNodesWithTag(destination).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun customer() = login("student.arya@demo.ritcanteen.test", "RitDemo!Student01")

    /** Customers land on the one canteen's menu; there is nothing to choose. */
    private fun onMenu() {
        rule.onNodeWithTag("seller_scoped_menu").assertExists()
    }

    private fun selectCategory(id: String) {
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("catalog_categories"))
        rule.onNodeWithTag("catalog_categories").performScrollToNode(hasTestTag("catalog_category_$id"))
        rule.onNodeWithTag("catalog_category_$id").performClick()
    }

    @Test fun mainCanteenBreakfastOpensNewPreparedFoodAndCart() {
        customer(); onMenu(); selectCategory("breakfast")
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_idli"))
        rule.onNodeWithTag("seller_menu_item_idli").assertExists()
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_rava-idli"))
        rule.onNodeWithTag("seller_menu_item_rava-idli").performClick()
        rule.onNodeWithTag("food_add_order").assertTextEquals("Add to Cart")
        rule.onNodeWithTag("food_add_order").performClick()
        rule.onNodeWithTag("food_order_type_dialog").assertExists()
        rule.onNodeWithTag("food_choose_individual_order").performClick()
        rule.onNodeWithTag("food_view_cart").performClick()
        rule.onNodeWithTag("customer_cart").assertExists()
        rule.onNodeWithTag("cart_line_individual_rava-idli").assertExists()
    }

    @Test fun dualEligibleFoodCanBeAddedAsBulkAfterChoosingOrderType() {
        customer(); onMenu(); selectCategory("breakfast")
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_idli"))
        rule.onNodeWithTag("seller_menu_item_idli").performClick()
        rule.onNodeWithTag("food_add_order").performClick()
        rule.onNodeWithTag("food_order_type_dialog").assertExists()
        rule.onNodeWithTag("food_choose_bulk_order").performClick()
        rule.onNodeWithTag("customer_cart").assertExists()
        rule.onNodeWithTag("cart_tab_bulk").assertExists()
    }

    @Test fun ownerCanEditNewItemThroughNormalSellerMenuAndLogout() {
        login("owner.main@demo.ritcanteen.test", "RitDemo!OwnerMain01")
        rule.onNodeWithTag("seller_daily_overview").assertExists()
        rule.onNodeWithTag("seller_bottom_menu").performClick()
        rule.onNodeWithTag("seller_menu_management").assertExists()
        rule.onNodeWithTag("seller_menu_items").performScrollToNode(hasTestTag("seller_item_plain-dosa"))
        rule.onNodeWithTag("seller_item_plain-dosa").performClick()
        rule.onNodeWithTag("seller_item_editor").assertExists()
        rule.onNodeWithTag("seller_item_name").assertExists()
        rule.onNodeWithText("Plain Dosa").assertExists()
        rule.onNodeWithText("Unavailable").performScrollTo().performClick()
        rule.onNodeWithTag("seller_save_item").performScrollTo().performClick()
        rule.waitUntil(25_000) { rule.onAllNodesWithTag("seller_menu_management").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("seller_menu_management").assertExists()
        rule.onNodeWithTag("seller_menu_items").performScrollToNode(hasTestTag("seller_item_plain-dosa"))
        rule.onNodeWithTag("seller_item_plain-dosa").assertExists()
        rule.onNodeWithTag("seller_bottom_account").performClick()
        rule.onNodeWithTag("role_account_logout").performScrollTo().performClick()
        rule.onNodeWithTag("welcome_screen").assertExists()
        rule.onNodeWithTag("seller_menu_management").assertDoesNotExist()
        rule.onNodeWithTag("debug_testing_hub").assertDoesNotExist()
        customer(); onMenu(); selectCategory("dosa")
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_plain-dosa"))
        rule.onNodeWithText("Unavailable").assertExists()
        rule.runOnUiThread { DevelopmentSellerBusiness.reset() }
        rule.runOnUiThread { assertEquals(ItemAvailability.AVAILABLE,
            PreviewCatalog.items.single { it.id == "plain-dosa" }.availability) }
        rule.onNodeWithText("Unavailable").assertDoesNotExist()
    }

    @Test fun staffCanViewMenuButCannotEditNewSeededItems() {
        login("staff.main@demo.ritcanteen.test", "RitDemo!Staff01")
        rule.onNodeWithTag("seller_bottom_menu").performClick()
        rule.onNodeWithTag("seller_menu_management").assertExists()
        rule.onNodeWithTag("seller_add_item").assertDoesNotExist()
        rule.onNodeWithTag("seller_menu_items").performScrollToNode(hasTestTag("seller_item_plain-dosa"))
        rule.onNodeWithTag("seller_item_plain-dosa").assertExists()
        rule.onNodeWithTag("seller_item_editor").assertDoesNotExist()
    }
}
