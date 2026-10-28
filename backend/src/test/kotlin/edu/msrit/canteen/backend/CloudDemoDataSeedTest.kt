package edu.msrit.canteen.backend

import edu.msrit.canteen.backend.auth.CloudDemoIdentitySeed
import edu.msrit.canteen.backend.core.BackendBootstrap
import edu.msrit.canteen.backend.core.BackendConfig
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.close
import edu.msrit.canteen.backend.features.commerce.CloudDemoDataSeed
import edu.msrit.canteen.backend.features.commerce.seedCommerceFixtures
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real PostgreSQL and HTTP readback, isolated in a unique schema of a disposable test database. */
class CloudDemoDataSeedTest {
    private val customerEmail = "cloud-seed-customer@example.test"
    private val ownerEmail = "cloud-seed-owner@example.test"
    private val customerPassword = "Unique-Customer-Password-2048!"
    private val ownerPassword = "Unique-Owner-Password-2048!"

    private fun localConfig(): BackendConfig {
        val env = System.getenv()
        val url = env["RIT_TEST_FRESH_DATABASE_URL"]
        assumeTrue(env["RIT_INTEGRATION_DISPOSABLE_FRESH_DB"] == "YES" && url != null &&
            env["RIT_TEST_DATABASE_PASSWORD"] != null, "Dedicated disposable fresh PostgreSQL required")
        require(Regex("^jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/rit_canteen_fresh$").matches(url!!))
        val user = env.getValue("RIT_TEST_DATABASE_USER")
        val password = env.getValue("RIT_TEST_DATABASE_PASSWORD")
        val schema = "itest_cloudseed_${UUID.randomUUID().toString().replace("-", "")}"
        DriverManager.getConnection(url, user, password).use { db ->
            db.createStatement().use { it.execute("CREATE SCHEMA $schema") }
        }
        return BackendConfig(RuntimeEnvironment.LOCAL, "127.0.0.1", 8080,
            "$url?currentSchema=$schema", user, password, demoSeedEnabled = false)
    }

    private fun cloud(context: BackendContext): BackendContext = context.copy(config = context.config.copy(
        environment = RuntimeEnvironment.CLOUD_DEMO,
        httpHost = "0.0.0.0",
        databaseUrl = "jdbc:postgresql://pool.example:5432/postgres?sslmode=require",
        cloudDemoSeedEnabled = true,
        cloudDemoCustomerEmail = customerEmail,
        cloudDemoCustomerPassword = customerPassword,
        cloudDemoOwnerEmail = ownerEmail,
        cloudDemoOwnerPassword = ownerPassword,
    ))

    private fun seed(context: BackendContext) {
        context.dataSource.connection.use { db ->
            db.createStatement().use { sql ->
                sql.executeUpdate("""INSERT INTO sellers(id,name,description,is_main_canteen) VALUES
                    ('main-canteen','Main Canteen','Demo',TRUE),
                    ('north-counter','North Counter','Demo',FALSE),
                    ('coffee-corner','Coffee Corner','Demo',FALSE),
                    ('campus-pantry','Campus Pantry','Demo',FALSE) ON CONFLICT(id) DO NOTHING""")
            }
        }
        seedCommerceFixtures(context)
        CloudDemoIdentitySeed.seed(context)
        CloudDemoDataSeed.seed(context)
    }

