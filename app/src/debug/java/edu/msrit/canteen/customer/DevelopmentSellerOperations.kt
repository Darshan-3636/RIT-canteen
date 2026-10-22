package edu.msrit.canteen.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.msrit.canteen.navigation.AppMode
import edu.msrit.canteen.navigation.AuthorizedIdentity
import edu.msrit.canteen.navigation.PresentationAccess
import edu.msrit.canteen.navigation.ProtectedArea
import java.time.LocalDateTime

/** In-memory, single-process role-scoped scenarios. Nothing is transmitted or persisted. */
class DemoBulkScenario(
    private val clock: () -> LocalDateTime = { LocalDateTime.now(RIT_TIME_ZONE) },
    private val catalog: CustomerCatalogSource = PreviewCatalog,
    private val policies: BulkPolicySource = DevelopmentBulkPreview,
) : BulkOperationsRepository {
    private var nextLocalNumber = 1
    override var records by mutableStateOf(seed(clock()))
        private set

    override fun reset() { nextLocalNumber = 1; records = seed(clock()) }

    private fun update(reference: String, replacement: BulkRequestRecord) {
        records = records.map { if (it.reference == reference) replacement else it }
    }

    private fun isCustomer(record: BulkRequestRecord, actor: AuthorizedIdentity?): Boolean =
        PresentationAccess.allows(actor, ProtectedArea.Customer) && actor?.userId == record.customerUserId

    private fun isSeller(record: BulkRequestRecord, actor: AuthorizedIdentity?): Boolean =
        PresentationAccess.allows(actor, ProtectedArea.Seller(record.sellerId, "bulk.respond"))

    private fun sellerRecord(reference: String, actor: AuthorizedIdentity?): BulkRequestRecord? =
        records.find { it.reference == reference && isSeller(it, actor) }

    private fun expired(thread: QuoteThread, now: LocalDateTime): Boolean =
        thread.expiresAt?.let { !it.isAfter(now) } == true

    override fun submitLocalDemo(draft: BulkDraft, actor: AuthorizedIdentity?,
                                 now: LocalDateTime): Pair<DemoTransitionResult, String?> {
        if (!PresentationAccess.allows(actor, ProtectedArea.Customer)) return DemoTransitionResult.UNAUTHORIZED to null
        val check = checkBulkDraft(draft, catalog, policies, now)
        if (!check.readyForLocalReview) return DemoTransitionResult.INVALID_PROPOSAL to null
        val sellerId = draft.sellerId ?: return DemoTransitionResult.INVALID_PROPOSAL to null
        val reference = "DEMO-LOCAL-${nextLocalNumber.toString().padStart(3, '0')}"
        nextLocalNumber++
        val lines = draft.lines.map { line ->
            val item = catalog.items.first { it.id == line.itemId }
            QuoteLineSnapshot(item.id, item.name, line.quantity, item.price.rupees * 100L)
        }
        val revision = QuoteRevision(1, QuoteActor.CUSTOMER, lines, draft.pickup!!,
            draft.specialPickup, check.provisionalPaise, draft.instructions, now, draft.specialReason)
        records = records + BulkRequestRecord(
            QuoteThread(sellerId, reference, listOf(revision), QuoteStage.SUBMITTED),
            actor!!.userId, "Demo Customer", draft, check.minimum, now)
        return DemoTransitionResult.APPLIED to reference
    }

    override fun propose(reference: String, expectedVersion: Int, proposal: BulkProposal,
                         actor: AuthorizedIdentity?, now: LocalDateTime): DemoTransitionResult {
        val record = records.find { it.reference == reference } ?: return DemoTransitionResult.NOT_FOUND
        val seller = isSeller(record, actor)
        val customer = isCustomer(record, actor)
        if (!seller && !customer) return DemoTransitionResult.UNAUTHORIZED
        val current = record.latest
        if (current.version != expectedVersion) return DemoTransitionResult.STALE_VERSION
        if (record.thread.stage !in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER)) return DemoTransitionResult.INVALID_STATE
        if (expired(record.thread, now)) return DemoTransitionResult.EXPIRED
        val newActor = if (seller) QuoteActor.SELLER else QuoteActor.CUSTOMER
        if (current.actor == newActor) return DemoTransitionResult.INVALID_STATE
        if (validateBulkProposal(proposal, record.sellerId, record.appliedMinimum, catalog,
                policies.forSeller(record.sellerId), now).isNotEmpty()) return DemoTransitionResult.INVALID_PROPOSAL
        if (proposal.lines == current.lines && proposal.pickup == current.pickup &&
            proposal.specialPickup == current.specialPickup && proposal.specialReason == current.specialReason &&
            proposal.note.isBlank()) return DemoTransitionResult.INVALID_PROPOSAL
        val newVersion = current.version + 1
        val revision = QuoteRevision(newVersion, newActor, proposal.lines, proposal.pickup,
            proposal.specialPickup, proposal.totalPaise, proposal.note, now, proposal.specialReason)
        val nextThread = record.thread.copy(
            revisions = record.thread.revisions + revision,
            stage = QuoteStage.COUNTEROFFER,
            sellerAcceptedVersion = if (seller) newVersion else null,
            customerAcceptedVersion = null,
            specialAcceptedVersion = null,
            expiresAt = if (seller) now.plusHours(2) else null, // Fictional demo expiry, not a product default.
        )
        update(reference, record.copy(thread = nextThread))
        return DemoTransitionResult.APPLIED
    }

    override fun acceptSpecial(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                               now: LocalDateTime): DemoTransitionResult {
        val record = sellerRecord(reference, actor) ?: return DemoTransitionResult.NOT_FOUND
        if (record.latest.version != expectedVersion) return DemoTransitionResult.STALE_VERSION
        if (record.thread.stage !in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER)) return DemoTransitionResult.INVALID_STATE
        if (expired(record.thread, now)) return DemoTransitionResult.EXPIRED
        if (!record.latest.specialPickup || record.latest.specialReason.isBlank()) return DemoTransitionResult.INVALID_STATE
        update(reference, record.copy(thread = record.thread.copy(specialAcceptedVersion = expectedVersion)))
        return DemoTransitionResult.APPLIED
    }

    override fun rejectSpecial(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                               now: LocalDateTime): DemoTransitionResult {
        val record = sellerRecord(reference, actor) ?: return DemoTransitionResult.NOT_FOUND
        if (record.latest.version != expectedVersion) return DemoTransitionResult.STALE_VERSION
        if (record.thread.stage !in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER) ||
            !record.latest.specialPickup) return DemoTransitionResult.INVALID_STATE
        if (expired(record.thread, now)) return DemoTransitionResult.EXPIRED
        update(reference, record.copy(thread = record.thread.copy(stage = QuoteStage.REJECTED)))
        return DemoTransitionResult.APPLIED
    }

    override fun sellerApprove(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                               now: LocalDateTime): DemoTransitionResult {
        val record = sellerRecord(reference, actor) ?: return DemoTransitionResult.NOT_FOUND
        if (record.latest.version != expectedVersion) return DemoTransitionResult.STALE_VERSION
        if (record.thread.stage !in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER) ||
            record.thread.sellerAcceptedVersion == expectedVersion) return DemoTransitionResult.INVALID_STATE
        if (expired(record.thread, now)) return DemoTransitionResult.EXPIRED
        if (record.latest.specialPickup && record.thread.specialAcceptedVersion != expectedVersion)
            return DemoTransitionResult.SPECIAL_APPROVAL_REQUIRED
        update(reference, record.copy(thread = record.thread.copy(
            stage = QuoteStage.COUNTEROFFER, sellerAcceptedVersion = expectedVersion,
            expiresAt = now.plusHours(2)))) // Fictional demo expiry, not a product default.
        return DemoTransitionResult.APPLIED
    }

    override fun customerApprove(reference: String, expectedVersion: Int, actor: AuthorizedIdentity?,
                                 now: LocalDateTime): DemoTransitionResult {
        val record = records.find { it.reference == reference } ?: return DemoTransitionResult.NOT_FOUND
        if (!isCustomer(record, actor)) return DemoTransitionResult.UNAUTHORIZED
        if (record.latest.version != expectedVersion) return DemoTransitionResult.STALE_VERSION
        if (record.thread.stage != QuoteStage.COUNTEROFFER) return DemoTransitionResult.INVALID_STATE
        if (expired(record.thread, now)) return DemoTransitionResult.EXPIRED
        if (record.thread.sellerAcceptedVersion != expectedVersion) return DemoTransitionResult.INVALID_STATE
        if (record.latest.specialPickup && record.thread.specialAcceptedVersion != expectedVersion)
            return DemoTransitionResult.SPECIAL_APPROVAL_REQUIRED
        update(reference, record.copy(thread = record.thread.copy(
            customerAcceptedVersion = expectedVersion, stage = QuoteStage.AGREED_AWAITING_PAYMENT)))
        return DemoTransitionResult.APPLIED
    }

    override fun withdraw(reference: String, actor: AuthorizedIdentity?): DemoTransitionResult {
        val record = records.find { it.reference == reference } ?: return DemoTransitionResult.NOT_FOUND
        if (!isCustomer(record, actor)) return DemoTransitionResult.UNAUTHORIZED
        if (record.thread.stage !in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER,
                QuoteStage.AGREED_AWAITING_PAYMENT)) return DemoTransitionResult.INVALID_STATE
        update(reference, record.copy(thread = record.thread.copy(stage = QuoteStage.WITHDRAWN)))
        return DemoTransitionResult.APPLIED
    }

    override fun reject(reference: String, actor: AuthorizedIdentity?): DemoTransitionResult {
        val record = sellerRecord(reference, actor) ?: return DemoTransitionResult.NOT_FOUND
        if (record.thread.stage !in setOf(QuoteStage.SUBMITTED, QuoteStage.COUNTEROFFER))
            return DemoTransitionResult.INVALID_STATE
        update(reference, record.copy(thread = record.thread.copy(stage = QuoteStage.REJECTED)))
        return DemoTransitionResult.APPLIED
    }

    override fun expire(reference: String, now: LocalDateTime): DemoTransitionResult {
        val record = records.find { it.reference == reference } ?: return DemoTransitionResult.NOT_FOUND
        if (!expired(record.thread, now) || record.thread.stage !in setOf(
                QuoteStage.COUNTEROFFER, QuoteStage.AGREED_AWAITING_PAYMENT)) return DemoTransitionResult.INVALID_STATE
        update(reference, record.copy(thread = record.thread.copy(stage = QuoteStage.EXPIRED)))
        return DemoTransitionResult.APPLIED
    }

    private fun seed(now: LocalDateTime): List<BulkRequestRecord> {
        val north = "north-counter"
        val northPolicy = policies.forSeller(north)
        val regular = (1L..14L).asSequence().map {
            now.plusDays(it).withHour(11).withMinute(30).withSecond(0).withNano(0)
        }.first { pickupWindow(northPolicy, it, now) == PickupWindow.REGULAR }
        val special = now.plusDays(4).withHour(19).withMinute(0).withSecond(0).withNano(0)
        val northLines = listOf(
            QuoteLineSnapshot("north-lunch", "North Counter Lunch", 7, 9_000),
            QuoteLineSnapshot("north-snack", "Savoury Snack Box", 3, 6_500))
        val counterLines = listOf(
            QuoteLineSnapshot("north-lunch", "North Counter Lunch", 6, 8_500),
            QuoteLineSnapshot("north-snack", "Savoury Snack Box", 4, 6_500))
        val first = QuoteRevision(1, QuoteActor.CUSTOMER, northLines, regular, false,
            82_500, "Group lunch · demo", now.minusHours(3))
        val second = QuoteRevision(2, QuoteActor.SELLER, counterLines, regular.plusMinutes(30),
            false, 77_000, "Seller suggests a different mix and pickup time · demo", now.minusHours(2))
        val third = QuoteRevision(3, QuoteActor.CUSTOMER, counterLines, regular.plusMinutes(45),
            false, 77_000, "Customer requests 15 minutes later · demo", now.minusHours(1))
        fun record(reference: String, revisions: List<QuoteRevision>, stage: QuoteStage,
                   createdAt: LocalDateTime = now.minusHours(3), sellerId: String = north,
                   sellerAccepted: Int? = null, customerAccepted: Int? = null,
                   expires: LocalDateTime? = null, specialAccepted: Int? = null,
                   paidFixture: Boolean = false): BulkRequestRecord {
            val initial = revisions.first()
            return BulkRequestRecord(
                QuoteThread(sellerId, reference, revisions, stage, sellerAccepted, customerAccepted,
                    expires, specialAccepted),
                "debug-customer", "Demo Customer", BulkDraft(sellerId,
                    initial.lines.map { BulkLine(it.itemId, it.quantity) }, initial.pickup,
                    initial.specialPickup, initial.specialReason, initial.note),
                policies.forSeller(sellerId)?.minimum ?: DEFAULT_BULK_MINIMUM,
                createdAt, paidFixture)
        }
        val specialRevision = QuoteRevision(1, QuoteActor.CUSTOMER,
            listOf(QuoteLineSnapshot("north-lunch", "North Counter Lunch", 10, 9_000)),
            special, true, 90_000, "Club event pickup · demo", now.minusHours(2),
            "Campus event needs pickup after regular hours")
        val mainRevision = QuoteRevision(1, QuoteActor.CUSTOMER,
            listOf(QuoteLineSnapshot("masala-dosa", "Masala Dosa", 8, 5_000)),
            regular, false, 40_000, "Main Canteen-only fixture", now.minusHours(1))
        return listOf(
            record("DEMO-NC-NEW", listOf(first), QuoteStage.SUBMITTED, now.minusMinutes(20)),
            record("DEMO-NC-SPECIAL", listOf(specialRevision), QuoteStage.SUBMITTED, now.minusMinutes(35)),
            record("DEMO-NC-CUSTOMER", listOf(first, second, third), QuoteStage.COUNTEROFFER,
                now.minusHours(4)),
            record("DEMO-NC-WAITING", listOf(first, second), QuoteStage.COUNTEROFFER,
                now.minusHours(5), sellerAccepted = 2, expires = now.plusHours(2)),
            record("DEMO-NC-UNPAID", listOf(first, second), QuoteStage.AGREED_AWAITING_PAYMENT,
                now.minusDays(1), sellerAccepted = 2, customerAccepted = 2, expires = now.plusHours(1)),
            record("DEMO-NC-EXPIRED", listOf(first, second), QuoteStage.EXPIRED,
                now.minusDays(2), sellerAccepted = 2, expires = now.minusHours(1)),
            record("DEMO-NC-WITHDRAWN", listOf(first), QuoteStage.WITHDRAWN, now.minusDays(3)),
            record("DEMO-NC-REJECTED", listOf(first), QuoteStage.REJECTED, now.minusDays(4)),
            record("DEMO-NC-PAID", listOf(first, second), QuoteStage.PAID_CONFIRMED,
                now.minusDays(1), sellerAccepted = 2, customerAccepted = 2,
                expires = now.plusHours(1), paidFixture = true),
            record("DEMO-MAIN-NEW", listOf(mainRevision), QuoteStage.SUBMITTED,
                now.minusHours(1), sellerId = PreviewCatalog.MAIN_ID),
        )
    }
}

object DevelopmentSellerOperations : BulkOperationsRepository by DemoBulkScenario()
