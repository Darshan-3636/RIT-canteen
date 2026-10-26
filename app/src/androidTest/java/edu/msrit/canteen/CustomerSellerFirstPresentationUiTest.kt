package edu.msrit.canteen

import android.R
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
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
    @get:Rule val rule = createAndroidComposeRule<DebugTestingActivity>()

    private fun setScreen(content: @Composable () -> Unit) {
        rule.runOnUiThread {
            val contentRoot = rule.activity.findViewById<ViewGroup>(R.id.content)
            val composeView = contentRoot.getChildAt(0) as ComposeView
            composeView.setContent(content)
        }
        rule.waitForIdle()
    }

    @Test fun sellerDirectoryFiltersAndReturnsTheChosenSellerId() {
        val chosenSellerId = AtomicReference<String?>(null)
        setScreen {
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
        setScreen {
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
        setScreen {
            RitTheme {
                SellerScopedMenuScreen(sellerId = "north-counter", onBack = {}, onFood = {})
            }
        }
        rule.onNodeWithText("Bulk Only").assertExists()
        rule.onNodeWithTag("seller_menu_item_north-lunch").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_menu_item_masala-dosa").assertDoesNotExist()
    }

    @Test fun offersLeadWithBenefitEligibilityAndClearAction() {
        val openedPromotion = AtomicReference<String?>(null)
        setScreen {
            RitTheme {
                CustomerPromotionsScreen(onBack = {}, onPromotion = openedPromotion::set)
            }
        }
        rule.onNodeWithTag("offer_benefit_dosa-spotlight", useUnmergedTree = true)
            .assertTextContains("20% OFF")
        rule.onNodeWithTag("offer_eligibility_dosa-spotlight", useUnmergedTree = true)
            .assertTextContains("Masala Dosa", substring = true)
        rule.onNodeWithTag("offer_action_dosa-spotlight", useUnmergedTree = true)
            .performScrollTo().performClick()
        assertEquals("dosa-spotlight", openedPromotion.get())
    }

}
