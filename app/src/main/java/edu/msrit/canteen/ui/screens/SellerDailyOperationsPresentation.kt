package edu.msrit.canteen.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.BulkDraft
import edu.msrit.canteen.customer.BulkLine
import edu.msrit.canteen.customer.BulkRequestRecord
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.QuoteActor
import edu.msrit.canteen.customer.QuoteRevision
import edu.msrit.canteen.customer.QuoteStage
import edu.msrit.canteen.customer.QuoteThread
import edu.msrit.canteen.customer.RIT_TIME_ZONE
import edu.msrit.canteen.customer.SellerRequestStatus
import edu.msrit.canteen.customer.SellerDisplayPreferences
import edu.msrit.canteen.customer.compareQuotes
import edu.msrit.canteen.customer.operationalStatus
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.FoodPhotoContent
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitIcon
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitMessageCard
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.components.SellerBottomItem
import edu.msrit.canteen.ui.components.SellerBottomNavigation
import edu.msrit.canteen.ui.screens.displayPaise
import edu.msrit.canteen.ui.screens.displayPickup
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitTheme
import edu.msrit.canteen.ui.theme.RitType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Seller-focused daily operations presentation. The host supplies seller-scoped records,
 * capability-aware destinations, and callbacks; this component does not own permissions or state.
 */
@Composable
fun SellerDailyOperationsOverview(
    records: List<BulkRequestRecord>,
    sellerId: String,
    sellerName: String,
    roleLabel: String,
    canRespond: Boolean,
    onBack: () -> Unit,
    onOpenRequest: (String) -> Unit,
    onOpenRequests: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenMenu: (() -> Unit)?,
    onOpenSales: (() -> Unit)?,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    catalog: CustomerCatalogSource = PreviewCatalog,
    now: LocalDateTime = LocalDateTime.now(RIT_TIME_ZONE),
    onOpenManagement: (() -> Unit)? = null,
    onOpenHistory: (() -> Unit)? = null,
    onOpenReports: (() -> Unit)? = null,
    onOpenSupport: (() -> Unit)? = null,
    onOpenAccount: (() -> Unit)? = null,
    onBottom: ((SellerBottomItem) -> Unit)? = null,
    canViewInsights: Boolean = true,
    newPaidIndividualOrders: Int = 0,
    onOpenIndividual: (() -> Unit)? = null,
    onVerifyPickup: (() -> Unit)? = null,
    onOpenHours: (() -> Unit)? = null,
    onOpenPromotions: (() -> Unit)? = null,
) {
    val colors = RitThemeTokens.colors
    val sellerRecords = records.filter { it.sellerId == sellerId }
    val needsReply = sellerRecords
        .filter { it.operationalStatus() in NEEDS_SELLER_RESPONSE && !isRequestQuoteExpired(it, now) }
        .sortedWith(
            compareBy<BulkRequestRecord> { responsePriority(it) }
                .thenByDescending { it.createdAt },
        )
    val upcoming = sellerRecords
        .filter { record ->
            record.thread.stage in UPCOMING_STAGES &&
                record.latest.pickup.isAfter(now) &&
                !isRequestQuoteExpired(record, now)
        }
        .sortedBy { it.latest.pickup }
    val nextPickup = upcoming.firstOrNull()
    // Paid individual orders waiting to be prepared.
    val newIndividual = newPaidIndividualOrders
    val newBulk = sellerRecords.count { it.thread.stage == QuoteStage.SUBMITTED && !isRequestQuoteExpired(it, now) }
    val waiting = sellerRecords.count {
        it.thread.stage == QuoteStage.AGREED_AWAITING_PAYMENT ||
            (it.thread.stage == QuoteStage.COUNTEROFFER && it.thread.sellerAcceptedVersion == it.latest.version)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("seller_daily_overview"),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            RitText("Seller Dashboard", RitType.heading, modifier = Modifier.testTag("seller_dashboard_title"))
            RitText("Overview and actions for today", RitType.small, colors.muted)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("seller_daily_overview_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RitText(sellerName, RitType.heading)
                    RitText("$roleLabel · seller operations", RitType.small, colors.muted)
                }
            }
            if (onOpenAccount != null) item {
                RitSecondaryButton("My account", onOpenAccount,
                    Modifier.fillMaxWidth().testTag("seller_daily_account"))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    DailyFocusCard(
                        title = if (canRespond) "Needs your reply" else "Needs seller attention",
                        count = needsReply.size,
                        description = when {
                            needsReply.isEmpty() && errorMessage != null -> "Refresh to check for replies"
                            needsReply.isEmpty() -> "No open replies right now"
                            canRespond -> "New requests and customer updates"
                            else -> "A seller owner or manager can respond"
                        },
                        action = if (canRespond) "Review requests" else "View requests",
                        onClick = onOpenRequests,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("seller_daily_reply_summary"),
                        icon = RitIconKind.Mail,
                    )
                    DailyFocusCard(
                        title = "Upcoming pickups",
                        count = upcoming.size,
                        description = when {
                            errorMessage != null -> "Refresh to check requested times"
                            nextPickup != null -> "Next · ${displayPickup(nextPickup.latest.pickup)}"
                            else -> "No requested times ahead"
                        },
                        action = "Open schedule",
                        onClick = onOpenSchedule,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("seller_daily_pickup_summary"),
                        icon = RitIconKind.Orders,
                        footnote = "Requested times, not reservations",
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    DailyFocusCard("New requests", newBulk + newIndividual,
                        "$newBulk bulk · $newIndividual individual", "Open orders", onOpenRequests,
                        Modifier.weight(1f).fillMaxHeight().testTag("seller_dashboard_new_summary"), RitIconKind.Orders)
                    DailyFocusCard("Waiting", waiting, "Customer response or payment",
                        "Review orders", onOpenRequests,
                        Modifier.weight(1f).fillMaxHeight().testTag("seller_dashboard_waiting_summary"), RitIconKind.Bulk)
                }
            }

            if (onOpenIndividual != null) item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    // Paid orders of both kinds are prepared from the same Orders page.
                    val toPrepare = newIndividual + sellerRecords.count { it.thread.stage == QuoteStage.PAID }
                    RitText("Orders to prepare", RitType.title)
                    RitText(if (toPrepare == 0) "Nothing to prepare right now" else "$toPrepare paid, not ready yet",
                        RitType.body, colors.muted)
                    RitSecondaryButton("Open orders", onOpenIndividual,
                        Modifier.fillMaxWidth().testTag("seller_dashboard_individual"))
                }
            }
            if (onVerifyPickup != null) item {
                RitSecondaryButton("Confirm pickup", onVerifyPickup,
                    Modifier.fillMaxWidth().testTag("seller_dashboard_verify"))
            }

            if (onOpenMenu != null || onOpenSales != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RitText("Your business", RitType.title)
                        Row(
                            modifier = Modifier.fillMaxWidth().testTag("seller_daily_quick_links"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (onOpenMenu != null) {
                                DailyEntryCard(
                                    title = "Menu",
                                    description = "Update what customers can request",
                                    icon = RitIconKind.Food,
                                    onClick = onOpenMenu,
                                    modifier = Modifier.weight(1f).testTag("seller_daily_menu"),
                                )
                            }
                            if (onOpenSales != null) {
                                DailyEntryCard(
                                    title = "Sales",
                                    description = "Open your sales overview",
                                    icon = RitIconKind.Orders,
                                    onClick = onOpenSales,
                                    modifier = Modifier.weight(1f).testTag("seller_daily_sales"),
                                )
                            }
                        }
                    }
                }
            }

            if (onOpenHours != null || onOpenPromotions != null) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RitText("Quick management", RitType.title)
                    if (onOpenHours != null) RitSecondaryButton("Hours & closures", onOpenHours,
                        Modifier.fillMaxWidth().testTag("seller_dashboard_hours"))
                    if (onOpenPromotions != null) RitSecondaryButton("Promotions", onOpenPromotions,
                        Modifier.fillMaxWidth().testTag("seller_dashboard_promotions"))
                }
            }

            if (onOpenManagement != null || onOpenHistory != null || onOpenReports != null || onOpenSupport != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RitText("More seller tools", RitType.title)
                        if (onOpenManagement != null) RitSecondaryButton("Business management", onOpenManagement,
                            Modifier.fillMaxWidth().testTag("seller_daily_management"))
                        if (onOpenHistory != null) RitSecondaryButton("Search and history", onOpenHistory,
                            Modifier.fillMaxWidth().testTag("seller_daily_history"))
                        if (onOpenReports != null) RitSecondaryButton("Reports", onOpenReports,
                            Modifier.fillMaxWidth().testTag("seller_daily_reports"))
                        if (onOpenSupport != null) RitSecondaryButton("Seller support", onOpenSupport,
                            Modifier.fillMaxWidth().testTag("seller_daily_support"))
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RitMessageCard(
                            title = "Requests could not be refreshed",
                            detail = errorMessage,
                            modifier = Modifier.testTag("seller_daily_error"),
                        )
                        if (onRetry != null) {
                            RitSecondaryButton(
                                "Try again",
                                onRetry,
                                Modifier.fillMaxWidth().testTag("seller_daily_retry"),
                            )
                        }
                    }
                }
            }

            item {
                DailySectionHeading(
                    title = if (canRespond) "Needs your reply" else "Needs seller attention",
                    detail = if (canRespond) "Start with the latest request details."
                        else "This view is read-only for your role.",
                    action = "All requests",
                    onAction = onOpenRequests,
                    tag = "seller_daily_all_requests",
                )
            }
            if (needsReply.isEmpty()) {
                item {
                    RitEmptyState(
                        if (errorMessage == null) "You're all caught up" else "Request status unavailable",
                        if (errorMessage == null) "No requests need a seller response right now."
                        else "Try again to check whether a seller response is needed.",
                        Modifier.testTag("seller_daily_no_replies"),
                    )
                }
            } else {
                items(needsReply.take(MAX_PRIORITY_REQUESTS), key = { "reply_${it.reference}" }) { record ->
                    SellerDailyRequestCard(
                        record = record,
                        canRespond = canRespond,
                        catalog = catalog,
                        onOpenRequest = onOpenRequest,
                        cardTag = "seller_daily_reply_card_${record.reference}",
                    )
                }
                if (needsReply.size > MAX_PRIORITY_REQUESTS) {
                    item {
                        RitSecondaryButton(
                            "See all ${needsReply.size} requests",
                            onOpenRequests,
                            Modifier.fillMaxWidth().testTag("seller_daily_more_replies"),
                        )
                    }
                }
            }

            item {
                DailySectionHeading(
                    title = "Upcoming pickups",
                    detail = "Check the requested time before replying.",
                    action = "Full schedule",
                    onAction = onOpenSchedule,
                    tag = "seller_daily_full_schedule",
                )
            }
            if (upcoming.isEmpty()) {
                item {
                    RitEmptyState(
                        if (errorMessage == null) "No upcoming pickup requests" else "Pickup schedule unavailable",
                        if (errorMessage == null) "New requests are not treated as confirmed reservations."
                        else "Try again to check for requested pickup times.",
                        Modifier.testTag("seller_daily_no_pickups"),
                    )
                }
            } else {
                items(upcoming.take(MAX_PRIORITY_REQUESTS), key = { "pickup_${it.reference}" }) { record ->
                    SellerDailyRequestCard(
                        record = record,
                        canRespond = canRespond,
                        catalog = catalog,
                        onOpenRequest = onOpenRequest,
                        cardTag = "seller_daily_pickup_card_${record.reference}",
                    )
                }
            }

        }
        if (onBottom != null) SellerBottomNavigation(SellerBottomItem.Dashboard, onBottom,
            showInsights = canViewInsights)
    }
}

