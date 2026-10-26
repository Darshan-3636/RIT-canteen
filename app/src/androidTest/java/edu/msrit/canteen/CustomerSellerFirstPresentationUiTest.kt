package edu.msrit.canteen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import edu.msrit.canteen.ui.screens.ChooseSellerScreen
import edu.msrit.canteen.ui.screens.CustomerPromotionsScreen
import edu.msrit.canteen.ui.screens.SellerScopedMenuScreen
import edu.msrit.canteen.ui.theme.RitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class CustomerSellerFirstPresentationUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sellerDirectoryFiltersAndReturnsTheChosenSellerId() {
        val chosenSellerId = AtomicReference<String?>(null)
        rule.setContent {
            RitTheme {
                ChooseSellerScreen(onSellerChosen = chosenSellerId::set, onBack = {})
            }
        }

        rule.onNodeWithTag("choose_seller").assertExists()
        rule.onNodeWithText("Individual & Bulk").assertExists()
        rule.onNodeWithTag("choose_seller_card_main-canteen").performScrollTo().performClick()
        assertEquals("main-canteen", chosenSellerId.get())

        rule.onNodeWithTag("search_input").performTextInput("North")
        rule.onNodeWithTag("choose_seller_card_north-counter").performScrollTo().assertExists()
        rule.onNodeWithText("Bulk Only").assertExists()
        rule.onNodeWithTag("choose_seller_card_coffee-corner").assertDoesNotExist()
    }

    @Test fun sellerMenuSearchStaysWithinTheSelectedSeller() {
        rule.setContent {
            RitTheme {
                SellerScopedMenuScreen(sellerId = "main-canteen", onBack = {}, onFood = {})
            }
        }

        rule.onNodeWithTag("seller_scoped_menu").assertExists()
        rule.onNodeWithTag("seller_menu_item_masala-dosa").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_menu_item_north-lunch").assertDoesNotExist()
        rule.onNodeWithTag("search_input").performTextInput("Biryani")
        rule.onNodeWithTag("seller_menu_item_veg-biryani").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_menu_item_masala-dosa").assertDoesNotExist()
    }

    @Test fun secondarySellerMenuContainsOnlyItsOwnItems() {
        rule.setContent {
            RitTheme {
                SellerScopedMenuScreen(sellerId = "north-counter", onBack = {}, onFood = {})
            }
        }

        rule.onNodeWithText("Bulk Only").assertExists()
        rule.onNodeWithTag("seller_menu_item_north-lunch").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_menu_item_masala-dosa").assertDoesNotExist()
    }

    @Test fun offersLeadWithBenefitEligibilityAndClearAction() {
        rule.setContent {
            RitTheme {
                CustomerPromotionsScreen(onBack = {}, onPromotion = {})
            }
        }

        rule.onNodeWithTag("offer_benefit_dosa-spotlight", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("offer_eligibility_dosa-spotlight", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("offer_action_dosa-spotlight", useUnmergedTree = true).assertExists()
    }
}
