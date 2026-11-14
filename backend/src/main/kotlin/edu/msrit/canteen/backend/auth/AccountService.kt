package edu.msrit.canteen.backend.auth

import edu.msrit.canteen.backend.api.CustomerAffiliation
import edu.msrit.canteen.backend.api.LoginRequest
import edu.msrit.canteen.backend.api.LoginResponse
import edu.msrit.canteen.backend.api.ProfileDto
import edu.msrit.canteen.backend.api.RegisterRequest
import edu.msrit.canteen.backend.api.SellerMembershipDto
import edu.msrit.canteen.backend.api.SellerRole
import edu.msrit.canteen.backend.api.UserIdentityDto
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.transaction
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

class ApiProblem(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)

/** All identity and permission data is database-backed. HTTP never supplies a role. */
class AccountService(private val context: BackendContext) {
    private val random = SecureRandom()

    fun register(request: RegisterRequest): UserIdentityDto {
        val name = validName(request.fullName)
        val email = validEmail(request.email)
        validPassword(request.password)
        val department = validDepartment(request.department)
        val id = UUID.randomUUID()
        try {
            context.transaction { db ->
                db.prepareStatement(
                    """INSERT INTO users (id,email,password_hash,full_name,affiliation,department)
                       VALUES (?,?,?,?,?,?)"""
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setString(2, email)
                    stmt.setString(3, PasswordHasher.hash(request.password))
                    stmt.setString(4, name)
                    stmt.setString(5, request.affiliation.name)
                    stmt.setString(6, department)
                    stmt.executeUpdate()
                }
            }
        } catch (error: SQLException) {
            if (error.sqlState == "23505") throw ApiProblem(HttpStatusCode.Conflict, "EMAIL_IN_USE", "This email is already registered")
            throw error
        }
        return UserIdentityDto(id.toString(), email, name, request.affiliation, false, emptyList())
    }

