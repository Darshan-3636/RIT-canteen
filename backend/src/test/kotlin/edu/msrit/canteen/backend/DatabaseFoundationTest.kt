package edu.msrit.canteen.backend

import edu.msrit.canteen.backend.auth.SellerCapabilities
import edu.msrit.canteen.backend.core.BackendBootstrap
import edu.msrit.canteen.backend.core.BackendConfig
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.close
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseFoundationTest {
    private fun testConfig(): BackendConfig {
        val env = System.getenv()
        assumeTrue(env["RIT_TEST_DATABASE_URL"] != null && env["RIT_TEST_DATABASE_PASSWORD"] != null,
            "Set RIT_TEST_DATABASE_URL/USER/PASSWORD for PostgreSQL integration tests")
        return BackendConfig(
            environment = RuntimeEnvironment.LOCAL,
            httpHost = "127.0.0.1",
            httpPort = 8080,
            databaseUrl = env.getValue("RIT_TEST_DATABASE_URL"),
            databaseUser = env["RIT_TEST_DATABASE_USER"] ?: "rit_canteen",
            databasePassword = env.getValue("RIT_TEST_DATABASE_PASSWORD"),
            demoSeedEnabled = true,
        )
    }

    @Test fun migrationsAndSellerSeedsAreIdempotent() {
        val config = testConfig()
        BackendBootstrap.open(config).useContext { first ->
            val installed = Flyway.configure().dataSource(first.dataSource)
                .locations("classpath:db/migration").load().info().applied()
            assertCoreMigrations(installed.map { it.version.version })
            val coreSuccess = first.dataSource.connection.use { db ->
                db.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT version, success FROM flyway_schema_history WHERE version IN ('001','002','003','004','005') ORDER BY installed_rank").use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1) to rows.getBoolean(2)) }
                    }
                }
            }
            assertEquals(listOf("001", "002", "003", "004", "005").map { it to true }, coreSuccess)
            val sellers = first.dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT id FROM sellers ORDER BY id").use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1)) }
                    }
                }
            }
            assertEquals(listOf("campus-pantry", "coffee-corner", "main-canteen", "north-counter"), sellers)
        }
        BackendBootstrap.open(config).useContext { second ->
            val count = second.dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT count(*) FROM sellers").use { it.next(); it.getInt(1) }
                }
            }
            assertEquals(4, count)
            assertTrue(BackendBootstrap.isReady(second.dataSource))
        }
    }

    @Test fun coreMigrationCheckAllowsLaterTeamRanges() {
        assertCoreMigrations(listOf("001", "002", "003", "004", "005", "020", "040"))
    }

    private fun assertCoreMigrations(versions: List<String>) {
        assertEquals(listOf("001", "002", "003", "004", "005"), versions.take(5))
    }

    @Test fun healthReadyAndAuthRoutesBehaveHonestly() {
        BackendBootstrap.open(testConfig()).useContext { context ->
            testApplication {
                application { backendModule(context) }
                assertEquals(HttpStatusCode.OK, client.get("/health").status)
                assertEquals(HttpStatusCode.OK, client.get("/ready").status)
                assertEquals(HttpStatusCode.BadRequest, client.post("/api/v1/auth/login").status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/auth/me").status)
                assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/not-a-route").status)
            }
        }
    }

    @Test fun liveSellerCapabilitiesRespectScopeAndRevocation() {
        BackendBootstrap.open(testConfig()).useContext { context ->
            val ownerUser = UUID.randomUUID(); val managerUser = UUID.randomUUID(); val staffUser = UUID.randomUUID()
            val ownerMembership = UUID.randomUUID(); val managerMembership = UUID.randomUUID(); val staffMembership = UUID.randomUUID()
            val sessionId = UUID.randomUUID()
            val token = "phaseA-local-only-token-" + UUID.randomUUID().toString().replace("-", "")
            val tokenHash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            val userIds = listOf(ownerUser, managerUser, staffUser)
            val membershipIds = listOf(ownerMembership, managerMembership, staffMembership)
            try {
                context.dataSource.connection.use { db ->
                    db.autoCommit = false
                    db.prepareStatement("INSERT INTO users (id,email,password_hash,full_name) VALUES (?,?,?,?)").use { stmt ->
                        userIds.forEachIndexed { index, id ->
                            stmt.setObject(1, id); stmt.setString(2, "phase-a-$id@example.test")
                            stmt.setString(3, "test-hash-not-a-password-value")
                            stmt.setString(4, "Phase A Tester $index"); stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                    db.prepareStatement(
                        "INSERT INTO seller_memberships (id,user_id,seller_id,role,supervisor_membership_id) VALUES (?,?,?,?,?)"
                    ).use { stmt ->
                        listOf(
                            Triple(ownerMembership, ownerUser, null),
                            Triple(managerMembership, managerUser, ownerMembership),
                            Triple(staffMembership, staffUser, managerMembership),
                        ).forEachIndexed { index, (id, user, parent) ->
                            stmt.setObject(1, id); stmt.setObject(2, user); stmt.setString(3, "main-canteen")
                            stmt.setString(4, listOf("OWNER", "MANAGER", "STAFF")[index]); stmt.setObject(5, parent)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                    db.prepareStatement(
                        "INSERT INTO seller_membership_capabilities (membership_id,capability) VALUES (?,?)"
                    ).use { stmt ->
                        listOf(managerMembership, staffMembership).forEach { id ->
                            stmt.setObject(1, id); stmt.setString(2, SellerCapabilities.BULK_VIEW); stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                    db.prepareStatement(
                        "INSERT INTO sessions (id,user_id,token_hash,expires_at) VALUES (?,?,?,?)"
                    ).use { stmt ->
                        stmt.setObject(1, sessionId); stmt.setObject(2, staffUser); stmt.setBytes(3, tokenHash)
                        stmt.setTimestamp(4, Timestamp.from(Instant.now().plusSeconds(3600)))
                        stmt.executeUpdate()
                    }
                    db.commit()
                }
                val principal = assertNotNull(context.authorization.authenticateBearer(token))
                assertTrue(context.authorization.hasSellerCapability(principal, "main-canteen", SellerCapabilities.BULK_VIEW))
                assertFalse(context.authorization.hasSellerCapability(principal, "north-counter", SellerCapabilities.BULK_VIEW))
                assertFalse(context.authorization.hasSellerCapability(principal, "main-canteen", SellerCapabilities.CATALOG_EDIT))
                assertFalse(context.authorization.isPlatformAdmin(principal))
                context.dataSource.connection.use { db ->
                    db.prepareStatement("UPDATE seller_memberships SET active = FALSE WHERE id = ?").use { stmt ->
                        stmt.setObject(1, managerMembership); stmt.executeUpdate()
                    }
                }
                assertFalse(context.authorization.hasSellerCapability(principal, "main-canteen", SellerCapabilities.BULK_VIEW))
                context.dataSource.connection.use { db ->
                    db.prepareStatement("UPDATE sessions SET revoked_at = now() WHERE id = ?").use { stmt ->
                        stmt.setObject(1, sessionId); stmt.executeUpdate()
                    }
                }
                assertEquals(null, context.authorization.authenticateBearer(token))
                assertFalse(context.authorization.hasSellerCapability(principal, "main-canteen", SellerCapabilities.BULK_VIEW))
            } finally {
                context.dataSource.connection.use { db ->
                    db.autoCommit = false
                    db.prepareStatement("DELETE FROM sessions WHERE id = ?").use { it.setObject(1, sessionId); it.executeUpdate() }
                    db.prepareStatement("DELETE FROM seller_membership_capabilities WHERE membership_id IN (?,?,?)").use {
                        membershipIds.forEachIndexed { index, id -> it.setObject(index + 1, id) }; it.executeUpdate()
                    }
                    db.prepareStatement("DELETE FROM seller_memberships WHERE id = ?").use { stmt ->
                        membershipIds.asReversed().forEach { stmt.setObject(1, it); stmt.executeUpdate() }
                    }
                    db.prepareStatement("DELETE FROM users WHERE id = ?").use { stmt ->
                        userIds.forEach { stmt.setObject(1, it); stmt.executeUpdate() }
                    }
                    db.commit()
                }
            }
        }
    }

    private inline fun <T> edu.msrit.canteen.backend.core.BackendContext.useContext(block: (edu.msrit.canteen.backend.core.BackendContext) -> T): T =
        try { block(this) } finally { close() }
}
