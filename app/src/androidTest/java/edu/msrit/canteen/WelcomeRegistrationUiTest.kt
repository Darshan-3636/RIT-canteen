package edu.msrit.canteen

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class WelcomeRegistrationUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun reachWelcome() {
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("welcome_screen").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Skip").performClick()
        }
        rule.onNodeWithTag("welcome_screen").assertExists()
    }

    @Test fun customerRegistrationHasOneAffiliationChoiceAndNoModePicker() {
        reachWelcome()
        rule.onNodeWithTag("welcome_get_started").assertExists().performScrollTo().performClick()
        rule.onNodeWithTag("demo_signup_screen").assertExists()
        rule.onNodeWithTag("demo_signup_student").assertExists()
        rule.onNodeWithTag("demo_signup_faculty").assertExists().performScrollTo().performClick()
        rule.onNodeWithText("Choose how you want\nto use the app").assertDoesNotExist()
        rule.onNodeWithTag("customer_home").assertDoesNotExist()
    }

    @Test fun sellerSignupForkIsAbsentAndSharedLoginIsProminent() {
        reachWelcome()
        rule.onNodeWithTag("welcome_get_started_seller").assertDoesNotExist()
        rule.onNodeWithTag("welcome_login").assertExists().performScrollTo().performClick()
        rule.onNodeWithTag("demo_login_screen").assertExists()
        rule.onNodeWithTag("customer_home").assertDoesNotExist()
    }

    @Test fun welcomeLoginIsSharedAndUsesLocalDemoPassword() {
        reachWelcome()
        rule.onNodeWithTag("welcome_login").performScrollTo().performClick()
        rule.onNodeWithTag("demo_login_screen").assertExists()
        rule.onNodeWithTag("demo_login_email").assertExists()
        rule.onNodeWithTag("demo_login_password").assertExists()
        rule.onNodeWithContentDescription("Show password").assertExists()
        rule.onNodeWithTag("demo_login_visibility").performClick()
        rule.onNodeWithContentDescription("Hide password").assertExists()
        rule.onNodeWithTag("demo_login_submit").assertExists()
        rule.onNodeWithTag("demo_signup_screen").assertDoesNotExist()
    }
}
