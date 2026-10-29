package edu.msrit.canteen.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.msrit.canteen.BuildConfig
import edu.msrit.canteen.navigation.AuthorizedIdentity
import edu.msrit.canteen.navigation.PresentationAccess
import edu.msrit.canteen.navigation.ProtectedArea
import java.security.SecureRandom
import java.time.LocalDateTime

/** In-process demonstration only. Never a payment, seller notification or trusted pickup credential. */
object DevelopmentDemoCheckout : DemoCheckoutRepository {
    private val random = SecureRandom()
    private const val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private var sequence = 1
    override var orders by mutableStateOf<List<DemoOrder>>(emptyList())
        private set

    private fun customer(actor: AuthorizedIdentity?, userId: String): Boolean =
        PresentationAccess.allows(actor, ProtectedArea.Customer) && actor?.userId == userId

    private fun seller(actor: AuthorizedIdentity?, sellerId: String): Boolean =
        PresentationAccess.allows(actor, ProtectedArea.Seller(sellerId, "bulk.respond"))

    override fun submitIndividual(draft: IndividualDraft, actor: AuthorizedIdentity?): Pair<DemoActionResult, String?> {
        if (BuildConfig.RIT_CLOUD_DEMO) return DemoActionResult.INVALID_STATE to null
        if (!PresentationAccess.allows(actor, ProtectedArea.Customer)) return DemoActionResult.UNAUTHORIZED to null
        val check = checkIndividualCart(draft, PreviewCatalog)
        if (!check.readyForReview || check.lines.any { it.first.sellerId != PreviewCatalog.MAIN_ID })
            return DemoActionResult.INVALID_STATE to null
        val reference = "DEMO-IND-${sequence++.toString().padStart(4, '0')}"
        val lines = check.lines.map { (item, quantity) ->
            QuoteLineSnapshot(item.id, item.name, quantity, item.price.rupees * 100L)
        }
        orders = orders + DemoOrder(reference, DemoOrderKind.INDIVIDUAL, actor!!.userId,
            PreviewCatalog.MAIN_ID, lines, check.subtotalPaise, null, null, DemoOrderState.SUBMITTED)
        return DemoActionResult.APPLIED to reference
    }

    override fun respondIndividual(reference: String, accept: Boolean, actor: AuthorizedIdentity?): DemoActionResult {
        if (BuildConfig.RIT_CLOUD_DEMO) return DemoActionResult.INVALID_STATE
        val order = orders.find { it.reference == reference && it.kind == DemoOrderKind.INDIVIDUAL }
            ?: return DemoActionResult.NOT_FOUND
        if (!seller(actor, order.sellerId)) return DemoActionResult.UNAUTHORIZED
        if (order.state != DemoOrderState.SUBMITTED) return DemoActionResult.INVALID_STATE
        replace(order.copy(state = if (accept) DemoOrderState.SELLER_ACCEPTED else DemoOrderState.SELLER_REJECTED))
        return DemoActionResult.APPLIED
    }

    override fun previewBulk(reference: String, actor: AuthorizedIdentity?): DemoOrder? {
        if (BuildConfig.RIT_CLOUD_DEMO) return null
        val record = DevelopmentSellerOperations.records.find { it.reference == reference } ?: return null
        if (!customer(actor, record.customerUserId) || !eligibleBulk(record)) return null
        return orders.find { it.reference == reference } ?: DemoOrder(reference, DemoOrderKind.BULK,
            record.customerUserId, record.sellerId, record.latest.lines, record.latest.totalPaise,
            record.latest.pickup, record.latest.version, DemoOrderState.SELLER_ACCEPTED)
    }

    private fun eligibleBulk(record: BulkRequestRecord): Boolean =
        record.thread.stage == QuoteStage.AGREED_AWAITING_PAYMENT &&
            record.thread.sellerAcceptedVersion == record.latest.version &&
            record.thread.customerAcceptedVersion == record.latest.version &&
            (!record.latest.specialPickup || record.thread.specialAcceptedVersion == record.latest.version) &&
            record.thread.expiresAt?.isAfter(LocalDateTime.now(RIT_TIME_ZONE)) != false