/**
 * Presentation-only seller request detail. The host owns the seller scope, permission gate,
 * state transitions, and destinations; callbacks are invoked only for visible actions.
 */
@Composable
fun SellerDailyRequestDetailPresentation(
    record: BulkRequestRecord,
    canRespond: Boolean,
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit,
    onReviewSpecialPickup: () -> Unit,
    onCompareRevisions: () -> Unit,
    modifier: Modifier = Modifier,
    catalog: CustomerCatalogSource = PreviewCatalog,
    now: LocalDateTime = LocalDateTime.now(RIT_TIME_ZONE),
    isDemoPreview: Boolean = true,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onCounteroffer: (() -> Unit)? = null,
    onReject: ((String) -> Unit)? = null,
    actionBusy: Boolean = false,
) {
    val colors = RitThemeTokens.colors
    val context = LocalContext.current
    val showJourney = remember(record.thread.sellerId) {
        SellerDisplayPreferences.showRequestJourney(context, record.thread.sellerId)
    }
    var rejectionReason by remember(record.reference) { mutableStateOf("") }
    val quoteExpired = isRequestQuoteExpired(record, now)
    val currentState = sellerDetailState(record, now)
    val specialNeedsApproval = record.latest.specialPickup &&
        record.thread.specialAcceptedVersion != record.latest.version &&
        record.thread.stage in ACTIVE_REQUEST_STAGES && !quoteExpired
    val sellerNeedsResponse = record.operationalStatus() in NEEDS_SELLER_RESPONSE

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("seller_daily_request_detail"),
    ) {
        CatalogHeader("Request details", onBack)
        LazyColumn(
            modifier = Modifier.testTag("seller_daily_detail_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SellerDetailSummaryCard(
                    record = record,
                    state = currentState,
                    catalog = catalog,
                    now = now,
                    isDemoPreview = isDemoPreview,
                )
            }

            item {
                when {
                    quoteExpired -> RitMessageCard(
                        title = "Quote expired",
                        detail = nextSafeStepDetail(record, now),
                        modifier = Modifier.testTag("seller_detail_next_step"),
                    )
                    !canRespond && (sellerNeedsResponse || specialNeedsApproval) -> RitMessageCard(
                        title = "Seller response needed",
                        detail = "A seller owner or manager can review this request. This screen is view only for your role.",
                        modifier = Modifier.testTag("seller_detail_next_step"),
                    )
                    specialNeedsApproval && canRespond -> Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("seller_detail_next_step"),
                    ) {
                        RitMessageCard(
                            title = "Pickup requires explicit exception approval",
                            detail = "Check the requested pickup time and reason. You accept or decline it separately from the order.",
                        )
                        RitPrimaryButton(
                            "Review pickup exception",
                            onReviewSpecialPickup,
                            Modifier.fillMaxWidth().testTag("seller_detail_primary_action"),
                        )
                    }
                    sellerNeedsResponse && canRespond -> Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("seller_detail_next_step"),
                    ) {
                        RitMessageCard(
                            title = "Seller needs to respond",
                            detail = "Review the latest items, amount and pickup time before choosing a response.",
                        )
                        RitPrimaryButton(
                            sellerResponseAction(record),
                            onPrimaryAction,
                            Modifier.fillMaxWidth().testTag("seller_detail_primary_action"),
                        )
                    }
                    else -> RitMessageCard(
                        title = currentState,
                        detail = nextSafeStepDetail(record, now),
                        modifier = Modifier.testTag("seller_detail_next_step"),
                    )
                }
            }

            if (canRespond && !quoteExpired && record.thread.stage in ACTIVE_REQUEST_STAGES) {
                if (onCounteroffer != null && record.latest.actor == QuoteActor.CUSTOMER) item {
                    RitSecondaryButton("Suggest changes", onCounteroffer,
                        Modifier.fillMaxWidth().testTag("seller_daily_counteroffer"))
                }
                if (onReject != null) item {
                    RitTextField(rejectionReason, { rejectionReason = it }, "Rejection reason",
                        "Required for the customer", Modifier.fillMaxWidth())
                    RitSecondaryButton(if (actionBusy) "Rejecting…" else "Reject request",
                        { onReject(rejectionReason.trim()) }, Modifier.fillMaxWidth().testTag("seller_daily_reject"),
                        enabled = !actionBusy && rejectionReason.trim().isNotEmpty())
                }
            }

            if (errorMessage != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RitMessageCard(
                            "Some request details could not be refreshed",
                            errorMessage,
                            Modifier.testTag("seller_detail_error"),
                        )
                        if (onRetry != null) {
                            RitSecondaryButton(
                                "Try again",
                                onRetry,
                                Modifier.fillMaxWidth().testTag("seller_detail_retry"),
                            )
                        }
                    }
                }
            }

            if (showJourney) {
                item {
                    SellerSectionHeading(
                        "Request journey",
                        "See who has agreed to the current version.",
                        "seller_detail_journey_heading",
                    )
                }
                item { SellerRequestJourney(record, canRespond, now) }
            }

            item {
                SellerSectionHeading(
                    "History",
                    "Earlier approvals do not carry over to changed terms.",
                    "seller_detail_history_heading",
                )
            }
            item { SellerRevisionHistoryCard(record) }
            if (record.thread.revisions.size > 1) {
                item {
                    RitSecondaryButton(
                        "Compare before and after",
                        onCompareRevisions,
                        Modifier.fillMaxWidth().testTag("seller_detail_compare_revisions"),
                    )
                }
            }
        }
    }
}

