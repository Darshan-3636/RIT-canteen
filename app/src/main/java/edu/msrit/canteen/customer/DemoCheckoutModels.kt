package edu.msrit.canteen.customer

import edu.msrit.canteen.navigation.AuthorizedIdentity
import java.time.LocalDateTime

/** Client-only prototype contract. Neither a receipt nor evidence of a server payment. */
enum class DemoOrderKind { INDIVIDUAL, BULK }
enum class DemoOrderState {
    SUBMITTED, SELLER_ACCEPTED, SELLER_REJECTED, PAYMENT_FAILED, PAYMENT_PENDING,
    DEMO_CONFIRMED, DEMO_COLLECTED
}
enum class DemoPaymentOutcome { SUCCESS, FAILURE, PENDING }
enum class DemoActionResult { APPLIED, NOT_FOUND, UNAUTHORIZED, INVALID_STATE, STALE_QUOTE, ALREADY_USED }

data class DemoOrder(
    val reference: String,
    val kind: DemoOrderKind,
    val customerId: String,
    val sellerId: String,
    val lines: List<QuoteLineSnapshot>,
    val totalPaise: Long,
    val pickup: LocalDateTime?,
    val quoteVersion: Int?,
    val state: DemoOrderState,
    val pickupCode: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(RIT_TIME_ZONE),
    val updatedAt: LocalDateTime = createdAt,
    val redeemedByUserId: String? = null,
    val redeemedAt: LocalDateTime? = null,
)

interface DemoCheckoutRepository {
    val orders: List<DemoOrder>
    fun submitIndividual(draft: IndividualDraft, actor: AuthorizedIdentity?): Pair<DemoActionResult, String?>
    fun respondIndividual(reference: String, accept: Boolean, actor: AuthorizedIdentity?): DemoActionResult
    fun previewBulk(reference: String, actor: AuthorizedIdentity?): DemoOrder?
    fun simulatePayment(reference: String, outcome: DemoPaymentOutcome, actor: AuthorizedIdentity?): DemoActionResult
    fun verifyCode(code: String, actor: AuthorizedIdentity?): Pair<DemoActionResult, DemoOrder?>
    fun collect(code: String, actor: AuthorizedIdentity?): DemoActionResult
    fun reset()
}
