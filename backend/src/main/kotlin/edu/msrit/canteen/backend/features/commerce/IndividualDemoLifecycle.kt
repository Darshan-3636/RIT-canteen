package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.transaction
import java.sql.Connection
import java.sql.Timestamp
import java.security.MessageDigest
import java.util.UUID
import edu.msrit.canteen.backend.features.engagement.recordAudit

internal fun loadDemoIndividual(db: Connection, id: UUID, includePickupCode: Boolean = false): IndividualDemoOrderDto? =
    db.prepareStatement("""SELECT seller_id,status,demo_only,payment_verified,subtotal_paise,created_at,demo_payment_outcome,
        discount_paise,promotion_id,pickup_reference,pickup_code,handed_over_at
        FROM individual_demo_orders WHERE id=?""").use { query ->
        query.setObject(1, id)
        query.executeQuery().use { row ->
            if (!row.next()) return@use null
            val lines = db.prepareStatement("""SELECT item_id,item_name_snapshot,quantity,unit_price_paise
                FROM individual_demo_order_lines WHERE order_id=? ORDER BY item_id""").use { items ->
                items.setObject(1, id)
                items.executeQuery().use { entries -> buildList {
                    while (entries.next()) add(IndividualDemoLineDto(entries.getString(1), entries.getString(2),
                        entries.getInt(3), entries.getLong(4)))
                } }
            }
            val sandboxStatus = db.prepareStatement("""SELECT state FROM sandbox_payment_attempts
                WHERE source_type='INDIVIDUAL' AND source_id=? ORDER BY created_at DESC LIMIT 1""").use { payment ->
                payment.setObject(1, id)
                payment.executeQuery().use { result -> if (result.next()) result.getString(1) else null }
            }
            IndividualDemoOrderDto(id.toString(), row.getString(1), row.getString(2), row.getBoolean(3),
                row.getBoolean(4), row.getLong(5), row.getTimestamp(6).toInstant().toString(), lines,
                row.getString(7), sandboxStatus, row.getLong(8), row.getLong(5)-row.getLong(8),
                row.getObject(9,UUID::class.java)?.toString(),row.getString(10),
                if(includePickupCode) row.getString(11) else null,
                row.getTimestamp(12)?.toInstant()?.toString())
        }
    }

internal fun sellerIndividual(context: BackendContext, sellerId: String): IndividualDemoOrderListDto =
    context.dataSource.connection.use { db ->
        val ids = db.prepareStatement("""SELECT id FROM individual_demo_orders WHERE seller_id=?
            ORDER BY created_at DESC""").use { query ->
            query.setString(1, sellerId)
            query.executeQuery().use { rows -> buildList {
                while (rows.next()) add(rows.getObject(1, UUID::class.java))
            } }
        }
        IndividualDemoOrderListDto(ids.mapNotNull { loadDemoIndividual(db, it) })
    }

/** Explicitly fictional payment; payment_verified remains false and no provider is contacted. */
internal fun simulateIndividualPayment(context: BackendContext, principal: AuthenticatedPrincipal,
                                       id: UUID, outcome: String): IndividualDemoOrderDto = context.transaction { db ->
    if (context.config.environment != RuntimeEnvironment.CLOUD_DEMO)
        throw CommerceFault(404, "DEMO_ONLY", "Demo payment simulation is unavailable")
    if (outcome !in setOf("SUCCESS", "FAILURE", "PENDING"))
        throw CommerceFault(400, "INVALID_DEMO_OUTCOME", "Choose SUCCESS, FAILURE or PENDING")
    val state = db.prepareStatement("""SELECT o.status,o.demo_payment_outcome FROM individual_demo_orders o
        JOIN users u ON u.id=o.customer_user_id
        WHERE o.id=? AND o.customer_user_id=? AND u.affiliation IN ('STUDENT','FACULTY') FOR UPDATE OF o""").use { query ->
        query.setObject(1, id); query.setObject(2, principal.userId)
        query.executeQuery().use { rows -> if (rows.next()) rows.getString(1) to rows.getString(2) else null }
    } ?: throw CommerceFault(404, "ORDER_NOT_FOUND", "Demo order not found")
    val (status, recordedOutcome) = state
    val sandboxAttempt = db.prepareStatement("""SELECT 1 FROM sandbox_payment_attempts
        WHERE source_type='INDIVIDUAL' AND source_id=?
        AND state IN ('INITIATED','PENDING','CANCELLED','SANDBOX_SUCCESS') LIMIT 1""").use { query ->
        query.setObject(1, id)
        query.executeQuery().use { it.next() }
    }
    if (sandboxAttempt) throw CommerceFault(409, "SANDBOX_PAYMENT_EXISTS", "Reconcile the PhonePe UAT attempt instead")
    // A lost response or rapid repeat must return the same persisted result, not a false failure.
    if (recordedOutcome == outcome && status == when (outcome) {
            "SUCCESS" -> "DEMO_PAYMENT_SIMULATED"
            "FAILURE" -> "DEMO_PAYMENT_FAILED"
            else -> "DEMO_PAYMENT_PENDING"
        }) return@transaction loadDemoIndividual(db, id, true)!!
    if (outcome == "SUCCESS" && recordedOutcome == "SUCCESS" &&
        status in setOf("DEMO_ACCEPTED", "DEMO_REJECTED", "DEMO_READY", "DEMO_COMPLETED"))
        return@transaction loadDemoIndividual(db, id, true)!!
    if (status !in setOf("DEMO_SAVED", "DEMO_PAYMENT_PENDING", "DEMO_PAYMENT_FAILED"))
        throw CommerceFault(409, "INVALID_DEMO_TRANSITION", "Demo payment cannot be changed in this state")
    val next = when (outcome) {
        "SUCCESS" -> "DEMO_PAYMENT_SIMULATED"
        "FAILURE" -> "DEMO_PAYMENT_FAILED"
        else -> "DEMO_PAYMENT_PENDING"
    }
    db.prepareStatement("""UPDATE individual_demo_orders SET status=?,demo_payment_outcome=?,updated_at=?
        WHERE id=?""").use { query ->
        query.setString(1, next); query.setString(2, outcome)
        query.setTimestamp(3, Timestamp.from(context.clock.instant())); query.setObject(4, id)
        query.executeUpdate()
    }
    notifyIndividualEvent(db, id, next)
    loadDemoIndividual(db, id, true)!!
}