/** Preview of a proposed seller revision; no quote or approval is changed here. */
@Composable
fun SellerCounterofferPreviewPresentation(
    record: BulkRequestRecord,
    proposal: QuoteRevision,
    canRespond: Boolean,
    onBack: () -> Unit,
    onContinueReview: () -> Unit,
    onReviewSpecialPickup: () -> Unit,
    modifier: Modifier = Modifier,
    now: LocalDateTime = LocalDateTime.now(RIT_TIME_ZONE),
    isDemoPreview: Boolean = true,
    proposalIsDraft: Boolean = false,
) {
    val colors = RitThemeTokens.colors
    val previous = record.latest
    val differences = compareQuotes(previous, proposal)
    val sourceQuoteExpired = isRequestQuoteExpired(record, now)
    val sourceRequestIsOpen = record.thread.stage in ACTIVE_REQUEST_STAGES
    val specialNeedsApproval = sourceRequestIsOpen && !sourceQuoteExpired && proposal.specialPickup &&
        record.thread.specialAcceptedVersion != proposal.version
    val expiry = record.thread.expiresAt?.let {
        val expiryState = if (it.isAfter(now)) "expires" else "expired"
        "Current quote $expiryState ${displayPickup(it)}. This draft does not have a new expiry yet."
    } ?: "Current quote expiry is not recorded. This draft does not have a new expiry yet."

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("seller_counteroffer_preview"),
    ) {
        CatalogHeader("Review changed terms", onBack)
        LazyColumn(
            modifier = Modifier.testTag("seller_counteroffer_preview_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SellerCounterofferSummaryCard(
                    record = record,
                    proposal = proposal,
                    expiry = expiry,
                    now = now,
                    isDemoPreview = isDemoPreview,
                )
            }
            item {
                SellerSectionHeading(
                    "What changed",
                    "Read the current quote beside the proposed terms.",
                    "seller_counteroffer_changes_heading",
                )
            }
            item { SellerRevisionDifferenceList(previous, proposal, differences) }
            item {
                SellerRevisionApprovalFacts(
                    record = record,
                    proposedVersion = proposal.version,
                    isPreview = true,
                )
            }
            item {
                if (canRespond && !sourceQuoteExpired && sourceRequestIsOpen) {
                    RitPrimaryButton(
                        if (proposalIsDraft) "Back to editing"
                        else if (specialNeedsApproval) "Review pickup exception first" else "Continue to seller response",
                        if (proposalIsDraft) onContinueReview
                        else if (specialNeedsApproval) onReviewSpecialPickup else onContinueReview,
                        Modifier.fillMaxWidth().testTag(
                            if (proposalIsDraft) "seller_counteroffer_continue"
                            else if (specialNeedsApproval) "seller_counteroffer_review_special"
                            else "seller_counteroffer_continue",
                        ),
                    )
                    if (proposalIsDraft && proposal.specialPickup) RitMessageCard(
                        "Separate pickup decision after sending",
                        "These changes aren't sent yet. Send them first, then decide on the pickup time.",
                    )
                } else if (sourceQuoteExpired) {
                    RitMessageCard(
                        "Current quote expired",
                        "This request expired, so these changes can't be sent.",
                        Modifier.testTag("seller_counteroffer_expired"),
                    )
                } else if (!sourceRequestIsOpen) {
                    RitMessageCard(
                        "This request can't be changed any more",
                        "Review the request status and expiry before starting another seller response.",
                        Modifier.testTag("seller_counteroffer_closed"),
                    )
                } else {
                    RitMessageCard(
                        "Seller response needed",
                        "An owner or manager can respond to this request.",
                        Modifier.testTag("seller_counteroffer_read_only"),
                    )
                }
            }
        }
    }
}

