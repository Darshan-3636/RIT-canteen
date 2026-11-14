package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.transaction
import java.sql.Connection
import java.sql.Timestamp
import java.security.MessageDigest
import java.util.UUID

private fun liveCartUser(db: Connection, context: BackendContext, principal: AuthenticatedPrincipal) {
    val live = db.prepareStatement("""SELECT 1 FROM sessions s JOIN users u ON u.id=s.user_id
        WHERE s.id=? AND s.user_id=? AND s.revoked_at IS NULL AND s.expires_at>? AND u.status='ACTIVE'""").use { query ->
        query.setObject(1, principal.sessionId)
        query.setObject(2, principal.userId)
        query.setTimestamp(3, Timestamp.from(context.clock.instant()))
        query.executeQuery().use { it.next() }
    }
    if (!live) throw CommerceFault(401, "UNAUTHENTICATED", "A valid bearer session is required")
}

private fun ensureCart(db: Connection, user: UUID) {
    db.prepareStatement("INSERT INTO individual_customer_carts(customer_user_id) VALUES (?) ON CONFLICT DO NOTHING").use {
        it.setObject(1, user)
        it.executeUpdate()
    }
}

private fun cartVersion(db: Connection, user: UUID): Long = db.prepareStatement(
    "SELECT version FROM individual_customer_carts WHERE customer_user_id=? FOR UPDATE"
).use { query ->
    query.setObject(1, user)
    query.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else error("Cart row missing") }
}

private fun loadCart(db: Connection, user: UUID): IndividualCartDto {
    val version = db.prepareStatement("SELECT version FROM individual_customer_carts WHERE customer_user_id=?").use { query ->
        query.setObject(1, user)
        query.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else 0L }
    }
    val lines = db.prepareStatement("""SELECT l.item_id,i.name,l.quantity,i.price_paise,
        (s.active AND i.published AND i.archived_at IS NULL AND $ACTIVE_CATEGORY_SQL AND i.availability='AVAILABLE'
         AND i.order_eligibility IN ('INDIVIDUAL_ONLY','BOTH')
         AND EXISTS (SELECT 1 FROM seller_ordering_policies p WHERE p.seller_id=s.id AND p.individual_enabled))
        FROM individual_customer_cart_lines l JOIN menu_items i ON i.seller_id=l.seller_id AND i.id=l.item_id
        JOIN sellers s ON s.id=l.seller_id WHERE l.customer_user_id=? ORDER BY l.item_id""").use { query ->
        query.setObject(1, user)
        query.executeQuery().use { rows -> buildList {
            while (rows.next()) add(IndividualCartLineDto(rows.getString(1), rows.getString(2), rows.getInt(3),
                rows.getLong(4), rows.getBoolean(5)))
        } }
    }
    val total = try { lines.fold(0L) { sum, line -> Math.addExact(sum,
        Math.multiplyExact(line.unitPricePaise, line.quantity.toLong())) } }
    catch (_: ArithmeticException) { throw CommerceFault(422, "TOTAL_TOO_LARGE", "Cart total exceeds the supported amount") }
    val sellers = db.prepareStatement("SELECT DISTINCT seller_id FROM individual_customer_cart_lines WHERE customer_user_id=?").use { query ->
        query.setObject(1, user)
        query.executeQuery().use { rows -> buildList { while(rows.next()) add(rows.getString(1)) } }
    }
    return IndividualCartDto(version, sellers.singleOrNull(), total,
        lines.isNotEmpty() && sellers.size == 1 && lines.all { it.available }, lines)
}

internal fun readIndividualCart(context: BackendContext, principal: AuthenticatedPrincipal): IndividualCartDto =
    context.transaction { db ->
        liveCartUser(db, context, principal)
        loadCart(db, principal.userId)
    }

internal fun quoteIndividualOffer(context: BackendContext, principal: AuthenticatedPrincipal,
                                  request: IndividualOfferQuoteRequest): IndividualOfferQuoteDto = context.transaction { db ->
    liveCartUser(db,context,principal)
    val id = runCatching { UUID.fromString(request.promotionId) }.getOrNull()
        ?: throw CommerceFault(400,"INVALID_PROMOTION_ID","promotionId must be a UUID")
    val cart = loadCart(db,principal.userId)
    if (!cart.readyForCheckout) throw CommerceFault(422,"CART_NOT_READY","Cart is empty or contains unavailable items")
    val seller = cart.sellerId ?: throw CommerceFault(422,"CART_NOT_READY","Cart is empty")
    val lines = cart.lines.map { IndividualDemoLineDto(it.itemId,it.itemName,it.quantity,it.unitPricePaise) }
    val discount = validatedPromotionDiscount(db,context,principal.userId,seller,lines,id)
    if (discount >= cart.subtotalPaise) throw CommerceFault(422,"ZERO_VALUE_ORDER_UNSUPPORTED","This checkout cannot process a zero-value order")
    IndividualOfferQuoteDto(cart.subtotalPaise,discount,cart.subtotalPaise-discount,id.toString())
}

