package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.transaction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Insert-once, visibly synthetic history for the private cloud-demo identities. Never a payment source. */
object CloudDemoDataSeed {
    private const val sellerId = "main-canteen"
    private const val marker = "[DEMO / SYNTHETIC] "
    private val orderHistory = listOf(
        OrderSample(0, "masala-dosa", 2), OrderSample(1, "veg-biryani", 1),
        OrderSample(3, "idli", 3), OrderSample(5, "filter-coffee", 2),
        OrderSample(9, "veg-thali", 1),
    )
    private val requestHistory = listOf(
        RequestSample(0, "WITHDRAWN", "masala-dosa"), RequestSample(1, "EXPIRED", "veg-biryani"),
        RequestSample(3, "REJECTED", "idli"), RequestSample(5, "WITHDRAWN", "veg-thali"),
        RequestSample(9, "EXPIRED", "masala-dosa"), RequestSample(15, "REJECTED", "filter-coffee"),
    )

    fun seed(context: BackendContext) {
        check(context.config.environment == RuntimeEnvironment.CLOUD_DEMO && context.config.cloudDemoSeedEnabled) {
            "Synthetic cloud data requires explicit cloud_demo seeding"
        }
        context.transaction { db ->
            val customerId = db.prepareStatement("SELECT id FROM users WHERE email=? AND status='ACTIVE'").use { sql ->
                sql.setString(1, requireNotNull(context.config.cloudDemoCustomerEmail).lowercase())
                sql.executeQuery().use { rows -> check(rows.next()) { "Cloud demo customer must be seeded first" }
                    rows.getObject(1, UUID::class.java) }
            }
            val items = (orderHistory.map { it.itemId } + requestHistory.map { it.itemId }).distinct()
                .associateWith { itemId ->
                    db.prepareStatement("SELECT name,price_paise,kind FROM menu_items WHERE id=? AND seller_id=?").use { sql ->
                        sql.setString(1, itemId); sql.setString(2, sellerId)
                        sql.executeQuery().use { rows ->
                            check(rows.next() && rows.getString(3) == "PREPARED") {
                                "Cloud demo prepared catalog must be seeded first"
                            }
                            Item(itemId, rows.getString(1), rows.getLong(2))
                        }
                    }
                }
            val now = context.clock.instant()
            orderHistory.forEachIndexed { index, sample ->
                insertOrder(db, customerId, items.getValue(sample.itemId), sample.quantity,
                    stableId(customerId, "order-$index"), sampleTime(context, now, sample.daysAgo))
            }
            requestHistory.forEachIndexed { index, sample ->
                insertRequest(db, customerId, items.getValue(sample.itemId), stableId(customerId, "request-$index"),
                    "cloud-demo-synthetic-request-$index", sampleTime(context, now, sample.daysAgo), sample.status)
            }
        }
    }

    private fun insertOrder(db: Connection, customerId: UUID, item: Item, quantity: Int, id: UUID, created: Instant) {
        val inserted = db.prepareStatement("""INSERT INTO individual_demo_orders
            (id,customer_user_id,seller_id,status,demo_only,payment_verified,subtotal_paise,created_at,updated_at)
            VALUES (?,?,?,'DEMO_SAVED',TRUE,FALSE,?,?,?) ON CONFLICT(id) DO NOTHING""").use { sql ->
            sql.setObject(1, id); sql.setObject(2, customerId); sql.setString(3, sellerId)
            sql.setLong(4, item.pricePaise * quantity); sql.setTimestamp(5, Timestamp.from(created))
            sql.setTimestamp(6, Timestamp.from(created)); sql.executeUpdate() == 1
        }
        if (inserted) db.prepareStatement("""INSERT INTO individual_demo_order_lines
            (order_id,seller_id,item_id,item_name_snapshot,quantity,unit_price_paise) VALUES (?,?,?,?,?,?)""").use { sql ->
            sql.setObject(1, id); sql.setString(2, sellerId); sql.setString(3, item.id)
            sql.setString(4, marker + item.name); sql.setInt(5, quantity)
            sql.setLong(6, item.pricePaise); sql.executeUpdate()
        }
    }

    private fun insertRequest(db: Connection, customerId: UUID, item: Item, id: UUID,
                              key: String, created: Instant, status: String) {
        val inserted = db.prepareStatement("""INSERT INTO bulk_requests
            (id,customer_user_id,seller_id,current_version,status,idempotency_key,idempotency_hash,
             instructions,created_at,updated_at) VALUES (?,?,?,1,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING""").use { sql ->
            sql.setObject(1, id); sql.setObject(2, customerId); sql.setString(3, sellerId)
            sql.setString(4, status); sql.setString(5, key)
            sql.setBytes(6, MessageDigest.getInstance("SHA-256").digest(key.toByteArray(StandardCharsets.UTF_8)))
            sql.setString(7, marker + "Historical sample request. No purchase or payment occurred.")
            sql.setTimestamp(8, Timestamp.from(created)); sql.setTimestamp(9, Timestamp.from(created))
            sql.executeUpdate() == 1
        }
        if (!inserted) return
        db.prepareStatement("""INSERT INTO bulk_quote_revisions
            (request_id,version,status,pickup_at,pickup_kind,subtotal_paise,expires_at,created_by,created_at)
            VALUES (?,1,?,?,'REGULAR',?,?,?,?)""").use { sql ->
            sql.setObject(1, id); sql.setString(2, status)
            sql.setTimestamp(3, Timestamp.from(created.plusSeconds(172_800)))
            sql.setLong(4, item.pricePaise * 8)
            sql.setTimestamp(5, Timestamp.from(created.plusSeconds(86_400)))
            sql.setObject(6, customerId); sql.setTimestamp(7, Timestamp.from(created)); sql.executeUpdate()
        }
        db.prepareStatement("""INSERT INTO bulk_quote_lines
            (request_id,version,seller_id,item_id,item_name_snapshot,quantity,unit_price_paise)
            VALUES (?,1,?,?,?,8,?)""").use { sql ->
            sql.setObject(1, id); sql.setString(2, sellerId); sql.setString(3, item.id)
            sql.setString(4, marker + item.name); sql.setLong(5, item.pricePaise); sql.executeUpdate()
        }
        db.prepareStatement("""INSERT INTO bulk_request_events
            (request_id,version,actor_user_id,event_type,occurred_at,details)
            VALUES (?,1,?,?,?,'{"synthetic":true,"demoOnly":true}'::jsonb)""").use { sql ->
            sql.setObject(1, id); sql.setObject(2, customerId); sql.setString(3, status)
            sql.setTimestamp(4, Timestamp.from(created)); sql.executeUpdate()
        }
    }

    private fun sampleTime(context: BackendContext, now: Instant, daysAgo: Long): Instant {
        val noon = LocalDate.ofInstant(now, context.config.sellerZone).minusDays(daysAgo)
            .atTime(12, 0).atZone(context.config.sellerZone).toInstant()
        return minOf(noon, now)
    }

    private fun stableId(customerId: UUID, key: String): UUID = UUID.nameUUIDFromBytes(
        "rit-canteen-cloud-synthetic-v1:$customerId:$key".toByteArray(StandardCharsets.UTF_8))

    private data class Item(val id: String, val name: String, val pricePaise: Long)
    private data class OrderSample(val daysAgo: Long, val itemId: String, val quantity: Int)
    private data class RequestSample(val daysAgo: Long, val status: String, val itemId: String)
}