/** Date-grouped seller schedule. Pickup times remain requests until the appropriate workflow confirms them. */
@Composable
fun SellerPickupSchedulePresentation(
    records: List<BulkRequestRecord>,
    sellerId: String,
    onBack: () -> Unit,
    onOpenRequest: (String) -> Unit,
    canRespond: Boolean,
    modifier: Modifier = Modifier,
    catalog: CustomerCatalogSource = PreviewCatalog,
    now: LocalDateTime = LocalDateTime.now(RIT_TIME_ZONE),
    isDemoPreview: Boolean = true,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val colors = RitThemeTokens.colors
    val upcoming = records
        .filter { record ->
            record.sellerId == sellerId &&
                record.thread.stage in UPCOMING_STAGES &&
                record.latest.pickup.isAfter(now) &&
                !isRequestQuoteExpired(record, now)
        }
        .sortedBy { it.latest.pickup }
    val groups = upcoming.groupBy { it.latest.pickup.toLocalDate() }.toSortedMap()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("seller_pickup_schedule"),
    ) {
        CatalogHeader("Pickup schedule", onBack)
        LazyColumn(
            modifier = Modifier.testTag("seller_pickup_schedule_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RitText("Requested pickup times", RitType.heading)
                    RitText(
                        "These are requested times, not reserved slots. Only a trusted payment verification can establish paid status.",
                        RitType.body,
                        colors.muted,
                    )
                    RitText("${upcoming.size} upcoming requested times", RitType.small, colors.muted)
                }
            }
            if (errorMessage != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RitMessageCard(
                            "Schedule could not be refreshed",
                            errorMessage,
                            Modifier.testTag("seller_schedule_error"),
                        )
                        if (onRetry != null) {
                            RitSecondaryButton(
                                "Try again",
                                onRetry,
                                Modifier.fillMaxWidth().testTag("seller_schedule_retry"),
                            )
                        }
                    }
                }
            }
            if (groups.isEmpty()) {
                item {
                    RitEmptyState(
                        if (errorMessage == null) "No upcoming requested pickups" else "Pickup times unavailable",
                        if (errorMessage == null) "New requests are not treated as confirmed reservations."
                        else "Try again to check the seller's upcoming requested times.",
                        Modifier.testTag("seller_schedule_empty"),
                    )
                }
            } else {
                groups.forEach { (date, dayRecords) ->
                    item(key = "pickup_date_$date") {
                        SellerSectionHeading(
                            pickupDateLabel(date, now.toLocalDate()),
                            "${dayRecords.size} requested ${if (dayRecords.size == 1) "time" else "times"}",
                            "seller_schedule_day_$date",
                        )
                    }
                    items(dayRecords, key = { "schedule_${it.reference}" }) { record ->
                        SellerDailyRequestCard(
                            record = record,
                            canRespond = canRespond,
                            catalog = catalog,
                            onOpenRequest = onOpenRequest,
                            cardTag = "seller_schedule_request_${record.reference}",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerDetailSummaryCard(
    record: BulkRequestRecord,
    state: String,
    catalog: CustomerCatalogSource,
    now: LocalDateTime,
    isDemoPreview: Boolean,
) {
    val colors = RitThemeTokens.colors
    val latest = record.latest
    val firstLine = latest.lines.firstOrNull()
    val firstItem = firstLine?.let { line -> catalog.items.firstOrNull { it.id == line.itemId } }
    val units = latest.lines.sumOf { it.quantity }
    val expiry = requestExpiryDescription(record, now)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitRadii.card))
            .background(colors.card)
            .border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
            .padding(16.dp)
            .testTag("seller_detail_summary"),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FoodPhotoContent(
                photo = firstItem?.photo,
                description = firstItem?.name?.let { "$it photo" } ?: "Food photo",
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(RitRadii.small)),
                localUri = firstItem?.localImageUri,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RitText(record.customerLabel, RitType.label, maxLines = 2)
                RitText(
                    state,
                    RitType.title,
                    colors.accent,
                    modifier = Modifier
                        .semantics(mergeDescendants = true) { contentDescription = "Current request state: $state" }
                        .testTag("seller_detail_state"),
                )
                RitText("Version ${latest.version}", RitType.small, colors.muted)
            }
        }
        latest.lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RitText(line.title, RitType.body, maxLines = 2)
                    RitText("${line.quantity} requested", RitType.small, colors.muted)
                }
                RitText(displayPaise(line.unitPricePaise * line.quantity), RitType.label)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                RitText("Items and units", RitType.small, colors.muted)
                RitText("${latest.lines.size} items · $units units", RitType.label)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                RitText("Current total", RitType.small, colors.muted)
                RitText(displayPaise(latest.totalPaise), RitType.label, colors.accent)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            RitText("Requested pickup", RitType.small, colors.muted)
            RitText(displayPickup(latest.pickup), RitType.label)
        }
        if (latest.specialPickup) {
            val accepted = record.thread.specialAcceptedVersion == latest.version
            val canStillReview = record.thread.stage in ACTIVE_REQUEST_STAGES && !isRequestQuoteExpired(record, now)
            RitMessageCard(
                when {
                    accepted -> "Pickup time outside hours accepted"
                    canStillReview -> "Pickup requires explicit exception approval"
                    else -> "No pickup exception decision is recorded"
                },
                when {
                    accepted -> latest.specialReason.ifBlank { "Accepted for this version of the order." }
                    canStillReview -> latest.specialReason.ifBlank { "The requested time is outside the regular pickup window." }
                    else -> "The request is expired or closed, so this exception cannot be reviewed here."
                },
                Modifier.testTag("seller_detail_special_state"),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            RitText("Quote expiry", RitType.small, colors.muted)
            RitText(expiry, RitType.body)
        }
    }
}

