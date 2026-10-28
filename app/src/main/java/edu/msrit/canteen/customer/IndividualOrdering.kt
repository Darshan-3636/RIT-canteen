package edu.msrit.canteen.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDateTime
import java.util.UUID

enum class CartMode { INDIVIDUAL, BULK }

data class IndividualLine(val itemId: String, val quantity: Int)
data class IndividualDraft(val lines: List<IndividualLine> = emptyList()) {
    val totalUnits: Int get() = lines.sumOf { it.quantity }
}

data class IndividualServerCartLine(val itemId: String, val quantity: Int, val unitPricePaise: Long,
                                    val available: Boolean)
data class IndividualServerCart(val version: Long, val subtotalPaise: Long,
                                val readyForCheckout: Boolean, val lines: List<IndividualServerCartLine>)

/** Selection only. This cannot submit, charge, reserve, or notify a kitchen. */
class IndividualCartSession(private val catalog: CustomerCatalogSource = PreviewCatalog) {
    var draft by mutableStateOf(IndividualDraft())
        private set
    var serverCart by mutableStateOf<IndividualServerCart?>(null)
        private set
    private var checkoutVersion: Long? = null
    private var checkoutKey: String = UUID.randomUUID().toString()

    fun applyServer(cart: IndividualServerCart) {
        draft = IndividualDraft(cart.lines.map { IndividualLine(it.itemId, it.quantity) })
        serverCart = cart
        if (checkoutVersion != cart.version) {
            checkoutVersion = cart.version
            checkoutKey = UUID.randomUUID().toString()
        }
    }

    fun checkoutIdempotencyKey(): String = checkoutKey

    fun setQuantity(item: MenuItem, quantity: Int): Boolean {
        val seller = catalog.sellers.find { it.id == item.sellerId } ?: return false
        if (quantity < 0 || itemEligibility(seller, item) != ItemEligibility.FUTURE_INDIVIDUAL_AND_BULK) return false
        val other = draft.lines.filterNot { it.itemId == item.id }
        draft = IndividualDraft(if (quantity == 0) other else other + IndividualLine(item.id, quantity))
        return true
    }

    fun add(item: MenuItem): Boolean {
        val current = draft.lines.find { it.itemId == item.id }?.quantity ?: 0
        if (current == Int.MAX_VALUE) return false // Numeric representation limit, not a product quantity rule.
        return setQuantity(item, current + 1)
    }

    fun clear() {
        draft = IndividualDraft()
        serverCart = null
        checkoutVersion = null
        checkoutKey = UUID.randomUUID().toString()
    }
}

data class IndividualCartCheck(val lines: List<Pair<MenuItem, Int>>, val subtotalPaise: Long,
                               val unavailableItemIds: List<String>) {
    val units: Int get() = lines.sumOf { it.second }
    val readyForReview: Boolean get() = lines.isNotEmpty() && unavailableItemIds.isEmpty()
}

fun checkIndividualCart(draft: IndividualDraft, catalog: CustomerCatalogSource): IndividualCartCheck {
    val valid = mutableListOf<Pair<MenuItem, Int>>()
    val unavailable = mutableListOf<String>()
    draft.lines.forEach { line ->
        val item = catalog.items.find { it.id == line.itemId }
        val seller = item?.let { found -> catalog.sellers.find { it.id == found.sellerId } }
        if (item == null || seller == null || line.quantity <= 0 ||
            itemEligibility(seller, item) != ItemEligibility.FUTURE_INDIVIDUAL_AND_BULK) unavailable += line.itemId
        else valid += item to line.quantity
    }
    return IndividualCartCheck(valid, valid.sumOf { (item, quantity) -> item.price.rupees.toLong() * quantity * 100L }, unavailable)
}

data class IndividualOrderPreview(val id: String, val placedAt: LocalDateTime,
                                  val lines: List<QuoteLineSnapshot>, val totalPaise: Long,
                                  val locallySaved: Boolean = false, val status: String = "SAMPLE",
                                  val demoPaymentOutcome: String = "NOT_ATTEMPTED")
