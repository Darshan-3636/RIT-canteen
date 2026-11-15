package edu.msrit.canteen.customer

import edu.msrit.canteen.BuildConfig
import org.junit.Assume.assumeFalse
import org.junit.Before
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import edu.msrit.canteen.navigation.DevelopmentRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DemoBulkScenarioTest {
    @Before fun localFixtureOnly() = assumeFalse("Local negotiation fixtures are disabled in cloud mode", BuildConfig.RIT_CLOUD_DEMO)
    private val now = LocalDateTime.of(2026, 10, 21, 10, 0)
    private fun scenario() = DemoBulkScenario(clock = { now })
    private fun identity(role: DevelopmentRole) =
        DevelopmentPreviewAccess.identities.first { it.role == role }.authorization
    private val owner get() = identity(DevelopmentRole.SellerOwner)
    private val manager get() = identity(DevelopmentRole.SellerManager)
    private val staff get() = identity(DevelopmentRole.SellerStaff)
    private val customer get() = identity(DevelopmentRole.Customer)

    @Test fun sellerScopeFiltersOtherSellerAndStaffCannotRespond() {
        val scenario = scenario()
        assertEquals(9, filterSellerRequests(scenario.records, "north-counter").size)
        assertEquals(1, filterSellerRequests(scenario.records, PreviewCatalog.MAIN_ID).size)
        assertEquals(DemoTransitionResult.NOT_FOUND,
            scenario.sellerApprove("DEMO-MAIN-NEW", 1, owner, now))
        assertEquals(DemoTransitionResult.NOT_FOUND,
            scenario.sellerApprove("DEMO-NC-NEW", 1, staff, now))
        assertEquals(DemoTransitionResult.APPLIED,
            scenario.sellerApprove("DEMO-NC-NEW", 1, manager, now))
    }

    @Test fun inboxFiltersDoNotCallUnpaidOrPendingPaid() {
        val records = scenario().records
        assertEquals(listOf("DEMO-NC-UNPAID"),
            filterSellerRequests(records, "north-counter", SellerRequestFilter.UNPAID).map { it.reference })
        assertEquals(listOf("DEMO-NC-PAID"),
            filterSellerRequests(records, "north-counter", SellerRequestFilter.PAID).map { it.reference })
        assertEquals(3, filterSellerRequests(records, "north-counter", SellerRequestFilter.NEEDS_ACTION).size)
        assertEquals(3, filterSellerRequests(records, "north-counter", SellerRequestFilter.CLOSED).size)
        assertEquals(1, filterSellerRequests(records, "north-counter", query = "SPECIAL").size)
    }

    @Test fun sellerCounterofferCreatesVersionAndInvalidatesOldAcceptance() {
        val scenario = scenario()
        val old = scenario.records.first { it.reference == "DEMO-NC-CUSTOMER" }
        val revision = old.latest
        val proposal = BulkProposal(listOf(
            QuoteLineSnapshot("north-lunch", "North Counter Lunch", 5, 8_000),
            QuoteLineSnapshot("north-snack", "Savoury Snack Box", 5, 6_000)),
            revision.pickup.plusMinutes(15), false, "", "New mix and timing")
        assertEquals(DemoTransitionResult.APPLIED,
            scenario.propose(old.reference, revision.version, proposal, owner, now))
        val changed = scenario.records.first { it.reference == old.reference }
        assertEquals(4, changed.latest.version)
        assertEquals(70_000L, changed.latest.totalPaise)
        assertEquals(proposal.pickup, changed.latest.pickup)
        assertEquals(4, changed.thread.sellerAcceptedVersion)
        assertNull(changed.thread.customerAcceptedVersion)
        assertNull(changed.thread.specialAcceptedVersion)
        assertEquals(DemoTransitionResult.STALE_VERSION,
            scenario.propose(old.reference, revision.version, proposal, owner, now))
        assertFalse(paymentEligibility(changed.thread, now, serverConfirmed = true))
    }

    @Test fun customerRevisionRequiresFreshSellerAndCustomerApproval() {
        val scenario = scenario()
        val record = scenario.records.first { it.reference == "DEMO-NC-WAITING" }
        val proposal = BulkProposal.from(record.latest).copy(
            pickup = record.latest.pickup.plusMinutes(15), note = "Can we collect later?")
        assertEquals(DemoTransitionResult.APPLIED,
            scenario.propose(record.reference, 2, proposal, customer, now))
        val changed = scenario.records.first { it.reference == record.reference }
        assertEquals(3, changed.latest.version)
        assertNull(changed.thread.sellerAcceptedVersion)
        assertEquals(DemoTransitionResult.INVALID_STATE,
            scenario.customerApprove(record.reference, 3, customer, now))
        assertEquals(DemoTransitionResult.APPLIED,
            scenario.sellerApprove(record.reference, 3, owner, now))
        assertEquals(DemoTransitionResult.APPLIED,
            scenario.customerApprove(record.reference, 3, customer, now))
        val agreed = scenario.records.first { it.reference == record.reference }
        assertEquals(QuoteStage.AGREED_AWAITING_PAYMENT, agreed.thread.stage)
        assertFalse(paymentEligibility(agreed.thread, now, serverConfirmed = false))
    }

    @Test fun specialTimingNeedsSeparateExactVersionDecision() {
        val scenario = scenario()
        val reference = "DEMO-NC-SPECIAL"
        assertEquals(DemoTransitionResult.SPECIAL_APPROVAL_REQUIRED,
            scenario.sellerApprove(reference, 1, owner, now))
        assertEquals(DemoTransitionResult.APPLIED,
            scenario.acceptSpecial(reference, 1, manager, now))
        assertEquals(DemoTransitionResult.APPLIED,
            scenario.sellerApprove(reference, 1, owner, now))
        assertEquals(DemoTransitionResult.APPLIED,
            scenario.customerApprove(reference, 1, customer, now))
        val agreed = scenario.records.first { it.reference == reference }
        assertEquals(1, agreed.thread.specialAcceptedVersion)
        assertFalse(paymentEligibility(agreed.thread, now, serverConfirmed = false))
    }

    @Test fun rejectionWithdrawalAndExpiredStateBlockInvalidTransitions() {
        val scenario = scenario()
        assertEquals(DemoTransitionResult.APPLIED,
            scenario.rejectSpecial("DEMO-NC-SPECIAL", 1, owner, now))
        assertEquals(DemoTransitionResult.INVALID_STATE,
            scenario.sellerApprove("DEMO-NC-SPECIAL", 1, owner, now))
        assertEquals(DemoTransitionResult.APPLIED,
            scenario.withdraw("DEMO-NC-UNPAID", customer))
        assertEquals(DemoTransitionResult.INVALID_STATE,
            scenario.customerApprove("DEMO-NC-UNPAID", 2, customer, now))
        assertEquals(DemoTransitionResult.INVALID_STATE,
            scenario.withdraw("DEMO-NC-PAID", customer))
        assertEquals(DemoTransitionResult.EXPIRED,
            scenario.customerApprove("DEMO-NC-WAITING", 2, customer, now.plusHours(3)))
        assertEquals(DemoTransitionResult.APPLIED,
            scenario.expire("DEMO-NC-WAITING", now.plusHours(3)))
    }

    @Test fun localSubmissionUsesOneSellerMinimumAndNeverBecomesRealPayment() {
        val scenario = scenario()
        val pickup = scenario.records.first { it.reference == "DEMO-NC-NEW" }.latest.pickup
        val draft = BulkDraft("north-counter", listOf(BulkLine("north-lunch", 8)), pickup)
        val (result, reference) = scenario.submitLocalDemo(draft, customer, now)
        assertEquals(DemoTransitionResult.APPLIED, result)
        assertNotNull(reference)
        val saved = scenario.records.first { it.reference == reference }
        assertEquals(8, saved.appliedMinimum)
        assertEquals(QuoteStage.SUBMITTED, saved.thread.stage)
        assertFalse(paymentEligibility(saved.thread, now, serverConfirmed = false))
        val mixed = draft.copy(lines = listOf(BulkLine("north-lunch", 9), BulkLine("masala-dosa", 1)))
        assertEquals(DemoTransitionResult.INVALID_PROPOSAL, scenario.submitLocalDemo(mixed, customer, now).first)
        assertEquals(DemoTransitionResult.UNAUTHORIZED, scenario.submitLocalDemo(draft, owner, now).first)
        scenario.reset()
        assertTrue(scenario.records.none { it.reference == reference })
    }

    @Test fun revisedProposalRejectsForeignItemAndMinimumShortfall() {
        val scenario = scenario()
        val record = scenario.records.first { it.reference == "DEMO-NC-NEW" }
        val foreign = BulkProposal(listOf(QuoteLineSnapshot("masala-dosa", "Masala Dosa", 10, 5_000)),
            record.latest.pickup, false, "", "")
        assertTrue(ProposalIssue.OTHER_SELLER_ITEM in validateBulkProposal(foreign, record.sellerId,
            record.appliedMinimum, PreviewCatalog, DevelopmentBulkPreview.forSeller(record.sellerId), now))
        assertEquals(DemoTransitionResult.INVALID_PROPOSAL,
            scenario.propose(record.reference, 1, foreign, owner, now))
        val short = BulkProposal.from(record.latest).copy(lines = listOf(
            QuoteLineSnapshot("north-lunch", "North Counter Lunch", 7, 9_000)))
        assertTrue(ProposalIssue.BELOW_MINIMUM in validateBulkProposal(short, record.sellerId,
            record.appliedMinimum, PreviewCatalog, DevelopmentBulkPreview.forSeller(record.sellerId), now))
    }

    @Test fun blankMessageWithoutTermChangeCannotCreateMisleadingRevision() {
        val scenario = scenario()
        val record = scenario.records.first { it.reference == "DEMO-NC-NEW" }
        val unchanged = BulkProposal.from(record.latest).copy(note = "")
        assertEquals(DemoTransitionResult.INVALID_PROPOSAL,
            scenario.propose(record.reference, 1, unchanged, owner, now))
        assertEquals(1, scenario.records.first { it.reference == record.reference }.latest.version)
    }
}
