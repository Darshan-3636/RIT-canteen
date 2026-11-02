package edu.msrit.canteen.customer

/** Release has no debug backend transport. */
object DebugPromotionsApi {
    data class Result<T>(val value: T? = null, val error: String? = "Unavailable in release")
    data class OfferQuote(val subtotalPaise: Long, val discountPaise: Long, val totalPaise: Long)
    fun customerOffers(sellerId: String? = null): Result<List<Promotion>> = Result()
    fun sellerPromotions(sellerId: String): Result<List<Promotion>> = Result()
    fun save(value: Promotion): Result<Promotion> = Result()
    fun quote(promotionId: String): Result<OfferQuote> = Result()
}
