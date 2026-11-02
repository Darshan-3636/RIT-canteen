package edu.msrit.canteen.customer

import edu.msrit.canteen.navigation.AuthorizedIdentity

/** Production builds contain no operational mock checkout state or privilege path. */
object DevelopmentDemoCheckout : DemoCheckoutRepository {
    override val orders: List<DemoOrder> = emptyList()
    override fun submitIndividual(draft: IndividualDraft, actor: AuthorizedIdentity?) =
        DemoActionResult.UNAUTHORIZED to null
    override fun respondIndividual(reference: String, accept: Boolean, actor: AuthorizedIdentity?) =
        DemoActionResult.UNAUTHORIZED
    override fun previewBulk(reference: String, actor: AuthorizedIdentity?): DemoOrder? = null
    override fun simulatePayment(reference: String, outcome: DemoPaymentOutcome, actor: AuthorizedIdentity?) =
        DemoActionResult.UNAUTHORIZED
    override fun verifyCode(code: String, actor: AuthorizedIdentity?) = DemoActionResult.UNAUTHORIZED to null
    override fun collect(code: String, actor: AuthorizedIdentity?) = DemoActionResult.UNAUTHORIZED
    override fun reset() = Unit
}
