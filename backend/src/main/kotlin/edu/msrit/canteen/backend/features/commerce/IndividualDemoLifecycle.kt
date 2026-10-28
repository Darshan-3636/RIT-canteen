package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.transaction
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID

internal fun loadDemoIndividual(db: Connection, id: UUID): IndividualDemoOrderDto? =
    db.prepareStatement("""SELECT seller_id,status,demo_only,payment_verified,subtotal_paise,created_at,demo_payment_outcome
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
            IndividualDemoOrderDto(id.toString(), row.getString(1), row.getString(2), row.getBoolean(3),
                row.getBoolean(4), row.getLong(5), row.getTimestamp(6).toInstant().toString(), lines,
                row.getString(7))
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
    // A lost response or rapid repeat must return the same persisted result, not a false failure.
    if (recordedOutcome == outcome && status == when (outcome) {
            "SUCCESS" -> "DEMO_PAYMENT_SIMULATED"
            "FAILURE" -> "DEMO_PAYMENT_FAILED"
            else -> "DEMO_PAYMENT_PENDING"
        }) return@transaction loadDemoIndividual(db, id)!!
    if (outcome == "SUCCESS" && recordedOutcome == "SUCCESS" &&
        status in setOf("DEMO_ACCEPTED", "DEMO_REJECTED", "DEMO_READY", "DEMO_COMPLETED"))
        return@transaction loadDemoIndividual(db, id)!!
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
    loadDemoIndividual(db, id)!!
}

/** Demo seller processing has no real fulfillment or financial consequence. */
internal fun transitionIndividualDemo(context: BackendContext, principal: AuthenticatedPrincipal,
                                      sellerId: String, id: UUID, next: String): IndividualDemoOrderDto =
    context.transaction { db ->
        if (context.config.environment !in setOf(RuntimeEnvironment.LOCAL, RuntimeEnvironment.CLOUD_DEMO))
            throw CommerceFault(404, "DEMO_ONLY", "Demo fulfillment simulation is unavailable")
        if (!context.authorization.hasSellerCapability(db, principal, sellerId, "bulk.respond"))
            throw CommerceFault(403, "SELLER_CAPABILITY_REQUIRED", "This seller action is not permitted")
        val current = db.prepareStatement("""SELECT status FROM individual_demo_orders
            WHERE id=? AND seller_id=? FOR UPDATE""").use { query ->
            query.setObject(1, id); query.setString(2, sellerId)
            query.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        } ?: throw CommerceFault(404, "ORDER_NOT_FOUND", "Demo order not found")
        val allowed = when (next) {
            "DEMO_ACCEPTED", "DEMO_REJECTED" -> current == "DEMO_PAYMENT_SIMULATED"
            "DEMO_READY" -> current == "DEMO_ACCEPTED"
            "DEMO_COMPLETED" -> current == "DEMO_READY"
            else -> false
        }
        if (!allowed) throw CommerceFault(409, "INVALID_DEMO_TRANSITION", "Demo order cannot move to $next")
        db.prepareStatement("UPDATE individual_demo_orders SET status=?,updated_at=? WHERE id=?").use { query ->
            query.setString(1, next); query.setTimestamp(2, Timestamp.from(context.clock.instant()))
            query.setObject(3, id); query.executeUpdate()
        }
        loadDemoIndividual(db, id)!!
    }
