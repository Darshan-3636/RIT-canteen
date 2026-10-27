package edu.msrit.canteen.backend

import edu.msrit.canteen.backend.auth.PasswordHasher
import edu.msrit.canteen.backend.auth.SellerCapabilities
import edu.msrit.canteen.backend.core.BackendBootstrap
import edu.msrit.canteen.backend.core.BackendConfig
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.close
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthIntegrationTest {
    private fun config(seed: Boolean = true): BackendConfig {
        val env = System.getenv()
        assumeTrue(env["RIT_TEST_DATABASE_URL"] != null && env["RIT_TEST_DATABASE_PASSWORD"] != null,
            "Set isolated RIT_TEST_DATABASE_URL/USER/PASSWORD")
        return BackendConfig(RuntimeEnvironment.LOCAL, "127.0.0.1", 8080,
            env.getValue("RIT_TEST_DATABASE_URL"), env["RIT_TEST_DATABASE_USER"] ?: "rit_canteen",
            env.getValue("RIT_TEST_DATABASE_PASSWORD"), seed)
    }

    private suspend fun HttpClient.register(json: String) = post("/api/v1/auth/register") {
        header(HttpHeaders.ContentType, "application/json"); setBody(json)
    }
    private suspend fun HttpClient.login(email: String, password: String) = post("/api/v1/auth/login") {
        header(HttpHeaders.ContentType, "application/json")
        setBody("""{"email":"$email","password":"$password"}""")
    }
    private suspend fun HttpClient.me(token: String) = get("/api/v1/auth/me") {
        header(HttpHeaders.Authorization, "Bearer $token")
    }
    private suspend fun HttpClient.profile(token: String) = get("/api/v1/me/profile") {
        header(HttpHeaders.Authorization, "Bearer $token")
    }
    private suspend fun HttpClient.patchProfile(token: String, json: String) = patch("/api/v1/me/profile") {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.ContentType, "application/json"); setBody(json)
    }
    private suspend fun HttpClient.logout(token: String) = post("/api/v1/auth/logout") {
        header(HttpHeaders.Authorization, "Bearer $token")
    }
    private fun parse(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject
    private fun token(body: String): String = parse(body).getValue("accessToken").jsonPrimitive.content
    private fun errorCode(body: String): String = parse(body).getValue("error").jsonObject.getValue("code").jsonPrimitive.content
    private fun user(body: String): JsonObject = parse(body).getValue("user").jsonObject

    @Test fun allEightPersonasHaveDistinctPersistentIdsAndLiveRoles() {
        val context = BackendBootstrap.open(config())
        try {
            testApplication {
                application { backendModule(context) }
                val personas = listOf(
                    "student.arya@demo.ritcanteen.test" to "RitDemo!Student01",
                    "student.kiran@demo.ritcanteen.test" to "RitDemo!Student02",
                    "faculty.maya@demo.ritcanteen.test" to "RitDemo!Faculty01",
                    "owner.main@demo.ritcanteen.test" to "RitDemo!OwnerMain01",
                    "owner.secondary@demo.ritcanteen.test" to "RitDemo!OwnerOther01",
                    "manager.main@demo.ritcanteen.test" to "RitDemo!Manager01",
                    "staff.main@demo.ritcanteen.test" to "RitDemo!Staff01",
                    "admin.platform@demo.ritcanteen.test" to "RitDemo!Admin01",
                )
                val ids = mutableSetOf<String>()
                for ((email, password) in personas) {
                    val response = client.login(email, password)
                    assertEquals(HttpStatusCode.OK, response.status, email)
                    val body = response.bodyAsText()
                    val identity = user(body)
                    assertEquals(email, identity.getValue("email").jsonPrimitive.content)
                    assertTrue(ids.add(identity.getValue("id").jsonPrimitive.content))
                    assertTrue(token(body).length >= 40)
                    assertTrue(parse(body).getValue("expiresAt").jsonPrimitive.content.endsWith("Z"))
                    val me = client.me(token(body))
                    assertEquals(HttpStatusCode.OK, me.status)
                    assertEquals(identity, user(me.bodyAsText()))
                    assertFalse(me.bodyAsText().contains("accessToken"))
                    val memberships = identity.getValue("memberships").jsonArray
                    when (email) {
                        "owner.main@demo.ritcanteen.test" -> assertEquals("main-canteen", memberships.single().jsonObject.getValue("sellerId").jsonPrimitive.content)
                        "owner.secondary@demo.ritcanteen.test" -> assertEquals("north-counter", memberships.single().jsonObject.getValue("sellerId").jsonPrimitive.content)
                        "manager.main@demo.ritcanteen.test" -> {
                            assertEquals("MANAGER", memberships.single().jsonObject.getValue("role").jsonPrimitive.content)
                            assertFalse(memberships.single().toString().contains("reports.export"))
                        }
                        "staff.main@demo.ritcanteen.test" -> assertEquals(listOf("bulk.view"), memberships.single().jsonObject.getValue("capabilities").jsonArray.map { it.jsonPrimitive.content })
                        "admin.platform@demo.ritcanteen.test" -> {
                            assertEquals("true", identity.getValue("platformAdmin").jsonPrimitive.content)
                            assertTrue(memberships.isEmpty())
                        }
                        else -> assertTrue(memberships.isEmpty())
                    }
                }
                assertEquals(8, ids.size)
                context.dataSource.connection.use { db ->
                    db.createStatement().use { stmt -> stmt.executeQuery("SELECT email,password_hash FROM users WHERE email LIKE '%@demo.ritcanteen.test'").use { rows ->
                        var count = 0
                        while (rows.next()) {
                            count++
                            val password = personas.single { it.first == rows.getString(1) }.second
                            val hash = rows.getString(2)
                            assertNotEquals(password, hash)
                            assertTrue(PasswordHasher.verify(password, hash))
                        }
                        assertEquals(8, count)
                    } }
                }
            }
        } finally { context.close() }
    }

    @Test fun registrationProfileRestartValidationAndTwoIndependentSessions() {
        val email = "phase-b-${UUID.randomUUID()}@example.test"
        val first = BackendBootstrap.open(config())
        var userId: UUID? = null
        try {
            testApplication {
                application { backendModule(first) }
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
                assertEquals(HttpStatusCode.OK, client.get("/ready").status)
                val badEmail = client.register("""{"fullName":"New Student","email":"not-email","password":"ValidPassword01","affiliation":"STUDENT"}""")
                assertEquals(HttpStatusCode.BadRequest, badEmail.status)
                assertEquals("INVALID_INPUT", errorCode(badEmail.bodyAsText()))
                assertEquals(HttpStatusCode.BadRequest, client.register("""{"fullName":"New Student","email":"$email","password":"short","affiliation":"STUDENT"}""").status)
                assertEquals(HttpStatusCode.BadRequest, client.register("""{"fullName":"New Student","email":"$email","password":"ValidPassword01","affiliation":"STUDENT","platformAdmin":true}""").status)
                assertEquals(HttpStatusCode.BadRequest, client.register("""{"fullName":"New Student","email":"$email","password":"ValidPassword01","affiliation":"OWNER"}""").status)
                val created = client.register("""{"fullName":"New Student","email":" ${email.uppercase()} ","password":"ValidPassword01","affiliation":"STUDENT","department":null}""")
                assertEquals(HttpStatusCode.Created, created.status)
                val body = created.bodyAsText()
                assertFalse(body.contains("accessToken"))
                assertEquals(email, user(body).getValue("email").jsonPrimitive.content)
                assertEquals("false", user(body).getValue("platformAdmin").jsonPrimitive.content)
                assertTrue(user(body).getValue("memberships").jsonArray.isEmpty())
                userId = UUID.fromString(user(body).getValue("id").jsonPrimitive.content)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/auth/me").status)
                assertEquals(HttpStatusCode.Conflict, client.register("""{"fullName":"Again","email":"$email","password":"ValidPassword01","affiliation":"STUDENT"}""").status)
                assertEquals(HttpStatusCode.Unauthorized, client.login(email, "wrong-password").status)
                assertEquals(HttpStatusCode.Unauthorized, client.login("unknown@example.test", "ValidPassword01").status)
                val login1 = client.login(email, "ValidPassword01")
                val login2 = client.login(email, "ValidPassword01")
                assertEquals(HttpStatusCode.OK, login1.status)
                val t1 = token(login1.bodyAsText()); val t2 = token(login2.bodyAsText())
                assertNotEquals(t1, t2)
                assertEquals(HttpStatusCode.OK, client.profile(t1).status)
                val patch = client.patchProfile(t1, """{"fullName":"Updated Student","department":"CSE"}""")
                assertEquals(HttpStatusCode.OK, patch.status)
                assertEquals("STUDENT", parse(patch.bodyAsText()).getValue("affiliation").jsonPrimitive.content)
                assertEquals("CSE", parse(patch.bodyAsText()).getValue("department").jsonPrimitive.content)
                assertEquals(HttpStatusCode.BadRequest, client.patchProfile(t1, """{"platformAdmin":true}""").status)
                assertEquals(HttpStatusCode.BadRequest, client.patchProfile(t1, """{"affiliation":null}""").status)
                assertEquals(HttpStatusCode.BadRequest, client.patchProfile(t1, """{}""").status)
                assertEquals(HttpStatusCode.OK, client.patchProfile(t1, """{"department":null}""").status)
                assertTrue(client.profile(t1).bodyAsText().contains("\"department\":null"))
                assertEquals(HttpStatusCode.NoContent, client.logout(t1).status)
                assertEquals(HttpStatusCode.Unauthorized, client.me(t1).status)
                assertEquals(HttpStatusCode.OK, client.me(t2).status)
                val hash = MessageDigest.getInstance("SHA-256").digest(t2.toByteArray())
                first.dataSource.connection.use { db -> db.prepareStatement("UPDATE sessions SET created_at=?, expires_at=? WHERE token_hash=?").use { stmt ->
                    stmt.setTimestamp(1, Timestamp.from(Instant.now().minusSeconds(172_800)))
                    stmt.setTimestamp(2, Timestamp.from(Instant.now().minusSeconds(1)))
                    stmt.setBytes(3, hash); assertEquals(1, stmt.executeUpdate())
                } }
                assertEquals(HttpStatusCode.Unauthorized, client.me(t2).status)
            }
        } finally { first.close() }
        val second = BackendBootstrap.open(config())
        try {
            testApplication {
                application { backendModule(second) }
                val relogin = client.login(email, "ValidPassword01")
                assertEquals(HttpStatusCode.OK, relogin.status)
                val body = client.profile(token(relogin.bodyAsText())).bodyAsText()
                assertEquals("Updated Student", parse(body).getValue("fullName").jsonPrimitive.content)
                assertTrue(body.contains("\"department\":null"))
                assertEquals(userId.toString(), parse(body).getValue("id").jsonPrimitive.content)
                second.dataSource.connection.use { db -> db.prepareStatement("UPDATE users SET status='DISABLED' WHERE id=?").use { stmt ->
                    stmt.setObject(1, userId); stmt.executeUpdate()
                } }
                assertEquals(HttpStatusCode.Unauthorized, client.me(token(relogin.bodyAsText())).status)
                assertEquals(HttpStatusCode.Unauthorized, client.login(email, "ValidPassword01").status)
            }
        } finally {
            second.dataSource.connection.use { db ->
                db.prepareStatement("DELETE FROM sessions WHERE user_id=?").use { it.setObject(1, userId); it.executeUpdate() }
                db.prepareStatement("DELETE FROM users WHERE id=?").use { it.setObject(1, userId); it.executeUpdate() }
            }
            second.close()
        }
    }

    @Test fun delegatedCapabilitiesAndSeedAreRevocationSafeAcrossRestart() {
        val context = BackendBootstrap.open(config())
        try {
            testApplication {
                application { backendModule(context) }
                val staffToken = token(client.login("staff.main@demo.ritcanteen.test", "RitDemo!Staff01").bodyAsText())
                val northToken = token(client.login("owner.secondary@demo.ritcanteen.test", "RitDemo!OwnerOther01").bodyAsText())
                val staff = assertNotNull(context.authorization.authenticateBearer(staffToken))
                val north = assertNotNull(context.authorization.authenticateBearer(northToken))
                assertTrue(context.authorization.hasSellerCapability(staff, "main-canteen", SellerCapabilities.BULK_VIEW))
                assertFalse(context.authorization.hasSellerCapability(north, "main-canteen", SellerCapabilities.BULK_VIEW))
                assertFalse(context.authorization.hasSellerCapability(staff, "main-canteen", "unknown.capability"))
                context.dataSource.connection.use { db -> db.createStatement().use { it.executeUpdate(
                    "UPDATE users SET status='DISABLED' WHERE email='manager.main@demo.ritcanteen.test'") } }
                assertFalse(context.authorization.hasSellerCapability(staff, "main-canteen", SellerCapabilities.BULK_VIEW))
                assertTrue(user(client.me(staffToken).bodyAsText()).getValue("memberships").jsonArray.isEmpty())
                context.dataSource.connection.use { db -> db.createStatement().use { it.executeUpdate(
                    "UPDATE users SET status='ACTIVE' WHERE email='manager.main@demo.ritcanteen.test'") } }
                assertTrue(context.authorization.hasSellerCapability(staff, "main-canteen", SellerCapabilities.BULK_VIEW))
                context.dataSource.connection.use { db -> db.createStatement().use { it.executeUpdate(
                    "DELETE FROM seller_membership_capabilities WHERE capability='bulk.view' AND membership_id IN (SELECT m.id FROM seller_memberships m JOIN users u ON u.id=m.user_id WHERE u.email='staff.main@demo.ritcanteen.test')") } }
                assertFalse(context.authorization.hasSellerCapability(staff, "main-canteen", SellerCapabilities.BULK_VIEW))
            }
        } finally { context.close() }
        val restarted = BackendBootstrap.open(config())
        try {
            val count = restarted.dataSource.connection.use { db -> db.createStatement().use { stmt ->
                stmt.executeQuery("SELECT count(*) FROM seller_membership_capabilities WHERE capability='bulk.view' AND membership_id IN (SELECT m.id FROM seller_memberships m JOIN users u ON u.id=m.user_id WHERE u.email='staff.main@demo.ritcanteen.test')").use { it.next(); it.getInt(1) }
            } }
            assertEquals(0, count)
        } finally {
            restarted.dataSource.connection.use { db -> db.createStatement().use { it.executeUpdate(
                "INSERT INTO seller_membership_capabilities (membership_id,capability) SELECT m.id,'bulk.view' FROM seller_memberships m JOIN users u ON u.id=m.user_id WHERE u.email='staff.main@demo.ritcanteen.test' ON CONFLICT DO NOTHING") } }
            restarted.close()
        }
    }

    @Test fun nonSeedTestEnvironmentStartsWithNoDemoIdentities() {
        val base = config(seed = false)
        val schema = "phase_b_" + UUID.randomUUID().toString().replace("-", "")
        val admin = BackendBootstrap.open(base)
        try {
            admin.dataSource.connection.use { db -> db.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
            val separator = if ('?' in base.databaseUrl) "&" else "?"
            val isolated = base.copy(environment = RuntimeEnvironment.TEST,
                databaseUrl = base.databaseUrl + separator + "currentSchema=$schema")
            val context = BackendBootstrap.open(isolated)
            try {
                context.dataSource.connection.use { db -> db.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT count(*) FROM users").use { rows -> rows.next(); assertEquals(0, rows.getInt(1)) }
                    stmt.executeQuery("SELECT count(*) FROM sellers").use { rows -> rows.next(); assertEquals(0, rows.getInt(1)) }
                } }
            } finally { context.close() }
        } finally {
            admin.dataSource.connection.use { db -> db.createStatement().use { it.execute("DROP SCHEMA IF EXISTS $schema CASCADE") } }
            admin.close()
        }
    }

    @Test fun repeatSeedPreservesEditedProfilePasswordAndUserId() {
        val email = "student.arya@demo.ritcanteen.test"
        val first = BackendBootstrap.open(config())
        var originalHash = ""
        var originalName = ""
        var originalId: UUID? = null
        try {
            first.dataSource.connection.use { db -> db.prepareStatement(
                "SELECT id,full_name,password_hash FROM users WHERE email=?"
            ).use { stmt ->
                stmt.setString(1, email)
                stmt.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    originalId = rows.getObject(1, UUID::class.java)
                    originalName = rows.getString(2)
                    originalHash = rows.getString(3)
                }
            } }
            val changedHash = PasswordHasher.hash("ChangedLocalPassword01")
            first.dataSource.connection.use { db -> db.prepareStatement(
                "UPDATE users SET full_name=?,password_hash=? WHERE email=?"
            ).use { stmt ->
                stmt.setString(1, "Locally Edited Arya")
                stmt.setString(2, changedHash)
                stmt.setString(3, email)
                assertEquals(1, stmt.executeUpdate())
            } }
        } finally { first.close() }
        val second = BackendBootstrap.open(config())
        try {
            second.dataSource.connection.use { db -> db.prepareStatement(
                "SELECT id,full_name,password_hash FROM users WHERE email=?"
            ).use { stmt ->
                stmt.setString(1, email)
                stmt.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals(originalId, rows.getObject(1, UUID::class.java))
                    assertEquals("Locally Edited Arya", rows.getString(2))
                    assertTrue(PasswordHasher.verify("ChangedLocalPassword01", rows.getString(3)))
                }
            } }
        } finally {
            second.dataSource.connection.use { db -> db.prepareStatement(
                "UPDATE users SET full_name=?,password_hash=? WHERE email=?"
            ).use { stmt ->
                stmt.setString(1, originalName)
                stmt.setString(2, originalHash)
                stmt.setString(3, email)
                stmt.executeUpdate()
            } }
            second.close()
        }
    }
}
