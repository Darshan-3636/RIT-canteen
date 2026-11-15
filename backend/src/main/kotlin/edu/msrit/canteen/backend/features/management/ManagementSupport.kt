package edu.msrit.canteen.backend.features.management

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.managementLock
import edu.msrit.canteen.backend.core.transaction
import edu.msrit.canteen.backend.features.engagement.*
import io.ktor.http.HttpStatusCode
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.util.UUID

internal fun uuid(value: String): UUID = runCatching { UUID.fromString(value) }.getOrNull()
    ?: throw badRequest("INVALID_ID", "Expected a UUID")
internal fun text(value: String, max: Int, field: String): String = value.trim().also {
    if (it.isEmpty() || it.length > max || it.any(Char::isISOControl))
        throw badRequest("INVALID_FIELD", "$field must contain 1 to $max readable characters")
}
internal fun version(actual: Long, expected: Long?) {
    if (expected != null && (expected < 0 || actual != expected))
        throw conflict("VERSION_CONFLICT", "Resource changed; reload before editing")
}
internal fun denied(): Nothing = throw ApiFailure(HttpStatusCode.Forbidden, "MANAGEMENT_FORBIDDEN", "This management action is not permitted")

/** Existing SQL/error/audit helpers are shared instead of introducing a second persistence abstraction. */
internal class ManagementTransactions(private val context: BackendContext) {
    fun <T> write(actor: AuthenticatedPrincipal, seller: String? = null, capability: String? = null,
                  admin: Boolean = false, lifecycle: Boolean = false, block: (Connection) -> T): T = try {
        context.transaction { db ->
            db.managementLock(exclusive = lifecycle)
            if (admin) context.requireAdminInTx(db, actor)
            else context.requireSellerInTx(db, actor, seller!!, capability!!)
            block(db)
        }
    } catch (e: PSQLException) {
        when (e.sqlState) {
            "23505" -> throw conflict("ALREADY_EXISTS", "This identity or assignment already exists")
            "23503" -> throw conflict("REFERENCE_CONFLICT", "Referenced record is missing or still in use")
            "23514" -> throw conflict("INVALID_RELATIONSHIP", "The change violates a relationship or value constraint")
            "40P01", "40001" -> throw conflict("RETRY_TRANSACTION", "Concurrent change; reload and retry")
            else -> throw e
        }
    }
}

internal fun Connection.requireViableOwner(seller: String) {
    if (queryOne("SELECT active FROM sellers WHERE id=?", seller) { it.getBoolean(1) } != true) return
    if (count("""SELECT count(*) FROM seller_memberships m JOIN users u ON u.id=m.user_id
        WHERE m.seller_id=? AND m.role='OWNER' AND m.active AND u.status='ACTIVE'""", seller) == 0L)
        throw conflict("LAST_ACTIVE_OWNER", "An active seller must retain an active owner; suspend the seller first")
}
internal fun Connection.sellerState(id: String): SellerState = queryOne("SELECT * FROM sellers WHERE id=?", id) {
    SellerState(id, it.getString("name"), it.getString("description"), it.getBoolean("active"), it.getLong("version"))
} ?: throw notFound()
internal fun audit(db: Connection, actor: AuthenticatedPrincipal, admin: Boolean, action: String,
                   target: String, id: String, seller: String? = null, reason: String? = null) =
    recordAudit(db, actor.userId, if (admin) "ADMIN" else "SELLER", action, target, id, seller,
        if (reason == null) emptyMap() else mapOf("reason" to reason))
