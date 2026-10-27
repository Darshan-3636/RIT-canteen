package edu.msrit.canteen.backend.auth

import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.transaction
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.util.UUID

/** Public, disposable local-demo identities. Never invoked unless local seed mode is explicit. */
object DemoIdentitySeed {
    private data class Persona(val email: String, val password: String, val name: String,
                               val affiliation: String? = null, val admin: Boolean = false,
                               val sellerId: String? = null, val role: String? = null,
                               val supervisorEmail: String? = null, val grants: Set<String> = emptySet())

    private val managerGrants = setOf(SellerCapabilities.BULK_VIEW, SellerCapabilities.BULK_RESPOND,
        SellerCapabilities.OPERATIONS_HISTORY, SellerCapabilities.CATALOG_EDIT,
        SellerCapabilities.AVAILABILITY_EDIT, SellerCapabilities.HOURS_EDIT,
        SellerCapabilities.PROMOTIONS_MANAGE, SellerCapabilities.EMPLOYEES_MANAGE,
        SellerCapabilities.ANALYTICS_VIEW)

    private val personas = listOf(
        Persona("student.arya@demo.ritcanteen.test", "RitDemo!Student01", "Arya Rao", "STUDENT"),
        Persona("student.kiran@demo.ritcanteen.test", "RitDemo!Student02", "Kiran Shah", "STUDENT"),
        Persona("faculty.maya@demo.ritcanteen.test", "RitDemo!Faculty01", "Dr Maya Iyer", "FACULTY"),
        Persona("owner.main@demo.ritcanteen.test", "RitDemo!OwnerMain01", "Main Canteen Owner",
            sellerId = "main-canteen", role = "OWNER"),
        Persona("owner.secondary@demo.ritcanteen.test", "RitDemo!OwnerOther01", "North Counter Owner",
            sellerId = "north-counter", role = "OWNER"),
        Persona("manager.main@demo.ritcanteen.test", "RitDemo!Manager01", "Main Canteen Manager",
            sellerId = "main-canteen", role = "MANAGER",
            supervisorEmail = "owner.main@demo.ritcanteen.test", grants = managerGrants),
        Persona("staff.main@demo.ritcanteen.test", "RitDemo!Staff01", "Main Canteen Staff",
            sellerId = "main-canteen", role = "STAFF",
            supervisorEmail = "manager.main@demo.ritcanteen.test", grants = setOf(SellerCapabilities.BULK_VIEW)),
        Persona("admin.platform@demo.ritcanteen.test", "RitDemo!Admin01", "Platform Administrator", admin = true),
    )

    fun seed(context: BackendContext) {
        check(context.config.environment == RuntimeEnvironment.LOCAL && context.config.demoSeedEnabled)
        context.transaction { db ->
            for (persona in personas) {
                val userId = demoId("user", persona.email)
                val created = db.prepareStatement(
                    """INSERT INTO users (id,email,password_hash,full_name,affiliation,is_platform_admin)
                       VALUES (?,?,?,?,?,?) ON CONFLICT (email) DO NOTHING"""
                ).use { stmt ->
                    stmt.setObject(1, userId)
                    stmt.setString(2, persona.email)
                    stmt.setString(3, PasswordHasher.hash(persona.password))
                    stmt.setString(4, persona.name)
                    stmt.setString(5, persona.affiliation)
                    stmt.setBoolean(6, persona.admin)
                    stmt.executeUpdate() == 1
                }
                // Never add elevated rights to an existing email, even if it matches a fixture.
                // A restart must not restore a removed membership or direct grant.
                if (!created || persona.sellerId == null) continue
                val supervisorId = persona.supervisorEmail?.let { membershipId(db, it, persona.sellerId) }
                if (persona.supervisorEmail != null && supervisorId == null) continue
                val memberId = demoId("membership", persona.email)
                val inserted = db.prepareStatement(
                    """INSERT INTO seller_memberships
                       (id,user_id,seller_id,role,supervisor_membership_id)
                       VALUES (?,?,?,?,?) ON CONFLICT (user_id,seller_id) DO NOTHING"""
                ).use { stmt ->
                    stmt.setObject(1, memberId)
                    stmt.setObject(2, userId)
                    stmt.setString(3, persona.sellerId)
                    stmt.setString(4, persona.role)
                    stmt.setObject(5, supervisorId)
                    stmt.executeUpdate() == 1
                }
                if (inserted) db.prepareStatement(
                    "INSERT INTO seller_membership_capabilities (membership_id,capability) VALUES (?,?)"
                ).use { stmt ->
                    persona.grants.forEach { grant ->
                        stmt.setObject(1, memberId)
                        stmt.setString(2, grant)
                        stmt.addBatch()
                    }
                    if (persona.grants.isNotEmpty()) stmt.executeBatch()
                }
            }
        }
    }

    private fun membershipId(db: Connection, email: String, sellerId: String): UUID? = db.prepareStatement(
        """SELECT m.id FROM seller_memberships m JOIN users u ON u.id=m.user_id
           WHERE u.email=? AND m.seller_id=?"""
    ).use { stmt ->
        stmt.setString(1, email)
        stmt.setString(2, sellerId)
        stmt.executeQuery().use { rows -> if (rows.next()) rows.getObject(1, UUID::class.java) else null }
    }

    private fun demoId(kind: String, email: String): UUID = UUID.nameUUIDFromBytes(
        "rit-canteen-local-demo:$kind:$email".toByteArray(StandardCharsets.UTF_8))
}