/** Demo seller processing has no real fulfillment or financial consequence. */
internal fun transitionIndividualDemo(context: BackendContext, principal: AuthenticatedPrincipal,
                                      sellerId: String, id: UUID, next: String,
                                      pickupCode: String?): IndividualDemoOrderDto =
    context.transaction { db ->
        if (context.config.environment !in setOf(RuntimeEnvironment.LOCAL, RuntimeEnvironment.CLOUD_DEMO))
            throw CommerceFault(404, "DEMO_ONLY", "Demo fulfillment simulation is unavailable")
        if (!context.authorization.hasSellerCapability(db, principal, sellerId, "bulk.respond"))
            throw CommerceFault(403, "SELLER_CAPABILITY_REQUIRED", "This seller action is not permitted")
        val state = db.prepareStatement("""SELECT status,pickup_code FROM individual_demo_orders
            WHERE id=? AND seller_id=? FOR UPDATE""").use { query ->
            query.setObject(1, id); query.setString(2, sellerId)
            query.executeQuery().use { rows -> if (rows.next()) rows.getString(1) to rows.getString(2) else null }
        } ?: throw CommerceFault(404, "ORDER_NOT_FOUND", "Demo order not found")
        val current=state.first
        if(next !in setOf("DEMO_ACCEPTED","DEMO_REJECTED","DEMO_READY","DEMO_COMPLETED"))
            throw CommerceFault(409,"INVALID_DEMO_TRANSITION","Unknown seller transition")
        if(current==next) return@transaction loadDemoIndividual(db,id)!!
        val allowed = when (next) {
            "DEMO_ACCEPTED", "DEMO_REJECTED" -> current == "DEMO_PAYMENT_SIMULATED"
            "DEMO_READY" -> current == "DEMO_ACCEPTED"
            "DEMO_COMPLETED" -> current == "DEMO_READY"
            else -> false
        }
        if (!allowed) throw CommerceFault(409, "INVALID_DEMO_TRANSITION", "Demo order cannot move to $next")
        if(next=="DEMO_COMPLETED" && !MessageDigest.isEqual(
                state.second.toByteArray(Charsets.US_ASCII),
                pickupCode?.trim()?.uppercase()?.toByteArray(Charsets.US_ASCII) ?: byteArrayOf()))
            throw CommerceFault(422,"INVALID_PICKUP_CODE","Ask the customer for their pickup code before confirming handover")
        if(next=="DEMO_COMPLETED") db.prepareStatement("""UPDATE individual_demo_orders SET
            status=?,updated_at=?,handed_over_at=?,handed_over_by=? WHERE id=?""").use { query ->
            val now=Timestamp.from(context.clock.instant())
            query.setString(1,next);query.setTimestamp(2,now);query.setTimestamp(3,now)
            query.setObject(4,principal.userId);query.setObject(5,id);query.executeUpdate()
        } else db.prepareStatement("UPDATE individual_demo_orders SET status=?,updated_at=? WHERE id=?").use { query ->
            query.setString(1, next); query.setTimestamp(2, Timestamp.from(context.clock.instant()))
            query.setObject(3, id); query.executeUpdate()
        }
        notifyIndividualEvent(db, id, next)
        recordAudit(db,principal.userId,"SELLER","seller.individual.status","INDIVIDUAL_DEMO_ORDER",id.toString(),sellerId,mapOf("status" to next))
        loadDemoIndividual(db, id)!!
    }
