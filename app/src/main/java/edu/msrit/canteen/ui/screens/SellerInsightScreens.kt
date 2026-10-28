package edu.msrit.canteen.ui.screens

import edu.msrit.canteen.BuildConfig
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.DevelopmentInsights
import edu.msrit.canteen.customer.DebugSellerAnalyticsApi
import edu.msrit.canteen.customer.SellerCloudAnalytics
import edu.msrit.canteen.customer.DevelopmentPromotions
import edu.msrit.canteen.customer.InsightPeriod
import edu.msrit.canteen.customer.InsightRange
import edu.msrit.canteen.customer.MetricRow
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.ReportSelection
import edu.msrit.canteen.customer.ReportType
import edu.msrit.canteen.customer.TicketCategory
import edu.msrit.canteen.customer.TicketStatus
import edu.msrit.canteen.customer.TrendGrouping
import edu.msrit.canteen.customer.groupSales
import edu.msrit.canteen.customer.range
import edu.msrit.canteen.customer.statusAt
import edu.msrit.canteen.customer.validateReport
import edu.msrit.canteen.navigation.AuthorizedIdentity
import edu.msrit.canteen.ui.components.AnalyticsCard
import edu.msrit.canteen.ui.components.CatalogPill
import edu.msrit.canteen.ui.components.weekdayDate
import edu.msrit.canteen.ui.components.AnalyticsRangeSelector
import edu.msrit.canteen.ui.components.AnalyticsToggle
import edu.msrit.canteen.ui.components.DonutChart
import edu.msrit.canteen.ui.components.LineLegend
import edu.msrit.canteen.ui.components.ShareBar
import edu.msrit.canteen.ui.components.ShareLegend
import edu.msrit.canteen.ui.components.ShareSlice
import edu.msrit.canteen.ui.components.formatRange
import edu.msrit.canteen.ui.components.percentShares
import edu.msrit.canteen.ui.components.periodName
import edu.msrit.canteen.ui.components.shortDate
import edu.msrit.canteen.ui.components.tabular
import edu.msrit.canteen.ui.components.SalesBarChart
import edu.msrit.canteen.ui.components.InsightNavEntry
import edu.msrit.canteen.ui.components.InsightNavList
import edu.msrit.canteen.ui.components.RankedItemRow
import edu.msrit.canteen.ui.components.RevenuePanel
import edu.msrit.canteen.ui.components.StatCell
import edu.msrit.canteen.ui.components.StatStrip
import edu.msrit.canteen.ui.components.fillDays
import edu.msrit.canteen.ui.components.formatRupees
import edu.msrit.canteen.ui.components.lastDays
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SellerInsightPage { OVERVIEW, TRENDS, PRODUCTS, CUSTOMERS, PROMOTIONS, FORECAST,
    REPORT_BUILDER, REPORT_HISTORY, SUPPORT, NEW_TICKET, TICKET_DETAIL, NOTIFICATIONS, PAYMENT_STATUS }

private val savedTime = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

@Composable
private fun InsightPage(title: String, tag: String, onBack: () -> Unit,
                        hasSample: Boolean = true, content: @Composable () -> Unit) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).testTag(tag)) {
        CatalogHeader(title, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            RitText(if (BuildConfig.RIT_CLOUD_DEMO) "SERVER DEMO REQUESTS · NOT VERIFIED SALES"
                else if (hasSample) "DEMO DATA · not live sales"
                else "NO VERIFIED APP SALES · preview state only", RitType.small, colors.accent)
            Spacer(Modifier.height(14.dp))
            content()
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun InsightCard(title: String, detail: String, onClick: (() -> Unit)? = null, tag: String? = null) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 9.dp)
        .border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
        .background(colors.card, RoundedCornerShape(RitRadii.card))
        .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
        .then(if (tag == null) Modifier else Modifier.testTag(tag)).padding(16.dp)) {
        RitText(title, RitType.label)
        RitText(detail, RitType.small, colors.muted)
    }
}

