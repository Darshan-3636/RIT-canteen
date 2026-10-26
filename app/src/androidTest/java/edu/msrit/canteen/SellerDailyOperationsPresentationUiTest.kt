package edu.msrit.canteen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.DemoBulkScenario
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.QuoteActor
import edu.msrit.canteen.ui.screens.SellerCounterofferPreviewPresentation
import edu.msrit.canteen.ui.screens.SellerDailyOperationsOverview
import edu.msrit.canteen.ui.screens.SellerDailyRequestDetailPresentation
import edu.msrit.canteen.ui.screens.SellerPickupSchedulePresentation
import edu.msrit.canteen.ui.theme.RitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SellerDailyOperationsPresentationUiTest {
    @get:Rule val rule = createComposeRule()

    private val now = LocalDateTime.of(2026, 10, 23, 9, 0)
    private val records get() = DemoBulkScenario(clock = { now }).records

    @Test fun ownerSeesPriorityRequestsPickupAndAvailableBusinessEntries() {
        val openedRequest = AtomicReference<String?>(null)
        val openedSchedule = AtomicBoolean(false)
        val openedMenu = AtomicBoolean(false)
        val openedSales = AtomicBoolean(false)
        render(
            records = records,
            sellerId = PreviewCatalog.MAIN_ID,
            canRespond = true,
            onOpenRequest = { openedRequest.set(it) },
            onOpenSchedule = { openedSchedule.set(true) },
            onOpenMenu = { openedMenu.set(true) },
            onOpenSales = { openedSales.set(true) },
        )

        rule.onNodeWithTag("seller_daily_reply_summary").assertExists()
        rule.onNodeWithTag("seller_daily_pickup_summary").assertExists()
        rule.onNodeWithTag("seller_daily_reply_card_DEMO-MAIN-NEW").assertExists()
        rule.onNodeWithContentDescription(
            "Masala Dosa request preview image",
            useUnmergedTree = true,
        ).assertExists()

        rule.onNodeWithTag("seller_daily_reply_card_DEMO-MAIN-NEW").performClick()
        assertEquals("DEMO-MAIN-NEW", openedRequest.get())
        rule.onNodeWithTag("seller_daily_pickup_summary").performClick()
        assertEquals(true, openedSchedule.get())

        rule.onNodeWithTag("seller_daily_overview_list")
            .performScrollToNode(hasTestTag("seller_daily_menu"))
        rule.onNodeWithTag("seller_daily_menu").performClick()
        rule.onNodeWithTag("seller_daily_sales").performClick()
        assertEquals(true, openedMenu.get())
        assertEquals(true, openedSales.get())
    }

    @Test fun staffCanOpenDetailsButDoesNotSeeReplyAction() {
        render(
            records = records,
            sellerId = "north-counter",
            canRespond = false,
        )

        rule.onNodeWithTag("seller_daily_reply_summary").assertExists()
        rule.onNodeWithTag("seller_daily_reply_card_DEMO-NC-NEW").assertExists()
        rule.onNodeWithText("Review and reply", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription(
            "Needs seller reply",
            substring = true,
        ).assertExists()
    }

    @Test fun noRecordsShowHelpfulEmptyStates() {
        render(records = emptyList(), sellerId = "north-counter", canRespond = true)
        rule.onNodeWithTag("seller_daily_no_replies").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_daily_no_pickups").performScrollTo().assertExists()
        rule.onNodeWithText("You're all caught up").assertExists()
    }

    @Test fun refreshErrorOffersRetryCallback() {
        val retried = AtomicBoolean(false)
        render(
            records = emptyList(),
            sellerId = "north-counter",
            canRespond = true,
            errorMessage = "Check your connection and try again.",
            onRetry = { retried.set(true) },
        )

        rule.onNodeWithText("Requests could not be refreshed").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_daily_retry").performScrollTo().performClick()
        assertEquals(true, retried.get())
    }

    @Test fun requestDetailShowsHumanJourneyAndRevisionBoundApprovals() {
        val record = records.first { it.reference == "DEMO-NC-CUSTOMER" }
        val continued = AtomicBoolean(false)
        renderDetail(record, canRespond = true, onPrimaryAction = { continued.set(true) })

        rule.onNodeWithTag("seller_daily_request_detail").assertExists()
        rule.onNodeWithTag("seller_detail_summary").assertExists()
        rule.onNodeWithContentDescription("Seller needs to respond", substring = true).assertExists()
        rule.onNodeWithTag("seller_detail_journey").assertExists()
        rule.onNodeWithContentDescription("Seller approval for revision 3: not recorded for this revision").assertExists()
        rule.onNodeWithContentDescription("Customer approval for revision 3: not recorded for this revision").assertExists()
        rule.onNodeWithTag("seller_detail_primary_action").performScrollTo().performClick()
        assertEquals(true, continued.get())
    }

    @Test fun waitingAndUnpaidStatesHaveNoSellerReplyAction() {
        val waiting = records.first { it.reference == "DEMO-NC-WAITING" }
        renderDetail(waiting, canRespond = true)
        rule.onNodeWithText("Waiting for customer").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_detail_primary_action").assertDoesNotExist()
        rule.onNodeWithContentDescription("Seller approval for revision 2: recorded").assertExists()
        rule.onNodeWithContentDescription("Customer approval for revision 2: not recorded for this revision").assertExists()

        val unpaid = records.first { it.reference == "DEMO-NC-UNPAID" }
        renderDetail(unpaid, canRespond = true)
        rule.onNodeWithText("Payment not yet verified").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_detail_primary_action").assertDoesNotExist()
        rule.onNodeWithText("Do not treat this as paid.", substring = true).assertExists()
    }

    @Test fun specialPickupUsesSeparateExplicitDecisionAndStaffHasNoAction() {
        val special = records.first { it.reference == "DEMO-NC-SPECIAL" }
        val decisionOpened = AtomicBoolean(false)
        renderDetail(
            special,
            canRespond = true,
            onReviewSpecialPickup = { decisionOpened.set(true) },
        )
        rule.onNodeWithText("Pickup requires explicit exception approval").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_detail_primary_action").performScrollTo().performClick()
        assertEquals(true, decisionOpened.get())

        renderDetail(special, canRespond = false)
        rule.onNodeWithText("Seller response needed").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_detail_primary_action").assertDoesNotExist()
    }

    @Test fun elapsedQuoteExpiryRemovesResponseActionAndScheduleEntry() {
        val request = records.first { it.reference == "DEMO-NC-NEW" }
        val expired = request.copy(
            thread = request.thread.copy(expiresAt = now.minusMinutes(1)),
        )
        renderDetail(expired, canRespond = true)
        rule.onNodeWithText("Quote expired").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_detail_primary_action").assertDoesNotExist()

        rule.setContent {
            RitTheme(darkTheme = false) {
                SellerPickupSchedulePresentation(
                    records = listOf(expired),
                    sellerId = expired.sellerId,
                    onBack = {},
                    onOpenRequest = {},
                    canRespond = true,
                    now = now,
                )
            }
        }
        rule.onNodeWithText("No upcoming requested pickups").assertExists()
        rule.onNodeWithTag("seller_schedule_request_DEMO-NC-NEW").assertDoesNotExist()
    }

    @Test fun counterofferPreviewShowsBeforeAfterAndIsReadableInDarkCompactLargeText() {
        val record = records.first { it.reference == "DEMO-NC-CUSTOMER" }
        val previous = record.latest
        val proposal = previous.copy(
            version = previous.version + 1,
            actor = QuoteActor.SELLER,
            lines = previous.lines.mapIndexed { index, line ->
                if (index == 0) line.copy(quantity = line.quantity + 1, unitPricePaise = line.unitPricePaise + 250)
                else line
            },
            pickup = previous.pickup.plusMinutes(20),
            totalPaise = 0L,
            note = "Seller suggests one additional lunch.",
            createdAt = now,
        ).let { candidate -> candidate.copy(totalPaise = candidate.lines.sumOf { it.quantity * it.unitPricePaise }) }
        val continued = AtomicBoolean(false)
        renderCompact(darkTheme = true) {
            SellerCounterofferPreviewPresentation(
                record = record,
                proposal = proposal,
                canRespond = true,
                onBack = {},
                onContinueReview = { continued.set(true) },
                onReviewSpecialPickup = {},
                now = now,
            )
        }

        rule.onNodeWithTag("seller_counteroffer_preview").assertExists()
        rule.onNodeWithTag("seller_counteroffer_summary").assertExists()
        rule.onNodeWithTag("seller_counteroffer_line_north-lunch").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Before:", substring = true).assertExists()
        rule.onNodeWithContentDescription("Proposed:", substring = true).assertExists()
        rule.onNodeWithText("No approvals are recorded for revision 4").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_counteroffer_continue").performScrollTo().performClick()
        assertEquals(true, continued.get())
    }

    @Test fun specialCounterofferPreviewRoutesToExplicitPickupDecisionFirst() {
        val record = records.first { it.reference == "DEMO-NC-CUSTOMER" }
        val previous = record.latest
        val proposal = previous.copy(
            version = previous.version + 1,
            actor = QuoteActor.SELLER,
            specialPickup = true,
            specialReason = "Campus event pickup requested outside regular hours.",
            pickup = previous.pickup.plusDays(1),
            totalPaise = previous.totalPaise,
            createdAt = now,
        )
        val continued = AtomicBoolean(false)
        val specialReviewOpened = AtomicBoolean(false)
        rule.setContent {
            RitTheme(darkTheme = false) {
                SellerCounterofferPreviewPresentation(
                    record = record,
                    proposal = proposal,
                    canRespond = true,
                    onBack = {},
                    onContinueReview = { continued.set(true) },
                    onReviewSpecialPickup = { specialReviewOpened.set(true) },
                    now = now,
                )
            }
        }

        rule.onNodeWithText("Pickup requires explicit exception approval").performScrollTo().assertExists()
        rule.onNodeWithTag("seller_counteroffer_review_special").performScrollTo().performClick()
        assertEquals(true, specialReviewOpened.get())
        assertEquals(false, continued.get())
    }

    @Test fun pickupScheduleGroupsFutureRequestsAndExcludesUnverifiedPaidState() {
        val scheduleRecords = records
        val paid = scheduleRecords.first { it.reference == "DEMO-NC-PAID" }
        val unpaidFixtureCopy = paid.copy(
            thread = paid.thread.copy(reference = "DEMO-NC-PAID-UNVERIFIED"),
            verifiedPaidFixture = false,
        )
        val opened = AtomicReference<String?>(null)
        rule.setContent {
            RitTheme(darkTheme = false) {
                SellerPickupSchedulePresentation(
                    records = scheduleRecords + unpaidFixtureCopy,
                    sellerId = "north-counter",
                    onBack = {},
                    onOpenRequest = { opened.set(it) },
                    canRespond = true,
                    now = now,
                )
            }
        }

        val firstUpcoming = scheduleRecords.first { it.sellerId == "north-counter" && it.latest.pickup.isAfter(now) }
        rule.onNodeWithTag("seller_schedule_day_${firstUpcoming.latest.pickup.toLocalDate()}")
            .performScrollTo().assertExists()
        rule.onNodeWithTag("seller_schedule_request_DEMO-NC-PAID-UNVERIFIED").assertDoesNotExist()
        rule.onNodeWithContentDescription("Requested pickup", substring = true).assertExists()
        rule.onNodeWithTag("seller_schedule_request_${firstUpcoming.reference}").performScrollTo().performClick()
        assertEquals(firstUpcoming.reference, opened.get())
    }

    @Test fun scheduleRefreshErrorHasRetryAndDoesNotClaimAllClear() {
        val retried = AtomicBoolean(false)
        rule.setContent {
            RitTheme(darkTheme = true) {
                SellerPickupSchedulePresentation(
                    records = emptyList(),
                    sellerId = "north-counter",
                    onBack = {},
                    onOpenRequest = {},
                    canRespond = true,
                    now = now,
                    errorMessage = "Check your connection and try again.",
                    onRetry = { retried.set(true) },
                )
            }
        }

        rule.onNodeWithText("Schedule could not be refreshed").performScrollTo().assertExists()
        rule.onNodeWithText("Pickup times unavailable").performScrollTo().assertExists()
        rule.onNodeWithText("No upcoming requested pickups").assertDoesNotExist()
        rule.onNodeWithTag("seller_schedule_retry").performScrollTo().performClick()
        assertEquals(true, retried.get())
    }

    private fun renderDetail(
        record: edu.msrit.canteen.customer.BulkRequestRecord,
        canRespond: Boolean,
        onPrimaryAction: () -> Unit = {},
        onReviewSpecialPickup: () -> Unit = {},
    ) {
        rule.setContent {
            RitTheme(darkTheme = false) {
                SellerDailyRequestDetailPresentation(
                    record = record,
                    canRespond = canRespond,
                    onBack = {},
                    onPrimaryAction = onPrimaryAction,
                    onReviewSpecialPickup = onReviewSpecialPickup,
                    onCompareRevisions = {},
                    now = now,
                )
            }
        }
    }

    private fun renderCompact(darkTheme: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.3f)) {
                RitTheme(darkTheme = darkTheme) {
                    Box(Modifier.width(320.dp).height(820.dp)) { content() }
                }
            }
        }
    }

    private fun render(
        records: List<edu.msrit.canteen.customer.BulkRequestRecord>,
        sellerId: String,
        canRespond: Boolean,
        onOpenRequest: (String) -> Unit = {},
        onOpenSchedule: () -> Unit = {},
        onOpenMenu: (() -> Unit)? = {},
        onOpenSales: (() -> Unit)? = {},
        errorMessage: String? = null,
        onRetry: (() -> Unit)? = null,
    ) {
        rule.setContent {
            RitTheme(darkTheme = false) {
                SellerDailyOperationsOverview(
                    records = records,
                    sellerId = sellerId,
                    sellerName = if (sellerId == PreviewCatalog.MAIN_ID) "Main Canteen" else "North Counter · demo",
                    roleLabel = if (canRespond) "Owner" else "Staff",
                    canRespond = canRespond,
                    onBack = {},
                    onOpenRequest = onOpenRequest,
                    onOpenRequests = {},
                    onOpenSchedule = onOpenSchedule,
                    onOpenMenu = onOpenMenu,
                    onOpenSales = onOpenSales,
                    errorMessage = errorMessage,
                    onRetry = onRetry,
                    now = now,
                )
            }
        }
    }
}
