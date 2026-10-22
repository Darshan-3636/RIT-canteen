package edu.msrit.canteen

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import edu.msrit.canteen.customer.DevelopmentInsights
import edu.msrit.canteen.customer.DevelopmentSellerBusiness
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Prompt8InsightsAdminUiTest {
    @get:Rule val rule = createAndroidComposeRule<DebugTestingActivity>()
    @Before fun reset() { rule.runOnUiThread {
        DevelopmentInsights.reset(); DevelopmentSellerBusiness.reset(); DevelopmentPreviewAccess.reset()
    } }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() } }

    @Test fun sellerAnalyticsShowsLabeledSampleAndHonestEmptyState() {
        rule.onNodeWithTag("debug_role_sellerowner").performScrollTo().performClick()
        rule.onNodeWithTag("debug_seller_analytics").performScrollTo().performClick()
        rule.onNodeWithTag("seller_insight_overview").assertExists()
        rule.onNodeWithTag("sample_net_sales").performScrollTo().assertExists()
        rule.onNodeWithText("Today").performScrollTo().performClick()
        rule.onNodeWithTag("seller_insight_overview").assertExists()
        back()
        rule.onNodeWithTag("debug_sales_trends").performScrollTo().performClick()
        rule.onNodeWithTag("trend_weekly").performScrollTo().performClick()
        rule.onNodeWithTag("seller_insight_trends").assertExists()
        back()
        rule.onNodeWithTag("debug_seller_analytics_empty").performScrollTo().performClick()
        rule.onNodeWithText("No verified sales history").performScrollTo().assertExists()
    }

    @Test fun forecastAndReportBuilderNeverClaimProductionOutput() {
        rule.onNodeWithTag("debug_role_sellerowner").performScrollTo().performClick()
        rule.onNodeWithTag("debug_forecast").performScrollTo().performClick()
        rule.onNodeWithText("Insufficient historical data").performScrollTo().assertExists()
        back()
        rule.onNodeWithTag("debug_report_builder").performScrollTo().performClick()
        rule.onNodeWithTag("seller_insight_report_builder").assertExists()
        rule.onNodeWithText("Save sample configuration").performScrollTo().performClick()
        rule.onNodeWithText("Sample configuration saved. No XLSX generated.").performScrollTo().assertExists()
    }

    @Test fun managerStaffAndAdminCapabilitiesStayDistinct() {
        rule.onNodeWithTag("debug_role_sellermanager").performScrollTo().performClick()
        rule.onNodeWithTag("debug_seller_analytics").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("debug_report_builder").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("debug_role_sellerstaff").performScrollTo().performClick()
        rule.onNodeWithTag("debug_seller_analytics").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("debug_role_platformadministrator").performScrollTo().performClick()
        rule.onNodeWithTag("debug_admin_dashboard").performScrollTo().assertIsEnabled()
        rule.onNodeWithTag("debug_seller_manage_menu").performScrollTo().assertIsNotEnabled()
    }

    @Test fun adminDashboardDirectorySupportModerationAndAuditNavigate() {
        rule.onNodeWithTag("debug_role_platformadministrator").performScrollTo().performClick()
        rule.onNodeWithTag("debug_admin_dashboard").performScrollTo().performClick()
        rule.onNodeWithTag("admin_dashboard").assertExists()
        rule.onNodeWithTag("admin_open_sellers").performScrollTo().performClick()
        rule.onNodeWithTag("admin_sellers").assertExists()
        back(); back()
        rule.onNodeWithTag("debug_admin_support").performScrollTo().performClick()
        rule.onNodeWithTag("admin_support").assertExists()
        rule.onNodeWithText("Payment status dispute").performScrollTo().performClick()
        rule.onNodeWithTag("admin_ticket_detail").assertExists()
        back(); back()
        rule.onNodeWithTag("debug_admin_moderation").performScrollTo().performClick()
        rule.onNodeWithTag("admin_moderation").assertExists()
        back()
        rule.onNodeWithTag("debug_admin_audit").performScrollTo().performClick()
        rule.onNodeWithTag("admin_audit").assertExists()
    }
}
