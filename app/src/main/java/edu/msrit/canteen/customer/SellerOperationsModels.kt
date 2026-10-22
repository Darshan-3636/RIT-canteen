package edu.msrit.canteen.customer

import java.time.LocalDateTime

/** Presentation record; production must replace this with seller-scoped server responses. */
data class BulkRequestRecord(
    val thread: QuoteThread,
    val customerUserId: String,
    val customerLabel: String,
    val original: BulkDraft,
    val appliedMinimum: Int,
    val createdAt: LocalDateTime,
    val verifiedPaidFixture: Boolean = false,
) {
    val reference: String get() = thread.reference
    val sellerId: String get() = thread.sellerId
    val latest: QuoteRevision get() = thread.revisions.maxBy { it.version }
}

data class BulkProposal(
    val lines: List<QuoteLineSnapshot>,
    val pickup: LocalDateTime,
    val specialPickup: Boolean,
    val specialReason: String,
    val note: String,
) {
    val totalPaise: Long get() = lines.sumOf { it.unitPricePaise * it.quantity }

    companion object {
        fun from(revision: QuoteRevision): BulkProposal = BulkProposal(
            revision.lines, revision.pickup, revision.specialPickup, revision.specialReason, revision.note)
    }
}

enum class ProposalIssue { NO_ITEMS, INVALID_LINE, OTHER_SELLER_ITEM, UNAVAILABLE_ITEM, BELOW_MINIMUM,
    INVALID_PICKUP, SPECIAL_FLAG_REQUIRED, SPECIAL_REASON_REQUIRED }

/** Local feedback only; authoritative submission must be rechecked by a future backend. */
fun validateBulkProposal(
    proposal: BulkProposal,
    sellerId: String,
    minimumSnapshot: Int,
    catalog: CustomerCatalogSource,
    policy: SellerBulkPolicy?,
    now: LocalDateTime,
): Set<ProposalIssue> {
    val issues = mutableSetOf<ProposalIssue>()
    val seller = catalog.sellers.find { it.id == sellerId }
    if (proposal.lines.isEmpty()) issues += ProposalIssue.NO_ITEMS
    if (proposal.lines.distinctBy { it.itemId }.size != proposal.lines.size) issues += ProposalIssue.INVALID_LINE
    var units = 0
    proposal.lines.forEach { line ->
        if (line.quantity !in 1..999 || line.unitPricePaise < 0 ||
            line.unitPricePaise > Long.MAX_VALUE / 999 / proposal.lines.size.coerceAtLeast(1)) {
            issues += ProposalIssue.INVALID_LINE
        }
        val item = catalog.items.find { it.id == line.itemId }
        if (item == null || item.sellerId != sellerId) issues += ProposalIssue.OTHER_SELLER_ITEM
        else {
            units += line.quantity.coerceAtLeast(0)
            if (seller == null || itemEligibility(seller, item) == ItemEligibility.UNAVAILABLE)
                issues += ProposalIssue.UNAVAILABLE_ITEM
        }
    }
    if (units < minimumSnapshot) issues += ProposalIssue.BELOW_MINIMUM
    when (pickupWindow(policy, proposal.pickup, now)) {
        PickupWindow.PAST, PickupWindow.HOURS_UNVERIFIED -> issues += ProposalIssue.INVALID_PICKUP
        PickupWindow.SPECIAL_REQUIRED -> {
            if (!proposal.specialPickup) issues += ProposalIssue.SPECIAL_FLAG_REQUIRED
            if (proposal.specialReason.isBlank()) issues += ProposalIssue.SPECIAL_REASON_REQUIRED
        }
        PickupWindow.REGULAR -> Unit
    }
    return issues
}

