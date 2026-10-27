package edu.msrit.canteen.backend.features.engagement

import edu.msrit.canteen.backend.auth.SellerCapabilities
import edu.msrit.canteen.backend.backendModule
import edu.msrit.canteen.backend.core.BackendBootstrap
import edu.msrit.canteen.backend.core.BackendConfig
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.close
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** PostgreSQL-backed route tests. Identities are disposable rows with SHA-256 hashed bearer sessions. */
class EngagementFeatureTest {
    private class FakeCommerce : CommerceReadPort {
        val items = mutableMapOf<String, String>()
        var counts: DemoRequestCounts? = null
        override fun lookupItem(itemId: String): ItemLookup = items[itemId]?.let(ItemLookup::Found) ?: ItemLookup.Missing
        override fun demoRequestCounts(sellerId: String?, from: LocalDate?, to: LocalDate?) = counts
    }

    private class Persona(val userId: UUID, val token: String, val email: String)

    private class Fixture(val context: BackendContext) {
        val users = mutableListOf<UUID>()
        val memberships = mutableListOf<UUID>()
        val membershipByUser = mutableMapOf<UUID, UUID>()

        fun persona(name: String, admin: Boolean = false): Persona {
            val id = UUID.randomUUID(); users += id
            val email = "b3-$name-$id@example.test"
            val token = "b3test_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
            context.dataSource.connection.use { db ->
                db.update("INSERT INTO users (id, email, password_hash, full_name, affiliation, is_platform_admin) VALUES (?, ?, ?, ?, ?, ?)",
                    id, email, "test-hash-not-a-password-value", "B3 $name", if (admin) null else "STUDENT", admin)
                db.update("INSERT INTO sessions (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), id, MessageDigest.getInstance("SHA-256").digest(token.toByteArray()),
                    Timestamp.from(Instant.now().plusSeconds(3600)))
            }
            return Persona(id, token, email)
        }

        fun member(persona: Persona, sellerId: String, role: String, supervisor: Persona?, vararg caps: String) {
            val id = UUID.randomUUID(); memberships += id; membershipByUser[persona.userId] = id
            context.dataSource.connection.use { db ->
                db.update("INSERT INTO seller_memberships (id, user_id, seller_id, role, supervisor_membership_id) VALUES (?, ?, ?, ?, ?)",
                    id, persona.userId, sellerId, role, supervisor?.let { membershipByUser.getValue(it.userId) })
                caps.forEach { db.update("INSERT INTO seller_membership_capabilities (membership_id, capability) VALUES (?, ?)", id, it) }
            }
        }

        fun sql(statement: String, vararg args: Any?) = context.dataSource.connection.use { it.update(statement, *args) }

        fun cleanup() = context.dataSource.connection.use { db ->
            db.autoCommit = false
            val ids = users.toTypedArray()
            val inUsers = ids.joinToString(",") { "?" }
            if (ids.isNotEmpty()) {
                db.update("DELETE FROM admin_audit_events WHERE actor_user_id IN ($inUsers)", *ids)
                db.update("DELETE FROM report_selections WHERE created_by IN ($inUsers)", *ids)
                db.update("DELETE FROM admin_setup_drafts WHERE created_by IN ($inUsers)", *ids)
                db.update("DELETE FROM support_tickets WHERE user_id IN ($inUsers)", *ids)
                db.update("DELETE FROM seller_membership_capabilities WHERE membership_id IN (SELECT id FROM seller_memberships WHERE user_id IN ($inUsers))", *ids)
                memberships.asReversed().forEach { db.update("DELETE FROM seller_memberships WHERE id = ?", it) }
                db.update("DELETE FROM sessions WHERE user_id IN ($inUsers)", *ids)
                db.update("DELETE FROM users WHERE id IN ($inUsers)", *ids)
            }
            db.commit()
        }
    }

