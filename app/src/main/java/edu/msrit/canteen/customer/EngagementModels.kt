package edu.msrit.canteen.customer

import java.time.Instant

enum class PromotionKind { FEATURED, TODAYS_SPECIAL, NEW_ARRIVAL, BANNER, PERCENT_DISCOUNT, FIXED_DISCOUNT }
enum class PromotionAudience { GENERAL, BEHAVIORAL }
enum class PromotionStatus { UPCOMING, ACTIVE, EXPIRED }

data class Promotion(
    val id: String,
    val sellerId: String,
    val title: String,
    val description: String,
    val kind: PromotionKind,
    val startsAt: Instant,
    val endsAt: Instant,
    val published: Boolean,
    val itemIds: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val audience: PromotionAudience = PromotionAudience.GENERAL,
    val discountValue: Int? = null,
) {
    init {
        require(endsAt.isAfter(startsAt))
        require(when (kind) {
            PromotionKind.PERCENT_DISCOUNT -> discountValue != null && discountValue in 1..100
            PromotionKind.FIXED_DISCOUNT -> discountValue != null && discountValue > 0
            else -> discountValue == null
        })
    }
}

data class PromotionPrivacy(val behavioralConsentGranted: Boolean = false, val optedOut: Boolean = true)

interface PromotionSource { val promotions: List<Promotion> }

fun Promotion.statusAt(now: Instant): PromotionStatus = when {
    now.isBefore(startsAt) -> PromotionStatus.UPCOMING
    !now.isBefore(endsAt) -> PromotionStatus.EXPIRED
    else -> PromotionStatus.ACTIVE
}

fun PromotionSource.presentable(privacy: PromotionPrivacy = PromotionPrivacy(), now: Instant = Instant.now()): List<Promotion> =
    promotions.filter { promotion ->
        promotion.published && (promotion.audience == PromotionAudience.GENERAL ||
            (privacy.behavioralConsentGranted && !privacy.optedOut))
    }.sortedWith(compareBy<Promotion> { it.statusAt(now) != PromotionStatus.ACTIVE }.thenBy { it.endsAt })

/** Historical snapshots are reference data, never proof of current item/price/eligibility. */
data class PastOrderLine(val itemId: String, val sellerId: String, val titleSnapshot: String, val priceSnapshot: Price)
data class PastOrderSnapshot(val id: String, val completedAt: Instant, val lines: List<PastOrderLine>)

interface OrderHistorySource { val orders: List<PastOrderSnapshot> }
object EmptyOrderHistory : OrderHistorySource { override val orders: List<PastOrderSnapshot> = emptyList() }

enum class CurrentItemState { AVAILABLE_FOR_REVIEW, UNAVAILABLE, REMOVED }

fun currentItemState(catalog: CustomerCatalogSource, line: PastOrderLine): CurrentItemState {
    val item = catalog.items.find { it.id == line.itemId && it.sellerId == line.sellerId } ?: return CurrentItemState.REMOVED
    val seller = catalog.sellers.find { it.id == line.sellerId } ?: return CurrentItemState.REMOVED
    if (item !in catalog.visibleItems() || itemEligibility(seller, item) == ItemEligibility.UNAVAILABLE) return CurrentItemState.UNAVAILABLE
    return CurrentItemState.AVAILABLE_FOR_REVIEW
}
