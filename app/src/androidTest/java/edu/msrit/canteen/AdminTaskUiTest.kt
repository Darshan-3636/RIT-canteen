package edu.msrit.canteen

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import edu.msrit.canteen.customer.DevelopmentInsights
import edu.msrit.canteen.customer.DevelopmentSellerBusiness
import edu.msrit.canteen.navigation.AppMode
import edu.msrit.canteen.navigation.AuthorizedIdentity
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import edu.msrit.canteen.ui.screens.AdminPage
import edu.msrit.canteen.ui.screens.PlatformAdminScreen
import edu.msrit.canteen.ui.theme.RitTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AdminTaskUiTest {
    @get:Rule val rule = createAndroidComposeRule<DebugTestingActivity>()
    @Before fun reset() { rule.runOnUiThread {
        DevelopmentInsights.reset(); DevelopmentSellerBusiness.reset(); DevelopmentPreviewAccess.reset()
    } }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() } }
    /** A count tile merges its number and label into one clickable node. */
    private fun tile(label: String, value: String) =
        rule.onNode(hasText(label) and hasText(value) and hasClickAction()).assertExists()
    private fun openAdmin(link: String) {
        rule.onNodeWithTag("debug_role_platformadministrator").performScrollTo().performClick()
        rule.onNodeWithTag(link).performScrollTo().performClick()
    }

    @Test fun overviewShowsDemoCountsAndEscalationFirst() {
        openAdmin("debug_admin_dashboard")
        rule.onNodeWithTag("admin_demo_banner").assertExists()
        rule.onNodeWithText("5 tasks need you").assertExists()
        tile("Open tickets", "3")
        tile("Escalated", "1")
        tile("Content to review", "2")
        tile("Setup drafts", "0")
        rule.onNodeWithTag("admin_queue_SAMPLE-T-102").performScrollTo().performClick()
        rule.onNodeWithTag("admin_ticket_detail").assertExists()
        rule.onNodeWithText("Pickup time question").assertExists()
    }

    @Test fun queueExpandsAndContentItemOpensModeration() {
        openAdmin("debug_admin_dashboard")
        rule.onNodeWithTag("admin_queue_SAMPLE-M-2").assertDoesNotExist()
        rule.onNodeWithTag("admin_queue_toggle").performScrollTo().performClick()
        rule.onNodeWithTag("admin_queue_SAMPLE-M-2").performScrollTo().assertExists()
        rule.onNodeWithTag("admin_queue_SAMPLE-M-1").performScrollTo().performClick()
        rule.onNodeWithTag("admin_moderation").assertExists()
    }

    @Test fun pendingSetupStartsEmptyThenShowsSavedDraft() {
        openAdmin("debug_admin_dashboard")
        rule.onNodeWithTag("admin_setup_start").performScrollTo().performClick()
        rule.onNodeWithTag("admin_seller_setup").assertExists()
        rule.onNodeWithTag("admin_new_seller_name").performTextInput("Juice Stall")
        rule.onNodeWithText("Save sample setup draft").performScrollTo().performClick()
        rule.onNodeWithText("Local seller draft saved. No owner access granted.").performScrollTo().assertExists()
        back()
        tile("Setup drafts", "1")
        rule.onNodeWithTag("admin_draft_sample-seller-1").performScrollTo().performClick()
        rule.onNodeWithTag("admin_seller_detail").assertExists()
        rule.onNodeWithText("Not in the customer catalog yet").assertExists()
    }

    @Test fun sellerDirectorySearchesFiltersAndRecoversFromEmpty() {
        openAdmin("debug_admin_sellers")
        rule.onNodeWithTag("admin_seller_search").performTextInput("coffee")
        rule.onNodeWithTag("admin_seller_coffee-corner").assertExists()
        rule.onNodeWithTag("admin_seller_main-canteen").assertDoesNotExist()
        rule.onNodeWithTag("admin_seller_filter_draft").performClick()
        rule.onNodeWithText("No sellers match").assertExists()
        rule.onNodeWithTag("admin_seller_clear").performScrollTo().performClick()
        rule.onNodeWithTag("admin_seller_main-canteen").performScrollTo().performClick()
        rule.onNodeWithTag("admin_seller_detail").assertExists()
        rule.onNodeWithText("Bulk · individual planned").assertExists()
    }

    @Test fun userDirectoryFiltersByRecordedRole() {
        openAdmin("debug_admin_users")
        rule.onNodeWithTag("admin_user_filter_seller_team").performClick()
        rule.onNodeWithTag("admin_user_debug-owner").assertExists()
        rule.onNodeWithTag("admin_user_sample-customer").assertDoesNotExist()
        rule.onNodeWithText("3 accounts").assertExists()
        rule.onNodeWithTag("admin_user_search").performTextReplacement("zzz")
        rule.onNodeWithText("No accounts match").assertExists()
    }

    @Test fun supportStartsOnNeedsActionAndFiltersRecover() {
        openAdmin("debug_admin_support")
        rule.onNodeWithText("3 tickets").assertExists()
        rule.onNodeWithTag("admin_ticket_filter_done").performScrollTo().performClick()
        rule.onNodeWithText("Nothing here").assertExists()
        rule.onNodeWithTag("admin_ticket_show_all").performScrollTo().performClick()
        rule.onNodeWithTag("admin_ticket_SAMPLE-T-103").performScrollTo().assertExists()
    }

    @Test fun ticketStatusChangeLeavesQueueAndIsAudited() {
        openAdmin("debug_admin_support")
        rule.onNodeWithTag("admin_ticket_SAMPLE-T-101").performScrollTo().performClick()
        rule.onNodeWithTag("admin_ticket_status_resolved").performScrollTo().performClick()
        rule.onNodeWithText("Status set to Resolved locally; no participant notified.").performScrollTo().assertExists()
        back()
        rule.onNodeWithTag("admin_ticket_SAMPLE-T-101").assertDoesNotExist()
        rule.onNodeWithText("2 tickets").assertExists()
        back()
        rule.onNodeWithTag("debug_admin_audit").performScrollTo().performClick()
        rule.onNodeWithText("Ticket status").performScrollTo().assertExists()
    }

    @Test fun moderationReviewMovesItemToReviewed() {
        openAdmin("debug_admin_moderation")
        rule.onNodeWithText("Needs review · 2").assertExists()
        rule.onNodeWithTag("admin_review_SAMPLE-M-1").performScrollTo().performClick()
        rule.onNodeWithText("Needs review · 1").assertExists()
        rule.onNodeWithText("Reviewed · 1").performScrollTo().assertExists()
        rule.onNodeWithTag("admin_review_SAMPLE-M-1").assertDoesNotExist()
    }

    @Test fun settingsAndAuditStateServiceLimitsHonestly() {
        openAdmin("debug_admin_settings")
        rule.onNodeWithText("Pending decision").performScrollTo().assertExists()
        rule.onNodeWithText("Unverified").performScrollTo().assertExists()
        back()
        rule.onNodeWithTag("debug_admin_audit").performScrollTo().performClick()
        rule.onNodeWithText("Real-time sync").assertExists()
        rule.onNodeWithText("Sample seller review").performScrollTo().assertExists()
    }

    @Test fun platformAnalyticsDrillsIntoOneSeller() {
        openAdmin("debug_admin_analytics")
        rule.onNodeWithTag("admin_sample_revenue").assertExists()
        rule.onNodeWithTag("admin_analytics_north-counter").performScrollTo().performClick()
        rule.onNodeWithTag("admin_seller_analytics").assertExists()
        rule.onNodeWithText("North Counter · demo · sample").assertExists()
    }

    @Test fun sellerDetailCountsAreDisplayOnlyBecauseListsAreNotSellerFiltered() {
        openAdmin("debug_admin_seller_detail")
        rule.onNodeWithTag("admin_seller_tickets").assertHasNoClickAction()
        rule.onNodeWithTag("admin_seller_review").assertHasNoClickAction()
    }

    @Test fun tappingCurrentTicketStatusChangesNothing() {
        openAdmin("debug_admin_ticket")
        rule.onNodeWithTag("admin_ticket_status_open").performScrollTo().performClick()
        rule.onNodeWithTag("admin_ticket_message").assertDoesNotExist()
        back()
        rule.onNodeWithTag("debug_admin_audit").performScrollTo().performClick()
        rule.onNodeWithText("Ticket status").assertDoesNotExist()
    }

    @Test fun overlongNoteExplainsTheLimit() {
        openAdmin("debug_admin_ticket")
        rule.onNodeWithTag("admin_ticket_note").performScrollTo().performTextInput("x".repeat(501))
        rule.onNodeWithText("Save local note").performScrollTo().performClick()
        rule.onNodeWithText("Notes can be up to 500 characters. Shorten it and save again.").performScrollTo().assertExists()
    }

    @Test fun reviewButtonsNameTheirItemForScreenReaders() {
        openAdmin("debug_admin_moderation")
        rule.onNodeWithContentDescription("Mark Sample promotional copy as reviewed").performScrollTo().performClick()
        rule.onNodeWithText("Reviewed · 1").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Mark Sample menu description as reviewed").assertExists()
    }

    @Test fun setupExplainsWhyANameIsRejectedAndClearsStaleMessages() {
        openAdmin("debug_admin_setup")
        rule.onNodeWithTag("admin_new_seller_name").performTextInput("main canteen")
        rule.onNodeWithText("Save sample setup draft").performScrollTo().performClick()
        rule.onNodeWithText("A seller named \"main canteen\" already exists.").performScrollTo().assertExists()
        rule.onNodeWithTag("admin_new_seller_name").performTextReplacement("x".repeat(81))
        rule.onNodeWithTag("admin_setup_message").assertDoesNotExist()
        rule.onNodeWithText("Save sample setup draft").performScrollTo().performClick()
        rule.onNodeWithText("Seller names can be up to 80 characters.").performScrollTo().assertExists()
        rule.onNodeWithTag("admin_new_seller_name").performTextReplacement("Juice Stall")
        rule.onNodeWithText("Save sample setup draft").performScrollTo().performClick()
        rule.onNodeWithTag("admin_setup_view").performScrollTo().assertExists()
        rule.onNodeWithTag("admin_new_seller_name").performTextReplacement("Juice Stall 2")
        rule.onNodeWithTag("admin_setup_message").assertDoesNotExist()
        rule.onNodeWithTag("admin_setup_view").assertDoesNotExist()
    }

    @Test fun sellerOwnerCannotOpenAdminLinks() {
        rule.onNodeWithTag("debug_role_sellerowner").performScrollTo().performClick()
        rule.onNodeWithTag("debug_admin_dashboard").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("debug_admin_users").performScrollTo().assertIsNotEnabled()
    }
}

/** Renders the screen directly with a non-admin identity to check its own guard, independent of route gating. */
class AdminAccessDeniedUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sellerOwnerIdentitySeesDeniedViewAndNoAdminData() {
        val owner = AuthorizedIdentity("debug-owner", setOf(AppMode.SellerOwner("north-counter")))
        rule.setContent { RitTheme(darkTheme = false) {
            PlatformAdminScreen(AdminPage.DASHBOARD, owner, true, null, null, {}, {}, {}, {}, {})
        } }
        rule.onNodeWithTag("admin_access_denied").assertExists()
        rule.onNodeWithText("Administrator access required").assertExists()
        rule.onNodeWithTag("admin_hero").assertDoesNotExist()
    }
}