@Composable
private fun SellerRequestJourney(record: BulkRequestRecord, canRespond: Boolean, now: LocalDateTime) {
    val latest = record.latest
    val status = record.operationalStatus()
    val quoteExpired = isRequestQuoteExpired(record, now)
    val workflowClosed = quoteExpired || record.thread.stage in setOf(QuoteStage.WITHDRAWN, QuoteStage.REJECTED)
    val sellerApproved = record.thread.sellerAcceptedVersion == latest.version
    val customerApproved = record.thread.customerAcceptedVersion == latest.version
    val specialAccepted = record.thread.specialAcceptedVersion == latest.version
    val specialNeedsApproval = latest.specialPickup && !specialAccepted &&
        record.thread.stage in ACTIVE_REQUEST_STAGES && !quoteExpired
    val paid = record.thread.stage in setOf(QuoteStage.PAID, QuoteStage.READY, QuoteStage.COMPLETED)
    val items = buildList {
        add(
            RequestJourneyStep(
                "Request received",
                "${record.customerLabel} shared ${latest.lines.sumOf { it.quantity }} units for review.",
                complete = true,
            ),
        )
        add(
            RequestJourneyStep(
                "Seller response",
                when {
                    quoteExpired -> "Quote expired${record.thread.expiresAt?.let { " on ${displayPickup(it)}" } ?: ""}; it can no longer be answered."
                    sellerApproved -> "You accepted this version."
                    status in NEEDS_SELLER_RESPONSE -> "Accept it, suggest changes or decline."
                    status == SellerRequestStatus.WAITING_CUSTOMER -> "You accepted this version; waiting for the customer."
                    else -> "You haven't accepted this version."
                },
                complete = sellerApproved,
                current = !quoteExpired && status in NEEDS_SELLER_RESPONSE,
                unavailable = workflowClosed && !sellerApproved,
            ),
        )
        if (latest.specialPickup) {
            add(
                RequestJourneyStep(
                    "Pickup exception",
                    when {
                        specialAccepted -> "Pickup time outside hours accepted."
                        quoteExpired -> "The request expired before you decided on the pickup time."
                        specialNeedsApproval -> "The pickup time is outside your hours. Accept or decline it."
                        workflowClosed -> "The request closed before an exception decision was recorded."
                        else -> "No exception decision is recorded for this request."
                    },
                    complete = specialAccepted,
                    current = specialNeedsApproval,
                    unavailable = workflowClosed && !specialAccepted,
                ),
            )
        }
        add(
            RequestJourneyStep(
                "Customer approval",
                when {
                    customerApproved -> "The customer agreed to this version."
                    status == SellerRequestStatus.WAITING_CUSTOMER -> "Waiting for the customer to accept."
                    else -> "The customer still needs to accept."
                },
                complete = customerApproved,
                current = status == SellerRequestStatus.WAITING_CUSTOMER,
                unavailable = workflowClosed && !customerApproved,
            ),
        )
        add(
            RequestJourneyStep(
                "Payment",
                when {
                    paid -> "Paid with PhonePe."
                    status == SellerRequestStatus.AGREED_UNPAID -> "Awaiting payment."
                    else -> "Not paid yet."
                },
                complete = paid,
                current = status == SellerRequestStatus.AGREED_UNPAID,
                unavailable = workflowClosed && !paid,
            ),
        )
        add(
            RequestJourneyStep(
                "Pickup time",
                when (record.thread.stage) {
                    QuoteStage.COMPLETED -> "Collected."
                    QuoteStage.READY -> "Ready. Confirm pickup from Orders → Ready with the customer's QR code or PIN."
                    QuoteStage.PAID -> "Pickup at ${displayPickup(latest.pickup)}. Mark it ready from Orders → New."
                    else -> "Requested time ${displayPickup(latest.pickup)}; confirmed once the customer pays."
                },
                complete = record.thread.stage == QuoteStage.COMPLETED,
                current = record.thread.stage in setOf(QuoteStage.PAID, QuoteStage.READY),
                unavailable = workflowClosed,
                stateLabel = when {
                    record.thread.stage == QuoteStage.COMPLETED -> "Collected"
                    paid -> "Confirmed"
                    workflowClosed -> "Unavailable"
                    else -> "Requested only"
                },
            ),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("seller_detail_journey"),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        items.forEachIndexed { index, step ->
            SellerJourneyStepCard(index + 1, items.size, step)
        }
        if (!canRespond && status in NEEDS_SELLER_RESPONSE && !quoteExpired) {
            RitText("This view is read only for your role.", RitType.small, RitThemeTokens.colors.muted)
        }
    }
}

@Composable
private fun SellerJourneyStepCard(index: Int, count: Int, step: RequestJourneyStep) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    val state = when {
        step.complete -> "Complete"
        step.current -> "Needs attention"
        step.unavailable -> "Unavailable"
        step.stateLabel != null -> step.stateLabel
        else -> "Up next"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (step.current) colors.warmCard else colors.card)
            .border(1.dp, colors.outline, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = "Step $index of $count. ${step.title}. $state. ${step.detail}"
            }
            .padding(13.dp)
            .testTag("seller_detail_step_$index"),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (step.complete || step.current) colors.accent else colors.outline),
                contentAlignment = Alignment.Center,
            ) {
                RitText("$index", RitType.small, if (step.complete || step.current) colors.onAccent else colors.foreground)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RitText(step.title, RitType.label)
                RitText(state, RitType.small, if (step.current) colors.accent else colors.muted)
            }
        }
        RitText(step.detail, RitType.body, colors.muted)
    }
}

@Composable
private fun SellerRevisionHistoryCard(record: BulkRequestRecord) {
    val colors = RitThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RitRadii.card))
            .background(colors.card)
            .border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
            .padding(15.dp)
            .testTag("seller_detail_approval_facts"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SellerApprovalRow("Seller", record.thread.sellerAcceptedVersion, record.latest.version)
        SellerApprovalRow("Customer", record.thread.customerAcceptedVersion, record.latest.version)
        RitText(
            "You and the customer must agree to the same version before the customer can pay. Any change needs agreement again.",
            RitType.small,
            colors.muted,
        )
        record.thread.revisions.sortedByDescending { it.version }.forEach { revision ->
            val role = if (revision.actor == QuoteActor.SELLER) "Seller changed terms" else "Customer changed terms"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Version ${revision.version}. $role. " +
                            "${revision.lines.sumOf { it.quantity }} units. Total ${displayPaise(revision.totalPaise)}. " +
                            "Requested pickup ${displayPickup(revision.pickup)}."
                    }
                    .testTag("seller_detail_revision_${revision.version}"),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                RitText("Version ${revision.version} · $role", RitType.label)
                RitText(
                    "${revision.lines.sumOf { it.quantity }} units · ${displayPaise(revision.totalPaise)}",
                    RitType.small,
                    colors.muted,
                )
                RitText("Requested pickup · ${displayPickup(revision.pickup)}", RitType.small, colors.muted)
            }
        }
    }
}

@Composable
private fun SellerApprovalRow(label: String, approvedVersion: Int?, currentVersion: Int) {
    val colors = RitThemeTokens.colors
    val approved = approvedVersion == currentVersion
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$label agreement to version $currentVersion: " +
                    if (approved) "recorded" else "not yet"
            }
            .testTag("seller_approval_${label.lowercase()}"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RitText(label, RitType.label, modifier = Modifier.weight(1f))
        RitText(
            if (approved) "Agreed · version $currentVersion" else "Not agreed yet",
            RitType.small,
            if (approved) colors.accent else colors.muted,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun SellerCounterofferSummaryCard(
    record: BulkRequestRecord,
    proposal: QuoteRevision,
    expiry: String,
    now: LocalDateTime,
    isDemoPreview: Boolean,
) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.outline, shape)
            .padding(16.dp)
            .testTag("seller_counteroffer_summary"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RitText("Version ${proposal.version}", RitType.heading)
        RitStatusIndicator(
            "Proposed changes · not yet approved",
            Modifier
                .semantics(mergeDescendants = true) {
                    contentDescription = "Your suggested changes · not sent yet"
                }
                .testTag("seller_counteroffer_state"),
        )
        proposal.lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RitText(line.title, RitType.body, maxLines = 2)
                    RitText("${line.quantity} requested", RitType.small, colors.muted)
                }
                RitText(displayPaise(line.unitPricePaise * line.quantity), RitType.label)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RitText("Items and units", RitType.small, colors.muted)
                RitText("${proposal.lines.size} items · ${proposal.lines.sumOf { it.quantity }} units", RitType.label)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RitText("Proposed total", RitType.small, colors.muted)
                RitText(displayPaise(proposal.totalPaise), RitType.label, colors.accent)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RitText("Proposed pickup", RitType.small, colors.muted)
            RitText(displayPickup(proposal.pickup), RitType.label)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RitText("Expiry", RitType.small, colors.muted)
            RitText(expiry, RitType.body)
        }
        if (proposal.specialPickup) {
            val acceptedForProposal = record.thread.specialAcceptedVersion == proposal.version
            val canStillReview = record.thread.stage in ACTIVE_REQUEST_STAGES && !isRequestQuoteExpired(record, now)
            RitMessageCard(
                when {
                    acceptedForProposal -> "Pickup time outside hours accepted"
                    canStillReview -> "Pickup requires explicit exception approval"
                    else -> "No pickup exception decision is recorded"
                },
                when {
                    acceptedForProposal -> "Accepted for this version of the order."
                    canStillReview -> proposal.specialReason.ifBlank { "A separate seller decision is needed for this requested time." }
                    else -> "The source request is expired or closed, so this exception cannot be reviewed here."
                },
                Modifier.testTag("seller_counteroffer_special_state"),
            )
        }
    }
}

