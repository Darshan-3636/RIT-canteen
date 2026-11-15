package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.features.engagement.query
import edu.msrit.canteen.backend.features.engagement.queryOne
import java.sql.Connection
import java.util.UUID

/** Reprices an offer from locked order lines; client amounts are never trusted. */
internal fun validatedPromotionDiscount(db: Connection, context: BackendContext, customer: UUID,
                                         seller: String, lines: List<IndividualDemoLineDto>, promotionId: UUID): Long {
    val row = db.queryOne(
        """SELECT p.kind,p.discount_percent,p.discount_paise,p.audience FROM promotions p
           JOIN sellers s ON s.id=p.seller_id WHERE p.id=? AND p.seller_id=? AND s.active
           AND p.published AND p.starts_at<=? AND p.ends_at>? FOR SHARE OF p,s""",
        promotionId,seller,context.clock.instant(),context.clock.instant(),
    ) { listOf(it.getString(1),it.getString(2),it.getString(3),it.getString(4)) }
        ?: throw CommerceFault(422,"OFFER_UNAVAILABLE","This offer is no longer available")
    if (row[3] == "BEHAVIORAL" && db.queryOne(
            "SELECT personalization_consent FROM customer_preferences WHERE user_id=?",customer,
        ) { it.getBoolean(1) } != true)
        throw CommerceFault(422,"OFFER_UNAVAILABLE","This offer requires personalization consent")
    val items = db.query("SELECT item_id FROM promotion_items WHERE promotion_id=?",promotionId) { it.getString(1) }.toSet()
    val categories = db.query("SELECT category_id FROM promotion_categories WHERE promotion_id=?",promotionId) { it.getString(1) }.toSet()
    val eligible = lines.filter { line ->
        if (items.isEmpty() && categories.isEmpty()) true
        else line.itemId in items || db.queryOne(
            "SELECT category_id FROM menu_items WHERE seller_id=? AND id=?",seller,line.itemId,
        ) { it.getString(1) } in categories
    }
    if (eligible.isEmpty()) throw CommerceFault(422,"OFFER_NOT_APPLICABLE","This offer does not apply to the selected items")
    val amount = try { eligible.fold(0L) { total,line -> Math.addExact(total,
        Math.multiplyExact(line.unitPricePaise,line.quantity.toLong())) } }
    catch (_: ArithmeticException) { throw CommerceFault(422,"TOTAL_TOO_LARGE","The order total exceeds the supported amount") }
    return when (row[0]) {
        "PERCENT_DISCOUNT" -> {
            val percent = row[1]!!.toLong()
            amount / 100L * percent + (amount % 100L) * percent / 100L
        }
        "FIXED_DISCOUNT" -> minOf(amount,row[2]!!.toLong())
        else -> 0L
    }
}
