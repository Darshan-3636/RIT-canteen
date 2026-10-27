package edu.msrit.canteen.backend.auth

import edu.msrit.canteen.backend.api.CustomerAffiliation
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

data class AuthenticatedPrincipal(
    val userId: UUID,
    val sessionId: UUID,
    val email: String,
    val fullName: String,
    val affiliation: CustomerAffiliation?,
    val platformAdmin: Boolean,
)

object SellerCapabilities {
    const val BULK_VIEW = "bulk.view"
    const val BULK_RESPOND = "bulk.respond"
    const val OPERATIONS_HISTORY = "operations.history"
    const val CATALOG_EDIT = "catalog.edit"
    const val AVAILABILITY_EDIT = "availability.edit"
    const val HOURS_EDIT = "hours.edit"
    const val PROMOTIONS_MANAGE = "promotions.manage"
    const val EMPLOYEES_MANAGE = "employees.manage"
    const val PROFILE_EDIT = "profile.edit"
    const val ANALYTICS_VIEW = "analytics.view"
    const val REPORTS_EXPORT = "reports.export"

    val ALL: Set<String> = setOf(BULK_VIEW, BULK_RESPOND, OPERATIONS_HISTORY, CATALOG_EDIT,
        AVAILABILITY_EDIT, HOURS_EDIT, PROMOTIONS_MANAGE, EMPLOYEES_MANAGE, PROFILE_EDIT,
        ANALYTICS_VIEW, REPORTS_EXPORT)
}

/** Every call reads the live session/user/membership state, never a client-declared role. */
class AuthorizationService(private val dataSource: DataSource, private val clock: Clock) {
    fun authenticateBearer(rawToken: String): AuthenticatedPrincipal? {
        if (rawToken.length !in 32..512 || rawToken.any { it.isWhitespace() }) return null
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray(StandardCharsets.UTF_8))
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """SELECT s.id AS session_id, u.id AS user_id, u.email, u.full_name,
                          u.affiliation, u.is_platform_admin
                   FROM sessions s JOIN users u ON u.id = s.user_id
                   WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ?
                     AND u.status = 'ACTIVE'"""
            ).use { statement ->
                statement.setBytes(1, hash)
                statement.setTimestamp(2, Timestamp.from(clock.instant()))
                statement.executeQuery().use { rows ->
                    if (!rows.next()) null else AuthenticatedPrincipal(
                        userId = rows.getObject("user_id", UUID::class.java),
                        sessionId = rows.getObject("session_id", UUID::class.java),
                        email = rows.getString("email"),
                        fullName = rows.getString("full_name"),
                        affiliation = rows.getString("affiliation")?.let(CustomerAffiliation::valueOf),
                        platformAdmin = rows.getBoolean("is_platform_admin"),
                    )
                }
            }
        }
    }

    fun isPlatformAdmin(principal: AuthenticatedPrincipal): Boolean = dataSource.connection.use { connection ->
        isPlatformAdmin(connection, principal)
    }

    /** Recheck in the same transaction as a guarded administrator write. */
    fun isPlatformAdmin(connection: Connection, principal: AuthenticatedPrincipal): Boolean =
        connection.prepareStatement(
            """SELECT u.is_platform_admin FROM users u JOIN sessions s ON s.user_id = u.id
               WHERE u.id = ? AND u.status = 'ACTIVE' AND s.id = ? AND s.revoked_at IS NULL
                 AND s.expires_at > ?"""
        )
            .use { statement ->
                statement.setObject(1, principal.userId)
                statement.setObject(2, principal.sessionId)
                statement.setTimestamp(3, Timestamp.from(clock.instant()))
                statement.executeQuery().use { rows -> rows.next() && rows.getBoolean(1) }
            }

    fun hasSellerCapability(principal: AuthenticatedPrincipal, sellerId: String, capability: String): Boolean =
        dataSource.connection.use { connection ->
            hasSellerCapability(connection, principal, sellerId, capability)
        }

    /** Call this overload inside [edu.msrit.canteen.backend.core.transaction] for guarded writes. */
    fun hasSellerCapability(connection: Connection, principal: AuthenticatedPrincipal,
                            sellerId: String, capability: String): Boolean {
        if (capability !in SellerCapabilities.ALL) return false
        if (!hasLiveSession(connection, principal)) return false
        val membershipId = connection.prepareStatement(
            """SELECT m.id FROM seller_memberships m JOIN sellers s ON s.id = m.seller_id
               JOIN users u ON u.id = m.user_id
               WHERE m.user_id = ? AND m.seller_id = ? AND m.active AND s.active AND u.status = 'ACTIVE'"""
        ).use { statement ->
            statement.setObject(1, principal.userId)
            statement.setString(2, sellerId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getObject(1, UUID::class.java) else null }
        } ?: return false
        return capability in effectiveCapabilities(connection, membershipId, sellerId, mutableSetOf())
    }

    private fun hasLiveSession(connection: Connection, principal: AuthenticatedPrincipal): Boolean =
        connection.prepareStatement(
            """SELECT 1 FROM sessions s JOIN users u ON u.id = s.user_id
               WHERE s.id = ? AND s.user_id = ? AND s.revoked_at IS NULL AND s.expires_at > ?
                 AND u.status = 'ACTIVE'"""
        ).use { statement ->
            statement.setObject(1, principal.sessionId)
            statement.setObject(2, principal.userId)
            statement.setTimestamp(3, Timestamp.from(clock.instant()))
            statement.executeQuery().use { it.next() }
        }

    private fun effectiveCapabilities(connection: Connection, membershipId: UUID, sellerId: String,
                                      visited: MutableSet<UUID>): Set<String> {
        if (!visited.add(membershipId)) return emptySet()
        val member = connection.prepareStatement(
            "SELECT seller_id, role, supervisor_membership_id, active FROM seller_memberships WHERE id = ?"
        ).use { statement ->
            statement.setObject(1, membershipId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else MembershipRow(
                    rows.getString(1), rows.getString(2), rows.getObject(3, UUID::class.java), rows.getBoolean(4))
            }
        } ?: return emptySet()
        if (!member.active || member.sellerId != sellerId) return emptySet()
        if (member.role == "OWNER") return if (member.supervisorId == null) SellerCapabilities.ALL else emptySet()
        val supervisorId = member.supervisorId ?: return emptySet()
        val supervisorRole = connection.prepareStatement(
            "SELECT role FROM seller_memberships WHERE id = ?"
        ).use { statement ->
            statement.setObject(1, supervisorId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
        if ((member.role == "MANAGER" && supervisorRole != "OWNER") ||
            (member.role == "STAFF" && supervisorRole != "MANAGER")) return emptySet()
        val inherited = effectiveCapabilities(connection, supervisorId, sellerId, visited)
        if (inherited.isEmpty()) return emptySet()
        val direct = connection.prepareStatement(
            "SELECT capability FROM seller_membership_capabilities WHERE membership_id = ?"
        ).use { statement ->
            statement.setObject(1, membershipId)
            statement.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
        }
        return direct intersect inherited
    }

    private data class MembershipRow(
        val sellerId: String, val role: String, val supervisorId: UUID?, val active: Boolean)
}
