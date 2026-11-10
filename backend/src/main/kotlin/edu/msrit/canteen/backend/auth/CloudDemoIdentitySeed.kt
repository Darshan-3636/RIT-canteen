package edu.msrit.canteen.backend.auth

import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.BackendConfig.Companion.RESERVED_LOCAL_DEMO_EMAILS
import edu.msrit.canteen.backend.core.transaction
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Creates two private, least-privilege demo identities once; never grants rights to an email collision. */
object CloudDemoIdentitySeed {
    fun seed(context: BackendContext) {
        check(context.config.environment == RuntimeEnvironment.CLOUD_DEMO && context.config.cloudDemoSeedEnabled)
        val customerEmail = requireNotNull(context.config.cloudDemoCustomerEmail).lowercase()
        val customerPassword = requireNotNull(context.config.cloudDemoCustomerPassword)
        val ownerEmail = requireNotNull(context.config.cloudDemoOwnerEmail).lowercase()
        val ownerPassword = requireNotNull(context.config.cloudDemoOwnerPassword)
        check(customerEmail !in RESERVED_LOCAL_DEMO_EMAILS && ownerEmail !in RESERVED_LOCAL_DEMO_EMAILS) {
            "Cloud demo account configuration conflicts with reserved local fixture identities"
        }
        context.transaction { db ->
            val oldFixtureCount = db.prepareStatement(
                "SELECT count(*) FROM users WHERE email = ANY (?)"
            ).use { stmt ->
                stmt.setArray(1, db.createArrayOf("text", RESERVED_LOCAL_DEMO_EMAILS.toTypedArray()))
                stmt.executeQuery().use { rows -> rows.next(); rows.getLong(1) }
            }
            check(oldFixtureCount == 0L) {
                "Cloud demo database contains public local fixture identities; use a fresh demo database"
            }
            ensureUser(db, customerEmail, customerPassword, "Demo Customer", "STUDENT", null)
            ensureUser(db, ownerEmail, ownerPassword, "Demo Main Canteen Owner", null, "main-canteen")
        }
    }

    private fun ensureUser(db: java.sql.Connection, email: String, password: String,
                           name: String, affiliation: String?, sellerId: String?) {
        val userId = stableId("user", email)
        val existing = db.prepareStatement(
            "SELECT id,status,is_platform_admin,full_name,affiliation FROM users WHERE email=?"
        ).use { stmt ->
            stmt.setString(1, email)
            stmt.executeQuery().use { rows ->
                if (rows.next()) ExistingUser(rows.getObject("id", UUID::class.java), rows.getString("status"),
                    rows.getBoolean("is_platform_admin"), rows.getString("full_name"), rows.getString("affiliation"))
                else null
            }
        }
        if (existing == null) {
            db.prepareStatement(
                """INSERT INTO users (id,email,password_hash,full_name,affiliation,is_platform_admin)
                   VALUES (?,?,?,?,?,FALSE)"""
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setString(2, email)
                stmt.setString(3, PasswordHasher.hash(password))
                stmt.setString(4, name)
                stmt.setString(5, affiliation)
                stmt.executeUpdate()
            }
            if (sellerId != null) {
                db.prepareStatement(
                    """INSERT INTO seller_memberships (id,user_id,seller_id,role,supervisor_membership_id)
                       VALUES (?, ?, ?, 'OWNER', NULL)"""
                ).use { stmt ->
                    stmt.setObject(1, stableId("membership", email))
                    stmt.setObject(2, userId)
                    stmt.setString(3, sellerId)
                    check(stmt.executeUpdate() == 1) { "Cloud demo seller membership could not be created" }
                }
            }
            return
        }

        // Deterministic identity + shape makes restarts idempotent. Do not reactivate, rehash, or repair records.
        check(existing.id == userId && existing.status == "ACTIVE" && !existing.platformAdmin &&
            existing.fullName == name && existing.affiliation == affiliation) {
            "Cloud demo account email already belongs to a different or inactive identity; choose a new private address"
        }
        val memberships = db.prepareStatement(
            "SELECT seller_id,role,active,supervisor_membership_id FROM seller_memberships WHERE user_id=?"
        ).use { stmt ->
            stmt.setObject(1, userId)
            stmt.executeQuery().use { rows -> buildList {
                while (rows.next()) add(Membership(rows.getString(1), rows.getString(2), rows.getBoolean(3),
                    rows.getObject(4, UUID::class.java)))
            } }
        }
        val valid = if (sellerId == null) memberships.isEmpty() else memberships == listOf(
            Membership(sellerId, "OWNER", true, null))
        check(valid) { "Cloud demo account membership differs from its expected least-privilege identity; review it privately" }
    }

    private fun stableId(kind: String, email: String): UUID = UUID.nameUUIDFromBytes(
        "rit-canteen-cloud-demo:$kind:$email".toByteArray(StandardCharsets.UTF_8))

    private data class ExistingUser(val id: UUID, val status: String, val platformAdmin: Boolean,
                                    val fullName: String, val affiliation: String?)
    private data class Membership(val sellerId: String, val role: String, val active: Boolean,
                                  val supervisorId: UUID?)
}
