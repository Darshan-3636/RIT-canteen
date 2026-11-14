package edu.msrit.canteen.customer

/** No demonstration offers or history are available to release builds. */
object DevelopmentPromotions : PromotionSource {
    override val promotions: List<Promotion> = emptyList()
    fun replaceDemo(value: List<Promotion>) = Unit
}
object DevelopmentSampleHistory : OrderHistorySource { override val orders: List<PastOrderSnapshot> = emptyList() }
