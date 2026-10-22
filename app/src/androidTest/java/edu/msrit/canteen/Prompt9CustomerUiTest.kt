package edu.msrit.canteen

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import edu.msrit.canteen.customer.DevelopmentCustomerAccount
import edu.msrit.canteen.customer.AppearanceChoice
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import edu.msrit.canteen.ui.theme.AppearancePreference
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Prompt9CustomerUiTest {
    @get:Rule val rule = createAndroidComposeRule<DebugTestingActivity>()
    @Before fun reset() { rule.runOnUiThread { DevelopmentPreviewAccess.reset(); DevelopmentCustomerAccount.reset() } }
    @After fun restoreAppearance() { rule.runOnUiThread { AppearancePreference(rule.activity).choose(AppearanceChoice.SYSTEM) } }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() } }

    @Test fun profileSettingsAndOptionalStoriesNavigate() {
        rule.onNodeWithTag("debug_customer_profile").performScrollTo().performClick()
        rule.onNodeWithTag("customer_profile").assertExists()
        rule.onNodeWithTag("profile_settings").performScrollTo().performClick()
        rule.onNodeWithTag("settings_appearance").performScrollTo().performClick()
        rule.onNodeWithTag("appearance_dark").performScrollTo().performClick()
        rule.onNodeWithTag("customer_appearance").assertExists()
        back(); back()
        rule.onNodeWithTag("profile_stories").performScrollTo().performClick()
        rule.onNodeWithTag("stories_minecraft").performScrollTo().performClick()
        rule.onNodeWithTag("story_detail_minecraft").assertExists()
        back(); back()
        rule.onNodeWithTag("customer_profile").assertExists()
        rule.onNodeWithTag("profile_quick_reorder").performScrollTo().performClick()
        rule.onNodeWithTag("quick_reorder_empty").assertExists()
    }

    @Test fun profileEditAndPrivacyAreLocalOnly() {
        rule.onNodeWithTag("debug_customer_profile").performScrollTo().performClick()
        rule.onNodeWithTag("profile_edit").performScrollTo().performClick()
        rule.onNodeWithTag("edit_profile_name").performTextInput(" Asha")
        rule.onNodeWithTag("edit_profile_faculty").performClick()
        rule.onNodeWithTag("profile_save").performScrollTo().performClick()
        rule.onNodeWithText("Saved to this debug preview only.").assertExists()
        back()
        rule.onNodeWithTag("profile_settings").performScrollTo().performClick()
        rule.onNodeWithTag("settings_privacy").performScrollTo().performClick()
        rule.onNodeWithText("Consent is not assumed").assertExists()
        rule.onNodeWithTag("privacy_personalization").performClick()
        rule.onNodeWithText("Local preference on; no activity collection exists").assertExists()
    }

    @Test fun supportFormUsesHonestLocalTicketAndHistory() {
        rule.onNodeWithTag("debug_customer_support").performScrollTo().performClick()
        rule.onNodeWithText("No support tickets").assertExists()
        rule.onNodeWithTag("support_create").performClick()
        rule.onNodeWithTag("ticket_submit").performScrollTo().performClick()
        rule.onNodeWithText("Choose an issue category.").assertExists()
        rule.onNodeWithTag("ticket_category_food").performScrollTo().performClick()
        rule.onNodeWithTag("ticket_subject").performTextInput("Missing snack")
        rule.onNodeWithTag("ticket_description").performTextInput("One item was missing from the bag")
        rule.onNodeWithTag("ticket_submit").performScrollTo().performClick()
        rule.onNodeWithTag("customer_ticket_detail").assertExists()
        rule.onNodeWithText("Not delivered").performScrollTo().assertExists()
        rule.onNodeWithTag("ticket_escalate").performScrollTo().performClick()
        rule.onNodeWithText("Escalation requested").performScrollTo().assertExists()
    }

    @Test fun hubOffersEmptyNotificationsTemplatesAndOfflineBoundary() {
        rule.onNodeWithTag("debug_customer_notifications").performScrollTo().performClick()
        rule.onNodeWithText("No notifications").assertExists()
        back()
        rule.onNodeWithTag("debug_customer_templates").performScrollTo().performClick()
        rule.onNodeWithText("No saved templates").performScrollTo().assertExists()
        back()
        rule.onNodeWithTag("debug_customer_offline").performScrollTo().performClick()
        rule.onNodeWithText("You're offline").assertExists()
    }
}
