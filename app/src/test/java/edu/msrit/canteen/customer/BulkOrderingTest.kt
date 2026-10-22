package edu.msrit.canteen.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BulkOrderingTest {
    private val now = LocalDateTime.of(2026, 10, 21, 10, 0)
    private val pickup = LocalDateTime.of(2026, 10, 23, 11, 30)
    private val policy = SellerBulkPolicy(PreviewCatalog.MAIN_ID, weeklyHours = mapOf(
        DayOfWeek.FRIDAY to listOf(BulkTimeWindow(LocalTime.of(9, 0), LocalTime.of(17, 0)))))
    private val policies = object : BulkPolicySource {
        override fun forSeller(sellerId: String): SellerBulkPolicy? = if (sellerId == policy.sellerId) policy else null
    }

    @Test fun combinedUnitsAndPackagedItemsCountForOneSeller() {
        val draft = BulkDraft(PreviewCatalog.MAIN_ID,
            listOf(BulkLine("masala-dosa", 3), BulkLine("water-bottle", 5)), pickup)
        val check = checkBulkDraft(draft, PreviewCatalog, policies, now)
        assertEquals(8, check.totalUnits)
        assertEquals(8, check.minimum)
        assertEquals(25_000L, check.provisionalPaise)
        assertTrue(check.readyForLocalReview)
        assertEquals(PickupWindow.REGULAR, check.pickupWindow)
    }

    @Test fun belowMinimumCrossSellerAndSessionSwitchAreRejected() {
        val seven = BulkDraft(PreviewCatalog.MAIN_ID,
            listOf(BulkLine("masala-dosa", 4), BulkLine("water-bottle", 3)), pickup)
        assertTrue(BulkDraftIssue.BELOW_MINIMUM in checkBulkDraft(seven, PreviewCatalog, policies, now).issues)
        val mixed = seven.copy(lines = listOf(BulkLine("masala-dosa", 5), BulkLine("pantry-juice", 3)))
        val mixedCheck = checkBulkDraft(mixed, PreviewCatalog, policies, now)
        assertEquals(5, mixedCheck.totalUnits)
        assertTrue(BulkDraftIssue.CROSS_SELLER_ITEM in mixedCheck.issues)
        assertTrue(BulkDraftIssue.BELOW_MINIMUM in mixedCheck.issues)
        val session = BulkDraftSession()
        assertTrue(session.selectSeller(PreviewCatalog.MAIN_ID))
        assertTrue(session.setQuantity(PreviewCatalog.items.first { it.id == "masala-dosa" }, 5))
        assertFalse(session.selectSeller("campus-pantry"))
        assertFalse(session.setQuantity(PreviewCatalog.items.first { it.id == "pantry-juice" }, 3))
        assertEquals(PreviewCatalog.MAIN_ID, session.draft.sellerId)
        session.clear()
        assertTrue(session.selectSeller("campus-pantry"))
    }

    @Test fun sellerMinimumOverrideIsIndependentOfDefault() {
        val overridden = object : BulkPolicySource {
            override fun forSeller(sellerId: String) = policy.copy(minimumOverride = 10)
        }
        val draft = BulkDraft(PreviewCatalog.MAIN_ID, listOf(BulkLine("masala-dosa", 8)), pickup)
        val check = checkBulkDraft(draft, PreviewCatalog, overridden, now)
        assertEquals(10, check.minimum)
        assertTrue(BulkDraftIssue.BELOW_MINIMUM in check.issues)
        assertEquals(8, policy.minimum)
    }

    @Test fun unavailableItemsAndInvalidQuantitiesCannotProceed() {
        val unavailable = BulkDraft(PreviewCatalog.MAIN_ID,
            listOf(BulkLine("lemon-rice", 8)), pickup)
        assertTrue(BulkDraftIssue.ITEM_UNAVAILABLE in checkBulkDraft(unavailable, PreviewCatalog, policies, now).issues)
        val invalid = unavailable.copy(lines = listOf(BulkLine("masala-dosa", -2)))
        val issues = checkBulkDraft(invalid, PreviewCatalog, policies, now).issues
        assertTrue(BulkDraftIssue.INVALID_QUANTITY in issues)
        assertTrue(BulkDraftIssue.BELOW_MINIMUM in issues)
    }

    @Test fun hoursHolidayClosureAndSpecialReasonAreValidated() {
        val exceptions = policy.copy(holidays = setOf(LocalDate.of(2026, 10, 23)),
            temporaryClosures = setOf(LocalDate.of(2026, 10, 24)))
        val source = object : BulkPolicySource { override fun forSeller(sellerId: String) = exceptions }
        val base = BulkDraft(PreviewCatalog.MAIN_ID, listOf(BulkLine("masala-dosa", 8)), pickup)
        val closed = checkBulkDraft(base, PreviewCatalog, source, now)
        assertEquals(PickupWindow.SPECIAL_REQUIRED, closed.pickupWindow)
        assertTrue(BulkDraftIssue.SPECIAL_REQUIRED in closed.issues)
        assertTrue(BulkDraftIssue.SPECIAL_REASON_REQUIRED in closed.issues)
        assertTrue(checkBulkDraft(base.copy(specialPickup = true, specialReason = "Late club event"),
            PreviewCatalog, source, now).readyForLocalReview)
        assertEquals(PickupWindow.SPECIAL_REQUIRED,
            pickupWindow(exceptions, LocalDateTime.of(2026, 10, 24, 11, 0), now))
        assertEquals(PickupWindow.SPECIAL_REQUIRED,
            pickupWindow(policy, LocalDateTime.of(2026, 10, 25, 11, 0), now))
        assertEquals(PickupWindow.PAST, pickupWindow(policy, now.minusMinutes(1), now))
        assertTrue(BulkDraftIssue.HOURS_UNVERIFIED in
            checkBulkDraft(base, PreviewCatalog, object : BulkPolicySource {
                override fun forSeller(sellerId: String): SellerBulkPolicy? = null
            }, now).issues)
    }

    @Test fun quoteDiffShowsItemsQuantityPickupAndAmount() {
        val first = QuoteRevision(1, QuoteActor.CUSTOMER, listOf(
            QuoteLineSnapshot("a", "A", 3, 5_000), QuoteLineSnapshot("b", "B", 5, 2_000)),
            pickup, false, 25_000, "", now)
        val second = QuoteRevision(2, QuoteActor.SELLER, listOf(
            QuoteLineSnapshot("b", "B", 6, 2_200), QuoteLineSnapshot("c", "C", 3, 8_000)),
            pickup.plusMinutes(30), true, 37_200, "", now.plusMinutes(1))
        val diff = compareQuotes(first, second)
        assertEquals(listOf("a"), diff.removed.map { it.itemId })
        assertEquals(listOf("c"), diff.added.map { it.itemId })
        assertEquals(listOf("b"), diff.changed.map { it.second.itemId })
        assertTrue(diff.pickupChanged)
        assertTrue(diff.specialChanged)
        assertTrue(diff.amountChanged)
    }

    @Test fun paymentRequiresExactLatestUnexpiredServerConfirmedRevision() {
        val first = QuoteRevision(1, QuoteActor.CUSTOMER,
            listOf(QuoteLineSnapshot("a", "A", 8, 1_000)), pickup, false, 8_000, "", now)
        val second = first.copy(version = 2, actor = QuoteActor.SELLER, totalPaise = 7_500)
        val agreed = QuoteThread(PreviewCatalog.MAIN_ID, "DEMO", listOf(first, second),
            QuoteStage.AGREED_AWAITING_PAYMENT, 2, 2, now.plusHours(1))
        assertFalse(paymentEligibility(agreed, now, serverConfirmed = false))
        assertTrue(paymentEligibility(agreed, now, serverConfirmed = true))
        assertFalse(paymentEligibility(agreed.copy(customerAcceptedVersion = 1), now, true))
        assertFalse(paymentEligibility(agreed.copy(expiresAt = now.minusSeconds(1)), now, true))
        assertFalse(paymentEligibility(agreed.copy(stage = QuoteStage.WITHDRAWN), now, true))
    }
}
