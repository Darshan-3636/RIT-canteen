package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.transaction
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** A draft is private account data, not a seller-visible request or a price commitment. */
internal fun readBulkDraft(context: BackendContext, principal: AuthenticatedPrincipal): BulkDraftDto =
    context.transaction { db ->
        requireLiveDraftCustomer(db, context, principal)
        loadBulkDraft(db, principal.userId)
    }

internal fun replaceBulkDraft(context: BackendContext, principal: AuthenticatedPrincipal,
                              request: BulkDraftReplaceRequest): BulkDraftDto = context.transaction { db ->
    requireLiveDraftCustomer(db, context, principal)
    val sellerId = request.sellerId?.takeIf(String::isNotBlank)
    val reason = request.specialPickupReason.trim()
    val instructions = request.instructions.trim()
    val pickup = request.pickupAt?.let {
        try { Instant.parse(it) } catch (_: Exception) {
            throw CommerceFault(400, "INVALID_PICKUP_TIME", "pickupAt must be an ISO-8601 UTC instant")
        }
    }
    if (request.expectedVersion < 0 || request.lines.size > 100 ||
        request.lines.map { it.itemId }.toSet().size != request.lines.size ||
        request.lines.any { it.itemId.isBlank() || it.quantity !in 1..999 } ||
        reason.length > 500 || instructions.length > 500 ||
        (!request.specialPickup && reason.isNotEmpty()) ||
        (sellerId == null && (request.lines.isNotEmpty() || pickup != null || request.specialPickup ||
            reason.isNotEmpty() || instructions.isNotEmpty()))) {
        throw CommerceFault(400, "INVALID_BULK_DRAFT", "Draft needs one seller, distinct items and valid quantities")
    }
    db.prepareStatement("INSERT INTO bulk_customer_drafts(customer_user_id) VALUES (?) ON CONFLICT DO NOTHING").use {
        it.setObject(1, principal.userId)
        it.executeUpdate()
    }
    val version = db.prepareStatement(
        "SELECT version FROM bulk_customer_drafts WHERE customer_user_id=? FOR UPDATE"
    ).use { query ->
        query.setObject(1, principal.userId)
        query.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
    }
    if (version != request.expectedVersion)
        throw CommerceFault(409, "STALE_BULK_DRAFT", "Draft changed; reload before editing")
    if (sellerId != null) {
        val active = db.prepareStatement("SELECT active FROM sellers WHERE id=? FOR SHARE").use { query ->
            query.setString(1, sellerId)
            query.executeQuery().use { rows -> if (rows.next()) rows.getBoolean(1) else false }
        }
        if (!active) throw CommerceFault(422, "SELLER_UNAVAILABLE", "Seller is unavailable")
    }
    request.lines.forEach { line ->
        val belongs = db.prepareStatement("SELECT 1 FROM menu_items WHERE seller_id=? AND id=? FOR SHARE").use { query ->
            query.setString(1, sellerId)
            query.setString(2, line.itemId)
            query.executeQuery().use { it.next() }
        }
        if (!belongs) throw CommerceFault(422, "ITEM_NOT_FROM_SELLER", "A draft item does not belong to this seller")
    }
    db.prepareStatement("DELETE FROM bulk_customer_draft_lines WHERE customer_user_id=?").use {
        it.setObject(1, principal.userId)
        it.executeUpdate()
    }
    db.prepareStatement("""UPDATE bulk_customer_drafts SET seller_id=?,pickup_at=?,special_pickup=?,
        special_pickup_reason=?,instructions=?,version=version+1,updated_at=now() WHERE customer_user_id=?""").use {
        it.setString(1, sellerId)
        it.setTimestamp(2, pickup?.let(Timestamp::from))
        it.setBoolean(3, request.specialPickup)
        it.setString(4, reason)
        it.setString(5, instructions)
        it.setObject(6, principal.userId)
        it.executeUpdate()
    }
    if (request.lines.isNotEmpty()) db.prepareStatement("""INSERT INTO bulk_customer_draft_lines
        (customer_user_id,seller_id,item_id,quantity) VALUES (?,?,?,?)""").use { query ->
        request.lines.forEach { line ->
            query.setObject(1, principal.userId)
            query.setString(2, sellerId)
            query.setString(3, line.itemId)
            query.setInt(4, line.quantity)
            query.addBatch()
        }
        query.executeBatch()
    }
    loadBulkDraft(db, principal.userId)
}

private fun requireLiveDraftCustomer(db: Connection, context: BackendContext,
                                     principal: AuthenticatedPrincipal) {
    val affiliation = db.prepareStatement("""SELECT u.affiliation FROM sessions s JOIN users u ON u.id=s.user_id
        WHERE s.id=? AND s.user_id=? AND s.revoked_at IS NULL AND s.expires_at>? AND u.status='ACTIVE'""").use {
        it.setObject(1, principal.sessionId)
        it.setObject(2, principal.userId)
        it.setTimestamp(3, Timestamp.from(context.clock.instant()))
        it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
    }
    if (affiliation == null) throw CommerceFault(403, "CUSTOMER_REQUIRED", "A customer account is required")
}

private fun loadBulkDraft(db: Connection, user: UUID): BulkDraftDto {
    val row = db.prepareStatement("""SELECT version,seller_id,pickup_at,special_pickup,
        special_pickup_reason,instructions FROM bulk_customer_drafts WHERE customer_user_id=?""").use {
        it.setObject(1, user)
        it.executeQuery().use { rows -> if (rows.next()) DraftRow(rows.getLong(1), rows.getString(2),
            rows.getTimestamp(3)?.toInstant()?.toString(), rows.getBoolean(4), rows.getString(5), rows.getString(6)) else null }
    } ?: return BulkDraftDto(0, null, null, false, "", "", 0, emptyList())
    val lines = db.prepareStatement("""SELECT l.item_id,i.name,l.quantity,i.price_paise,
        (s.active AND i.published AND i.availability='AVAILABLE') FROM bulk_customer_draft_lines l
        JOIN menu_items i ON i.seller_id=l.seller_id AND i.id=l.item_id
        JOIN sellers s ON s.id=l.seller_id WHERE l.customer_user_id=? ORDER BY l.item_id""").use {
        it.setObject(1, user)
        it.executeQuery().use { rows -> buildList {
            while (rows.next()) add(BulkDraftLineDto(rows.getString(1), rows.getString(2),
                rows.getInt(3), rows.getLong(4), rows.getBoolean(5)))
        } }
    }
    val total = try { lines.fold(0L) { sum, line -> Math.addExact(sum,
        Math.multiplyExact(line.unitPricePaise, line.quantity.toLong())) } }
    catch (_: ArithmeticException) { throw CommerceFault(422, "TOTAL_TOO_LARGE", "Draft total exceeds the supported amount") }
    return BulkDraftDto(row.version, row.sellerId, row.pickupAt, row.specialPickup,
        row.reason, row.instructions, total, lines)
}

private data class DraftRow(val version: Long, val sellerId: String?, val pickupAt: String?,
                            val specialPickup: Boolean, val reason: String, val instructions: String)
