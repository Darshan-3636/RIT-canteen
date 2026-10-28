package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.transaction
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.postgresql.util.PSQLException

private data class PaymentSource(val sellerId: String, val amountPaise: Long, val quoteVersion: Int?)

private fun requireSandboxEnvironment(context: BackendContext) {
    if (context.config.environment != RuntimeEnvironment.CLOUD_DEMO)
        throw CommerceFault(404, "SANDBOX_ONLY", "Sandbox payments are unavailable")
}

private fun requireUat(context: BackendContext, gateway: SandboxPaymentGateway) {
    requireSandboxEnvironment(context)
    if (gateway.merchantId == null)
        throw CommerceFault(503, "PHONEPE_UAT_NOT_CONFIGURED", "PhonePe UAT credentials are not configured")
}

private fun source(db: Connection, principal: AuthenticatedPrincipal, type: String, id: UUID,
                   now: Instant): PaymentSource {
    val customer = db.prepareStatement("SELECT affiliation FROM users WHERE id=? AND status='ACTIVE'").use { query ->
        query.setObject(1, principal.userId)
        query.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
    }
    if (customer !in setOf("STUDENT", "FACULTY"))
        throw CommerceFault(403, "CUSTOMER_REQUIRED", "A Student or Faculty customer account is required")
    return when (type) {
        "INDIVIDUAL" -> db.prepareStatement("""SELECT seller_id,subtotal_paise,status FROM individual_demo_orders
            WHERE id=? AND customer_user_id=? FOR UPDATE""").use { query ->
            query.setObject(1, id); query.setObject(2, principal.userId)
            query.executeQuery().use { rows ->
                if (!rows.next()) throw CommerceFault(404, "ORDER_NOT_FOUND", "Order not found")
                if (rows.getString(3) !in setOf("DEMO_SAVED", "DEMO_PAYMENT_PENDING", "DEMO_PAYMENT_FAILED"))
                    throw CommerceFault(409, "ORDER_NOT_PAYABLE", "This order cannot start a sandbox payment")
                PaymentSource(rows.getString(1), rows.getLong(2), null)
            }
        }
        "BULK" -> db.prepareStatement("""SELECT r.seller_id,q.subtotal_paise,r.current_version,r.status,
            q.expires_at,q.pickup_at
            FROM bulk_requests r JOIN bulk_quote_revisions q ON q.request_id=r.id AND q.version=r.current_version
            WHERE r.id=? AND r.customer_user_id=? FOR UPDATE OF r""").use { query ->
            query.setObject(1, id); query.setObject(2, principal.userId)
            query.executeQuery().use { rows ->
                if (!rows.next()) throw CommerceFault(404, "REQUEST_NOT_FOUND", "Bulk request not found")
                if (rows.getString(4) != "AGREED_AWAITING_PAYMENT")
                    throw CommerceFault(409, "REQUEST_NOT_PAYABLE", "The current quote is not fully approved")
                if (!rows.getTimestamp(5).toInstant().isAfter(now) ||
                    !rows.getTimestamp(6).toInstant().isAfter(now))
                    throw CommerceFault(409, "QUOTE_EXPIRED", "The approved quote or pickup window has expired")
                PaymentSource(rows.getString(1), rows.getLong(2), rows.getInt(3))
            }
        }
        else -> throw CommerceFault(400, "INVALID_PAYMENT_SOURCE", "Choose INDIVIDUAL or BULK")
    }
}