    /** Null means a deliberately generic credentials failure, including inactive accounts. */
    fun login(request: LoginRequest): LoginResponse? {
        val email = request.email.trim().lowercase()
        if (email.length !in 3..320 || request.password.length > 1024) return null
        return context.transaction { db ->
            val user = db.prepareStatement(
                "SELECT id,email,password_hash,full_name,affiliation,department,status,is_platform_admin FROM users WHERE email = ?"
            ).use { stmt ->
                stmt.setString(1, email)
                stmt.executeQuery().use { rows -> if (rows.next()) readUser(rows) else null }
            } ?: return@transaction null
            if (!PasswordHasher.verify(request.password, user.passwordHash) || user.status != "ACTIVE") return@transaction null
            val tokenBytes = ByteArray(32).also(random::nextBytes)
            val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
            val now = context.clock.instant()
            val expiry = now.plus(24, ChronoUnit.HOURS)
            val sessionId = UUID.randomUUID()
            db.prepareStatement(
                "INSERT INTO sessions (id,user_id,token_hash,created_at,expires_at) VALUES (?,?,?,?,?)"
            ).use { stmt ->
                stmt.setObject(1, sessionId)
                stmt.setObject(2, user.id)
                stmt.setBytes(3, MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))
                stmt.setTimestamp(4, Timestamp.from(now))
                stmt.setTimestamp(5, Timestamp.from(expiry))
                stmt.executeUpdate()
            }
            LoginResponse(token, expiry.toString(), identity(db, user, sessionId))
        }
    }

    fun logout(principal: AuthenticatedPrincipal): Boolean = context.transaction { db ->
        val live = liveUser(db, principal, lock = true) ?: return@transaction false
        db.prepareStatement("UPDATE sessions SET revoked_at = ? WHERE id = ? AND user_id = ? AND revoked_at IS NULL").use { stmt ->
            stmt.setTimestamp(1, Timestamp.from(context.clock.instant()))
            stmt.setObject(2, principal.sessionId)
            stmt.setObject(3, live.id)
            stmt.executeUpdate() == 1
        }
    }

    fun me(principal: AuthenticatedPrincipal): UserIdentityDto? = context.dataSource.connection.use { db ->
        liveUser(db, principal)?.let { identity(db, it, principal.sessionId) }
    }

    fun profile(principal: AuthenticatedPrincipal): ProfileDto? = context.dataSource.connection.use { db ->
        liveUser(db, principal)?.toProfile()
    }

    /** Missing fields leave data unchanged; explicit null clears department, but not name/affiliation. */
    fun updateProfile(principal: AuthenticatedPrincipal, patch: ProfilePatch): ProfileDto? = context.transaction { db ->
        val user = liveUser(db, principal, lock = true) ?: return@transaction null
        val name = if (patch.hasName) validName(patch.name ?: invalid("fullName cannot be null")) else user.fullName
        val affiliation = if (patch.hasAffiliation) patch.affiliation
            ?: invalid("affiliation cannot be null") else user.affiliation
        val department = if (patch.hasDepartment) validDepartment(patch.department) else user.department
        if (!patch.hasName && !patch.hasAffiliation && !patch.hasDepartment) invalid("Provide at least one profile field")
        db.prepareStatement("UPDATE users SET full_name=?, affiliation=?, department=?, updated_at=? WHERE id=?").use { stmt ->
            stmt.setString(1, name)
            stmt.setString(2, affiliation?.name)
            stmt.setString(3, department)
            stmt.setTimestamp(4, Timestamp.from(context.clock.instant()))
            stmt.setObject(5, user.id)
            stmt.executeUpdate()
        }
        user.copy(fullName = name, affiliation = affiliation, department = department).toProfile()
    }

    private fun liveUser(db: Connection, principal: AuthenticatedPrincipal, lock: Boolean = false): UserRow? =
        db.prepareStatement(
            """SELECT u.id,u.email,u.password_hash,u.full_name,u.affiliation,u.department,u.status,u.is_platform_admin
               FROM users u JOIN sessions s ON s.user_id=u.id
               WHERE u.id=? AND u.status='ACTIVE' AND s.id=? AND s.revoked_at IS NULL AND s.expires_at>?
               ${if (lock) "FOR UPDATE OF u,s" else ""}"""
        ).use { stmt ->
            stmt.setObject(1, principal.userId)
            stmt.setObject(2, principal.sessionId)
            stmt.setTimestamp(3, Timestamp.from(context.clock.instant()))
            stmt.executeQuery().use { rows -> if (rows.next()) readUser(rows) else null }
        }

    private fun identity(db: Connection, user: UserRow, sessionId: UUID): UserIdentityDto {
        val memberships = db.prepareStatement(
            """SELECT m.seller_id,m.role FROM seller_memberships m JOIN sellers s ON s.id=m.seller_id
               WHERE m.user_id=? AND m.active AND s.active ORDER BY m.seller_id"""
        ).use { stmt ->
            stmt.setObject(1, user.id)
            stmt.executeQuery().use { rows -> buildList {
                while (rows.next()) add(rows.getString(1) to SellerRole.valueOf(rows.getString(2)))
            } }
        }
        // Use the existing live capability evaluator; do not infer privileges from a role label.
        val principal = AuthenticatedPrincipal(user.id, sessionId, user.email, user.fullName,
            user.affiliation, user.platformAdmin)
        return UserIdentityDto(user.id.toString(), user.email, user.fullName, user.affiliation,
            user.platformAdmin, memberships.map { (sellerId, role) ->
                SellerMembershipDto(sellerId, role, SellerCapabilities.ALL.sorted().filter { capability ->
                    context.authorization.hasSellerCapability(db, principal, sellerId, capability)
                })
            }.filter { it.capabilities.isNotEmpty() })
    }

    private fun readUser(rows: ResultSet) = UserRow(
        rows.getObject("id", UUID::class.java), rows.getString("email"), rows.getString("password_hash"),
        rows.getString("full_name"), rows.getString("affiliation")?.let(CustomerAffiliation::valueOf),
        rows.getString("department"), rows.getString("status"), rows.getBoolean("is_platform_admin"))

    private fun validName(value: String): String = value.trim().replace(Regex("\\s+"), " ").also {
        if (it.length !in 2..200 || it.any(Char::isISOControl)) invalid("fullName must be 2–200 readable characters")
    }

    private fun validEmail(value: String): String = value.trim().lowercase().also {
        if (it.length !in 3..320 || !EMAIL.matches(it) || ".." in it) invalid("A valid email address is required")
    }

    private fun validPassword(value: String) {
        if (value.length !in 10..128 || !value.any(Char::isLetter) || !value.any(Char::isDigit))
            invalid("Password must be 10–128 characters with a letter and a digit")
    }

    private fun validDepartment(value: String?): String? = value?.trim()?.also {
        if (it.length !in 1..120 || it.any(Char::isISOControl)) invalid("department must be 1–120 readable characters")
    }

    private fun invalid(message: String): Nothing = throw ApiProblem(HttpStatusCode.BadRequest, "INVALID_INPUT", message)

    private data class UserRow(val id: UUID, val email: String, val passwordHash: String, val fullName: String,
                               val affiliation: CustomerAffiliation?, val department: String?, val status: String,
                               val platformAdmin: Boolean) {
        fun toProfile() = ProfileDto(id.toString(), email, fullName, affiliation, department)
    }

    companion object { private val EMAIL = Regex("^[a-z0-9._%+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,63}$") }
}

data class ProfilePatch(
    val hasName: Boolean, val name: String?,
    val hasAffiliation: Boolean, val affiliation: CustomerAffiliation?,
    val hasDepartment: Boolean, val department: String?,
)