internal fun replaceIndividualCart(context: BackendContext, principal: AuthenticatedPrincipal,
                                   request: IndividualCartReplaceRequest): IndividualCartDto = context.transaction { db ->
    liveCartUser(db, context, principal)
    if (request.expectedVersion < 0 || request.lines.size > 100 ||
        request.lines.map { it.itemId }.toSet().size != request.lines.size ||
        request.lines.any { it.itemId.isBlank() || it.quantity !in 1..999 })
        throw CommerceFault(400, "INVALID_CART", "Cart needs distinct items with quantities from 1 to 999")
    ensureCart(db, principal.userId)
    val current = cartVersion(db, principal.userId)
    if (current != request.expectedVersion) throw CommerceFault(409, "STALE_CART", "Cart changed; refresh before editing")
    val seller = db.prepareStatement("""SELECT s.id FROM menu_items i JOIN sellers s ON s.id=i.seller_id
        JOIN seller_ordering_policies p ON p.seller_id=s.id
        WHERE i.id=? AND s.active AND p.individual_enabled FOR SHARE OF s,p""").use { query ->
        query.setString(1, request.lines.firstOrNull()?.itemId)
        query.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
    }
    if (request.lines.isNotEmpty() && seller == null)
        throw CommerceFault(422, "SELLER_UNAVAILABLE", "This seller does not accept individual orders")
    request.lines.forEach { line ->
        val valid = db.prepareStatement("""SELECT 1 FROM menu_items i WHERE i.seller_id=? AND i.id=?
            AND i.order_eligibility IN ('INDIVIDUAL_ONLY','BOTH')
            AND i.published AND i.archived_at IS NULL AND $ACTIVE_CATEGORY_SQL AND i.availability='AVAILABLE' FOR SHARE OF i""").use { query ->
            query.setString(1, seller)
            query.setString(2, line.itemId)
            query.executeQuery().use { it.next() }
        }
        if (!valid) throw CommerceFault(422, "INDIVIDUAL_ITEM_UNAVAILABLE", "An item is unavailable for individual ordering")
    }
    db.prepareStatement("DELETE FROM individual_customer_cart_lines WHERE customer_user_id=?").use {
        it.setObject(1, principal.userId)
        it.executeUpdate()
    }
    db.prepareStatement("""INSERT INTO individual_customer_cart_lines(customer_user_id,seller_id,item_id,quantity)
        VALUES (?,?,?,?)""").use { query ->
        request.lines.forEach { line ->
            query.setObject(1, principal.userId)
            query.setString(2, seller)
            query.setString(3, line.itemId)
            query.setInt(4, line.quantity)
            query.addBatch()
        }
        query.executeBatch()
    }
    db.prepareStatement("UPDATE individual_customer_carts SET version=version+1,updated_at=now() WHERE customer_user_id=?").use {
        it.setObject(1, principal.userId)
        it.executeUpdate()
    }
    loadCart(db, principal.userId)
}

private fun loadCheckoutOrder(db: Connection, user: UUID, id: UUID): IndividualDemoOrderDto =
    db.prepareStatement("SELECT 1 FROM individual_demo_orders WHERE id=? AND customer_user_id=?").use { query ->
        query.setObject(1, id)
        query.setObject(2, user)
        query.executeQuery().use { row ->
            if (!row.next()) throw CommerceFault(409, "ORDER_REPLAY_MISSING", "Previously placed order was not found")
        }
    }
        .let { loadDemoIndividual(db,id,true)!! }

