package edu.msrit.canteen

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import edu.msrit.canteen.customer.DevelopmentFavoritesRepository
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Prompt4DiscoveryUiTest {
    @get:Rule val rule = createAndroidComposeRule<DebugTestingActivity>()

    @Before fun resetDemo() {
        rule.runOnUiThread {
            DevelopmentPreviewAccess.reset()
            DevelopmentFavoritesRepository(rule.activity).resetAll()
        }
    }

    private fun openHubLink(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun searchMatchesAndCombinedFiltersResetWithoutLeavingSearch() {
        openHubLink("debug_open_search")
        rule.onNodeWithTag("customer_search").assertExists()
        rule.onNodeWithTag("search_input").assertIsFocused()
        rule.onNodeWithTag("search_input").performTextInput(" MASALA   dosa ")
        rule.onNodeWithTag("search_results_list").performScrollToNode(hasTestTag("search_item_masala-dosa"))
        rule.onNodeWithTag("search_item_masala-dosa").assertExists()
        rule.onNodeWithTag("search_item_masala-dosa").performClick()
        rule.onNodeWithTag("food_details").assertExists()
        back()
        rule.onNodeWithTag("customer_search").assertExists()
        rule.onNodeWithTag("search_clear").performClick()
        rule.onNodeWithTag("search_filters_toggle").performClick()
        rule.onNodeWithTag("search_filters_panel").assertExists()
        rule.onNodeWithTag("search_results_list").performScrollToNode(hasTestTag("filter_category_rice"))
        rule.onNodeWithTag("filter_category_rice").performClick()
        rule.onNodeWithTag("search_results_list").performScrollToNode(hasTestTag("filter_price_up-to-50"))
        rule.onNodeWithTag("filter_price_up-to-50").performClick()
        rule.onNodeWithTag("search_results_list").performScrollToNode(hasTestTag("filter_availability_marked"))
        rule.onNodeWithTag("filter_availability_marked").performClick()
        rule.onNodeWithTag("search_results_list").performScrollToNode(hasTestTag("search_reset_filters"))
        rule.onNodeWithTag("search_reset_filters").performClick()
        rule.onNodeWithTag("customer_search").assertExists()
        rule.onNodeWithTag("search_results_list").performScrollToNode(hasTestTag("search_sort_choices"))
        rule.onNodeWithTag("search_sort_choices").performScrollToNode(hasTestTag("sort_price_low"))
        rule.onNodeWithTag("sort_price_low").performClick()
        rule.onNodeWithTag("search_sort_choices").performScrollToNode(hasTestTag("sort_price_high"))
        rule.onNodeWithTag("sort_price_high").performClick()
    }

    @Test fun favoritesAddRemoveAndMockIdentityStorageStayScoped() {
        openHubLink("debug_open_favorites_empty")
        rule.onNodeWithTag("favorites_empty").assertExists()
        back()
        openHubLink("debug_open_home")
        rule.onNodeWithTag("choose_seller_card_main-canteen").performScrollTo().performClick()
        rule.onNodeWithTag("seller_menu_item_masala-dosa").performScrollTo()
        rule.onNodeWithTag("favorite_item_masala-dosa").performClick()
        rule.onNodeWithText("Saved").performClick()
        rule.onNodeWithTag("favorite_saved_item_masala-dosa").assertExists()
        rule.onNodeWithTag("favorite_item_masala-dosa").performClick()
        rule.onNodeWithTag("favorites_empty").assertExists()
        back()
        back()
        back()
        openHubLink("debug_open_favorites_seeded")
        rule.onNodeWithTag("favorite_saved_item_masala-dosa").assertExists()
        rule.onNodeWithTag("favorite_saved_seller_main-canteen").performScrollTo().assertExists()
        rule.onNodeWithTag("favorite_seller_main-canteen").performClick()
        rule.onNodeWithTag("favorite_saved_seller_main-canteen").assertDoesNotExist()

        val repo = DevelopmentFavoritesRepository(rule.activity)
        rule.runOnUiThread { repo.toggleItem("different-debug-customer", "idli") }
        assertFalse("idli" in repo.itemIds("debug-customer"))
        assertTrue("idli" in repo.itemIds("different-debug-customer"))
        assertTrue("idli" in DevelopmentFavoritesRepository(rule.activity).itemIds("different-debug-customer"))
        rule.runOnUiThread { repo.resetAll() }
        assertTrue(repo.itemIds("debug-customer").isEmpty())
        assertTrue(repo.itemIds("different-debug-customer").isEmpty())
    }

    @Test fun demoPromotionsAndReorderStatesAreReadOnlyAndNavigable() {
        openHubLink("debug_open_promotions")
        rule.onNodeWithTag("promotions_screen").assertExists()
        rule.onNodeWithTag("promotion_dosa-spotlight").performClick()
        rule.onNodeWithTag("promotion_detail").assertExists()
        rule.onNodeWithTag("promotion_open_seller").performScrollTo().performClick()
        rule.onNodeWithTag("seller_profile").assertExists()
        back()
        back()
        back()
        openHubLink("debug_open_reorder_empty")
        rule.onNodeWithTag("quick_reorder_empty").assertExists()
        back()
        openHubLink("debug_open_reorder_sample")
        rule.onNodeWithTag("quick_reorder_sample").assertExists()
        rule.onNodeWithTag("reorder_submit").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("reorder_open_item_masala-dosa").performScrollTo().performClick()
        rule.onNodeWithTag("food_details").assertExists()
    }
}