enum class SellerRequestStatus(val label: String) {
    NEW("New request"), CUSTOMER_COUNTER("Customer revision · seller action"),
    SELLER_REVIEW("Seller approval needed"), WAITING_CUSTOMER("Awaiting customer approval"),
    AGREED_UNPAID("Agreed · payment unverified"), PAID_FIXTURE("Verified-paid fixture"),
    EXPIRED("Expired"), WITHDRAWN("Withdrawn"), REJECTED("Rejected"),
}

fun BulkRequestRecord.operationalStatus(): SellerRequestStatus = when (thread.stage) {
    QuoteStage.SUBMITTED -> SellerRequestStatus.NEW
    QuoteStage.COUNTEROFFER -> when {
        thread.sellerAcceptedVersion == latest.version &&
            (!latest.specialPickup || thread.specialAcceptedVersion == latest.version) -> SellerRequestStatus.WAITING_CUSTOMER
        latest.actor == QuoteActor.CUSTOMER -> SellerRequestStatus.CUSTOMER_COUNTER
        else -> SellerRequestStatus.SELLER_REVIEW
    }
    QuoteStage.AGREED_AWAITING_PAYMENT -> SellerRequestStatus.AGREED_UNPAID
    QuoteStage.PAID_CONFIRMED -> SellerRequestStatus.PAID_FIXTURE
    QuoteStage.EXPIRED -> SellerRequestStatus.EXPIRED
    QuoteStage.WITHDRAWN -> SellerRequestStatus.WITHDRAWN
    QuoteStage.REJECTED -> SellerRequestStatus.REJECTED
}

enum class SellerRequestFilter(val label: String) {
    ALL("All"), NEEDS_ACTION("Needs action"), WAITING_CUSTOMER("Customer response"),
    UNPAID("Unpaid"), PAID("Paid fixture"), CLOSED("Closed")
}

fun filterSellerRequests(records: List<BulkRequestRecord>, sellerId: String,
                         filter: SellerRequestFilter = SellerRequestFilter.ALL, query: String = ""): List<BulkRequestRecord> =
    records.filter { record ->
        if (record.sellerId != sellerId) return@filter false
        val status = record.operationalStatus()
        val matchesStatus = when (filter) {
            SellerRequestFilter.ALL -> true
            SellerRequestFilter.NEEDS_ACTION -> status in setOf(SellerRequestStatus.NEW,
                SellerRequestStatus.CUSTOMER_COUNTER, SellerRequestStatus.SELLER_REVIEW)
            SellerRequestFilter.WAITING_CUSTOMER -> status == SellerRequestStatus.WAITING_CUSTOMER
            SellerRequestFilter.UNPAID -> status == SellerRequestStatus.AGREED_UNPAID
            SellerRequestFilter.PAID -> status == SellerRequestStatus.PAID_FIXTURE
            SellerRequestFilter.CLOSED -> status in setOf(SellerRequestStatus.EXPIRED,
                SellerRequestStatus.WITHDRAWN, SellerRequestStatus.REJECTED)
        }
        matchesStatus && (query.isBlank() || record.reference.contains(query, ignoreCase = true) ||
            record.customerLabel.contains(query, ignoreCase = true) ||
            record.thread.revisions.any { rev -> rev.lines.any { it.title.contains(query, ignoreCase = true) } })
    }.sortedByDescending { it.createdAt }

enum class DemoTransitionResult(val explanation: String) {
    APPLIED("Saved in this debug-only scenario. Nothing was sent to a server."),
    NOT_FOUND("This request is not available in the current seller/customer context."),
    UNAUTHORIZED("This mock role cannot perform that action for this seller."),
    STALE_VERSION("The quote changed. Reopen the latest revision before responding."),
    INVALID_STATE("This request state does not permit that action."),
    INVALID_PROPOSAL("Review the seller, items, quantities, price, pickup and special-request details."),
    EXPIRED("The quote expired. It needs a fresh seller-approved revision."),
    SPECIAL_APPROVAL_REQUIRED("This exceptional pickup needs a separate seller decision on the exact revision."),
}