/** Demo-only: an atomic, idempotent saved order. No payment, reservation, or kitchen dispatch is implied. */
internal fun checkoutIndividualCart(context: BackendContext, principal: AuthenticatedPrincipal,
                                    request: IndividualCartCheckoutRequest, key: String): IndividualDemoOrderDto =
    context.transaction { db ->
        liveCartUser(db, context, principal)
        if (key.length !in 1..128 || key.any(Char::isISOControl))
            throw CommerceFault(400, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 1 to 128 visible characters")
        if (request.expectedVersion < 0) throw CommerceFault(400, "INVALID_VERSION", "expectedVersion cannot be negative")
        val promotionId = request.promotionId?.let { runCatching { UUID.fromString(it) }.getOrNull()
            ?: throw CommerceFault(400,"INVALID_PROMOTION_ID","promotionId must be a UUID") }
        ensureCart(db, principal.userId)
        val version = cartVersion(db, principal.userId)
        val hashInput = request.expectedVersion.toString() + (promotionId?.let { "|promotion:$it" } ?: "")
        val hash = MessageDigest.getInstance("SHA-256").digest(hashInput.toByteArray())
        val prior = db.prepareStatement("""SELECT id,idempotency_hash FROM individual_demo_orders
            WHERE customer_user_id=? AND idempotency_key=?""").use { query ->
            query.setObject(1, principal.userId)
            query.setString(2, key)
            query.executeQuery().use { row -> if (row.next()) row.getObject(1, UUID::class.java) to row.getBytes(2) else null }
        }
        if (prior != null) {
            if (!MessageDigest.isEqual(hash, prior.second))
                throw CommerceFault(409, "IDEMPOTENCY_KEY_REUSED", "This key belongs to a different checkout attempt")
            return@transaction loadCheckoutOrder(db, principal.userId, prior.first)
        }
        if (version != request.expectedVersion)
            throw CommerceFault(409, "STALE_CART", "Cart changed; refresh before checkout")
        val cart = loadCart(db, principal.userId)
        if (!cart.readyForCheckout || cart.lines.size > 100)
            throw CommerceFault(422, "CART_NOT_READY", "Cart is empty or contains unavailable items")
        val seller = cart.sellerId ?: throw CommerceFault(422, "CART_NOT_READY", "Cart is empty")
        val locked = cart.lines.map { line ->
            db.prepareStatement("""SELECT i.name,i.price_paise FROM menu_items i JOIN sellers s ON s.id=i.seller_id
                JOIN seller_ordering_policies p ON p.seller_id=s.id
                WHERE i.seller_id=? AND i.id=? AND s.active AND p.individual_enabled AND
                i.order_eligibility IN ('INDIVIDUAL_ONLY','BOTH') AND i.published
                AND i.archived_at IS NULL AND $ACTIVE_CATEGORY_SQL AND i.availability='AVAILABLE' FOR SHARE OF i,s,p""").use { query ->
                query.setString(1, seller)
                query.setString(2, line.itemId)
                query.executeQuery().use { row ->
                    if (!row.next()) throw CommerceFault(422, "CART_NOT_READY", "An item is no longer available")
                    IndividualDemoLineDto(line.itemId, row.getString(1), line.quantity, row.getLong(2))
                }
            }
        }
        val total = try { locked.fold(0L) { sum, line -> Math.addExact(sum,
            Math.multiplyExact(line.unitPricePaise, line.quantity.toLong())) } }
        catch (_: ArithmeticException) { throw CommerceFault(422, "TOTAL_TOO_LARGE", "Cart total exceeds the supported amount") }
        val discount = promotionId?.let { validatedPromotionDiscount(db,context,principal.userId,seller,locked,it) } ?: 0L
        if (discount >= total) throw CommerceFault(422,"ZERO_VALUE_ORDER_UNSUPPORTED","This checkout cannot process a zero-value order")
        val id = UUID.randomUUID()
        val now = context.clock.instant()
        db.prepareStatement("""INSERT INTO individual_demo_orders
            (id,customer_user_id,seller_id,subtotal_paise,discount_paise,promotion_id,created_at,idempotency_key,idempotency_hash)
            VALUES (?,?,?,?,?,?,?,?,?)""").use { query ->
            query.setObject(1, id)
            query.setObject(2, principal.userId)
            query.setString(3, seller)
            query.setLong(4, total)
            query.setLong(5, discount)
            query.setObject(6, promotionId)
            query.setTimestamp(7, Timestamp.from(now))
            query.setString(8, key)
            query.setBytes(9, hash)
            query.executeUpdate()
        }
        db.prepareStatement("""INSERT INTO individual_demo_order_lines
            (order_id,seller_id,item_id,item_name_snapshot,quantity,unit_price_paise) VALUES (?,?,?,?,?,?)""").use { query ->
            locked.forEach { line ->
                query.setObject(1, id)
                query.setString(2, seller)
                query.setString(3, line.itemId)
                query.setString(4, line.itemName)
                query.setInt(5, line.quantity)
                query.setLong(6, line.unitPricePaise)
                query.addBatch()
            }
            query.executeBatch()
        }
        // Creating this demo order is not a verified payment. Keep the versioned cart so
        // failures, Back navigation, app restarts, and retrying the same checkout cannot lose it.
        notifyIndividualEvent(db, id, "DEMO_SAVED")
        loadDemoIndividual(db,id,true)!!
    }
