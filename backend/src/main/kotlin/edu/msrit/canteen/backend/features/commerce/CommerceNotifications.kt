package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.features.engagement.query
import edu.msrit.canteen.backend.features.engagement.update
import java.sql.Connection
import java.util.UUID

/** Notices commit with the order event. A retry cannot create a second notice. */
internal fun notifyBulkEvent(db: Connection, requestId: UUID, version: Int, event: String) {
    val request = db.query(
        "SELECT customer_user_id, seller_id FROM bulk_requests WHERE id=?", requestId,
    ) { it.getObject(1, UUID::class.java) to it.getString(2) }.singleOrNull() ?: return
    val customer = request.first
    val seller = request.second
    val customerEvents = setOf("COUNTEROFFER", "SELLER_APPROVED", "SPECIAL_PICKUP_DECIDED",
        "REJECTED", "EXPIRED", "AGREED")
    val sellerEvents = setOf("SUBMITTED", "CUSTOMER_APPROVED", "WITHDRAWN", "AGREED", "EXPIRED")
    val title = when (event) {
        "SUBMITTED" -> "New bulk request"
        "COUNTEROFFER" -> "New bulk quote"
        "CUSTOMER_APPROVED", "SELLER_APPROVED" -> "Bulk quote approved"
        "SPECIAL_PICKUP_DECIDED" -> "Special pickup decision"
        "AGREED" -> "Bulk quote agreed"
        "REJECTED" -> "Bulk request declined"
        "WITHDRAWN" -> "Bulk request withdrawn"
        "EXPIRED" -> "Bulk quote expired"
        else -> return
    }
    val body = when (event) {
        "SUBMITTED" -> "A customer sent a bulk request. Open the request queue to respond."
        "COUNTEROFFER" -> "The seller sent an updated quote. Review it before the expiry time."
        "AGREED" -> "Both sides agreed to the quote. Payment is still pending."
        "REJECTED" -> "The bulk request was declined. Open the request for details."
        "WITHDRAWN" -> "The customer withdrew a bulk request."
        "EXPIRED" -> "The current bulk quote expired."
        else -> "A bulk request changed. Open it to see the latest status."
    }
    val recipients = buildSet {
        if (event in customerEvents) add(customer)
        if (event in sellerEvents) addAll(db.query(
            """SELECT DISTINCT m.user_id FROM seller_memberships m JOIN users u ON u.id=m.user_id
               WHERE m.seller_id=? AND m.role='OWNER' AND m.active AND u.status='ACTIVE'""", seller,
        ) { it.getObject(1, UUID::class.java) })
    }
    val key = "bulk:$requestId:$version:$event"
    recipients.forEach { user -> db.update(
        """INSERT INTO notification_inbox(id,user_id,event_type,title,body,event_key)
           VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING""",
        UUID.randomUUID(), user, "BULK_REQUEST_UPDATE", title, body, key,
    ) }
}

internal fun notifyIndividualEvent(db: Connection, orderId: UUID, status: String) {
    val request = db.query(
        "SELECT customer_user_id,seller_id FROM individual_demo_orders WHERE id=?", orderId,
    ) { it.getObject(1, UUID::class.java) to it.getString(2) }.singleOrNull() ?: return
    val customer = request.first
    val seller = request.second
    val title = when (status) {
        "DEMO_PAYMENT_SIMULATED" -> "New paid order"
        "DEMO_ACCEPTED" -> "Order accepted"
        "DEMO_REJECTED" -> "Order declined"
        "DEMO_READY" -> "Order ready for pickup"
        "DEMO_COMPLETED" -> "Order collected"
        else -> return
    }
    val body = when (status) {
        "DEMO_PAYMENT_SIMULATED" -> "A customer paid for an order. Mark it ready when it's prepared."
        "DEMO_READY" -> "Your order is ready. Show your pickup QR code or PIN at the counter."
        "DEMO_COMPLETED" -> "You collected your order. Enjoy your meal!"
        else -> "The individual order status changed. Open it for details."
    }
    // Orders are prepaid, so the canteen hears about an order once it is paid.
    val recipients = if (status == "DEMO_PAYMENT_SIMULATED") db.query(
        """SELECT DISTINCT m.user_id FROM seller_memberships m JOIN users u ON u.id=m.user_id
           WHERE m.seller_id=? AND m.role='OWNER' AND m.active AND u.status='ACTIVE'""", seller,
    ) { it.getObject(1, UUID::class.java) } else listOf(customer)
    val key = "individual:$orderId:$status"
    recipients.forEach { user -> db.update(
        """INSERT INTO notification_inbox(id,user_id,event_type,title,body,event_key)
           VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING""",
        UUID.randomUUID(), user, "INDIVIDUAL_ORDER_UPDATE", title, body, key,
    ) }
}