    private class Cast(f: Fixture) {
        val studentA = f.persona("student-a")
        val studentB = f.persona("student-b")
        val owner = f.persona("owner-main").also { f.member(it, "main-canteen", "OWNER", null) }
        val manager = f.persona("manager-main").also {
            f.member(it, "main-canteen", "MANAGER", owner, SellerCapabilities.BULK_VIEW, SellerCapabilities.BULK_RESPOND)
        }
        val staff = f.persona("staff-main").also { f.member(it, "main-canteen", "STAFF", manager, SellerCapabilities.BULK_VIEW) }
        val otherOwner = f.persona("owner-north").also { f.member(it, "north-counter", "OWNER", null) }
        val admin = f.persona("admin", admin = true)
    }

    private fun testConfig(): BackendConfig {
        val env = System.getenv()
        assumeTrue(env["RIT_TEST_DATABASE_URL"] != null && env["RIT_TEST_DATABASE_PASSWORD"] != null,
            "Set RIT_TEST_DATABASE_URL/USER/PASSWORD for PostgreSQL integration tests")
        return BackendConfig(RuntimeEnvironment.LOCAL, "127.0.0.1", 8080, env.getValue("RIT_TEST_DATABASE_URL"),
            env["RIT_TEST_DATABASE_USER"] ?: "rit_canteen", env.getValue("RIT_TEST_DATABASE_PASSWORD"), demoSeedEnabled = true)
    }

    private fun scenario(commerce: CommerceReadPort? = null, block: suspend ApplicationTestBuilder.(Fixture, Cast) -> Unit) {
        val context = BackendBootstrap.open(testConfig())
        val fixture = Fixture(context)
        try {
            val cast = Cast(fixture)
            testApplication {
                application {
                    // Preserve the contributor's isolated port tests; production mounts the real adapter.
                    backendModule(context, mountContributorRoutes = false)
                    routing {
                        route("/api/v1") {
                            if (commerce == null) registerEngagementRoutes(context) else registerEngagementRoutes(context, commerce)
                        }
                    }
                }
                block(fixture, cast)
            }
        } finally {
            fixture.cleanup()
            context.close()
        }
    }

    private suspend fun ApplicationTestBuilder.api(method: HttpMethod, path: String, who: Persona?, body: String? = null):
        Pair<HttpStatusCode, JsonElement> {
        val response = client.request("/api/v1$path") {
            this.method = method
            who?.let { header(HttpHeaders.Authorization, "Bearer ${it.token}") }
            body?.let { contentType(ContentType.Application.Json); setBody(it) }
        }
        val text = response.bodyAsText()
        return response.status to (if (text.isBlank()) JsonNull else Json.parseToJsonElement(text))
    }

    private val JsonElement.o: JsonObject get() = jsonObject
    private fun JsonElement.str(key: String) = jsonObject.getValue(key).jsonPrimitive.content
    private fun JsonElement.errorCode() = jsonObject.getValue("error").jsonObject.getValue("code").jsonPrimitive.content

    @Test fun preferencesDefaultOffAndAreIsolatedPerAccount() = scenario { _, cast ->
        assertEquals(HttpStatusCode.Unauthorized, api(HttpMethod.Get, "/me/preferences", null).first)
        val (status, defaults) = api(HttpMethod.Get, "/me/preferences", cast.studentA)
        assertEquals(HttpStatusCode.OK, status)
        assertFalse(defaults.o.getValue("personalizationConsent").jsonPrimitive.boolean)
        assertFalse(defaults.o.getValue("promotionalNotifications").jsonPrimitive.boolean)
        assertTrue(defaults.o.getValue("operationalNotifications").jsonPrimitive.boolean)

        val (patched, updated) = api(HttpMethod.Patch, "/me/preferences", cast.studentA, """{"personalizationConsent":true}""")
        assertEquals(HttpStatusCode.OK, patched)
        assertTrue(updated.o.getValue("personalizationConsent").jsonPrimitive.boolean)
        assertTrue(updated.o.getValue("transactionalNotifications").jsonPrimitive.boolean)
        val other = api(HttpMethod.Get, "/me/preferences", cast.studentB).second
        assertFalse(other.o.getValue("personalizationConsent").jsonPrimitive.boolean)

        val (bad, error) = api(HttpMethod.Patch, "/me/preferences", cast.studentA, """{"customerId":"x","personalizationConsent":false}""")
        assertEquals(HttpStatusCode.BadRequest, bad)
        assertEquals("INVALID_REQUEST_BODY", error.errorCode())
    }

