package edu.msrit.canteen.customer

import java.time.LocalDateTime

/** No demo schedule or seller-response fixtures in a release artifact. */
object DevelopmentBulkPreview : BulkPolicySource {
    override fun forSeller(sellerId: String): SellerBulkPolicy? = null
    fun sample(stage: QuoteStage, now: LocalDateTime = LocalDateTime.now(RIT_TIME_ZONE)): QuoteThread? = null
}
