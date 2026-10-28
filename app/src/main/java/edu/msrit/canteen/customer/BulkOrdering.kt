package edu.msrit.canteen.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

val RIT_TIME_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
const val DEFAULT_BULK_MINIMUM = 8

data class BulkLine(val itemId: String, val quantity: Int)

/** A local composition only. It is not a submitted order or server-held cart. */
data class BulkDraft(
    val sellerId: String? = null,
    val lines: List<BulkLine> = emptyList(),
    val pickup: LocalDateTime? = null,
    val specialPickup: Boolean = false,
    val specialReason: String = "",
    val instructions: String = "",
)

data class BulkServerDraft(val version: Long, val draft: BulkDraft, val subtotalPaise: Long,
                           val unavailableItemIds: Set<String>)

/** Account-scoped backend key survives app restarts and a timed-out submission retry. */
fun bulkSubmissionKeyForVersion(version: Long): String {
    require(version >= 0)
    return "bulk-draft-version-$version"
}

class BulkDraftSession {
    var draft by mutableStateOf(BulkDraft())
        private set
    var revision: Long = 0
        private set
    var onChanged: (() -> Unit)? = null
    private var submissionKey: String = UUID.randomUUID().toString()

    /** Stable across transport retries; changing the draft starts a new server attempt. */
    fun idempotencyKey(): String = submissionKey
    private fun changed() { revision++; submissionKey = UUID.randomUUID().toString(); onChanged?.invoke() }

    /** Server responses replace local edits only after an explicit fetch, not after a stale-write failure. */
    fun applyServer(value: BulkServerDraft) {
        draft = value.draft
        revision++
        submissionKey = UUID.randomUUID().toString()
    }

    fun selectSeller(sellerId: String): Boolean {
        if (draft.sellerId != null && draft.sellerId != sellerId && draft.lines.isNotEmpty()) return false
        if (draft.sellerId != sellerId) { draft = BulkDraft(sellerId = sellerId); changed() }
        return true
    }

    fun setQuantity(item: MenuItem, quantity: Int): Boolean {
        if (item.sellerId != draft.sellerId || quantity < 0 || quantity > 999) return false
        val other = draft.lines.filterNot { it.itemId == item.id }
        draft = draft.copy(lines = if (quantity == 0) other else other + BulkLine(item.id, quantity))
        changed()
        return true
    }

    fun setPickup(value: LocalDateTime?) { draft = draft.copy(pickup = value); changed() }
    fun setSpecial(value: Boolean) { draft = draft.copy(specialPickup = value, specialReason = if (value) draft.specialReason else ""); changed() }
    fun setSpecialReason(value: String) { draft = draft.copy(specialReason = value.take(500)); changed() }
    fun setInstructions(value: String) { draft = draft.copy(instructions = value.take(500)); changed() }
    fun clear() { draft = BulkDraft(); changed() }
}

data class BulkTimeWindow(val opens: LocalTime, val closes: LocalTime) {
    init { require(opens < closes) }
    fun contains(time: LocalTime): Boolean = !time.isBefore(opens) && time.isBefore(closes)
}

data class SellerBulkPolicy(
    val sellerId: String,
    val minimumOverride: Int? = null,
    val weeklyHours: Map<DayOfWeek, List<BulkTimeWindow>> = emptyMap(),
    val holidays: Set<LocalDate> = emptySet(),
    val temporaryClosures: Set<LocalDate> = emptySet(),
    val specialPickupEnabled: Boolean = true,
) {
    init { require(minimumOverride == null || minimumOverride > 0) }
    val minimum: Int get() = minimumOverride ?: DEFAULT_BULK_MINIMUM
}

interface BulkPolicySource { fun forSeller(sellerId: String): SellerBulkPolicy? }

enum class PickupWindow { REGULAR, SPECIAL_REQUIRED, HOURS_UNVERIFIED, PAST }

fun pickupWindow(policy: SellerBulkPolicy?, pickup: LocalDateTime, now: LocalDateTime): PickupWindow {
    if (!pickup.isAfter(now)) return PickupWindow.PAST
    if (policy == null || policy.weeklyHours.isEmpty()) return PickupWindow.HOURS_UNVERIFIED
    val date = pickup.toLocalDate()
    if (date in policy.holidays || date in policy.temporaryClosures) return PickupWindow.SPECIAL_REQUIRED
    return if (policy.weeklyHours[pickup.dayOfWeek].orEmpty().any { it.contains(pickup.toLocalTime()) })
        PickupWindow.REGULAR else PickupWindow.SPECIAL_REQUIRED
}

enum class BulkDraftIssue {
    NO_SELLER, SELLER_NOT_BULK, NO_ITEMS, INVALID_QUANTITY, CROSS_SELLER_ITEM,
    ITEM_UNAVAILABLE, BELOW_MINIMUM, NO_PICKUP, PAST_PICKUP, HOURS_UNVERIFIED,
    SPECIAL_REQUIRED, SPECIAL_REASON_REQUIRED,
}

data class BulkDraftCheck(
    val issues: Set<BulkDraftIssue>,
    val totalUnits: Int,
    val minimum: Int,
    val provisionalPaise: Long,
    val pickupWindow: PickupWindow?,
) {
    val readyForLocalReview: Boolean get() = issues.isEmpty()
}

