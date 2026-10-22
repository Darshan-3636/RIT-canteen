package edu.msrit.canteen

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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

    @Test fun customerRegistrationPreviewsProfileWithOneAffiliationChoiceAndNoModePicker() {
        reachWelcome()
        rule.onNodeWithTag("welcome_get_started").assertExists().performScrollTo().performClick()
        rule.onNodeWithTag("registration_email_screen").assertExists()
        rule.onNodeWithTag("email_otp_action").assertIsNotEnabled()
        rule.onNodeWithTag("registration_profile_preview").performScrollTo().performClick()
        rule.onNodeWithTag("profile_setup").assertExists()
        rule.onNodeWithTag("profile_continue").performScrollTo().performClick()
        rule.onNodeWithText("Select Student or Faculty.").assertExists()
        rule.onNodeWithTag("profile_full_name").performTextInput("Asha Kumar")
        rule.onNodeWithTag("profile_student").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithTag("profile_faculty").performClick().assertIsSelected()
        rule.onNodeWithTag("profile_continue").performScrollTo().performClick()
        rule.onNodeWithTag("profile_preview_complete").assertExists()
        rule.onNodeWithText("Choose how you want\nto use the app").assertDoesNotExist()
        rule.onNodeWithTag("customer_home").assertDoesNotExist()
    }

    @Test fun sellerEntryIsInvitationOnlyAndLeadsToSharedLogin() {
        reachWelcome()
        rule.onNodeWithTag("welcome_get_started_seller").performScrollTo().performClick()
        rule.onNodeWithTag("seller_onboarding_entry").assertExists()
        rule.onNodeWithText("Seller accounts begin with an invitation, not a public sign-up.").assertExists()
        rule.onNodeWithTag("seller_invitation_login").performClick()
        rule.onNodeWithTag("login_screen").assertExists()
        rule.onNodeWithTag("customer_home").assertDoesNotExist()
    }

    @Test fun welcomeLoginIsSharedAndNeverClaimsOtpWasSent() {
        reachWelcome()
        rule.onNodeWithTag("welcome_login").performScrollTo().performClick()
        rule.onNodeWithTag("login_screen").assertExists()
        rule.onNodeWithTag("email_address_field").performTextInput("existing@example.com")
        rule.onNodeWithTag("email_otp_action").performScrollTo().performClick()
        rule.onNodeWithText("Email OTP service is unavailable. No code was sent.").assertExists()
        rule.onNodeWithTag("profile_setup").assertDoesNotExist()
    }
}
