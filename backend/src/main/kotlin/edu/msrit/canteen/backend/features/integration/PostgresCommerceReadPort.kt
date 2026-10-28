package edu.msrit.canteen.backend.features.integration

import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.features.engagement.CommerceReadPort
import edu.msrit.canteen.backend.features.engagement.DemoRequestCounts
import edu.msrit.canteen.backend.features.engagement.ItemLookup
import edu.msrit.canteen.backend.features.engagement.SimulatedSales
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import java.time.LocalDate

/** Live read-only Commerce adapter; never infers access from a client-supplied seller ID. */
class PostgresCommerceReadPort(private val context: BackendContext) : CommerceReadPort {
    override fun simulatedSales(sellerId: String?, from: LocalDate?, to: LocalDate?): SimulatedSales? =
        if (context.config.environment != RuntimeEnvironment.CLOUD_DEMO) null else context.dataSource.connection.use { db ->
            val clauses = mutableListOf("demo_only=TRUE", "payment_verified=FALSE", "demo_payment_outcome='SUCCESS'")
            if (sellerId != null) clauses += "seller_id=?"
            if (from != null) clauses += "(created_at AT TIME ZONE 'Asia/Kolkata')::date >= ?"
            if (to != null) clauses += "(created_at AT TIME ZONE 'Asia/Kolkata')::date <= ?"
            db.prepareStatement("SELECT count(*),coalesce(sum(subtotal_paise),0) FROM individual_demo_orders WHERE ${clauses.joinToString(" AND ")}").use { sql ->
                var n = 1
                if (sellerId != null) sql.setString(n++, sellerId)
                if (from != null) sql.setObject(n++, from)
                if (to != null) sql.setObject(n, to)
                sql.executeQuery().use { rows -> rows.next(); SimulatedSales(Math.toIntExact(rows.getLong(1)), rows.getLong(2)) }
            }
        }
    override fun lookupItem(itemId: String): ItemLookup = context.dataSource.connection.use { db ->
        db.prepareStatement("""SELECT i.seller_id FROM menu_items i JOIN sellers s ON s.id=i.seller_id
            WHERE i.id=? AND s.active AND i.published
              AND (i.availability='AVAILABLE' OR i.unavailable_visibility='SHOW')""").use { sql ->
            sql.setString(1, itemId)
            sql.executeQuery().use { rows -> if (rows.next()) ItemLookup.Found(rows.getString(1)) else ItemLookup.Missing }
        }
    }

    override fun demoRequestCounts(sellerId: String?, from: LocalDate?, to: LocalDate?): DemoRequestCounts =
        context.dataSource.connection.use { db ->
            val clauses = mutableListOf<String>()
            if (sellerId != null) clauses += "seller_id=?"
            if (from != null) clauses += "(created_at AT TIME ZONE 'Asia/Kolkata')::date >= ?"
            if (to != null) clauses += "(created_at AT TIME ZONE 'Asia/Kolkata')::date <= ?"
            val where = if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}"
            db.prepareStatement("SELECT status, count(*) FROM bulk_requests $where GROUP BY status ORDER BY status").use { sql ->
                var n = 1
                if (sellerId != null) sql.setString(n++, sellerId)
                if (from != null) sql.setObject(n++, from)
                if (to != null) sql.setObject(n, to)
                sql.executeQuery().use { rows ->
                    val counts = linkedMapOf<String, Int>()
                    while (rows.next()) counts[rows.getString(1)] = Math.toIntExact(rows.getLong(2))
                    DemoRequestCounts(counts.values.sum(), counts)
                }
            }
        }
}
