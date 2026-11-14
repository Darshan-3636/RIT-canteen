package edu.msrit.canteen.customer

/** Where a paid individual order sits in the seller's Orders page. */
enum class SellerOrderTab(val label: String) { NEW("New"), READY("Ready"), DONE("Completed") }

/** Every food item is prepaid, so unpaid or cancelled orders never reach the seller's queue. */
fun sellerOrderTab(status: String): SellerOrderTab? = when (status) {
    "DEMO_PAYMENT_SIMULATED", "DEMO_ACCEPTED" -> SellerOrderTab.NEW
    "DEMO_READY" -> SellerOrderTab.READY
    "DEMO_COMPLETED" -> SellerOrderTab.DONE
    else -> null
}

fun groupSellerOrders(orders: List<IndividualOrderPreview>): Map<SellerOrderTab, List<IndividualOrderPreview>> {
    val byTab = orders.groupBy { sellerOrderTab(it.status) }
    return SellerOrderTab.entries.associateWith { tab ->
        byTab[tab].orEmpty().sortedBy { it.placedAt }.let { if (tab == SellerOrderTab.DONE) it.reversed() else it }
    }
}

fun customerOrderStatus(status: String): String = when (status) {
    "DEMO_SAVED", "DEMO_PAYMENT_FAILED" -> "Waiting for payment"
    "DEMO_PAYMENT_PENDING" -> "Payment processing"
    "DEMO_PAYMENT_SIMULATED", "DEMO_ACCEPTED" -> "Preparing"
    "DEMO_READY" -> "Ready for pickup"
    "DEMO_COMPLETED" -> "Collected"
    "DEMO_REJECTED" -> "Cancelled"
    else -> status.removePrefix("DEMO_").replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
}

fun isPaidOrder(status: String) = sellerOrderTab(status) != null

const val PREPARATION_TIME_LABEL = "Ready in 10–12 min"

private const val PICKUP_QR_PREFIX = "RITPICKUP:"
private val PIN = Regex("[0-9]{4}")

fun pickupQrPayload(pin: String) = PICKUP_QR_PREFIX + pin

/** Accepts the app's pickup QR or a bare 4-digit PIN; anything else is not a pickup code. */
fun pickupPinFromScan(text: String?): String? =
    text?.trim()?.removePrefix(PICKUP_QR_PREFIX)?.takeIf { PIN.matches(it) }