    override fun simulatePayment(reference: String, outcome: DemoPaymentOutcome,
                                 actor: AuthorizedIdentity?): DemoActionResult {
        if (BuildConfig.RIT_CLOUD_DEMO) return DemoActionResult.INVALID_STATE
        val existing = orders.find { it.reference == reference }
        val order = existing ?: previewBulk(reference, actor) ?: return DemoActionResult.NOT_FOUND
        if (!customer(actor, order.customerId)) return DemoActionResult.UNAUTHORIZED
        if (order.kind == DemoOrderKind.BULK) {
            val record = DevelopmentSellerOperations.records.find { it.reference == reference }
                ?: return DemoActionResult.NOT_FOUND
            if (!eligibleBulk(record) || order.quoteVersion != record.latest.version)
                return DemoActionResult.STALE_QUOTE
        }
        if (order.state in setOf(DemoOrderState.DEMO_CONFIRMED, DemoOrderState.DEMO_COLLECTED))
            return DemoActionResult.ALREADY_USED
        if (order.state !in setOf(DemoOrderState.SELLER_ACCEPTED, DemoOrderState.PAYMENT_FAILED,
                DemoOrderState.PAYMENT_PENDING)) return DemoActionResult.INVALID_STATE
        val state = when (outcome) {
            DemoPaymentOutcome.SUCCESS -> DemoOrderState.DEMO_CONFIRMED
            DemoPaymentOutcome.FAILURE -> DemoOrderState.PAYMENT_FAILED
            DemoPaymentOutcome.PENDING -> DemoOrderState.PAYMENT_PENDING
        }
        replace(order.copy(state = state,
            pickupCode = if (state == DemoOrderState.DEMO_CONFIRMED) uniqueCode() else null))
        return DemoActionResult.APPLIED
    }

    private fun uniqueCode(): String {
        while (true) {
            val code = buildString {
                repeat(4) { group ->
                    if (group > 0) append('-')
                    repeat(4) { append(alphabet[random.nextInt(alphabet.length)]) }
                }
            }
            if (orders.none { it.pickupCode == code }) return code
        }
    }

    override fun verifyCode(code: String, actor: AuthorizedIdentity?): Pair<DemoActionResult, DemoOrder?> {
        if (BuildConfig.RIT_CLOUD_DEMO) return DemoActionResult.INVALID_STATE to null
        val compact = code.uppercase().filterNot { it == '-' || it.isWhitespace() }
        if (compact.length != 16) return DemoActionResult.NOT_FOUND to null
        val normalized = compact.chunked(4).joinToString("-")
        val order = orders.find { it.pickupCode == normalized } ?: return DemoActionResult.NOT_FOUND to null
        if (!seller(actor, order.sellerId)) return DemoActionResult.UNAUTHORIZED to null
        if (order.kind == DemoOrderKind.BULK) {
            val record = DevelopmentSellerOperations.records.find { it.reference == order.reference }
            if (record == null || !eligibleBulk(record) || record.latest.version != order.quoteVersion)
                return DemoActionResult.STALE_QUOTE to null
        }
        return when (order.state) {
            DemoOrderState.DEMO_CONFIRMED -> DemoActionResult.APPLIED to order
            DemoOrderState.DEMO_COLLECTED -> DemoActionResult.ALREADY_USED to null
            else -> DemoActionResult.INVALID_STATE to null
        }
    }

    override fun collect(code: String, actor: AuthorizedIdentity?): DemoActionResult {
        if (BuildConfig.RIT_CLOUD_DEMO) return DemoActionResult.INVALID_STATE
        val (result, order) = verifyCode(code, actor)
        if (result != DemoActionResult.APPLIED || order == null) return result
        replace(order.copy(state = DemoOrderState.DEMO_COLLECTED,
            redeemedByUserId = actor?.userId, redeemedAt = LocalDateTime.now(RIT_TIME_ZONE)))
        return DemoActionResult.APPLIED
    }

    private fun replace(order: DemoOrder) {
        val updated = order.copy(updatedAt = LocalDateTime.now(RIT_TIME_ZONE))
        orders = if (orders.any { it.reference == order.reference })
            orders.map { if (it.reference == order.reference) updated else it }
        else orders + updated
    }

    override fun reset() { orders = emptyList(); sequence = 1 }
}