    @Test fun favoritesAreIsolatedAndItemValidationFailsClosedWithoutCommerce() = scenario { _, cast ->
        assertEquals(HttpStatusCode.OK, api(HttpMethod.Put, "/me/favorites/seller/main-canteen", cast.studentA).first)
        assertEquals(HttpStatusCode.OK, api(HttpMethod.Put, "/me/favorites/seller/main-canteen", cast.studentA).first)
        assertEquals(1, api(HttpMethod.Get, "/me/favorites", cast.studentA).second.o.getValue("items").jsonArray.size)
        assertEquals(0, api(HttpMethod.Get, "/me/favorites", cast.studentB).second.o.getValue("items").jsonArray.size)

        val (itemStatus, itemError) = api(HttpMethod.Put, "/me/favorites/item/masala-dosa", cast.studentA)
        assertEquals(HttpStatusCode.ServiceUnavailable, itemStatus)
        assertEquals("CATALOG_UNAVAILABLE", itemError.errorCode())
        assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Put, "/me/favorites/seller/no-such-seller", cast.studentA).first)
        assertEquals(HttpStatusCode.BadRequest, api(HttpMethod.Put, "/me/favorites/order/main-canteen", cast.studentA).first)
        assertEquals(HttpStatusCode.BadRequest, api(HttpMethod.Put, "/me/favorites/seller/Bad%20Id", cast.studentA).first)

        assertEquals(HttpStatusCode.NoContent, api(HttpMethod.Delete, "/me/favorites/seller/main-canteen", cast.studentB).first)
        assertEquals(1, api(HttpMethod.Get, "/me/favorites", cast.studentA).second.o.getValue("items").jsonArray.size)
        assertEquals(HttpStatusCode.NoContent, api(HttpMethod.Delete, "/me/favorites/seller/main-canteen", cast.studentA).first)
        assertEquals(0, api(HttpMethod.Get, "/me/favorites", cast.studentA).second.o.getValue("items").jsonArray.size)
    }

    @Test fun itemFavoritesUseTheInjectedCatalogPort() {
        val commerce = FakeCommerce().apply { items["masala-dosa"] = "main-canteen" }
        scenario(commerce) { _, cast ->
            val (status, favorite) = api(HttpMethod.Put, "/me/favorites/item/masala-dosa", cast.studentA)
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("main-canteen", favorite.str("sellerId"))
            assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Put, "/me/favorites/item/unknown-item", cast.studentA).first)
        }
    }

    @Test fun bulkTemplatesAreOwnerScopedAndHaveNoBulkMinimum() = scenario { _, cast ->
        val body = """{"sellerId":"north-counter","name":"Club lunch","lines":[{"itemId":"north-lunch","quantity":1}]}"""
        val (created, template) = api(HttpMethod.Post, "/me/bulk-templates", cast.studentA, body)
        assertEquals(HttpStatusCode.Created, created)
        assertFalse(template.o.getValue("catalogValidated").jsonPrimitive.boolean)
        val id = template.str("id")

        assertEquals(HttpStatusCode.Conflict, api(HttpMethod.Post, "/me/bulk-templates", cast.studentA, body).first)
        assertEquals(HttpStatusCode.Created, api(HttpMethod.Post, "/me/bulk-templates", cast.studentB, body).first)
        assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Get, "/me/bulk-templates/$id", cast.studentB).first)
        assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Patch, "/me/bulk-templates/$id", cast.studentB, """{"name":"Mine now"}""").first)
        assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Delete, "/me/bulk-templates/$id", cast.studentB).first)
        assertEquals(1, api(HttpMethod.Get, "/me/bulk-templates", cast.studentB).second.o.getValue("items").jsonArray.size)

        val invalid = """{"sellerId":"north-counter","name":"Bad","lines":[{"itemId":"north-lunch","quantity":0}]}"""
        assertEquals(HttpStatusCode.BadRequest, api(HttpMethod.Post, "/me/bulk-templates", cast.studentA, invalid).first)
        val noSeller = """{"sellerId":"ghost-seller","name":"Bad","lines":[{"itemId":"north-lunch","quantity":2}]}"""
        assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Post, "/me/bulk-templates", cast.studentA, noSeller).first)

        val (patched, renamed) = api(HttpMethod.Patch, "/me/bulk-templates/$id", cast.studentA,
            """{"lines":[{"itemId":"north-lunch","quantity":12},{"itemId":"north-snack","quantity":3}]}""")
        assertEquals(HttpStatusCode.OK, patched)
        assertEquals(2, renamed.o.getValue("lines").jsonArray.size)
        assertEquals(HttpStatusCode.NoContent, api(HttpMethod.Delete, "/me/bulk-templates/$id", cast.studentA).first)
        assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Get, "/me/bulk-templates/not-a-uuid", cast.studentA).first)
    }

    @Test fun supportTicketsFlowBetweenCustomerSellerAndAdminWithIsolation() = scenario { fixture, cast ->
        val create = """{"category":"PAYMENT","subject":"Charged twice","message":"I think I paid twice","sellerId":"main-canteen",
            "requestReference":"REQ-1001","clientRequestId":"retry-key-0001"}"""
        val (created, detail) = api(HttpMethod.Post, "/me/support/tickets", cast.studentA, create)
        assertEquals(HttpStatusCode.Created, created)
        val ticketId = detail.o.getValue("ticket").str("id")
        val (replayed, again) = api(HttpMethod.Post, "/me/support/tickets", cast.studentA, create)
        assertEquals(HttpStatusCode.OK, replayed)
        assertEquals(ticketId, again.o.getValue("ticket").str("id"))
        assertEquals(1, again.o.getValue("messages").jsonArray.size)

        assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Get, "/me/support/tickets/$ticketId", cast.studentB).first)
        assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Post, "/me/support/tickets/$ticketId/messages", cast.studentB, """{"body":"hi"}""").first)
        assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Post, "/me/support/tickets/$ticketId/escalate", cast.studentB).first)
        assertEquals(0L, api(HttpMethod.Get, "/me/support/tickets", cast.studentB).second.o.getValue("total").jsonPrimitive.long)
        assertEquals(HttpStatusCode.Forbidden, api(HttpMethod.Get, "/sellers/main-canteen/support/tickets", cast.otherOwner).first)
        assertEquals(0L, api(HttpMethod.Get, "/sellers/north-counter/support/tickets", cast.otherOwner).second.o.getValue("total").jsonPrimitive.long)
        assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Get, "/sellers/north-counter/support/tickets/$ticketId", cast.otherOwner).first)
        assertEquals(HttpStatusCode.BadRequest,
            api(HttpMethod.Post, "/me/support/tickets", cast.studentA, """{"category":"REFUND","subject":"x y z","message":"m"}""").first)

        // Staff holds bulk.view only: may read the routed ticket but not reply.
        val staffView = api(HttpMethod.Get, "/sellers/main-canteen/support/tickets/$ticketId", cast.staff)
        assertEquals(HttpStatusCode.OK, staffView.first)
        assertEquals(JsonNull, staffView.second.o.getValue("ticket").o.getValue("customerUserId"))
        assertEquals(HttpStatusCode.Forbidden,
            api(HttpMethod.Post, "/sellers/main-canteen/support/tickets/$ticketId/messages", cast.staff, """{"body":"hello"}""").first)
        val reply = """{"body":"We are checking with the counter","clientMessageId":"seller-msg-0001"}"""
        assertEquals(HttpStatusCode.Created, api(HttpMethod.Post, "/sellers/main-canteen/support/tickets/$ticketId/messages", cast.manager, reply).first)
        assertEquals(HttpStatusCode.OK, api(HttpMethod.Post, "/sellers/main-canteen/support/tickets/$ticketId/messages", cast.manager, reply).first)

        val inbox = api(HttpMethod.Get, "/me/notifications", cast.studentA).second
        assertEquals(1L, inbox.o.getValue("unreadCount").jsonPrimitive.long)
        val notification = inbox.o.getValue("items").jsonArray.single()
        assertEquals("IN_APP_ONLY", notification.str("deliveryChannel"))
        val notificationId = notification.str("id")
        assertEquals(HttpStatusCode.NotFound, api(HttpMethod.Patch, "/me/notifications/$notificationId/read", cast.studentB).first)
        assertEquals(0, api(HttpMethod.Get, "/me/notifications", cast.studentB).second.o.getValue("items").jsonArray.size)
        assertEquals(HttpStatusCode.OK, api(HttpMethod.Patch, "/me/notifications/$notificationId/read", cast.studentA).first)
        assertEquals(0L, api(HttpMethod.Get, "/me/notifications", cast.studentA).second.o.getValue("unreadCount").jsonPrimitive.long)

        val escalated = api(HttpMethod.Post, "/me/support/tickets/$ticketId/escalate", cast.studentA)
        assertEquals(HttpStatusCode.OK, escalated.first)
        assertEquals("ESCALATED", escalated.second.o.getValue("ticket").str("status"))
        val adminList = api(HttpMethod.Get, "/admin/support/tickets?status=ESCALATED", cast.admin).second
        assertTrue(adminList.o.getValue("items").jsonArray.any { it.str("id") == ticketId && it.str("customerUserId") == cast.studentA.userId.toString() })
        assertEquals(HttpStatusCode.Created, api(HttpMethod.Post, "/admin/support/tickets/$ticketId/messages", cast.admin, """{"body":"Admin here"}""").first)
        assertEquals(HttpStatusCode.OK, api(HttpMethod.Patch, "/admin/support/tickets/$ticketId", cast.admin, """{"status":"CLOSED"}""").first)
        val closed = api(HttpMethod.Patch, "/admin/support/tickets/$ticketId", cast.admin, """{"status":"OPEN"}""")
        assertEquals(HttpStatusCode.Conflict, closed.first)
        assertEquals("INVALID_TICKET_TRANSITION", closed.second.errorCode())
        assertEquals(HttpStatusCode.Conflict, api(HttpMethod.Post, "/me/support/tickets/$ticketId/messages", cast.studentA, """{"body":"one more"}""").first)

        val final = api(HttpMethod.Get, "/me/support/tickets/$ticketId", cast.studentA).second
        assertEquals(listOf("CUSTOMER", "SELLER", "ADMIN"), final.o.getValue("messages").jsonArray.map { it.str("authorRole") })
        assertEquals(listOf("OPEN", "ESCALATED", "CLOSED"), final.o.getValue("statusHistory").jsonArray.map { it.str("toStatus") })
        // Support never carries or alters payment state: no payment field exists on tickets in API or schema.
        assertFalse(final.o.getValue("ticket").o.keys.any { it.contains("pay", ignoreCase = true) })
        val paymentColumns = fixture.context.dataSource.connection.use { db ->
            db.count("""SELECT count(*) FROM information_schema.columns WHERE table_name LIKE 'support_%' AND column_name ILIKE '%pay%'""")
        }
        assertEquals(0, paymentColumns)

        val audit = api(HttpMethod.Get, "/admin/audit?targetType=SUPPORT_TICKET&limit=100", cast.admin).second
        val actions = audit.o.getValue("items").jsonArray.filter { it.str("targetId") == ticketId }.map { it.str("action") }.toSet()
        assertEquals(setOf("support.reply_posted", "support.ticket_escalated", "support.status_changed"), actions)
        assertFalse(audit.toString().contains("We are checking"), "Audit must not copy ticket bodies")
    }

    @Test fun adminEndpointsRejectCustomersOwnersAndStaff() = scenario { _, cast ->
        val adminGets = listOf("/admin/overview", "/admin/sellers", "/admin/users", "/admin/audit", "/admin/support/tickets",
            "/admin/analytics/summary", "/admin/setup-drafts")
        for (persona in listOf(cast.studentA, cast.owner, cast.staff)) {
            adminGets.forEach { path ->
                val (status, body) = api(HttpMethod.Get, path, persona)
                assertEquals(HttpStatusCode.Forbidden, status, path)
                assertEquals("PLATFORM_ADMIN_REQUIRED", body.errorCode())
            }
            assertEquals(HttpStatusCode.Forbidden, api(HttpMethod.Post, "/admin/setup-drafts", persona,
                """{"proposedSellerId":"new-kiosk","sellerName":"New Kiosk"}""").first)
        }
        adminGets.forEach { assertEquals(HttpStatusCode.Unauthorized, api(HttpMethod.Get, it, null).first) }
        adminGets.forEach { assertEquals(HttpStatusCode.OK, api(HttpMethod.Get, it, cast.admin).first, it) }
        // The administrator is not implicitly a seller member.
        assertEquals(HttpStatusCode.Forbidden, api(HttpMethod.Get, "/sellers/main-canteen/support/tickets", cast.admin).first)
        assertEquals(HttpStatusCode.Forbidden, api(HttpMethod.Get, "/sellers/main-canteen/dashboard-summary", cast.admin).first)
    }

    @Test fun adminDirectoryIsRealMinimalAndPaginatedSafely() = scenario { fixture, cast ->
        val overview = api(HttpMethod.Get, "/admin/overview", cast.admin).second
        val realUsers = fixture.context.dataSource.connection.use { it.count("SELECT count(*) FROM users") }
        assertEquals(realUsers, overview.o.getValue("users").o.getValue("total").jsonPrimitive.long)
        assertEquals("CORE_DATABASE", overview.str("source"))

        val found = api(HttpMethod.Get, "/admin/users?q=${cast.studentA.email}", cast.admin).second
        assertEquals(1L, found.o.getValue("total").jsonPrimitive.long)
        val row = found.o.getValue("items").jsonArray.single().o
        assertEquals(setOf("id", "email", "fullName", "affiliation", "status", "platformAdmin", "activeMembershipCount", "createdAt"), row.keys)
        assertEquals(0L, api(HttpMethod.Get, "/admin/users?q=%25", cast.admin).second.o.getValue("total").jsonPrimitive.long)

        listOf("limit=0", "limit=101", "offset=-1", "limit=abc", "status=ROOT").forEach {
            val (status, body) = api(HttpMethod.Get, "/admin/users?$it", cast.admin)
            assertEquals(HttpStatusCode.BadRequest, status, it)
            assertTrue(body.o.containsKey("error"))
        }
        val sellers = api(HttpMethod.Get, "/admin/sellers?limit=2", cast.admin).second
        assertEquals(2, sellers.o.getValue("items").jsonArray.size)
        assertEquals(4L, sellers.o.getValue("total").jsonPrimitive.long)
        assertEquals("main-canteen", sellers.o.getValue("items").jsonArray.first().str("id"))

        val draftBody = """{"proposedSellerId":"b3-kiosk-${UUID.randomUUID().toString().take(8)}","sellerName":"Test Kiosk"}"""
        val (created, draft) = api(HttpMethod.Post, "/admin/setup-drafts", cast.admin, draftBody)
        assertEquals(HttpStatusCode.Created, created)
        assertFalse(draft.o.getValue("invitationSent").jsonPrimitive.boolean)
        assertFalse(draft.o.getValue("sellerCreated").jsonPrimitive.boolean)
        assertEquals(HttpStatusCode.Conflict, api(HttpMethod.Post, "/admin/setup-drafts", cast.admin, draftBody).first)
        assertEquals(HttpStatusCode.Conflict, api(HttpMethod.Post, "/admin/setup-drafts", cast.admin,
            """{"proposedSellerId":"main-canteen","sellerName":"Dup"}""").first)
        val archived = api(HttpMethod.Post, "/admin/setup-drafts/${draft.str("id")}/archive", cast.admin).second
        assertEquals("ARCHIVED", archived.str("status"))
        val audit = api(HttpMethod.Get, "/admin/audit?targetType=SETUP_DRAFT", cast.admin).second.o.getValue("items").jsonArray
        assertEquals(setOf("admin.setup_draft_created", "admin.setup_draft_archived"),
            audit.filter { it.str("targetId") == draft.str("id") }.map { it.str("action") }.toSet())
        assertTrue(audit.all { it.str("actorUserId") == cast.admin.userId.toString() || it.str("targetId") != draft.str("id") })
    }

    @Test fun analyticsNeverInventRevenueOrExports() = scenario { _, cast ->
        val (status, dashboard) = api(HttpMethod.Get, "/sellers/main-canteen/dashboard-summary", cast.owner)
        assertEquals(HttpStatusCode.OK, status)
        assertTrue(dashboard.o.getValue("insufficientData").jsonPrimitive.boolean)
        assertEquals(JsonNull, dashboard.o.getValue("verifiedRevenuePaise"))
        assertEquals(JsonNull, dashboard.o.getValue("demoRequestCounts"))
        assertEquals("UNAVAILABLE_NO_COMMERCE_SOURCE", dashboard.str("demoRequestCountsStatus"))

        val summary = api(HttpMethod.Get, "/sellers/main-canteen/analytics/summary?from=2026-10-01&to=2026-10-27", cast.owner).second
        assertEquals("UNVERIFIED_OR_INSUFFICIENT", summary.str("dataStatus"))
        assertEquals("NO_VERIFIED_PAYMENTS", summary.str("source"))
        assertEquals("INSUFFICIENT_DATA", summary.str("forecast"))
        assertEquals(JsonNull, summary.o.getValue("verifiedRevenuePaise"))
        assertEquals(HttpStatusCode.BadRequest, api(HttpMethod.Get, "/sellers/main-canteen/analytics/summary?from=2026-10-27&to=2026-10-01", cast.owner).first)
        assertEquals(HttpStatusCode.BadRequest, api(HttpMethod.Get, "/sellers/main-canteen/analytics/summary?from=yesterday", cast.owner).first)
        assertEquals(HttpStatusCode.Forbidden, api(HttpMethod.Get, "/sellers/main-canteen/analytics/summary", cast.staff).first)
        assertEquals(HttpStatusCode.Forbidden, api(HttpMethod.Get, "/sellers/main-canteen/analytics/summary", cast.otherOwner).first)

        val (export, exportBody) = api(HttpMethod.Get, "/sellers/main-canteen/reports/export", cast.owner)
        assertEquals(HttpStatusCode.NotImplemented, export)
        assertEquals("EXPORT_NOT_IMPLEMENTED", exportBody.errorCode())
        val selection = api(HttpMethod.Post, "/sellers/main-canteen/reports/selections", cast.owner,
            """{"name":"Weekly","metrics":["SUPPORT_TICKETS"],"from":"2026-10-01","to":"2026-10-07"}""")
        assertEquals(HttpStatusCode.Created, selection.first)
        assertFalse(selection.second.o.getValue("exportAvailable").jsonPrimitive.boolean)
        assertEquals(HttpStatusCode.BadRequest, api(HttpMethod.Post, "/sellers/main-canteen/reports/selections", cast.owner,
            """{"name":"Revenue","metrics":["VERIFIED_REVENUE"]}""").first)
        assertEquals(HttpStatusCode.Forbidden, api(HttpMethod.Get, "/sellers/main-canteen/reports/export", cast.manager).first)

        val platform = api(HttpMethod.Get, "/admin/analytics/summary", cast.admin).second
        assertEquals("PLATFORM", platform.str("scope"))
        assertEquals(JsonNull, platform.o.getValue("verifiedRevenuePaise"))
    }

    @Test fun wiredCommerceCountsStayLabelledDemoAndRevenueStaysNull() {
        val commerce = FakeCommerce().apply { counts = DemoRequestCounts(3, mapOf("SUBMITTED" to 2, "APPROVED" to 1)) }
        scenario(commerce) { _, cast ->
            val dashboard = api(HttpMethod.Get, "/sellers/main-canteen/dashboard-summary", cast.owner).second
            assertFalse(dashboard.o.getValue("insufficientData").jsonPrimitive.boolean)
            assertEquals(3, dashboard.o.getValue("demoRequestCounts").o.getValue("total").jsonPrimitive.int)
            assertEquals("DEMO_UNVERIFIED", dashboard.str("demoRequestCountsStatus"))
            assertEquals(JsonNull, dashboard.o.getValue("verifiedRevenuePaise"))
            assertEquals("UNVERIFIED_OR_INSUFFICIENT", dashboard.str("dataStatus"))
        }
    }

    @Test fun membershipAndSessionRevocationDenyActions() = scenario { fixture, cast ->
        val ticket = api(HttpMethod.Post, "/me/support/tickets", cast.studentA,
            """{"category":"FOOD","subject":"Cold food","message":"Lunch was cold","sellerId":"main-canteen"}""").second
        val ticketId = ticket.o.getValue("ticket").str("id")
        assertEquals(HttpStatusCode.OK, api(HttpMethod.Get, "/sellers/main-canteen/support/tickets/$ticketId", cast.staff).first)

        fixture.sql("UPDATE seller_memberships SET active = FALSE WHERE id = ?", fixture.membershipByUser.getValue(cast.manager.userId))
        assertEquals(HttpStatusCode.Forbidden,
            api(HttpMethod.Post, "/sellers/main-canteen/support/tickets/$ticketId/messages", cast.manager, """{"body":"hi"}""").first)
        assertEquals(HttpStatusCode.Forbidden, api(HttpMethod.Get, "/sellers/main-canteen/support/tickets/$ticketId", cast.staff).first)
        assertEquals(HttpStatusCode.OK, api(HttpMethod.Get, "/sellers/main-canteen/support/tickets/$ticketId", cast.owner).first)

        fixture.sql("UPDATE sessions SET revoked_at = now() WHERE user_id = ?", cast.studentA.userId)
        assertEquals(HttpStatusCode.Unauthorized, api(HttpMethod.Get, "/me/support/tickets/$ticketId", cast.studentA).first)
        assertEquals(HttpStatusCode.Unauthorized, api(HttpMethod.Patch, "/me/preferences", cast.studentA, "{}").first)
        fixture.sql("UPDATE users SET is_platform_admin = FALSE WHERE id = ?", cast.admin.userId)
        assertEquals(HttpStatusCode.Forbidden, api(HttpMethod.Get, "/admin/overview", cast.admin).first)
    }

    @Test fun customerStatePersistsAcrossBackendRestart() {
        var persona: Persona? = null
        scenario { fixture, cast ->
            persona = cast.studentA
            api(HttpMethod.Patch, "/me/preferences", cast.studentA, """{"promotionalNotifications":true}""")
            api(HttpMethod.Put, "/me/favorites/seller/coffee-corner", cast.studentA)
            // Close this pool and open a fresh one (as on server restart) before the fixture is cleaned up.
            val restarted = BackendBootstrap.open(testConfig())
            try {
                val stored = restarted.read { db ->
                    db.queryOne("SELECT promotional_notifications FROM customer_preferences WHERE user_id = ?", persona!!.userId) {
                        it.getBoolean(1)
                    } to db.count("SELECT count(*) FROM favorites WHERE user_id = ?", persona!!.userId)
                }
                assertEquals(true to 1L, stored)
            } finally {
                restarted.close()
            }
            assertTrue(fixture.users.contains(persona!!.userId))
        }
    }

    @Test fun emptyHistoryGivesHonestNoDataStatus() = scenario { _, cast ->
        val tickets = api(HttpMethod.Get, "/me/support/tickets", cast.studentB).second
        assertEquals(0L, tickets.o.getValue("total").jsonPrimitive.long)
        val inbox = api(HttpMethod.Get, "/me/notifications", cast.studentB).second
        assertEquals(0, inbox.o.getValue("items").jsonArray.size)
        val dashboard = api(HttpMethod.Get, "/sellers/north-counter/dashboard-summary", cast.otherOwner).second
        assertTrue(dashboard.o.getValue("insufficientData").jsonPrimitive.boolean)
        assertNotEquals("0", dashboard.o.getValue("demoRequestCounts").toString())
    }
}
