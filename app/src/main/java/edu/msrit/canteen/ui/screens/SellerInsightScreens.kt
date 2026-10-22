package edu.msrit.canteen.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.DevelopmentInsights
import edu.msrit.canteen.customer.DevelopmentPromotions
import edu.msrit.canteen.customer.InsightPeriod
import edu.msrit.canteen.customer.InsightRange
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.ReportSelection
import edu.msrit.canteen.customer.ReportType
import edu.msrit.canteen.customer.SalesSummary
import edu.msrit.canteen.customer.MetricRow
import edu.msrit.canteen.customer.TicketCategory
import edu.msrit.canteen.customer.TicketStatus
import edu.msrit.canteen.customer.TrendGrouping
import edu.msrit.canteen.customer.groupSales
import edu.msrit.canteen.customer.range
import edu.msrit.canteen.customer.statusAt
import edu.msrit.canteen.customer.validateReport
import edu.msrit.canteen.navigation.AuthorizedIdentity
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitIcon
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SellerInsightPage { OVERVIEW, TRENDS, PRODUCTS, CUSTOMERS, PROMOTIONS, FORECAST,
    REPORT_BUILDER, REPORT_HISTORY, SUPPORT, NEW_TICKET, TICKET_DETAIL, NOTIFICATIONS, PAYMENT_STATUS }

private fun money(paise: Long): String = "₹" + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"))
    .format(paise / 100.0)

