package edu.msrit.canteen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import edu.msrit.canteen.customer.DevelopmentInsights
import edu.msrit.canteen.customer.InsightPeriod
import edu.msrit.canteen.customer.range
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.SalesSummary
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import edu.msrit.canteen.ui.components.fillDays
import edu.msrit.canteen.ui.components.formatRange
import edu.msrit.canteen.ui.components.formatRupees
import edu.msrit.canteen.ui.components.lastDays
import edu.msrit.canteen.ui.components.salesChartSummary
import edu.msrit.canteen.ui.components.weekdayDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class SellerAnalyticsVisualUiTest {
    @get:Rule val rule = createAndroidComposeRule<DebugTestingActivity>()
    @Before fun reset() = rule.runOnUiThread { DevelopmentPreviewAccess.reset(); DevelopmentInsights.reset() }
    private fun hub(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
    private val today get() = LocalDate.now(RIT_TIME_ZONE)

    private fun openOverviewAsOwner() {
        hub("debug_role_sellerowner")
        hub("debug_seller_analytics")
    }

    /** Same provider call the screen makes, so displayed figures can be checked against it. */
    private fun expected(days: Int): SalesSummary = DevelopmentInsights.sales(
        DevelopmentPreviewAccess.activeIdentity?.authorization, "north-counter", lastDays(days, today), true)

    private fun statValue(tag: String, value: String) =
        rule.onNode(hasText(value) and hasParent(hasTestTag(tag)), useUnmergedTree = true).assertExists()

    @Test fun overviewFiguresMatchTheExistingSalesCalculationForEachRange() {
        openOverviewAsOwner()
        listOf(7, 30, 1).forEach { days ->
            rule.onNodeWithTag("overview_range_$days").performClick()
            val summary = expected(days)
            rule.onNodeWithTag("sample_net_sales").assertTextEquals(formatRupees(summary.netPaise))
            statValue("overview_orders", "${summary.orders}")
            statValue("overview_average", formatRupees(summary.averagePaise))
            statValue("overview_repeat", "${summary.returningCustomers}")
        }
    }

    @Test fun chartDescribesEveryDayInRangeAndTodayHasNoChart() {
        openOverviewAsOwner()
        rule.onNodeWithTag("overview_range_7").performClick()
        val range = lastDays(7, today)
        val description = salesChartSummary(fillDays(expected(7).daily, range), "last 7 days")
        rule.onNode(hasTestTag("sales_bar_chart") and
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(description))).assertExists()
        rule.onNodeWithTag("overview_range_1").performClick()
        rule.onNodeWithTag("sales_bar_chart").assertDoesNotExist()
        rule.onNodeWithText("Choose 7 or 30 days to see the daily trend.").assertExists()
    }

    @Test fun tappingABarShowsThatDaysFigures() {
        openOverviewAsOwner()
        rule.onNodeWithTag("overview_range_7").performClick()
        rule.onNodeWithTag("sales_chart_selection").assertTextEquals("Tap a bar to see that day")
        rule.onNodeWithTag("sales_bar_chart").performTouchInput { click(centerRight.copy(x = right - 4f)) }
        val lastDay = fillDays(expected(7).daily, lastDays(7, today)).last()
        rule.onNodeWithTag("sales_chart_selection").assertTextEquals(
            "${weekdayDate(lastDay.day)} · ${formatRupees(lastDay.netPaise)} · ${lastDay.orders} " +
                if (lastDay.orders == 1) "order" else "orders")
    }

    @Test fun emptyPreviewStaysHonestWithNoFigures() {
        hub("debug_role_sellerowner")
        hub("debug_seller_analytics_empty")
        rule.onNodeWithText("No verified sales history").assertExists()
        rule.onNodeWithTag("sample_net_sales").assertDoesNotExist()
        rule.onNodeWithTag("sales_bar_chart").assertDoesNotExist()
    }

    private fun openAsOwner(hubTag: String) { hub("debug_role_sellerowner"); hub(hubTag) }
    private fun chartDescribed(text: String) =
        rule.onNode(hasTestTag("sales_bar_chart") and hasContentDescription(text, substring = true)).assertExists()

    @Test fun trendsGroupingRedrawsTheChartAndNamesTheBestPeriod() {
        openAsOwner("debug_sales_trends")
        chartDescribed("Daily sales")
        rule.onNodeWithTag("trend_weekly").performScrollTo().performClick()
        chartDescribed("Weekly sales")
        rule.onNode(hasText("Best week", substring = true) and hasParent(hasTestTag("trends_best")), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("trend_monthly").performScrollTo().performClick()
        chartDescribed("Monthly sales")
    }

    @Test fun itemsPageSwitchesCategoryShareBetweenRevenueAndUnits() {
        openAsOwner("debug_product_analytics")
        rule.onNode(hasTestTag("share_donut") and hasContentDescription("Category share by revenue:", substring = true))
            .performScrollTo().assertExists()
        rule.onNodeWithTag("products_by_units").performScrollTo().performClick()
        rule.onNode(hasTestTag("share_donut") and hasContentDescription("Category share by units:", substring = true))
            .performScrollTo().assertExists()
    }

    @Test fun customerSplitAddsUpToUniqueBuyers() {
        openAsOwner("debug_customer_insights")
        val summary = DevelopmentInsights.sales(DevelopmentPreviewAccess.activeIdentity?.authorization, "north-counter",
            InsightPeriod.MONTH.range(today), true)
        statValue("customers_unique", "${summary.customers}")
        statValue("customers_repeat", "${summary.returningCustomers}")
        statValue("customers_one_time", "${summary.customers - summary.returningCustomers}")
        rule.onNodeWithTag("share_bar").assertExists()
    }

    @Test fun forecastIsDemoOnlyAndProductionStaysInsufficient() {
        openAsOwner("debug_forecast_sample")
        rule.onNodeWithText("Illustrative Forecast — Demo Only").assertExists()
        rule.onNodeWithText("Illustrative projection").performScrollTo().assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        hub("debug_forecast")
        rule.onNodeWithText("Insufficient historical data").assertExists()
    }

    @Test fun reportPresetsSetTheRangeAndSavingNeverClaimsAFile() {
        openAsOwner("debug_report_builder")
        rule.onNodeWithTag("report_start").assertDoesNotExist()
        rule.onNodeWithTag("report_range_last_7_days").performClick()
        rule.onNodeWithTag("report_summary").performScrollTo()
            .assertTextEquals("Sales summary · ${formatRange(lastDays(7, today))} · all items")
        rule.onNodeWithTag("report_range_custom").performScrollTo().performClick()
        rule.onNodeWithTag("report_start").assertExists()
        rule.onNodeWithText("Save sample configuration").performScrollTo().performClick()
        rule.onNodeWithText("Sample configuration saved. No XLSX generated.").performScrollTo().assertExists()
        rule.onNodeWithText("XLSX export isn't available yet. It will use verified, seller-scoped sales.").performScrollTo().assertExists()
    }

    @Test fun exploreListOpensDrillDowns() {
        openOverviewAsOwner()
        rule.onNodeWithTag("overview_open_trends").performScrollTo().performClick()
        rule.onNodeWithTag("seller_insight_trends").assertExists()
    }
}