@Composable
private fun SellerRevisionDifferenceList(
    previous: QuoteRevision,
    proposed: QuoteRevision,
    differences: edu.msrit.canteen.customer.QuoteDifference,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("seller_counteroffer_differences"),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        differences.changed.forEach { (old, new) ->
            SellerBeforeAfterCard(
                title = new.title,
                before = "${old.quantity} × ${displayPaise(old.unitPricePaise)} each · ${displayPaise(old.quantity * old.unitPricePaise)}",
                after = "${new.quantity} × ${displayPaise(new.unitPricePaise)} each · ${displayPaise(new.quantity * new.unitPricePaise)}",
                tag = "seller_counteroffer_line_${new.itemId}",
            )
        }
        differences.added.forEach { line ->
            SellerBeforeAfterCard(
                title = line.title,
                before = "Not in current quote",
                after = "${line.quantity} × ${displayPaise(line.unitPricePaise)} each · ${displayPaise(line.quantity * line.unitPricePaise)}",
                tag = "seller_counteroffer_added_${line.itemId}",
            )
        }
        differences.removed.forEach { line ->
            SellerBeforeAfterCard(
                title = line.title,
                before = "${line.quantity} × ${displayPaise(line.unitPricePaise)} each · ${displayPaise(line.quantity * line.unitPricePaise)}",
                after = "Removed from proposal",
                tag = "seller_counteroffer_removed_${line.itemId}",
            )
        }
        SellerBeforeAfterCard(
            "Total",
            displayPaise(previous.totalPaise),
            displayPaise(proposed.totalPaise),
            "seller_counteroffer_total_change",
        )
        SellerBeforeAfterCard(
            "Requested pickup",
            displayPickup(previous.pickup),
            displayPickup(proposed.pickup),
            "seller_counteroffer_pickup_change",
        )
        SellerBeforeAfterCard(
            "Pickup timing",
            pickupTypeLabel(previous.specialPickup),
            pickupTypeLabel(proposed.specialPickup),
            "seller_counteroffer_pickup_type_change",
        )
        if (differences.specialChanged || previous.specialReason != proposed.specialReason) {
            SellerBeforeAfterCard(
                "Pickup exception reason",
                previous.specialReason.ifBlank { "No exception requested" },
                proposed.specialReason.ifBlank { "No exception requested" },
                "seller_counteroffer_special_reason_change",
            )
        }
        if (previous.note != proposed.note) {
            SellerBeforeAfterCard(
                "Message about the quote",
                previous.note.ifBlank { "No message" },
                proposed.note.ifBlank { "No message" },
                "seller_counteroffer_note_change",
            )
        }
        if (differences.added.isEmpty() && differences.removed.isEmpty() && differences.changed.isEmpty() &&
            !differences.amountChanged && !differences.pickupChanged && !differences.specialChanged &&
            previous.specialReason == proposed.specialReason && previous.note == proposed.note
        ) {
            RitMessageCard("No terms changed", "Nothing has changed yet.", Modifier.testTag("seller_counteroffer_no_changes"))
        }
    }
}

@Composable
private fun SellerBeforeAfterCard(title: String, before: String, after: String, tag: String) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.small)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.outline, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. Before: $before. Proposed: $after."
            }
            .padding(12.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        RitText(title, RitType.label)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                RitText("Before", RitType.small, colors.muted)
                RitText(before, RitType.body)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                RitText("Proposed", RitType.small, colors.accent)
                RitText(after, RitType.body)
            }
        }
    }
}

@Composable
private fun SellerRevisionApprovalFacts(
    record: BulkRequestRecord,
    proposedVersion: Int,
    isPreview: Boolean,
) {
    val currentVersion = record.latest.version
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("seller_counteroffer_approval_facts"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RitText("Who has approved what", RitType.title)
        SellerApprovalRow("Seller", record.thread.sellerAcceptedVersion, currentVersion)
        SellerApprovalRow("Customer", record.thread.customerAcceptedVersion, currentVersion)
        if (isPreview) {
            RitMessageCard(
                "Not sent yet",
                "Once you send these changes, the customer accepts them and then pays.",
                Modifier.testTag("seller_counteroffer_new_revision_approvals"),
            )
        }
    }
}

@Composable
private fun SellerSectionHeading(title: String, detail: String, tag: String) {
    val colors = RitThemeTokens.colors
    Column(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        RitText(title, RitType.title)
        RitText(detail, RitType.small, colors.muted)
    }
}

private data class RequestJourneyStep(
    val title: String,
    val detail: String,
    val complete: Boolean,
    val current: Boolean = false,
    val unavailable: Boolean = false,
    val stateLabel: String? = null,
)

private fun sellerDetailState(record: BulkRequestRecord, now: LocalDateTime): String {
    val status = record.operationalStatus()
    if (isRequestQuoteExpired(record, now)) return "Quote expired"
    val specialNeedsApproval = record.latest.specialPickup &&
        record.thread.specialAcceptedVersion != record.latest.version &&
        record.thread.stage in ACTIVE_REQUEST_STAGES
    return when {
        specialNeedsApproval -> "Pickup requires explicit exception approval"
        status in NEEDS_SELLER_RESPONSE -> "Seller needs to respond"
        status == SellerRequestStatus.WAITING_CUSTOMER -> "Waiting for customer"
        status == SellerRequestStatus.AGREED_UNPAID -> "Awaiting payment"
        status == SellerRequestStatus.PAID -> "Paid · to prepare"
        status == SellerRequestStatus.READY -> "Ready for pickup"
        status == SellerRequestStatus.COLLECTED -> "Collected"
        status == SellerRequestStatus.EXPIRED -> "Quote expired"
        status == SellerRequestStatus.WITHDRAWN -> "Request withdrawn"
        status == SellerRequestStatus.REJECTED -> "Request closed"
        else -> "Request status needs review"
    }
}