@Composable
private fun InsightPage(title: String, tag: String, onBack: () -> Unit,
                        hasSample: Boolean = true, content: @Composable () -> Unit) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).testTag(tag)) {
        CatalogHeader(title, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            RitText(if (hasSample) "DEMO DATA · not live sales"
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
private fun SalesChart(summary: SalesSummary, points: List<edu.msrit.canteen.customer.DailySales> = summary.daily) {
    if (points.isEmpty()) { RitEmptyState("No eligible sales", "No verified app-payment history exists in this view."); return }
    val colors = RitThemeTokens.colors
    val max = (points.maxOfOrNull { it.netPaise } ?: 0L).coerceAtLeast(1L)
    Canvas(Modifier.fillMaxWidth().height(170.dp).semantics {
        contentDescription = "Sample daily sales trend: " + points.joinToString { "${it.day}: ${money(it.netPaise)}" }
    }) {
        val left = 8.dp.toPx(); val right = size.width - 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx(); val top = 14.dp.toPx()
        drawLine(colors.outline, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
        drawLine(colors.outline, Offset(left, top), Offset(left, bottom), 1.dp.toPx())
        val coords = points.mapIndexed { index, point -> Offset(
            if (points.size == 1) (left + right) / 2 else left + (right - left) * index / (points.size - 1),
            bottom - (bottom - top) * point.netPaise.toFloat() / max) }
        coords.zipWithNext().forEach { (a, b) -> drawLine(colors.accent, a, b, 3.dp.toPx()) }
        coords.forEach { drawCircle(colors.accent, 4.dp.toPx(), it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        RitText(points.first().day.toString(), RitType.small, colors.muted)
        RitText("₹0 to ${money(max)}", RitType.small, colors.muted)
        RitText(points.last().day.toString(), RitType.small, colors.muted)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun OverviewMetric(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Column(modifier.border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
        .background(colors.card, RoundedCornerShape(RitRadii.card)).padding(14.dp)) {
        RitText(label, RitType.small, colors.muted)
        Spacer(Modifier.height(4.dp))
        RitText(value, RitType.heading)
        RitText(detail, RitType.small, colors.muted)
    }
}

@Composable
private fun PerformanceBar(row: MetricRow, maximum: Long, rank: Int? = null) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RitText("${rank?.let { "$it. " }.orEmpty()}${row.label}", RitType.label, modifier = Modifier.weight(1f))
            RitText(money(row.netPaise), RitType.label, colors.accent)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(colors.accentSoft, RoundedCornerShape(8.dp))) {
            Box(Modifier.fillMaxWidth((row.netPaise.toFloat() / maximum.coerceAtLeast(1L)).coerceIn(0.03f, 1f))
                .height(8.dp).background(colors.accent, RoundedCornerShape(8.dp)))
        }
        RitText("${row.units} units", RitType.small, colors.muted)
    }
}

@Composable
private fun OverviewLink(label: String, icon: RitIconKind, onClick: () -> Unit, tag: String) {
    val colors = RitThemeTokens.colors
    Row(Modifier.fillMaxWidth().border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
        .background(colors.card, RoundedCornerShape(RitRadii.card)).clickable(onClick = onClick)
        .testTag(tag).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RitIcon(icon, colors.accent, size = 22.dp)
        RitText(label, RitType.label, modifier = Modifier.weight(1f))
        RitText("›", RitType.label, colors.accent)
    }
}

@Composable
private fun IllustrativeForecastChart(summary: SalesSummary) {
    val colors = RitThemeTokens.colors
    val history = summary.daily.takeLast(8).map { it.netPaise.toFloat() }
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
    RitText("Solid: historical demo sales   - - -  Illustrative projection", RitType.small, colors.muted)
}

@Composable
fun SellerInsightScreen(page: SellerInsightPage, sellerId: String, identity: AuthorizedIdentity?,
                        sample: Boolean, ticketId: String?, onBack: () -> Unit,
                        onNavigate: (SellerInsightPage) -> Unit, onTicket: (String) -> Unit) {
    val today = LocalDate.now(RIT_TIME_ZONE)
    var range by remember(page) { mutableStateOf(if (page == SellerInsightPage.OVERVIEW || page == SellerInsightPage.FORECAST)
        InsightRange(today.minusDays(29), today) else InsightPeriod.MONTH.range(today)) }
    var overviewDays by remember(page) { mutableIntStateOf(30) }
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
        when (page) {
            SellerInsightPage.OVERVIEW -> {
                RitText(sellerName, RitType.small, RitThemeTokens.colors.muted)
                RitText("Sales Overview", RitType.heading)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(1 to "Today", 7 to "7 Days", 30 to "30 Days").forEach { (days, label) ->
                        RitChip(label, overviewDays == days, {
                            overviewDays = days; range = InsightRange(today.minusDays(days.toLong() - 1), today)
                        }, Modifier.weight(1f).testTag("overview_range_$days"))
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (summary.orders == 0) RitEmptyState("No verified sales history", "Real analytics will appear after provider-verified app payments.")
                else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OverviewMetric("Total Sales", money(summary.netPaise), "App demo sales", Modifier.weight(1f).testTag("sample_net_sales"))
                        OverviewMetric("Orders", "${summary.orders}", "Demo records", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OverviewMetric("Average Order", money(summary.averagePaise), "Per demo order", Modifier.weight(1f))
                        OverviewMetric("Returning Buyers", "${summary.returningCustomers}", "Sample customers", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(22.dp))
                    RitText("Sales trend", RitType.title)
                    SalesChart(summary)
                    RitText("Best-selling items", RitType.title)
                    summary.products.take(3).forEachIndexed { index, row ->
                        PerformanceBar(row, summary.products.first().netPaise, index + 1)
                    }
                    Spacer(Modifier.height(13.dp))
                    RitText("Category performance", RitType.title)
                    summary.categories.take(4).forEach { row ->
                        PerformanceBar(row, summary.categories.first().netPaise)
                    }
                }
                Spacer(Modifier.height(16.dp))
                RitText("Explore your business", RitType.title)
                listOf(
                    Triple("Sales Trends", RitIconKind.Orders, SellerInsightPage.TRENDS),
                    Triple("Products", RitIconKind.Food, SellerInsightPage.PRODUCTS),
                    Triple("Customers", RitIconKind.Person, SellerInsightPage.CUSTOMERS),
                    Triple("Promotions", RitIconKind.Heart, SellerInsightPage.PROMOTIONS),
                    Triple("Forecasting", RitIconKind.Arrow, SellerInsightPage.FORECAST),
                    Triple("Reports", RitIconKind.Bulk, SellerInsightPage.REPORT_BUILDER),
                ).chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { (label, icon, destination) ->
                            Box(Modifier.weight(1f)) { OverviewLink(label, icon, { onNavigate(destination) },
                                "overview_open_${destination.name.lowercase()}") }
                        }
                    }
                }
            }
            SellerInsightPage.TRENDS -> {
                var grouping by remember { mutableStateOf(TrendGrouping.DAILY) }
                RitText("Sample sales over time", RitType.title)
                PeriodPicker { range = it }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TrendGrouping.entries.forEach { mode -> RitChip(mode.label, grouping == mode, { grouping = mode },
                        Modifier.testTag("trend_${mode.name.lowercase()}")) }
                }
                if (summary.orders == 0) RitEmptyState("No sales in range", "Only future verified app payments can populate real trends.")
                else { val grouped = groupSales(summary.daily, grouping)
                    InsightCard("Sample net sales", money(summary.netPaise)); SalesChart(summary, grouped)
                    grouped.forEach { InsightCard(it.day.toString(), "${it.orders} paid sample orders · ${money(it.netPaise)}") } }
            }
            SellerInsightPage.PRODUCTS -> {
                PeriodPicker { range = it }
                if (summary.orders == 0) RitEmptyState("No product sales", "No eligible paid history for this period.")
                else {
                    RitText("Best-selling products · sample", RitType.title)
                    summary.products.forEach { InsightCard(it.label, "${it.units} units · ${money(it.netPaise)} sample net") }
                    RitText("Category contribution · sample", RitType.title)
                    summary.categories.forEach { InsightCard(it.label, "${it.units} units · ${money(it.netPaise)} sample net") }
                }
            }
            SellerInsightPage.CUSTOMERS -> {
                PeriodPicker { range = it }
                if (summary.orders == 0) RitEmptyState("No purchasing insights", "Insights require eligible app-payment history.")
                else { InsightCard("Sample unique buyers", summary.customers.toString())
                    InsightCard("Sample returning buyers", summary.returningCustomers.toString())
                    InsightCard("Privacy", "Only aggregate counts are shown; no student records or personal profiles.") }
            }
            SellerInsightPage.PROMOTIONS -> {
                val offers = DevelopmentPromotions.promotions.filter { it.sellerId == sellerId }
                if (offers.isEmpty()) RitEmptyState("No promotions", "Create a seller promotion to preview its state.")
                else {
                    val active = offers.count { it.published && it.statusAt(java.time.Instant.now()) == edu.msrit.canteen.customer.PromotionStatus.ACTIVE }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OverviewMetric("Active offers", "$active", "Demo placements", Modifier.weight(1f))
                        OverviewMetric("Sample discounts", money(summary.discountPaise), "Not attributed", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    RitText("Offer previews", RitType.title)
                    offers.forEach { promotion ->
                        PromotionCard(promotion, sellerName, java.time.Instant.now(), {}, PreviewCatalog)
                        Spacer(Modifier.height(9.dp))
                    }
                }
                InsightCard("Campaign results pending", "Views, taps and paid-order attribution need consented events and verified payment data.")
            }
            SellerInsightPage.FORECAST -> {
                if (!sample || summary.orders == 0) RitEmptyState("Insufficient historical data",
                    "Predictions become available only after enough verified app sales accumulate. No model is trained or confidence claimed.")
                else {
                    RitText("Illustrative Forecast — Demo Only", RitType.heading)
                    RitText("A visual example based on synthetic app-sale patterns. Not a trained model or a reliable operational prediction.",
                        RitType.body, RitThemeTokens.colors.muted)
                    Spacer(Modifier.height(14.dp))
                    IllustrativeForecastChart(summary)
                    Spacer(Modifier.height(18.dp))
                    RitText("Illustrative next-week demand", RitType.title)
                    summary.products.take(3).forEach { row ->
                        InsightCard(row.label, "About ${((row.units / 30.0) * 7).toInt().coerceAtLeast(1)} units in this mock illustration")
                    }
                    RitText("Walk-in sales, events and kitchen capacity are not included.", RitType.small, RitThemeTokens.colors.muted)
                }
            }
            SellerInsightPage.REPORT_BUILDER -> ReportBuilder(sellerId, identity, onNavigate)
            SellerInsightPage.REPORT_HISTORY -> {
                val history = DevelopmentInsights.reports(identity, sellerId)
                if (history.isEmpty()) RitEmptyState("No generated reports", "Real XLSX generation and secure download are not available yet.")
                else history.forEach { InsightCard(it.title, "${it.range.start}–${it.range.end} · ${it.status} · ${it.generatedAt}") }
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
    var start by remember { mutableStateOf(LocalDate.now(RIT_TIME_ZONE).withDayOfMonth(1).toString()) }
    var end by remember { mutableStateOf(LocalDate.now(RIT_TIME_ZONE).toString()) }
    var type by remember { mutableStateOf(ReportType.SALES) }
    var message by remember { mutableStateOf("") }
    var selectedItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    RitText("Five-sheet XLSX specification", RitType.title)
    InsightCard("Planned worksheets", "Sales Summary · Item-wise Sales · Category-wise Sales · Order Details · Daily Breakdown")
    RitTextField(start, { start = it }, "From", "YYYY-MM-DD", Modifier.fillMaxWidth(), inputTag = "report_start")
    Spacer(Modifier.height(8.dp))
    RitTextField(end, { end = it }, "Through", "YYYY-MM-DD", Modifier.fillMaxWidth(), inputTag = "report_end")
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ReportType.entries.forEach { value -> RitChip(value.label, value == type, { type = value }) }
    }
    RitText("Optional product filter", RitType.label)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        PreviewCatalog.items.filter { it.sellerId == sellerId }.forEach { item ->
            RitChip(item.name, item.id in selectedItems, { selectedItems = if (item.id in selectedItems)
                selectedItems - item.id else selectedItems + item.id })
        }
    }
    val parsed = runCatching { InsightRange(LocalDate.parse(start), LocalDate.parse(end)) }.getOrNull()
    val selection = parsed?.let { ReportSelection(sellerId, type, it, selectedItems) }
    val error = if (selection == null) "Enter valid ISO dates" else
        validateReport(selection, PreviewCatalog.items.filter { item -> item.sellerId == sellerId }.map { item -> item.id }.toSet())
    if (error != null) RitText(error, RitType.small, RitThemeTokens.colors.accent)
    else InsightCard("Preview", "${type.label} · ${parsed?.start}–${parsed?.end} · ${if (selectedItems.isEmpty()) "all products" else "${selectedItems.size} products"}")
    Spacer(Modifier.height(8.dp))
    RitPrimaryButton("Save sample configuration", {
        message = if (selection != null && DevelopmentInsights.saveReportPreview(identity, selection))
            "Sample configuration saved. No XLSX generated." else "Unable to save sample configuration."
    }, Modifier.fillMaxWidth(), enabled = error == null)
    RitText(message, RitType.small, RitThemeTokens.colors.accent)
    RitSecondaryButton("Reports & downloads", { onNavigate(SellerInsightPage.REPORT_HISTORY) }, Modifier.fillMaxWidth())
    InsightCard("Real export unavailable", "Prompt 24 will generate XLSX from verified, seller-scoped transaction history.")
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