    @Test fun cloudDemoHistoryIsIdempotentUnpaidSellerVisibleAndAnalyticsBacked() {
        val config = localConfig()
        BackendBootstrap.open(config).also { opened ->
            try { seed(cloud(opened)) } finally { opened.close() }
        }
        BackendBootstrap.open(config).also { opened ->
            val context = cloud(opened)
            try {
                seed(context)
                seed(context)
                context.dataSource.connection.use { db ->
                    db.createStatement().use { sql ->
                        listOf("sellers" to 4, "menu_categories" to 15, "menu_items" to 72,
                            "individual_demo_orders" to 5, "bulk_requests" to 6).forEach { (table, expected) ->
                            sql.executeQuery("SELECT count(*) FROM $table").use { rows ->
                                assertTrue(rows.next()); assertEquals(expected, rows.getInt(1), table)
                            }
                        }
                        sql.executeQuery("SELECT count(*) FROM individual_demo_orders WHERE payment_verified OR NOT demo_only").use { rows ->
                            assertTrue(rows.next()); assertEquals(0, rows.getInt(1))
                        }
                    }
                }
                testApplication {
                    // Hosted signup is opt-in; exercise it here without changing any provider setting.
                    application { backendModule(context.copy(config = context.config.copy(cloudDemoSelfSignupEnabled = true))) }
                    suspend fun login(email: String, password: String): String {
                        val response = client.post("/api/v1/auth/login") {
                            header(HttpHeaders.ContentType, "application/json")
                            setBody("""{"email":"$email","password":"$password"}""")
                        }
                        assertEquals(HttpStatusCode.OK, response.status)
                        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
                            .getValue("accessToken").jsonPrimitive.content
                    }
                    val customer = login(customerEmail, customerPassword)
                    val owner = login(ownerEmail, ownerPassword)
                    val newEmail = "cloud-signup-${UUID.randomUUID()}@example.test"
                    val registration = client.post("/api/v1/auth/register") {
                        header(HttpHeaders.ContentType, "application/json")
                        setBody("""{"fullName":"Sample Student","email":"$newEmail","password":"SamplePassword123!","affiliation":"STUDENT","department":"CSE"}""")
                    }
                    assertEquals(HttpStatusCode.Created, registration.status)
                    context.dataSource.connection.use { db ->
                        db.prepareStatement("SELECT affiliation,is_platform_admin FROM users WHERE email=?").use { sql ->
                            sql.setString(1, newEmail)
                            sql.executeQuery().use { rows ->
                                assertTrue(rows.next(), "Registered account must exist in PostgreSQL")
                                assertEquals("STUDENT", rows.getString(1))
                                assertEquals(false, rows.getBoolean(2))
                            }
                        }
                    }
                    val registeredToken = login(newEmail, "SamplePassword123!")
                    assertEquals(HttpStatusCode.OK, client.get("/api/v1/auth/me") {
                        header(HttpHeaders.Authorization, "Bearer $registeredToken")
                    }.status)
                    val customerOrders = client.get("/api/v1/me/individual-demo-orders") {
                        header(HttpHeaders.Authorization, "Bearer $customer")
                    }
                    assertEquals(HttpStatusCode.OK, customerOrders.status)
                    val orders = Json.parseToJsonElement(customerOrders.bodyAsText()).jsonObject.getValue("orders").jsonArray
                    assertEquals(5, orders.size)
                    assertTrue(orders.all { row ->
                        val order = row.jsonObject
                        order.getValue("demoOnly").jsonPrimitive.content == "true" &&
                            order.getValue("paymentVerified").jsonPrimitive.content == "false" &&
                            order.getValue("status").jsonPrimitive.content == "DEMO_SAVED" &&
                            order.getValue("lines").jsonArray.first().jsonObject.getValue("itemName")
                                .jsonPrimitive.content.startsWith("[DEMO / SYNTHETIC]")
                    })
                    val sellerOrders = client.get("/api/v1/sellers/main-canteen/individual-demo-orders") {
                        header(HttpHeaders.Authorization, "Bearer $owner")
                    }
                    assertEquals(HttpStatusCode.OK, sellerOrders.status)
                    assertEquals(orders.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet(),
                        Json.parseToJsonElement(sellerOrders.bodyAsText()).jsonObject.getValue("orders").jsonArray
                            .map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet())
                    val requests = client.get("/api/v1/me/bulk/requests") {
                        header(HttpHeaders.Authorization, "Bearer $customer")
                    }
                    assertEquals(HttpStatusCode.OK, requests.status)
                    assertEquals(6, Json.parseToJsonElement(requests.bodyAsText()).jsonObject
                        .getValue("requests").jsonArray.size)
                    assertTrue(requests.bodyAsText().contains("[DEMO / SYNTHETIC]"))
                    val sellerRequests = client.get("/api/v1/sellers/main-canteen/bulk/requests") {
                        header(HttpHeaders.Authorization, "Bearer $owner")
                    }
                    assertEquals(HttpStatusCode.OK, sellerRequests.status)
                    assertEquals(6, Json.parseToJsonElement(sellerRequests.bodyAsText()).jsonObject
                        .getValue("requests").jsonArray.size)
                    val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
                    val analytics = client.get("/api/v1/sellers/main-canteen/analytics/summary?from=${today.minusDays(29)}&to=$today") {
                        header(HttpHeaders.Authorization, "Bearer $owner")
                    }
                    assertEquals(HttpStatusCode.OK, analytics.status)
                    val summary = Json.parseToJsonElement(analytics.bodyAsText()).jsonObject
                    assertEquals("6", summary.getValue("demoRequestCounts").jsonObject.getValue("total").jsonPrimitive.content)
                    assertEquals("DEMO_UNVERIFIED", summary.getValue("demoRequestCountsStatus").jsonPrimitive.content)
                    assertEquals("null", summary.getValue("verifiedRevenuePaise").toString())
                    val week = client.get("/api/v1/sellers/main-canteen/analytics/summary?from=${today.minusDays(6)}&to=$today") {
                        header(HttpHeaders.Authorization, "Bearer $owner")
                    }
                    assertEquals("4", Json.parseToJsonElement(week.bodyAsText()).jsonObject
                        .getValue("demoRequestCounts").jsonObject.getValue("total").jsonPrimitive.content)
                    val todayOnly = client.get("/api/v1/sellers/main-canteen/analytics/summary?from=$today&to=$today") {
                        header(HttpHeaders.Authorization, "Bearer $owner")
                    }
                    assertEquals("1", Json.parseToJsonElement(todayOnly.bodyAsText()).jsonObject
                        .getValue("demoRequestCounts").jsonObject.getValue("total").jsonPrimitive.content)
                    assertEquals(HttpStatusCode.Forbidden,
                        client.get("/api/v1/sellers/north-counter/analytics/summary") {
                            header(HttpHeaders.Authorization, "Bearer $owner")
                        }.status)
                    assertEquals(HttpStatusCode.Unauthorized,
                        client.get("/api/v1/me/individual-demo-orders").status)
                }
            } finally { opened.close() }
        }
    }
}