private fun sellerResponseAction(record: BulkRequestRecord): String =
    when (record.operationalStatus()) {
        SellerRequestStatus.CUSTOMER_COUNTER -> "Review customer changes"
        SellerRequestStatus.SELLER_REVIEW -> "Review seller response"
        else -> "Accept, change or decline"
    }

private fun nextSafeStepDetail(record: BulkRequestRecord, now: LocalDateTime): String {
    if (isRequestQuoteExpired(record, now)) {
        val expiry = record.thread.expiresAt?.let { " on ${displayPickup(it)}" }.orEmpty()
        return "The request expired$expiry. It can no longer be answered."
    }
    return when (record.operationalStatus()) {
        SellerRequestStatus.WAITING_CUSTOMER -> "You've responded. Waiting for the customer to accept."
        SellerRequestStatus.AGREED_UNPAID -> "Both sides have accepted the current terms. Waiting for the customer to pay."
        SellerRequestStatus.PAID -> "Paid. Prepare it for pickup at ${displayPickup(record.latest.pickup)}, then mark it ready from Orders → New."
        SellerRequestStatus.READY -> "Ready. Confirm pickup from Orders → Ready with the customer's QR code or PIN."
        SellerRequestStatus.COLLECTED -> "Collected. Nothing left to do."
        SellerRequestStatus.EXPIRED -> "The quote expired${record.thread.expiresAt?.let { " on ${displayPickup(it)}" } ?: ""}. The customer can send a new request."
        SellerRequestStatus.WITHDRAWN -> "The customer withdrew this request. No seller action is available."
        SellerRequestStatus.REJECTED -> "This request is closed. No seller action is available."
        else -> if (record.thread.expiresAt?.isBefore(now) == true)
            "This quote has expired. Reopen the current seller workflow before responding."
        else "Requested pickup ${displayPickup(record.latest.pickup)} is not a confirmed reservation."
    }
}

private fun requestExpiryDescription(record: BulkRequestRecord, now: LocalDateTime): String =
    record.thread.expiresAt?.let { expiry ->
        when {
            record.thread.stage == QuoteStage.EXPIRED -> "Quote expired · ${displayPickup(expiry)}"
            expiry.isAfter(now) -> "Expires ${displayPickup(expiry)}"
            else -> "Expired ${displayPickup(expiry)}"
        }
    } ?: if (record.thread.stage == QuoteStage.EXPIRED) "Quote expired · expiry time not recorded" else "Expiry not recorded"

private fun isRequestQuoteExpired(record: BulkRequestRecord, now: LocalDateTime): Boolean =
    record.thread.stage == QuoteStage.EXPIRED ||
        (record.thread.stage in setOf(
            QuoteStage.SUBMITTED,
            QuoteStage.COUNTEROFFER,
            QuoteStage.AGREED_AWAITING_PAYMENT,
        ) && record.thread.expiresAt?.isAfter(now) == false)

private fun pickupTypeLabel(special: Boolean): String =
    if (special) "Exceptional pickup requested" else "Regular pickup time requested"

private fun pickupDateLabel(date: LocalDate, today: LocalDate): String {
    val pattern = if (date.year == today.year) "EEEE, d MMM" else "EEEE, d MMM yyyy"
    val formatted = date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    return when (date) {
        today -> "Today · $formatted"
        today.plusDays(1) -> "Tomorrow · $formatted"
        else -> formatted
    }
}

private val ACTIVE_REQUEST_STAGES = setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER)

private val SELLER_PREVIEW_NOW = LocalDateTime.of(2026, 10, 23, 9, 0)

private fun sellerPreviewRecord(): BulkRequestRecord {
    val pickup = SELLER_PREVIEW_NOW.plusDays(1).withHour(11).withMinute(30)
    val first = QuoteRevision(
        version = 1,
        actor = QuoteActor.SELLER,
        lines = listOf(edu.msrit.canteen.customer.QuoteLineSnapshot("north-lunch", "Ramaiah's Cafeteria Lunch", 8, 9_000)),
        pickup = pickup,
        specialPickup = false,
        totalPaise = 72_000,
        note = "Seller starting terms for a campus group.",
        createdAt = SELLER_PREVIEW_NOW.minusHours(2),
    )
    val second = QuoteRevision(
        version = 2,
        actor = QuoteActor.CUSTOMER,
        lines = listOf(
            edu.msrit.canteen.customer.QuoteLineSnapshot("north-lunch", "Ramaiah's Cafeteria Lunch", 6, 8_500),
            edu.msrit.canteen.customer.QuoteLineSnapshot("north-snack", "Savoury Snack Box", 4, 6_500),
        ),
        pickup = pickup.plusMinutes(30),
        specialPickup = false,
        totalPaise = 77_000,
        note = "Customer asks for a different mix.",
        createdAt = SELLER_PREVIEW_NOW.minusHours(1),
    )
    val sellerId = "north-counter"
    return BulkRequestRecord(
        thread = QuoteThread(
            sellerId = sellerId,
            reference = "DEMO-PREVIEW-REQUEST",
            revisions = listOf(first, second),
            stage = QuoteStage.COUNTEROFFER,
            expiresAt = SELLER_PREVIEW_NOW.plusHours(5),
        ),
        customerUserId = "demo-preview-only",
        customerLabel = "Demo customer",
        original = BulkDraft(
            sellerId = sellerId,
            lines = listOf(BulkLine("north-lunch", 8)),
            pickup = pickup,
        ),
        appliedMinimum = 8,
        createdAt = SELLER_PREVIEW_NOW.minusHours(2),
    )
}

private fun sellerPreviewProposal(): QuoteRevision {
    val previous = sellerPreviewRecord().latest
    return QuoteRevision(
        version = previous.version + 1,
        actor = QuoteActor.SELLER,
        lines = listOf(
            edu.msrit.canteen.customer.QuoteLineSnapshot("north-lunch", "Ramaiah's Cafeteria Lunch", 7, 8_500),
            edu.msrit.canteen.customer.QuoteLineSnapshot("north-snack", "Savoury Snack Box", 4, 6_500),
        ),
        pickup = previous.pickup.plusMinutes(15),
        specialPickup = false,
        totalPaise = 85_500,
        note = "Seller suggests one additional lunch.",
        createdAt = SELLER_PREVIEW_NOW,
    )
}

@Preview(name = "Seller detail · light · compact · 1.3×", widthDp = 360, heightDp = 900, fontScale = 1.3f, showBackground = true)
@Composable
private fun SellerDailyDetailLightPreview() {
    RitTheme(darkTheme = false) {
        SellerDailyRequestDetailPresentation(
            record = sellerPreviewRecord(),
            canRespond = true,
            onBack = {},
            onPrimaryAction = {},
            onReviewSpecialPickup = {},
            onCompareRevisions = {},
            now = SELLER_PREVIEW_NOW,
        )
    }
}

@Preview(
    name = "Counteroffer · dark · compact · 1.3×",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 360,
    heightDp = 900,
    fontScale = 1.3f,
    showBackground = true,
)
@Composable
private fun SellerCounterofferDarkPreview() {
    RitTheme(darkTheme = true) {
        SellerCounterofferPreviewPresentation(
            record = sellerPreviewRecord(),
            proposal = sellerPreviewProposal(),
            canRespond = true,
            onBack = {},
            onContinueReview = {},
            onReviewSpecialPickup = {},
            now = SELLER_PREVIEW_NOW,
        )
    }
}