@Composable
private fun PeriodPicker(onRange: (InsightRange) -> Unit) {
    var selected by remember { mutableStateOf(InsightPeriod.MONTH) }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    val today = LocalDate.now(RIT_TIME_ZONE)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        InsightPeriod.entries.forEach { period -> RitChip(period.label, selected == period, {
            selected = period
            if (period != InsightPeriod.CUSTOM) onRange(period.range(today))
        }) }
    }
    if (selected == InsightPeriod.CUSTOM) {
        Spacer(Modifier.height(8.dp))
        RitTextField(start, { start = it }, "From", "YYYY-MM-DD", Modifier.fillMaxWidth(), inputTag = "insight_start")
        Spacer(Modifier.height(7.dp))
        RitTextField(end, { end = it }, "Through", "YYYY-MM-DD", Modifier.fillMaxWidth(), inputTag = "insight_end")
        val parsed = runCatching { InsightRange(LocalDate.parse(start), LocalDate.parse(end)) }.getOrNull()
        if (parsed != null && parsed.valid()) RitSecondaryButton("Apply date range", { onRange(parsed) }, Modifier.fillMaxWidth())
        else RitText("Enter a valid start and end date.", RitType.small, RitThemeTokens.colors.muted)
    }
}

@Composable
private fun IllustrativeForecastChart(days: List<edu.msrit.canteen.customer.DailySales>, today: LocalDate) {
    val colors = RitThemeTokens.colors
    val shown = days.takeLast(8)
    val history = shown.map { it.netPaise.toFloat() }
    if (history.isEmpty()) { RitEmptyState("No demo history", "Select a sample period to see an illustration."); return }
    val average = history.average().toFloat()
    val projection = (1..5).map { offset -> average * (1f + ((offset % 3) - 1) * 0.09f) }
    val values = history + projection
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Canvas(Modifier.fillMaxWidth().height(190.dp).semantics {
        contentDescription = "Illustrative forecast demo only. Historical sample values followed by five projected points; no trained model."
    }) {
        val left = 10.dp.toPx(); val right = size.width - 10.dp.toPx()
        val bottom = size.height - 22.dp.toPx(); val top = 14.dp.toPx()
        drawLine(colors.outline, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
        val coords = values.mapIndexed { index, value -> Offset(
            left + (right - left) * index / (values.size - 1), bottom - (bottom - top) * value / max) }
        coords.take(history.size).zipWithNext().forEach { (a, b) -> drawLine(colors.accent, a, b, 3.dp.toPx()) }
        coords.drop(history.size - 1).zipWithNext().forEach { (a, b) -> drawLine(colors.muted, a, b,
            2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))) }
        coords.forEachIndexed { index, point -> drawCircle(if (index < history.size) colors.accent else colors.muted, 3.dp.toPx(), point) }
    }
    // Labels sit under their actual points: first day, the last real day, and the end of the projection.
    val lastReal = (history.size - 1).toFloat() / (values.size - 1)
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        RitText(shortDate(shown.first().day), RitType.small, colors.muted, Modifier.align(Alignment.CenterStart))
        RitText(if (shown.last().day == today) "Today" else shortDate(shown.last().day), RitType.small, colors.muted,
            Modifier.align(BiasAlignment(lastReal * 2f - 1f, 0f)))
        RitText("+5 days", RitType.small, colors.muted, Modifier.align(Alignment.CenterEnd))
    }
    Spacer(Modifier.height(12.dp))
    LineLegend(listOf("Demo sales" to false, "Illustrative projection" to true))
}