/** Inserts once using a server-owned price snapshot; network I/O happens after releasing the DB transaction. */
internal fun beginSandboxPayment(context: BackendContext, gateway: SandboxPaymentGateway,
                                 principal: AuthenticatedPrincipal, type: String, sourceId: UUID,
                                 idempotencyKey: String): SandboxPaymentDto {
    requireUat(context, gateway)
    if (idempotencyKey.length !in 1..128 || idempotencyKey.any(Char::isISOControl))
        throw CommerceFault(400, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key is required")
    val id = context.transaction { db ->
        val existing = db.prepareStatement("""SELECT id FROM sandbox_payment_attempts
            WHERE customer_user_id=? AND source_type=? AND source_id=? AND idempotency_key=?""").use { query ->
            query.setObject(1, principal.userId); query.setString(2, type)
            query.setObject(3, sourceId); query.setString(4, idempotencyKey)
            query.executeQuery().use { rows -> if (rows.next()) rows.getObject(1, UUID::class.java) else null }
        }
        if (existing != null) return@transaction existing
        val priced = source(db, principal, type, sourceId, context.clock.instant())
        if (priced.amountPaise < 100) throw CommerceFault(422, "AMOUNT_TOO_SMALL", "PhonePe UAT requires at least 100 paise")
        val active = db.prepareStatement("""SELECT id FROM sandbox_payment_attempts WHERE source_type=? AND source_id=?
            AND state IN ('INITIATED','PENDING','CANCELLED','SANDBOX_SUCCESS') FOR UPDATE""").use { query ->
            query.setString(1, type); query.setObject(2, sourceId)
            query.executeQuery().use { rows -> rows.next() }
        }
        if (active) throw CommerceFault(409, "PAYMENT_ALREADY_STARTED", "Reconcile the existing sandbox payment first")
        val attemptId = UUID.randomUUID()
        val merchantOrderId = "RIT_UAT_${attemptId.toString().replace("-", "")}"
        try {
            db.prepareStatement("""INSERT INTO sandbox_payment_attempts
                (id,customer_user_id,seller_id,source_type,source_id,quote_version,amount_paise,state,
                 merchant_order_id,idempotency_key,expires_at) VALUES (?,?,?,?,?,?,?,'INITIATED',?,?,?)""").use { query ->
                query.setObject(1, attemptId); query.setObject(2, principal.userId)
                query.setString(3, priced.sellerId); query.setString(4, type); query.setObject(5, sourceId)
                if (priced.quoteVersion == null) query.setNull(6, java.sql.Types.INTEGER) else query.setInt(6, priced.quoteVersion)
                query.setLong(7, priced.amountPaise); query.setString(8, merchantOrderId)
                query.setString(9, idempotencyKey)
                query.setTimestamp(10, Timestamp.from(context.clock.instant().plusSeconds(1200)))
                query.executeUpdate()
            }
        } catch (failure: PSQLException) {
            if (failure.sqlState == "23505") throw CommerceFault(409, "PAYMENT_ALREADY_STARTED", "Reconcile the existing sandbox payment first")
            throw failure
        }
        attemptId
    }
    val snapshot = customerSandboxPayment(context, principal, id, gateway.merchantId)
    // Reuse the same merchant order ID when an earlier network call failed before its response was saved.
    // PhonePe may have created the order; never generate a second local attempt for this retry key.
    if (snapshot.status == "INITIATED") {
        val provider = try { gateway.create(snapshot.merchantOrderId, snapshot.amountPaise) }
        catch (_: SandboxGatewayUnavailable) {
            throw CommerceFault(503, "PHONEPE_UAT_UNAVAILABLE", "PhonePe UAT could not create the payment; check its status before retrying")
        }
        context.transaction { db ->
            db.prepareStatement("""UPDATE sandbox_payment_attempts SET state='PENDING',provider_state=?,
                provider_order_id=?,sdk_token=?,expires_at=?,updated_at=? WHERE id=? AND state='INITIATED'""").use { query ->
                query.setString(1, provider.providerState); query.setString(2, provider.providerOrderId)
                query.setString(3, provider.sdkToken); query.setTimestamp(4, Timestamp.from(provider.expiresAt))
                query.setTimestamp(5, Timestamp.from(context.clock.instant())); query.setObject(6, id)
                query.executeUpdate()
            }
        }
    }
    return customerSandboxPayment(context, principal, id, gateway.merchantId)
}

internal fun customerSandboxPayment(context: BackendContext, principal: AuthenticatedPrincipal,
                                    id: UUID, merchantId: String?): SandboxPaymentDto {
    requireSandboxEnvironment(context)
    return context.dataSource.connection.use { db ->
        db.prepareStatement("SELECT * FROM sandbox_payment_attempts WHERE id=? AND customer_user_id=?").use { query ->
            query.setObject(1, id); query.setObject(2, principal.userId)
            query.executeQuery().use { rows -> if (rows.next()) rows.paymentDto(true, merchantId)
                else throw CommerceFault(404, "PAYMENT_NOT_FOUND", "Sandbox payment not found") }
        }
    }
}

internal fun customerSandboxPayments(context: BackendContext, principal: AuthenticatedPrincipal): SandboxPaymentListDto {
    requireSandboxEnvironment(context)
    return context.dataSource.connection.use { db ->
        db.prepareStatement("SELECT * FROM sandbox_payment_attempts WHERE customer_user_id=? ORDER BY created_at DESC LIMIT 100").use { query ->
            query.setObject(1, principal.userId)
            query.executeQuery().use { rows -> SandboxPaymentListDto(buildList { while (rows.next()) add(rows.paymentDto(false, null)) }) }
        }
    }
}

internal fun sellerSandboxPayments(context: BackendContext, sellerId: String): SandboxPaymentListDto {
    requireSandboxEnvironment(context)
    return context.dataSource.connection.use { db ->
        db.prepareStatement("SELECT * FROM sandbox_payment_attempts WHERE seller_id=? ORDER BY created_at DESC LIMIT 100").use { query ->
            query.setString(1, sellerId)
            query.executeQuery().use { rows -> SandboxPaymentListDto(buildList { while (rows.next()) add(rows.paymentDto(false, null)) }) }
        }
    }
}

/** Only an authenticated server-to-PhonePe status read can turn an attempt into sandbox success. */
internal fun reconcileSandboxPayment(context: BackendContext, gateway: SandboxPaymentGateway,
                                     principal: AuthenticatedPrincipal, id: UUID): SandboxPaymentDto {
    requireUat(context, gateway)
    val snapshot = customerSandboxPayment(context, principal, id, gateway.merchantId)
    if (snapshot.status in setOf("SANDBOX_SUCCESS", "SANDBOX_FAILED", "EXPIRED")) return snapshot
    val provider = try { gateway.status(snapshot.merchantOrderId) }
    catch (_: SandboxGatewayUnavailable) {
        throw CommerceFault(503, "PHONEPE_UAT_UNAVAILABLE", "PhonePe UAT status could not be confirmed")
    }
    if (provider.amountPaise != snapshot.amountPaise)
        throw CommerceFault(502, "PROVIDER_AMOUNT_MISMATCH", "PhonePe UAT amount did not match the saved order")
    val next = when (provider.providerState) {
        "COMPLETED" -> "SANDBOX_SUCCESS"
        "FAILED" -> "SANDBOX_FAILED"
        // Local time alone is not proof of provider expiry; keep polling until PhonePe confirms a terminal state.
        "PENDING" -> "PENDING"
        else -> throw CommerceFault(502, "UNKNOWN_PROVIDER_STATE", "PhonePe UAT returned an unknown state")
    }
    context.transaction { db ->
        val current = db.prepareStatement("SELECT state FROM sandbox_payment_attempts WHERE id=? AND customer_user_id=? FOR UPDATE").use { query ->
            query.setObject(1, id); query.setObject(2, principal.userId)
            query.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        } ?: throw CommerceFault(404, "PAYMENT_NOT_FOUND", "Sandbox payment not found")
        if (current !in setOf("SANDBOX_SUCCESS", "SANDBOX_FAILED", "EXPIRED")) {
            db.prepareStatement("""UPDATE sandbox_payment_attempts SET state=?,provider_state=?,
                transaction_reference=?,updated_at=? WHERE id=?""").use { query ->
                query.setString(1, next); query.setString(2, provider.providerState)
                query.setString(3, provider.transactionReference)
                query.setTimestamp(4, Timestamp.from(context.clock.instant())); query.setObject(5, id)
                query.executeUpdate()
            }
            if (next == "SANDBOX_SUCCESS" && snapshot.sourceType == "INDIVIDUAL") {
                db.prepareStatement("""UPDATE individual_demo_orders SET status='DEMO_PAYMENT_SIMULATED',updated_at=?
                    WHERE id=? AND status IN ('DEMO_SAVED','DEMO_PAYMENT_PENDING','DEMO_PAYMENT_FAILED')""").use { query ->
                    query.setTimestamp(1, Timestamp.from(context.clock.instant()))
                    query.setObject(2, UUID.fromString(snapshot.sourceId)); query.executeUpdate()
                }
            }
        }
    }
    return customerSandboxPayment(context, principal, id, gateway.merchantId)
}

/** Client cancellation is provisional; later provider reconciliation can still report success. */
internal fun cancelSandboxPayment(context: BackendContext, principal: AuthenticatedPrincipal, id: UUID): SandboxPaymentDto {
    requireSandboxEnvironment(context)
    context.transaction { db ->
        db.prepareStatement("""UPDATE sandbox_payment_attempts SET state='CANCELLED',updated_at=?
            WHERE id=? AND customer_user_id=? AND state IN ('INITIATED','PENDING')""").use { query ->
            query.setTimestamp(1, Timestamp.from(context.clock.instant()))
            query.setObject(2, id); query.setObject(3, principal.userId); query.executeUpdate()
        }
    }
    return customerSandboxPayment(context, principal, id, null)
}

private fun ResultSet.paymentDto(includeToken: Boolean, merchantId: String?): SandboxPaymentDto = SandboxPaymentDto(
    id = getObject("id", UUID::class.java).toString(), sourceType = getString("source_type"),
    sourceId = getObject("source_id", UUID::class.java).toString(), sellerId = getString("seller_id"),
    amountPaise = getLong("amount_paise"), status = getString("state"),
    merchantOrderId = getString("merchant_order_id"), providerOrderId = getString("provider_order_id"),
    transactionReference = getString("transaction_reference"),
    sdkToken = if (includeToken && getString("state") == "PENDING") getString("sdk_token") else null,
    merchantId = if (includeToken) merchantId else null,
    idempotencyKey = if (includeToken) getString("idempotency_key") else null,
)