/** Client-side preview validation. A future server must revalidate every field before submission. */
fun checkBulkDraft(
    draft: BulkDraft,
    catalog: CustomerCatalogSource,
    policies: BulkPolicySource,
    now: LocalDateTime,
): BulkDraftCheck {
    val issues = mutableSetOf<BulkDraftIssue>()
    val seller = catalog.sellers.find { it.id == draft.sellerId }
    val policy = draft.sellerId?.let(policies::forSeller)
    if (seller == null) issues += BulkDraftIssue.NO_SELLER
    else if (!seller.capabilities.bulk || seller.availability == SellerAvailability.UNAVAILABLE) issues += BulkDraftIssue.SELLER_NOT_BULK
    if (draft.lines.isEmpty()) issues += BulkDraftIssue.NO_ITEMS
    // Foreign-seller lines must never help satisfy this seller's minimum.
    val total = draft.lines.sumOf { line ->
        if (catalog.items.any { it.id == line.itemId && it.sellerId == draft.sellerId }) line.quantity.coerceAtLeast(0) else 0
    }
    if (draft.lines.any { it.quantity <= 0 || it.quantity > 999 }) issues += BulkDraftIssue.INVALID_QUANTITY
    var estimate = 0L
    for (line in draft.lines) {
        val item = catalog.items.find { it.id == line.itemId }
        if (item == null || item.sellerId != draft.sellerId) { issues += BulkDraftIssue.CROSS_SELLER_ITEM; continue }
        if (seller == null || itemEligibility(seller, item) == ItemEligibility.UNAVAILABLE) issues += BulkDraftIssue.ITEM_UNAVAILABLE
        estimate += item.price.rupees.toLong() * line.quantity * 100L
    }
    val minimum = policy?.minimum ?: DEFAULT_BULK_MINIMUM
    if (total < minimum) issues += BulkDraftIssue.BELOW_MINIMUM
    val window = draft.pickup?.let { pickupWindow(policy, it, now) }
    when (window) {
        null -> issues += BulkDraftIssue.NO_PICKUP
        PickupWindow.PAST -> issues += BulkDraftIssue.PAST_PICKUP
        PickupWindow.HOURS_UNVERIFIED -> issues += BulkDraftIssue.HOURS_UNVERIFIED
        PickupWindow.SPECIAL_REQUIRED -> {
            if (!draft.specialPickup) issues += BulkDraftIssue.SPECIAL_REQUIRED
            if (draft.specialReason.isBlank()) issues += BulkDraftIssue.SPECIAL_REASON_REQUIRED
        }
        PickupWindow.REGULAR -> Unit
    }
    return BulkDraftCheck(issues, total, minimum, estimate, window)
}

enum class QuoteActor { CUSTOMER, SELLER }
enum class QuoteStage { SUBMITTED, COUNTEROFFER, AGREED_AWAITING_PAYMENT, PAID_CONFIRMED, EXPIRED, REJECTED, WITHDRAWN }

data class QuoteLineSnapshot(val itemId: String, val title: String, val quantity: Int, val unitPricePaise: Long)
data class QuoteRevision(
    val version: Int,
    val actor: QuoteActor,
    val lines: List<QuoteLineSnapshot>,
    val pickup: LocalDateTime,
    val specialPickup: Boolean,
    val totalPaise: Long,
    val note: String,
    val createdAt: LocalDateTime,
    val specialReason: String = "",
) {
    init { require(version > 0 && totalPaise >= 0 && lines.all { it.quantity > 0 && it.unitPricePaise >= 0 }) }
}

data class QuoteThread(
    val sellerId: String,
    val reference: String,
    val revisions: List<QuoteRevision>,
    val stage: QuoteStage,
    val sellerAcceptedVersion: Int? = null,
    val customerAcceptedVersion: Int? = null,
    val expiresAt: LocalDateTime? = null,
    /** Explicit seller decision for exceptional pickup, bound to this exact revision. */
    val specialAcceptedVersion: Int? = null,
)

data class QuoteDifference(
    val added: List<QuoteLineSnapshot>,
    val removed: List<QuoteLineSnapshot>,
    val changed: List<Pair<QuoteLineSnapshot, QuoteLineSnapshot>>,
    val pickupChanged: Boolean,
    val specialChanged: Boolean,
    val amountChanged: Boolean,
)

fun compareQuotes(previous: QuoteRevision, current: QuoteRevision): QuoteDifference {
    val oldLines = previous.lines.associateBy { it.itemId }
    val newLines = current.lines.associateBy { it.itemId }
    return QuoteDifference(
        added = current.lines.filter { it.itemId !in oldLines },
        removed = previous.lines.filter { it.itemId !in newLines },
        changed = current.lines.mapNotNull { new -> oldLines[new.itemId]?.takeIf { it != new }?.let { it to new } },
        pickupChanged = previous.pickup != current.pickup,
        specialChanged = previous.specialPickup != current.specialPickup,
        amountChanged = previous.totalPaise != current.totalPaise,
    )
}

/** Only an authoritative, unexpired agreement on the exact latest revision can become payable. */
fun paymentEligibility(thread: QuoteThread, now: LocalDateTime, serverConfirmed: Boolean): Boolean {
    val latest = thread.revisions.maxByOrNull { it.version } ?: return false
    return serverConfirmed && thread.stage == QuoteStage.AGREED_AWAITING_PAYMENT &&
        thread.sellerAcceptedVersion == latest.version && thread.customerAcceptedVersion == latest.version &&
        (!latest.specialPickup || thread.specialAcceptedVersion == latest.version) &&
        thread.expiresAt?.isAfter(now) == true
}
