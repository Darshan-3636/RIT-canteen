package edu.msrit.canteen.backend.features.engagement

import edu.msrit.canteen.backend.api.respondApiError
import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A feature-level rejection rendered with the frozen error envelope. */
internal class ApiFailure(val status: HttpStatusCode, val code: String, override val message: String) :
    RuntimeException(message)

internal fun badRequest(code: String, message: String) = ApiFailure(HttpStatusCode.BadRequest, code, message)
internal fun notFound(message: String = "The requested resource was not found") =
    ApiFailure(HttpStatusCode.NotFound, "NOT_FOUND", message)
internal fun conflict(code: String, message: String) = ApiFailure(HttpStatusCode.Conflict, code, message)

/** Maps [ApiFailure]; anything else still reaches the core StatusPages handler without a stack trace. */
internal suspend inline fun ApplicationCall.handled(block: () -> Unit) {
    try {
        block()
    } catch (failure: ApiFailure) {
        respondApiError(failure.status, failure.code, failure.message)
    }
}

internal suspend inline fun <reified T : Any> ApplicationCall.receiveBody(): T = try {
    receive<T>()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    throw badRequest("INVALID_REQUEST_BODY", "The request body is not valid JSON for this operation")
}

internal fun ApplicationCall.pathUuid(name: String): UUID =
    parameters[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw notFound()

private val slugPattern = Regex("^[a-z0-9][a-z0-9-]{1,62}$")
internal fun isSlug(value: String) = slugPattern.matches(value)

internal data class Page(val limit: Int, val offset: Int)

@Serializable
data class PageDto<T>(val items: List<T>, val limit: Int, val offset: Int, val total: Long)

internal fun ApplicationCall.page(): Page {
    val query = request.queryParameters
    val limit = query["limit"]?.let { it.toIntOrNull() ?: -1 } ?: 25
    val offset = query["offset"]?.let { it.toIntOrNull() ?: -1 } ?: 0
    if (limit !in 1..100) throw badRequest("INVALID_PAGINATION", "limit must be between 1 and 100")
    if (offset !in 0..10_000) throw badRequest("INVALID_PAGINATION", "offset must be between 0 and 10000")
    return Page(limit, offset)
}

internal fun ApplicationCall.optionalDate(name: String): LocalDate? = request.queryParameters[name]?.let {
    runCatching { LocalDate.parse(it) }.getOrNull()
        ?: throw badRequest("INVALID_DATE", "$name must be an ISO date such as 2026-10-27")
}

internal fun <T> BackendContext.read(block: (Connection) -> T): T = dataSource.connection.use(block)

/** Same-transaction recheck for customer-scoped writes; the core only exposes seller/admin connection checks. */
internal fun BackendContext.requireLiveSession(connection: Connection, principal: AuthenticatedPrincipal) {
    connection.queryOne(
        """SELECT 1 FROM sessions s JOIN users u ON u.id = s.user_id
           WHERE s.id = ? AND s.user_id = ? AND s.revoked_at IS NULL AND s.expires_at > ? AND u.status = 'ACTIVE'""",
        principal.sessionId, principal.userId, clock.instant(),
    ) { true } ?: throw ApiFailure(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "A valid bearer session is required")
}

internal fun BackendContext.requireSellerInTx(connection: Connection, principal: AuthenticatedPrincipal,
                                              sellerId: String, capability: String) {
    if (!authorization.hasSellerCapability(connection, principal, sellerId, capability)) {
        throw ApiFailure(HttpStatusCode.Forbidden, "SELLER_CAPABILITY_REQUIRED", "This seller action is not permitted")
    }
}

internal fun BackendContext.requireAdminInTx(connection: Connection, principal: AuthenticatedPrincipal) {
    if (!authorization.isPlatformAdmin(connection, principal)) {
        throw ApiFailure(HttpStatusCode.Forbidden, "PLATFORM_ADMIN_REQUIRED", "Platform administrator access is required")
    }
}

/** Detail values must be identifiers or enum wires only — never ticket bodies, tokens or passwords. */
internal fun recordAudit(connection: Connection, actor: UUID, actorRole: String, action: String,
                         targetType: String, targetId: String, sellerId: String? = null,
                         detail: Map<String, String> = emptyMap()) {
    connection.update(
        """INSERT INTO admin_audit_events (id, actor_user_id, actor_role, action, target_type, target_id, seller_id, detail)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)""",
        UUID.randomUUID(), actor, actorRole, action, targetType, targetId, sellerId,
        JsonObject(detail.mapValues { JsonPrimitive(it.value) }).toString(),
    )
}

internal fun Connection.update(sql: String, vararg args: Any?): Int =
    prepareStatement(sql).use { it.bind(args); it.executeUpdate() }

internal fun <T> Connection.query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { statement ->
        statement.bind(args)
        statement.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
    }

internal fun <T> Connection.queryOne(sql: String, vararg args: Any?, map: (ResultSet) -> T): T? =
    query(sql, *args, map = map).firstOrNull()

internal fun Connection.count(sql: String, vararg args: Any?): Long = queryOne(sql, *args) { it.getLong(1) } ?: 0

private fun PreparedStatement.bind(args: Array<out Any?>) = args.forEachIndexed { index, value ->
    when (value) {
        is Instant -> setTimestamp(index + 1, Timestamp.from(value))
        is LocalDate -> setObject(index + 1, value)
        else -> setObject(index + 1, value)
    }
}

internal fun ResultSet.uuid(column: String): UUID = getObject(column, UUID::class.java)
internal fun ResultSet.instant(column: String): String? = getTimestamp(column)?.toInstant()?.toString()

/** Escapes LIKE wildcards so a user-supplied search term matches literally. */
internal fun likePattern(term: String) =
    "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
