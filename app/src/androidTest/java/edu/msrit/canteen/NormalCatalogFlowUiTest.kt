package edu.msrit.canteen

import androidx.compose.ui.test.hasTestTag
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
    }

    private fun customer() = login("student.arya@demo.ritcanteen.test", "RitDemo!Student01")

    private fun chooseSeller(id: String) {
        rule.onNodeWithTag("choose_seller_results").performScrollToNode(hasTestTag("choose_seller_card_$id"))
        rule.onNodeWithTag("choose_seller_card_$id").performClick()
        rule.onNodeWithTag("seller_scoped_menu").assertExists()
    }

    private fun selectCategory(id: String) {
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("catalog_categories"))
        rule.onNodeWithTag("catalog_categories").performScrollToNode(hasTestTag("catalog_category_$id"))
        rule.onNodeWithTag("catalog_category_$id").performClick()
    }

    @Test fun pantryPackagedJuiceAndWaterAppearTogetherInBeverages() {
        customer(); chooseSeller("campus-pantry"); selectCategory("drinks")
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_pantry-juice"))
        rule.onNodeWithTag("seller_menu_item_pantry-juice").assertExists().performClick()
        rule.onNodeWithTag("food_details").assertExists()
        rule.onNodeWithText("Fruit Juice Carton").assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_pantry-water"))
        rule.onNodeWithTag("seller_menu_item_pantry-water").assertExists().performClick()
        rule.onNodeWithText("Water Bottle").assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        selectCategory("packs")
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_potato-chips"))
        rule.onNodeWithTag("seller_menu_item_potato-chips").assertExists()
        rule.onNodeWithTag("seller_menu_item_pantry-juice").assertDoesNotExist()
        rule.onNodeWithTag("debug_testing_hub").assertDoesNotExist()
    }

    @Test fun mainCanteenBreakfastOpensNewPreparedFoodAndCart() {
        customer(); chooseSeller("main-canteen"); selectCategory("breakfast")
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_idli"))
        rule.onNodeWithTag("seller_menu_item_idli").assertExists()
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_rava-idli"))
        rule.onNodeWithTag("seller_menu_item_rava-idli").performClick()
        rule.onNodeWithTag("food_add_individual").performClick()
        rule.onNodeWithTag("food_view_cart").performClick()
        rule.onNodeWithTag("customer_cart").assertExists()
        rule.onNodeWithTag("cart_line_individual_rava-idli").assertExists()
    }

    @Test fun pantryMenuSearchDistinguishesJuiceFromWaterWithinOneSeller() {
        customer(); chooseSeller("campus-pantry")
        rule.onNodeWithTag("search_input").performTextInput("juice")
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_pantry-juice"))
        rule.onNodeWithTag("seller_menu_item_pantry-juice").assertExists()
        rule.onNodeWithTag("seller_menu_item_pantry-water").assertDoesNotExist()
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("search_input"))
        rule.onNodeWithTag("search_input").performTextReplacement("water")
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_pantry-water"))
        rule.onNodeWithTag("seller_menu_item_pantry-water").assertExists()
        rule.onNodeWithTag("seller_menu_item_orange-juice-bottle").assertDoesNotExist()
    }

    @Test fun bakeryPuffsAreDistinctAndStayWithinBakerySeller() {
        customer(); chooseSeller("coffee-corner"); selectCategory("puffs")
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_bakery-veg-puff"))
        rule.onNodeWithTag("seller_menu_item_bakery-veg-puff").assertExists()
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_bakery-paneer-puff"))
        rule.onNodeWithTag("seller_menu_item_bakery-paneer-puff").performClick()
        rule.onNodeWithText("Paneer Puff").assertExists()
        rule.onNodeWithTag("food_add_individual").assertDoesNotExist()
        rule.onNodeWithTag("seller_menu_item_masala-dosa").assertDoesNotExist()
    }

    @Test fun northCounterSnacksStayBulkOnly() {
        customer(); chooseSeller("north-counter"); selectCategory("snacks")
        rule.onNodeWithTag("seller_menu_list").performScrollToNode(hasTestTag("seller_menu_item_veg-samosa"))
        rule.onNodeWithTag("seller_menu_item_veg-samosa").performClick()
        rule.onNodeWithText("Veg Samosa").assertExists()
        rule.onNodeWithTag("food_add_individual").assertDoesNotExist()
        rule.onNodeWithTag("food_add_bulk").assertExists()
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
        rule.onNodeWithTag("seller_menu_management").assertExists()
        rule.onNodeWithTag("seller_menu_items").performScrollToNode(hasTestTag("seller_item_plain-dosa"))
        rule.onNodeWithTag("seller_item_plain-dosa").assertExists()
        rule.onNodeWithTag("seller_bottom_account").performClick()
        rule.onNodeWithTag("role_account_logout").performScrollTo().performClick()
        rule.onNodeWithTag("welcome_screen").assertExists()
        rule.onNodeWithTag("seller_menu_management").assertDoesNotExist()
        rule.onNodeWithTag("debug_testing_hub").assertDoesNotExist()
        customer(); chooseSeller("main-canteen"); selectCategory("dosa")
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