@Composable
fun SellerInsightScreen(page: SellerInsightPage, sellerId: String, identity: AuthorizedIdentity?,
                        sample: Boolean, ticketId: String?, onBack: () -> Unit,
                        onNavigate: (SellerInsightPage) -> Unit, onTicket: (String) -> Unit) {
    val today = LocalDate.now(RIT_TIME_ZONE)
    var range by remember(page) { mutableStateOf(if (page == SellerInsightPage.OVERVIEW || page == SellerInsightPage.FORECAST)
        InsightRange(today.minusDays(29), today) else InsightPeriod.MONTH.range(today)) }
    var overviewDays by remember(page) { mutableIntStateOf(30) }
    var cloudSummary by remember(sellerId, page) { mutableStateOf<SellerCloudAnalytics?>(null) }
    var cloudError by remember(sellerId, page) { mutableStateOf<String?>(null) }
    var cloudLoading by remember(sellerId, page) { mutableStateOf(false) }
    var cloudRetry by remember(sellerId, page) { mutableIntStateOf(0) }
    LaunchedEffect(sellerId, page, range, cloudRetry) {
        if (BuildConfig.RIT_CLOUD_DEMO && page in setOf(SellerInsightPage.OVERVIEW, SellerInsightPage.TRENDS)) {
            cloudLoading = true
            cloudError = null
            val result = withContext(Dispatchers.IO) { DebugSellerAnalyticsApi.summary(sellerId, range.start, range.end) }
            cloudSummary = result.value
            cloudError = result.error
            cloudLoading = false
        }
    }
    val summary = DevelopmentInsights.sales(identity, sellerId, range, sample)
    val sellerName = PreviewCatalog.sellers.find { it.id == sellerId }?.name ?: "Seller"
    val title = when (page) {
        SellerInsightPage.OVERVIEW -> "Sales Overview"; SellerInsightPage.TRENDS -> "Sales trends"
        SellerInsightPage.PRODUCTS -> "Items & categories"; SellerInsightPage.CUSTOMERS -> "Customer insights"
        SellerInsightPage.PROMOTIONS -> "Promotion performance"; SellerInsightPage.FORECAST -> "Sales forecasting"
        SellerInsightPage.REPORT_BUILDER -> "Configure report"; SellerInsightPage.REPORT_HISTORY -> "Reports & downloads"
        SellerInsightPage.SUPPORT -> "Seller support"; SellerInsightPage.NEW_TICKET -> "New support request"
        SellerInsightPage.TICKET_DETAIL -> "Support ticket"; SellerInsightPage.NOTIFICATIONS -> "Notifications"
        SellerInsightPage.PAYMENT_STATUS -> "Payment configuration"
    }
    InsightPage(title, "seller_insight_${page.name.lowercase()}", onBack,
        hasSample = sample) {
        if (BuildConfig.RIT_CLOUD_DEMO) {
            if (page in setOf(SellerInsightPage.OVERVIEW, SellerInsightPage.TRENDS)) {
                AnalyticsRangeSelector(overviewDays, { days -> overviewDays = days; range = lastDays(days, today) })
                Spacer(Modifier.height(14.dp))
                when {
                    cloudLoading -> RitText("Loading saved request counts…", RitType.body,
                        modifier = Modifier.testTag("seller_cloud_analytics_loading"))
                    cloudError != null -> {
                        RitEmptyState("Could not load analytics", cloudError!!)
                        RitSecondaryButton("Retry", { cloudRetry++ }, Modifier.fillMaxWidth()
                            .testTag("seller_cloud_analytics_retry"))
                    }
                    cloudSummary == null -> RitEmptyState("Analytics unavailable", "No server response was confirmed.")
                    else -> {
                        val current = cloudSummary!!
                        RitText("${current.simulatedOrderCount} DEMO/SIMULATED orders · ${displayPaise(current.simulatedTotalPaise)} fictional total",
                            RitType.heading, modifier = Modifier.testTag("seller_cloud_simulated_sales"))
                        RitText("Synthetic/demo transactions only. Payment verified: false. Not real sales or revenue.",
                            RitType.small, RitThemeTokens.colors.muted)
                        Spacer(Modifier.height(10.dp))
                        RitText("${current.sandboxOrderCount} PhonePe UAT sandbox orders · ${displayPaise(current.sandboxTotalPaise)} test total",
                            RitType.body, modifier = Modifier.testTag("seller_cloud_uat_sales"))
                        RitText("Separate sandbox metric · payment verified: false · no real money or settlement.",
                            RitType.small, RitThemeTokens.colors.muted)
                        Spacer(Modifier.height(12.dp))
                        RitText("${current.totalRequests} saved bulk requests", RitType.heading,
                            modifier = Modifier.testTag("seller_cloud_request_total"))
                        RitText("${range.start} to ${range.end} · ${current.status.replace('_', ' ').lowercase()}",
                            RitType.small, RitThemeTokens.colors.muted)
                        Spacer(Modifier.height(12.dp))
                        if (current.byStatus.isEmpty()) RitEmptyState("No requests in this period",
                            "Counts will appear after customers submit requests to this seller.")
                        else current.byStatus.toSortedMap().forEach { (status, count) ->
                            InsightCard(status.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
                                "$count saved requests", tag = "seller_cloud_status_${status.lowercase()}")
                        }
                        Spacer(Modifier.height(10.dp))
                        RitText("Verified revenue and forecasts are unavailable: no payment provider is connected.",
                            RitType.small, RitThemeTokens.colors.muted)
                    }
                }
            } else RitEmptyState("Feature unavailable", when (page) {
                SellerInsightPage.PRODUCTS, SellerInsightPage.CUSTOMERS, SellerInsightPage.PROMOTIONS,
                SellerInsightPage.FORECAST -> "No verified payment or product-sales source exists for this report."
                SellerInsightPage.REPORT_BUILDER, SellerInsightPage.REPORT_HISTORY ->
                    "Report selections can be stored by the backend, but file generation and download are not implemented."
                SellerInsightPage.SUPPORT, SellerInsightPage.NEW_TICKET, SellerInsightPage.TICKET_DETAIL ->
                    "Seller support is not connected to this Android screen yet."
                else -> "This seller workflow is not connected to the cloud backend yet."
            })
            return@InsightPage
        }
        when (page) {
            SellerInsightPage.OVERVIEW -> {
                val period = when (overviewDays) { 1 -> "Today so far"; 7 -> "Last 7 days"; else -> "Last 30 days" }
                RitText(sellerName, RitType.heading)
                Spacer(Modifier.height(14.dp))
                AnalyticsRangeSelector(overviewDays, { days -> overviewDays = days; range = lastDays(days, today) })
                Spacer(Modifier.height(16.dp))
                if (summary.orders == 0) RitEmptyState("No verified sales history", "Real analytics will appear after provider-verified app payments.")
                else {
                    RevenuePanel(summary.netPaise, period, chart = if (overviewDays == 1) null else ({
                        SalesBarChart(fillDays(summary.daily, range), period.lowercase())
                    }))
                    Spacer(Modifier.height(10.dp))
                    StatStrip(listOf(
                        StatCell("Orders", "${summary.orders}", "overview_orders"),
                        StatCell("Average order", formatRupees(summary.averagePaise), "overview_average"),
                        StatCell("Repeat buyers", "${summary.returningCustomers}", "overview_repeat"),
                    ))
                    Spacer(Modifier.height(26.dp))
                    RitText("Best sellers", RitType.title)
                    Spacer(Modifier.height(4.dp))
                    summary.products.take(3).forEach { row ->
                        val item = PreviewCatalog.items.find { it.id == row.id }
                        RankedItemRow(row.label, row.units, row.netPaise, row.netPaise.toFloat() / summary.products.first().netPaise,
                            item?.photo, item?.localImageUri)
                    }
                }
                Spacer(Modifier.height(26.dp))
                RitText("Explore your business", RitType.title)
                Spacer(Modifier.height(10.dp))
                InsightNavList(listOf(
                    Triple("Sales trends", "Daily, weekly and monthly totals", SellerInsightPage.TRENDS) to RitIconKind.Orders,
                    Triple("Items & categories", "What sells most, by value and units", SellerInsightPage.PRODUCTS) to RitIconKind.Food,
                    Triple("Customers", "Unique and repeat buyers", SellerInsightPage.CUSTOMERS) to RitIconKind.Person,
                    Triple("Promotions", "Your offers and discounts given", SellerInsightPage.PROMOTIONS) to RitIconKind.Heart,
                    Triple("Forecasting", "Illustrative demo only", SellerInsightPage.FORECAST) to RitIconKind.Arrow,
                    Triple("Reports", "Set up a sales workbook", SellerInsightPage.REPORT_BUILDER) to RitIconKind.Bulk,
                ).map { (entry, icon) ->
                    val (label, detail, destination) = entry
                    InsightNavEntry(label, detail, icon, "overview_open_${destination.name.lowercase()}") { onNavigate(destination) }
                })
            }
            SellerInsightPage.TRENDS -> {
                var grouping by remember { mutableStateOf(TrendGrouping.DAILY) }
                PeriodPicker { range = it }
                Spacer(Modifier.height(10.dp))
                AnalyticsToggle(TrendGrouping.entries.map { it to it.label }, grouping, { grouping = it }, tagPrefix = "trend")
                Spacer(Modifier.height(16.dp))
                if (summary.orders == 0) RitEmptyState("No sales in range", "Only future verified app payments can populate real trends.")
                else {
                    val grouped = groupSales(fillDays(summary.daily, range), grouping)
                    val best = grouped.maxBy { it.netPaise }
                    val noun = when (grouping) { TrendGrouping.DAILY -> "day"; TrendGrouping.WEEKLY -> "week"; TrendGrouping.MONTHLY -> "month" }
                    RevenuePanel(summary.netPaise, formatRange(range), chart = {
                        SalesBarChart(grouped, formatRange(range), grouping = grouping)
                        if (grouping != TrendGrouping.DAILY) {
                            Spacer(Modifier.height(10.dp))
                            val which = if (grouped.size > 1) "The first and last ${noun}s" else "This $noun"
                            RitText("${if (grouping == TrendGrouping.WEEKLY) "Weeks start on Monday. " else ""}$which may only " +
                                "partly fall in this range.", RitType.small, RitThemeTokens.colors.muted)
                        }
                    })
                    Spacer(Modifier.height(10.dp))
                    StatStrip(listOf(
                        StatCell("Orders", "${summary.orders}"),
                        StatCell("Average order", formatRupees(summary.averagePaise)),
                        StatCell("Best $noun · ${grouping.periodName(best.day).removePrefix("week of ")}", formatRupees(best.netPaise), "trends_best"),
                    ))
                }
            }
            SellerInsightPage.PRODUCTS -> {
                var byUnits by remember { mutableStateOf(false) }
                PeriodPicker { range = it }
                Spacer(Modifier.height(16.dp))
                if (summary.orders == 0) RitEmptyState("No product sales", "No eligible paid history for this period.")
                else {
                    fun metric(row: MetricRow) = if (byUnits) row.units.toLong() else row.netPaise
                    AnalyticsToggle(listOf(false to "Revenue", true to "Units"), byUnits, { byUnits = it }, tagPrefix = "products_by")
                    Spacer(Modifier.height(20.dp))
                    RitText("Items", RitType.title)
                    Spacer(Modifier.height(8.dp))
                    val items = summary.products.sortedByDescending(::metric)
                    AnalyticsCard {
                        items.forEach { row ->
                            val item = PreviewCatalog.items.find { it.id == row.id }
                            RankedItemRow(row.label, row.units, row.netPaise, metric(row).toFloat() / metric(items.first()).coerceAtLeast(1),
                                item?.photo, item?.localImageUri)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    RitText("Categories", RitType.title)
                    Spacer(Modifier.height(8.dp))
                    val slices = summary.categories.sortedByDescending(::metric).map {
                        ShareSlice(it.label, metric(it), if (byUnits) "${it.units} units" else formatRupees(it.netPaise))
                    }
                    val shares = percentShares(slices.map { it.value })
                    AnalyticsCard {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            DonutChart(slices,
                                if (byUnits) "${summary.categories.sumOf { it.units }}" else formatRupees(summary.netPaise),
                                if (byUnits) "units" else "revenue",
                                "Category share by ${if (byUnits) "units" else "revenue"}: " +
                                    slices.indices.joinToString { "${slices[it].label} ${shares[it]}%" } + ".")
                        }
                        Spacer(Modifier.height(14.dp))
                        ShareLegend(slices)
                    }
                }
            }
            SellerInsightPage.CUSTOMERS -> {
                PeriodPicker { range = it }
                Spacer(Modifier.height(16.dp))
                if (summary.orders == 0) RitEmptyState("No purchasing insights", "Insights require eligible app-payment history.")
                else {
                    val oneTime = summary.customers - summary.returningCustomers
                    StatStrip(listOf(
                        StatCell("Unique buyers", "${summary.customers}", "customers_unique"),
                        StatCell("Repeat buyers", "${summary.returningCustomers}", "customers_repeat"),
                        StatCell("One-time buyers", "$oneTime", "customers_one_time"),
                    ))
                    Spacer(Modifier.height(10.dp))
                    val slices = listOf(ShareSlice("Repeat buyers", summary.returningCustomers.toLong(), "${summary.returningCustomers}"),
                        ShareSlice("One-time buyers", oneTime.toLong(), "$oneTime"))
                    val shares = percentShares(slices.map { it.value })
                    AnalyticsCard {
                        RitText("Repeat vs one-time", RitType.label)
                        Spacer(Modifier.height(12.dp))
                        ShareBar(slices, "Buyers: ${shares[0]}% repeat, ${shares[1]}% one-time.")
                        Spacer(Modifier.height(8.dp))
                        ShareLegend(slices)
                    }
                    Spacer(Modifier.height(12.dp))
                    RitText("Repeat means 2 or more orders in the selected period. Only totals are shown, never individual customers.",
                        RitType.small, RitThemeTokens.colors.muted)
                }
            }
            SellerInsightPage.PROMOTIONS -> {
                val offers = DevelopmentPromotions.promotions.filter { it.sellerId == sellerId }
                if (offers.isEmpty()) RitEmptyState("No promotions", "Create a seller promotion to preview its state.")
                else {
                    val active = offers.count { it.published && it.statusAt(java.time.Instant.now()) == edu.msrit.canteen.customer.PromotionStatus.ACTIVE }
                    StatStrip(listOf(
                        StatCell("Active offers", "$active", "promotions_active"),
                        StatCell("Discounts given this month", formatRupees(summary.discountPaise), "promotions_discounts"),
                    ))
                    Spacer(Modifier.height(22.dp))
                    RitText("Your offers", RitType.title)
                    Spacer(Modifier.height(8.dp))
                    offers.forEach { promotion ->
                        PromotionCard(promotion, sellerName, java.time.Instant.now(), {}, PreviewCatalog)
                        Spacer(Modifier.height(9.dp))
                    }
                }
                InsightCard("Offer results aren't measured yet", "Views, taps and paid orders from an offer need consented tracking and verified payments.")
            }
            SellerInsightPage.FORECAST -> {
                if (!sample || summary.orders == 0) RitEmptyState("Insufficient historical data",
                    "Predictions become available only after enough verified app sales accumulate. No model is trained or confidence claimed.")
                else {
                    RitText("Illustrative Forecast — Demo Only", RitType.heading)
                    Spacer(Modifier.height(4.dp))
                    RitText("A visual example from synthetic sales. Not a trained model or a reliable prediction.",
                        RitType.body, RitThemeTokens.colors.muted)
                    Spacer(Modifier.height(16.dp))
                    AnalyticsCard { IllustrativeForecastChart(fillDays(summary.daily, range), today) }
                    Spacer(Modifier.height(24.dp))
                    RitText("Illustrative next-week demand", RitType.title)
                    Spacer(Modifier.height(8.dp))
                    AnalyticsCard {
                        summary.products.take(3).forEachIndexed { index, row ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth()) {
                                RitText(row.label, RitType.label, modifier = Modifier.weight(1f))
                                RitText("about ${((row.units / 30.0) * 7).toInt().coerceAtLeast(1)} units",
                                    RitType.label.tabular(), RitThemeTokens.colors.muted)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    RitText("Walk-in sales, events and kitchen capacity are not included.", RitType.small, RitThemeTokens.colors.muted)
                }
            }
            SellerInsightPage.REPORT_BUILDER -> ReportBuilder(sellerId, identity, onNavigate)
            SellerInsightPage.REPORT_HISTORY -> {
                val history = DevelopmentInsights.reports(identity, sellerId)
                if (history.isEmpty()) RitEmptyState("No generated reports", "Real XLSX generation and secure download are not available yet.")
                else AnalyticsCard {
                    history.forEachIndexed { index, row ->
                        if (index > 0) Spacer(Modifier.height(16.dp))
                        RitText(row.title, RitType.label)
                        RitText("${formatRange(row.range)} · saved ${weekdayDate(row.generatedAt.toLocalDate())}, " +
                            savedTime.format(row.generatedAt), RitType.small, RitThemeTokens.colors.muted)
                        Spacer(Modifier.height(8.dp))
                        CatalogPill(row.status)
                    }
                }
                Spacer(Modifier.height(12.dp))
                InsightCard("Download unavailable", "No financial workbook has been generated or saved on this device.")
            }
            SellerInsightPage.SUPPORT -> {
                InsightCard("Create a local sample request", "Nothing is sent to a support agent", { onNavigate(SellerInsightPage.NEW_TICKET) })
                val tickets = DevelopmentInsights.tickets(identity, sellerId)
                if (tickets.isEmpty()) RitEmptyState("No sample tickets", "No seller support history is available.")
                else tickets.forEach { InsightCard(it.subject, "${it.id} · ${it.status.label} · ${it.category.label}", { onTicket(it.id) }) }
            }
            SellerInsightPage.NEW_TICKET -> SellerTicketForm(sellerId, identity, onBack)
            SellerInsightPage.TICKET_DETAIL -> {
                val ticket = ticketId?.let { DevelopmentInsights.ticket(identity, it, sellerId) }
                if (ticket == null) RitEmptyState("Ticket unavailable", "It may belong to another seller or no longer exist.")
                else { InsightCard(ticket.subject, "${ticket.id} · ${ticket.status.label} · ${ticket.category.label}")
                    InsightCard("Sample issue", ticket.detail)
                    InsightCard("Routing", if (ticket.escalated) "Escalated to platform support · demonstration" else "Seller-handled · demonstration")
                    InsightCard("No live message", "No customer or support agent was contacted.") }
            }
            SellerInsightPage.NOTIFICATIONS -> {
                RitText("Local preference preview", RitType.title)
                listOf("Requests", "Schedule", "Support", "Promotions").forEach { key ->
                    val enabled = key in DevelopmentInsights.notificationChoices(identity, sellerId)
                    InsightCard(key, if (enabled) "On · local only" else "Off · local only", {
                        DevelopmentInsights.setNotificationChoice(identity, sellerId, key, !enabled)
                    })
                }
                InsightCard("Delivery not connected", "No push, email or durable in-app notification is sent by this preview.")
            }
            SellerInsightPage.PAYMENT_STATUS -> {
                InsightCard("PhonePe integration", "Unverified · merchant eligibility and settlement routing pending")
                InsightCard("Payment configuration", "No merchant credentials collected; no checkout or payout enabled.")
            }
        }
    }
}

@Composable
private fun ReportBuilder(sellerId: String, identity: AuthorizedIdentity?, onNavigate: (SellerInsightPage) -> Unit) {
    val colors = RitThemeTokens.colors
    val today = LocalDate.now(RIT_TIME_ZONE)
    val presets = listOf("This month" to InsightPeriod.MONTH.range(today), "Last 7 days" to lastDays(7, today),
        "Last 30 days" to lastDays(30, today), "Custom" to null)
    var preset by remember { mutableStateOf(presets.first().first) }
    var start by remember { mutableStateOf(today.withDayOfMonth(1).toString()) }
    var end by remember { mutableStateOf(today.toString()) }
    var type by remember { mutableStateOf(ReportType.SALES) }
    var message by remember { mutableStateOf("") }
    var selectedItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    RitText("Date range", RitType.title)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        presets.forEach { (label, presetRange) ->
            RitChip(label, preset == label, {
                preset = label
                if (presetRange != null) { start = presetRange.start.toString(); end = presetRange.end.toString() }
            }, Modifier.testTag("report_range_${label.lowercase().replace(' ', '_')}"))
        }
    }
    if (preset == "Custom") {
        Spacer(Modifier.height(10.dp))
        RitTextField(start, { start = it }, "From", "e.g. 2026-10-01", Modifier.fillMaxWidth(), inputTag = "report_start")
        Spacer(Modifier.height(8.dp))
        RitTextField(end, { end = it }, "Through", "e.g. 2026-10-26", Modifier.fillMaxWidth(), inputTag = "report_end")
    }
    Spacer(Modifier.height(20.dp))
    RitText("Report type", RitType.title)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ReportType.entries.forEach { value -> RitChip(value.label, value == type, { type = value }) }
    }
    Spacer(Modifier.height(20.dp))
    RitText("Items", RitType.title)
    RitText("All items are included unless you pick some.", RitType.small, colors.muted)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        PreviewCatalog.items.filter { it.sellerId == sellerId }.forEach { item ->
            RitChip(item.name, item.id in selectedItems, { selectedItems = if (item.id in selectedItems)
                selectedItems - item.id else selectedItems + item.id })
        }
    }
    Spacer(Modifier.height(20.dp))
    RitText("Workbook sheets", RitType.title)
    Spacer(Modifier.height(8.dp))
    AnalyticsCard {
        listOf("Sales summary", "Item-wise sales", "Category-wise sales", "Order details", "Daily breakdown").forEachIndexed { index, sheet ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(6.dp).height(6.dp).background(colors.accent, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(12.dp))
                RitText(sheet, RitType.body)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    val parsed = runCatching { InsightRange(LocalDate.parse(start), LocalDate.parse(end)) }.getOrNull()
    val selection = parsed?.let { ReportSelection(sellerId, type, it, selectedItems) }
    val error = if (selection == null) "Enter dates as YYYY-MM-DD" else
        validateReport(selection, PreviewCatalog.items.filter { item -> item.sellerId == sellerId }.map { item -> item.id }.toSet())
    if (error != null) RitText(error, RitType.small, colors.accent)
    else RitText("${type.label} · ${parsed?.let(::formatRange)} · ${if (selectedItems.isEmpty()) "all items" else
        "${selectedItems.size} ${if (selectedItems.size == 1) "item" else "items"}"}", RitType.label, modifier = Modifier.testTag("report_summary"))
    Spacer(Modifier.height(12.dp))
    RitPrimaryButton("Save sample configuration", {
        message = if (selection != null && DevelopmentInsights.saveReportPreview(identity, selection))
            "Sample configuration saved. No XLSX generated." else "Unable to save sample configuration."
    }, Modifier.fillMaxWidth(), enabled = error == null)
    if (message.isNotEmpty()) { Spacer(Modifier.height(8.dp)); RitText(message, RitType.small, colors.accent) }
    Spacer(Modifier.height(10.dp))
    RitSecondaryButton("Reports & downloads", { onNavigate(SellerInsightPage.REPORT_HISTORY) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    RitText("XLSX export isn't available yet. It will use verified, seller-scoped sales.", RitType.small, colors.muted)
}

@Composable
private fun SellerTicketForm(sellerId: String, identity: AuthorizedIdentity?, onSaved: () -> Unit) {
    var subject by remember { mutableStateOf("") }; var detail by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TicketCategory.FOOD) }; var message by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        TicketCategory.entries.forEach { RitChip(it.label, category == it, { category = it }) }
    }
    RitTextField(subject, { subject = it }, "Subject", "Brief issue summary", Modifier.fillMaxWidth())
    RitTextField(detail, { detail = it }, "Details", "Describe the issue", Modifier.fillMaxWidth(), singleLine = false)
    RitPrimaryButton("Create local sample ticket", {
        message = if (DevelopmentInsights.createSellerTicket(identity, sellerId, category, subject, detail))
            "Saved locally. No support agent notified." else "Enter a subject and details."
    }, Modifier.fillMaxWidth())
    RitText(message, RitType.small, RitThemeTokens.colors.accent)
    RitSecondaryButton("Back to support", onSaved, Modifier.fillMaxWidth())
}