@Preview(name = "Pickup schedule · light · compact", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun SellerPickupSchedulePreview() {
    val record = sellerPreviewRecord()
    RitTheme(darkTheme = false) {
        SellerPickupSchedulePresentation(
            records = listOf(record),
            sellerId = record.sellerId,
            onBack = {},
            onOpenRequest = {},
            canRespond = true,
            now = SELLER_PREVIEW_NOW,
        )
    }
}

@Composable
private fun DailyFocusCard(
    title: String,
    count: Int,
    description: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: RitIconKind,
    footnote: String? = null,
) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.warmCard)
            .border(1.dp, colors.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$title, $count. $description. $action."
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        RitIcon(icon, colors.accent, modifier = Modifier.size(21.dp), size = 21.dp)
        RitText(title, RitType.label, maxLines = 2)
        RitText(count.toString(), RitType.display, colors.accent, modifier = Modifier.testTag("seller_focus_count_$title"))
        RitText(description, RitType.small, colors.muted, maxLines = 3)
        if (footnote != null) RitText(footnote, RitType.small, colors.muted, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        RitText(action, RitType.label, colors.accent)
    }
}

@Composable
private fun DailySectionHeading(
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    tag: String,
) {
    val colors = RitThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            RitText(title, RitType.title)
            RitText(detail, RitType.small, colors.muted)
        }
        RitText(
            action,
            RitType.small,
            colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(RitRadii.pill))
                .clickable(role = Role.Button, onClick = onAction)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("${tag}_action"),
        )
    }
}

@Composable
private fun SellerDailyRequestCard(
    record: BulkRequestRecord,
    canRespond: Boolean,
    catalog: CustomerCatalogSource,
    onOpenRequest: (String) -> Unit,
    cardTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    val status = record.operationalStatus()
    val lineCount = record.latest.lines.size
    val units = record.latest.lines.sumOf { it.quantity }
    val firstLine = record.latest.lines.firstOrNull()
    val item = firstLine?.let { line -> catalog.items.firstOrNull { it.id == line.itemId } }
    val needsReply = status in NEEDS_SELLER_RESPONSE
    val requiresSpecialDecision = record.latest.specialPickup &&
        record.thread.specialAcceptedVersion != record.latest.version
    val action = when {
        needsReply && !canRespond -> "View details · ask an owner or manager to reply"
        status == SellerRequestStatus.SELLER_REVIEW && requiresSpecialDecision -> "Review pickup exception"
        needsReply -> "Review and reply"
        status == SellerRequestStatus.WAITING_CUSTOMER -> "Waiting for customer · view details"
        status == SellerRequestStatus.AGREED_UNPAID -> "Awaiting payment · view details"
        status == SellerRequestStatus.PAID -> "Paid · prepare for pickup"
        status == SellerRequestStatus.READY -> "Ready · waiting for pickup"
        status == SellerRequestStatus.COLLECTED -> "Collected"
        else -> "View request"
    }
    val statusLabel = dailyStatusLabel(record, canRespond)
    val accessibleSummary = "$statusLabel. $units units across $lineCount items. " +
        "Requested pickup ${displayPickup(record.latest.pickup)}. $action."

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.outline, shape)
            .clickable(role = Role.Button, onClick = { onOpenRequest(record.reference) })
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = accessibleSummary
            }
            .padding(14.dp)
            .testTag(cardTag),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FoodPhotoContent(
                photo = item?.photo,
                description = item?.name?.let { "$it photo" } ?: "Food photo",
                modifier = Modifier.size(70.dp).clip(RoundedCornerShape(RitRadii.small)),
                localUri = item?.localImageUri,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RitText(statusLabel, RitType.label, colors.accent, maxLines = 2)
                RitText(record.customerLabel, RitType.body, maxLines = 2)
            }
        }
        RitText(
            record.latest.lines.joinToString(" · ") { "${it.title} × ${it.quantity}" },
            RitType.small,
            colors.muted,
            maxLines = 2,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RitText("$lineCount items · $units units", RitType.small, colors.muted)
            RitText(displayPaise(record.latest.totalPaise), RitType.label)
        }
        RitText("Requested pickup · ${displayPickup(record.latest.pickup)}", RitType.small)
        if (record.latest.specialPickup) {
            RitText(
                if (requiresSpecialDecision) "Needs explicit pickup exception approval"
                else "Pickup time outside hours accepted",
                RitType.small,
                colors.accent,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RitText("Next step", RitType.small, colors.muted)
            RitText(action, RitType.label, colors.accent, maxLines = 2)
        }
    }
}

@Composable
private fun DailyEntryCard(
    title: String,
    description: String,
    icon: RitIconKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$title. $description."
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RitIcon(icon, colors.accent, modifier = Modifier.size(22.dp), size = 22.dp)
        RitText(title, RitType.label)
        RitText(description, RitType.small, colors.muted, maxLines = 3)
    }
}

private fun responsePriority(record: BulkRequestRecord): Int = when (record.operationalStatus()) {
    SellerRequestStatus.CUSTOMER_COUNTER -> 0
    SellerRequestStatus.NEW -> 1
    SellerRequestStatus.SELLER_REVIEW -> 2
    else -> 3
}

private fun dailyStatusLabel(record: BulkRequestRecord, canRespond: Boolean): String =
    when (record.operationalStatus()) {
        SellerRequestStatus.NEW -> if (canRespond) "New request" else "Needs seller reply"
        SellerRequestStatus.CUSTOMER_COUNTER -> if (canRespond) "Customer updated the request" else "Needs seller review"
        SellerRequestStatus.SELLER_REVIEW -> if (record.latest.specialPickup &&
            record.thread.specialAcceptedVersion != record.latest.version) "Pickup exception needs a decision"
        else "Review the latest terms"
        SellerRequestStatus.WAITING_CUSTOMER -> "Waiting for customer"
        SellerRequestStatus.AGREED_UNPAID -> "Agreed · awaiting payment"
        SellerRequestStatus.PAID -> "Paid · to prepare"
        SellerRequestStatus.READY -> "Ready for pickup"
        SellerRequestStatus.COLLECTED -> "Collected"
        SellerRequestStatus.EXPIRED -> "Quote expired"
        SellerRequestStatus.WITHDRAWN -> "Request withdrawn"
        SellerRequestStatus.REJECTED -> "Request closed"
    }

private val NEEDS_SELLER_RESPONSE = setOf(
    SellerRequestStatus.NEW,
    SellerRequestStatus.CUSTOMER_COUNTER,
    SellerRequestStatus.SELLER_REVIEW,
)

private val UPCOMING_STAGES = setOf(
    QuoteStage.SUBMITTED,
    QuoteStage.COUNTEROFFER,
    QuoteStage.AGREED_AWAITING_PAYMENT,
    QuoteStage.PAID,
    QuoteStage.READY,
)

private const val MAX_PRIORITY_REQUESTS = 2
