package edu.msrit.canteen.customer

import java.time.LocalDateTime

/** Where a paid order sits in the seller's Orders page. */
enum class SellerOrderTab(val label: String) { NEW("New"), READY("Ready"), DONE("Completed") }

/** Every food item is prepaid, so unpaid or cancelled orders never reach the seller's queue. */
fun sellerOrderTab(status: String): SellerOrderTab? = when (status) {
    "DEMO_PAYMENT_SIMULATED", "DEMO_ACCEPTED" -> SellerOrderTab.NEW
    "DEMO_READY" -> SellerOrderTab.READY
    "DEMO_COMPLETED" -> SellerOrderTab.DONE
    else -> null
}

enum class PickupKind { INDIVIDUAL, BULK }

/** A paid order on the seller's Orders page, individual or bulk; both go New → Ready → collected. */
data class SellerPickupOrder(
    val id: String, val kind: PickupKind, val tab: SellerOrderTab, val customerName: String?,
    val totalPaise: Long, val reference: String, val placedAt: LocalDateTime,
    /** When the customer expects it: a bulk order's agreed pickup time, or about 11 minutes after an individual order. */
    val dueAt: LocalDateTime, val scheduledPickup: Boolean, val lines: List<QuoteLineSnapshot>, val handedOverAt: String?,
)

private fun IndividualOrderPreview.toPickupOrder(): SellerPickupOrder? {
    val tab = sellerOrderTab(status) ?: return null
    return SellerPickupOrder(id, PickupKind.INDIVIDUAL, tab, customerName, totalPaise, pickupReference, placedAt,
        placedAt.plusMinutes(11), scheduledPickup = false, lines = lines, handedOverAt = handedOverAt)
}

private fun BulkRequestRecord.toPickupOrder(): SellerPickupOrder? {
    val tab = when (thread.stage) {
        QuoteStage.PAID -> SellerOrderTab.NEW
        QuoteStage.READY -> SellerOrderTab.READY
        QuoteStage.COMPLETED -> SellerOrderTab.DONE
        else -> return null
    }
    return SellerPickupOrder(reference, PickupKind.BULK, tab, customerLabel, latest.totalPaise, "Bulk", createdAt,
        latest.pickup, scheduledPickup = true, lines = latest.lines, handedOverAt = handedOverAt)
}

/** Paid individual and bulk orders by tab: open tabs soonest-due first, completed newest first. */
fun groupSellerPickups(orders: List<IndividualOrderPreview>, bulk: List<BulkRequestRecord>):
    Map<SellerOrderTab, List<SellerPickupOrder>> {
    val byTab = (orders.mapNotNull { it.toPickupOrder() } + bulk.mapNotNull { it.toPickupOrder() }).groupBy { it.tab }
    return SellerOrderTab.entries.associateWith { tab ->
        byTab[tab].orEmpty().sortedBy { it.dueAt }.let { if (tab == SellerOrderTab.DONE) it.reversed() else it }
    }
}

/** What a bulk order's customer sees as its status: always what happens next, in plain words. */
fun customerBulkStatus(record: BulkRequestRecord): String = when (record.thread.stage) {
    QuoteStage.SUBMITTED -> "Waiting for the canteen"
    QuoteStage.COUNTEROFFER -> if (record.thread.sellerAcceptedVersion == record.latest.version)
        "Canteen changed your order" else "Waiting for the canteen"
    QuoteStage.AGREED_AWAITING_PAYMENT -> "Accepted · pay to confirm"
    QuoteStage.PAID -> "Paid · being prepared"
    QuoteStage.READY -> "Ready for pickup"
    QuoteStage.COMPLETED -> "Collected"
    QuoteStage.EXPIRED -> "Expired"
    QuoteStage.REJECTED -> "Declined by the canteen"
    QuoteStage.WITHDRAWN -> "Cancelled"
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
