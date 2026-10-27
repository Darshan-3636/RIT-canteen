package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.core.BackendBootstrap
import edu.msrit.canteen.backend.core.BackendConfig
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.close
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CommerceIntegrationTest {
    private data class Actor(val user:UUID,val token:String,val session:UUID)
    private data class Fixture(val context:edu.msrit.canteen.backend.core.BackendContext,val customer:Actor,val seller:Actor,val stranger:Actor)

    private fun fixture():Fixture {
        val env=System.getenv()
        assumeTrue(env["RIT_TEST_DATABASE_URL"]!=null && env["RIT_TEST_DATABASE_PASSWORD"]!=null,
            "Commerce integration tests require the isolated RIT_TEST_DATABASE_* variables")
        val config=BackendConfig(RuntimeEnvironment.LOCAL,"127.0.0.1",8080,
            env.getValue("RIT_TEST_DATABASE_URL"),env["RIT_TEST_DATABASE_USER"]?:"rit_canteen",
            env.getValue("RIT_TEST_DATABASE_PASSWORD"),true)
        val context=BackendBootstrap.open(config)
        seedCommerceFixtures(context)
        // This dedicated database is disposable: restore only the known demo fixture state between test cases.
        context.dataSource.connection.use { c ->
            c.createStatement().use { s ->
                s.executeUpdate("UPDATE seller_ordering_policies SET bulk_minimum_units=8,special_pickup_enabled=FALSE")
                s.executeUpdate("UPDATE menu_items SET availability=CASE WHEN id='lemon-rice' THEN 'UNAVAILABLE' ELSE 'AVAILABLE' END,published=TRUE,unavailable_visibility='SHOW'")
                s.executeUpdate("DELETE FROM seller_closures")
            }
        }
        val customer=actor(context,"customer");val seller=actor(context,"seller");val stranger=actor(context,"stranger")
        context.dataSource.connection.use { c ->
            c.prepareStatement("INSERT INTO seller_memberships(id,user_id,seller_id,role,supervisor_membership_id) VALUES (?,?,?,'OWNER',NULL)").use { s -> s.setObject(1,UUID.randomUUID());s.setObject(2,seller.user);s.setString(3,"north-counter");s.executeUpdate() }
            c.prepareStatement("INSERT INTO seller_hours(seller_id,day_of_week,opens_at,closes_at) VALUES ('north-counter',?,TIME '00:00',TIME '23:59') ON CONFLICT(seller_id,day_of_week) DO UPDATE SET opens_at=EXCLUDED.opens_at,closes_at=EXCLUDED.closes_at").use { s -> (1..7).forEach { s.setInt(1,it);s.addBatch() };s.executeBatch() }
            c.prepareStatement("INSERT INTO seller_hours(seller_id,day_of_week,opens_at,closes_at) VALUES ('main-canteen',?,TIME '00:00',TIME '23:59') ON CONFLICT(seller_id,day_of_week) DO UPDATE SET opens_at=EXCLUDED.opens_at,closes_at=EXCLUDED.closes_at").use { s -> (1..7).forEach { s.setInt(1,it);s.addBatch() };s.executeBatch() }
        }
        return Fixture(context,customer,seller,stranger)
    }

    private fun actor(context:edu.msrit.canteen.backend.core.BackendContext,prefix:String):Actor {
        val user=UUID.randomUUID();val session=UUID.randomUUID();val token=(prefix+UUID.randomUUID().toString().replace("-","" )+UUID.randomUUID().toString().replace("-","" )).take(64)
        val hash=MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        context.dataSource.connection.use { c ->
            c.prepareStatement("INSERT INTO users(id,email,password_hash,full_name) VALUES (?,?,?,?)").use { s -> s.setObject(1,user);s.setString(2,"$prefix-$user@example.test");s.setString(3,"integration-fixture-password-hash-000000");s.setString(4,"Commerce Integration $prefix");s.executeUpdate() }
            c.prepareStatement("INSERT INTO sessions(id,user_id,token_hash,expires_at) VALUES (?,?,?,?)").use { s -> s.setObject(1,session);s.setObject(2,user);s.setBytes(3,hash);s.setTimestamp(4,Timestamp.from(Instant.now().plusSeconds(7200)));s.executeUpdate() }
        }
        return Actor(user,token,session)
    }

    private fun app(f:Fixture, block:suspend ApplicationTestBuilder.()->Unit)=testApplication {
        application {
            install(ContentNegotiation) { json(Json { encodeDefaults=true;explicitNulls=true;ignoreUnknownKeys=false }) }
            routing { route("/api/v1") { registerCommerceRoutes(f.context) } }
        }
        block()
    }

    private fun cleanup(f:Fixture) {
        f.context.dataSource.connection.use { c -> c.autoCommit=false
            listOf(f.customer.user,f.seller.user,f.stranger.user).forEach { user ->
                c.prepareStatement("DELETE FROM bulk_request_events WHERE request_id IN (SELECT id FROM bulk_requests WHERE customer_user_id=?)").use { it.setObject(1,user);it.executeUpdate() }
                c.prepareStatement("DELETE FROM bulk_special_pickup_decisions WHERE request_id IN (SELECT id FROM bulk_requests WHERE customer_user_id=?)").use { it.setObject(1,user);it.executeUpdate() }
                c.prepareStatement("DELETE FROM bulk_quote_approvals WHERE request_id IN (SELECT id FROM bulk_requests WHERE customer_user_id=?)").use { it.setObject(1,user);it.executeUpdate() }
                c.prepareStatement("DELETE FROM bulk_quote_lines WHERE request_id IN (SELECT id FROM bulk_requests WHERE customer_user_id=?)").use { it.setObject(1,user);it.executeUpdate() }
                c.prepareStatement("DELETE FROM bulk_quote_revisions WHERE request_id IN (SELECT id FROM bulk_requests WHERE customer_user_id=?)").use { it.setObject(1,user);it.executeUpdate() }
                c.prepareStatement("DELETE FROM bulk_requests WHERE customer_user_id=?").use { it.setObject(1,user);it.executeUpdate() }
                c.prepareStatement("DELETE FROM individual_demo_order_lines WHERE order_id IN (SELECT id FROM individual_demo_orders WHERE customer_user_id=?)").use { it.setObject(1,user);it.executeUpdate() }
                c.prepareStatement("DELETE FROM individual_demo_orders WHERE customer_user_id=?").use { it.setObject(1,user);it.executeUpdate() }
            }
            c.prepareStatement("DELETE FROM seller_memberships WHERE user_id=?").use { it.setObject(1,f.seller.user);it.executeUpdate() }
            listOf(f.customer,f.seller,f.stranger).forEach { c.prepareStatement("DELETE FROM sessions WHERE id=?").use { s -> s.setObject(1,it.session);s.executeUpdate() } }
            listOf(f.customer,f.seller,f.stranger).forEach { c.prepareStatement("DELETE FROM users WHERE id=?").use { s -> s.setObject(1,it.user);s.executeUpdate() } }
            c.commit()
        }
        f.context.close()
    }

    @Test fun sellerFirstCatalogIsSellerScopedAndGuestReadable() {
        val f=fixture()
        try { app(f) {
            seedCommerceFixtures(f.context)
            f.context.dataSource.connection.use { c ->
                c.createStatement().use { s ->
                    s.executeQuery("SELECT count(*) FROM sellers").use { r -> r.next();assertEquals(4,r.getInt(1)) }
                    s.executeQuery("SELECT count(*) FROM menu_items").use { r -> r.next();assertEquals(12,r.getInt(1)) }
                }
            }
            val directory=client.get("/api/v1/sellers");assertEquals(HttpStatusCode.OK,directory.status)
            assertTrue(directory.bodyAsText().contains("main-canteen"));assertTrue(directory.bodyAsText().contains("UNKNOWN"))
            val menu=client.get("/api/v1/sellers/north-counter/menu?query=coffee");assertEquals(HttpStatusCode.OK,menu.status)
            assertTrue(menu.bodyAsText().contains("\"items\":[]"));assertFalse(menu.bodyAsText().contains("pantry-water"))
            assertEquals(HttpStatusCode.NotFound,client.get("/api/v1/sellers/north-counter/menu/masala-dosa").status)
        } } finally { cleanup(f) }
    }

    @Test fun individualDemoOrderAllowsMultiplePreparedItemsWithoutBulkMinimumAndNeverMarksPaid() {
        val f=fixture()
        try { app(f) {
            val response=client.post("/api/v1/me/individual-demo-orders") {
                header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");contentType(ContentType.Application.Json)
                setBody("""{"lines":[{"itemId":"masala-dosa","quantity":1},{"itemId":"idli","quantity":2}]}""")
            }
            assertEquals(HttpStatusCode.Created,response.status);val payload=response.bodyAsText()
            assertTrue(payload.contains("\"demoOnly\":true"));assertTrue(payload.contains("\"paymentVerified\":false"));assertTrue(payload.contains("\"subtotalPaise\":12000"))
            val history=client.get("/api/v1/me/individual-demo-orders") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}") }
            assertEquals(HttpStatusCode.OK,history.status);assertTrue(history.bodyAsText().contains("masala-dosa"))
            val packaged=client.post("/api/v1/me/individual-demo-orders") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");contentType(ContentType.Application.Json);setBody("""{"lines":[{"itemId":"water-bottle","quantity":1}]}""") }
            assertEquals(HttpStatusCode.UnprocessableEntity,packaged.status)
        } } finally { cleanup(f) }
    }

    @Test fun bulkRequestIdempotencyRevisionApprovalsAndSellerAuthorizationAreAuthoritative() {
        val f=fixture()
        try { app(f) {
            val pickup=Instant.now().plusSeconds(48*3600).toString()
            val body="""{"sellerId":"north-counter","lines":[{"itemId":"north-lunch","quantity":8}],"pickupAt":"$pickup"}"""
            fun io.ktor.client.request.HttpRequestBuilder.customer() { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");header("Idempotency-Key","commerce-replay-1");contentType(ContentType.Application.Json);setBody(body) }
            val created=client.post("/api/v1/bulk/requests") { customer() };assertEquals(HttpStatusCode.Created,created.status)
            val json=created.bodyAsText();val id=Regex("""\"id\":\"([^\"]+)\"""").find(json)!!.groupValues[1]
            assertTrue(json.contains("\"subtotalPaise\":72000"));assertTrue(json.contains("\"status\":\"SUBMITTED\""))
            val privateRead=client.get("/api/v1/me/bulk/requests/$id") { header(HttpHeaders.Authorization,"Bearer ${f.stranger.token}") }
            assertEquals(HttpStatusCode.NotFound,privateRead.status)
            val unavailable=client.patch("/api/v1/sellers/north-counter/menu/items/north-lunch/availability") { header(HttpHeaders.Authorization,"Bearer ${f.seller.token}");contentType(ContentType.Application.Json);setBody("""{"availability":"UNAVAILABLE"}""") }
            assertEquals(HttpStatusCode.OK,unavailable.status)
            val hidden=client.patch("/api/v1/sellers/north-counter/menu/items/north-lunch") { header(HttpHeaders.Authorization,"Bearer ${f.seller.token}");contentType(ContentType.Application.Json);setBody("""{"unavailableVisibility":"HIDE"}""") }
            assertEquals(HttpStatusCode.OK,hidden.status);assertTrue(hidden.bodyAsText().contains("\"published\":true"))
            assertEquals(HttpStatusCode.NotFound,client.get("/api/v1/sellers/north-counter/menu/north-lunch").status)
            val replay=client.post("/api/v1/bulk/requests") { customer() };assertEquals(HttpStatusCode.Created,replay.status);assertEquals(id,Regex("""\"id\":\"([^\"]+)\"""").find(replay.bodyAsText())!!.groupValues[1])
            val reused=client.post("/api/v1/bulk/requests") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");header("Idempotency-Key","commerce-replay-1");contentType(ContentType.Application.Json);setBody(body.replace("\"quantity\":8","\"quantity\":9")) }
            assertEquals(HttpStatusCode.Conflict,reused.status)
            val denied=client.get("/api/v1/sellers/north-counter/bulk/requests");assertEquals(HttpStatusCode.Unauthorized,denied.status)
            val sellerInbox=client.get("/api/v1/sellers/north-counter/bulk/requests") { header(HttpHeaders.Authorization,"Bearer ${f.seller.token}") }
            assertEquals(HttpStatusCode.OK,sellerInbox.status);assertTrue(sellerInbox.bodyAsText().contains(id))
            val unauthorizedEdit=client.patch("/api/v1/sellers/main-canteen/menu/items/masala-dosa/availability") { header(HttpHeaders.Authorization,"Bearer ${f.seller.token}");contentType(ContentType.Application.Json);setBody("""{"availability":"UNAVAILABLE"}""") }
            assertEquals(HttpStatusCode.Forbidden,unauthorizedEdit.status)
            val counter=client.post("/api/v1/sellers/north-counter/bulk/requests/$id/counteroffers") {
                header(HttpHeaders.Authorization,"Bearer ${f.seller.token}");contentType(ContentType.Application.Json)
                setBody("""{"expectedVersion":1,"lines":[{"itemId":"north-snack","quantity":8}],"pickupAt":"$pickup"}""")
            }
            assertEquals(HttpStatusCode.OK,counter.status);assertTrue(counter.bodyAsText().contains("\"version\":2"))
            val stale=client.post("/api/v1/bulk/requests/$id/customer-approval") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");contentType(ContentType.Application.Json);setBody("""{"expectedVersion":1}""") }
            assertEquals(HttpStatusCode.Conflict,stale.status)
            val approvals=coroutineScope {
                val customer=async { client.post("/api/v1/bulk/requests/$id/customer-approval") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");contentType(ContentType.Application.Json);setBody("""{"expectedVersion":2}""") } }
                val seller=async { client.post("/api/v1/sellers/north-counter/bulk/requests/$id/seller-approval") { header(HttpHeaders.Authorization,"Bearer ${f.seller.token}");contentType(ContentType.Application.Json);setBody("""{"expectedVersion":2}""") } }
                listOf(customer.await(),seller.await())
            }
            assertTrue(approvals.all { it.status==HttpStatusCode.OK })
            val agreed=client.get("/api/v1/me/bulk/requests/$id") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}") }.bodyAsText()
            assertTrue(agreed.contains("\"status\":\"AGREED_AWAITING_PAYMENT\""));assertFalse(agreed.contains("PAID_CONFIRMED"))
            val expiring=client.post("/api/v1/bulk/requests") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");header("Idempotency-Key","expiry-test");contentType(ContentType.Application.Json);setBody(body.replace("north-lunch","north-snack")) }
            assertEquals(HttpStatusCode.Created,expiring.status);val expiringId=Regex("""\"id\":\"([^\"]+)\"""").find(expiring.bodyAsText())!!.groupValues[1]
            f.context.dataSource.connection.use { c -> c.prepareStatement("UPDATE bulk_quote_revisions SET created_at=now()-INTERVAL '2 days',expires_at=now()-INTERVAL '1 day' WHERE request_id=? AND version=1").use { it.setObject(1,UUID.fromString(expiringId));assertEquals(1,it.executeUpdate()) } }
            val expired=client.get("/api/v1/me/bulk/requests/$expiringId") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}") }
            assertEquals(HttpStatusCode.OK,expired.status);assertTrue(expired.bodyAsText().contains("\"status\":\"EXPIRED\""))
            val expiredApproval=client.post("/api/v1/bulk/requests/$expiringId/customer-approval") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");contentType(ContentType.Application.Json);setBody("""{"expectedVersion":1}""") }
            assertEquals(HttpStatusCode.Conflict,expiredApproval.status)
        } } finally { cleanup(f) }
    }

    @Test fun sellerPolicyHoursAndRevisionBoundSpecialPickupApproval() {
        val f=fixture()
        f.context.dataSource.connection.use { c -> c.prepareStatement("DELETE FROM seller_hours WHERE seller_id='north-counter'").use { it.executeUpdate() } }
        try { app(f) {
            val ownerHeader="Bearer ${f.seller.token}"
            val regularPickup=LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2).atTime(12,0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toString()
            val unknownHours=client.post("/api/v1/bulk/requests") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");header("Idempotency-Key","unknown-hours");contentType(ContentType.Application.Json);setBody("""{"sellerId":"north-counter","lines":[{"itemId":"north-snack","quantity":8}],"pickupAt":"$regularPickup"}""") }
            assertEquals(HttpStatusCode.UnprocessableEntity,unknownHours.status);assertTrue(unknownHours.bodyAsText().contains("HOURS_UNKNOWN"))
            val policy=client.patch("/api/v1/sellers/north-counter/bulk-policy") {
                header(HttpHeaders.Authorization,ownerHeader);contentType(ContentType.Application.Json)
                setBody("""{"minimumUnits":10,"specialPickupEnabled":true}""")
            }
            assertEquals(HttpStatusCode.OK,policy.status);assertTrue(policy.bodyAsText().contains("\"bulkMinimumUnits\":10"))
            val hours=client.put("/api/v1/sellers/north-counter/hours") {
                header(HttpHeaders.Authorization,ownerHeader);contentType(ContentType.Application.Json)
                setBody("""{"hours":[${(1..7).joinToString(",") { "{\"dayOfWeek\":$it,\"opensAt\":\"09:00\",\"closesAt\":\"17:00\"}" }}]}""")
            }
            assertEquals(HttpStatusCode.NoContent,hours.status)
            val zone=ZoneId.of("Asia/Kolkata");val pickup=LocalDate.now(zone).plusDays(2).atTime(2,0).atZone(zone).toInstant().toString()
            val wrongSellerItem=client.post("/api/v1/bulk/requests") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");header("Idempotency-Key","wrong-seller-item");contentType(ContentType.Application.Json);setBody("""{"sellerId":"north-counter","lines":[{"itemId":"masala-dosa","quantity":10}],"pickupAt":"$regularPickup"}""") }
            assertEquals(HttpStatusCode.UnprocessableEntity,wrongSellerItem.status)
            val tooFew=client.post("/api/v1/bulk/requests") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");header("Idempotency-Key","under-minimum");contentType(ContentType.Application.Json);setBody("""{"sellerId":"north-counter","lines":[{"itemId":"north-snack","quantity":9}],"pickupAt":"$pickup"}""") }
            assertEquals(HttpStatusCode.UnprocessableEntity,tooFew.status)
            val request=client.post("/api/v1/bulk/requests") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");header("Idempotency-Key","special-pickup-test");contentType(ContentType.Application.Json);setBody("""{"sellerId":"north-counter","lines":[{"itemId":"north-snack","quantity":10}],"pickupAt":"$pickup","specialPickupReason":"Before opening for a scheduled group"}""") }
            assertEquals(HttpStatusCode.Created,request.status);val id=Regex("""\"id\":\"([^\"]+)\"""").find(request.bodyAsText())!!.groupValues[1]
            val customerApproval=client.post("/api/v1/bulk/requests/$id/customer-approval") { header(HttpHeaders.Authorization,"Bearer ${f.customer.token}");contentType(ContentType.Application.Json);setBody("""{"expectedVersion":1}""") }
            assertEquals(HttpStatusCode.OK,customerApproval.status);assertTrue(customerApproval.bodyAsText().contains("\"status\":\"SUBMITTED\""))
            val sellerApproval=client.post("/api/v1/sellers/north-counter/bulk/requests/$id/seller-approval") { header(HttpHeaders.Authorization,ownerHeader);contentType(ContentType.Application.Json);setBody("""{"expectedVersion":1}""") }
            assertEquals(HttpStatusCode.OK,sellerApproval.status);assertTrue(sellerApproval.bodyAsText().contains("\"status\":\"SUBMITTED\""))
            val special=client.post("/api/v1/sellers/north-counter/bulk/requests/$id/special-pickup-decision") { header(HttpHeaders.Authorization,ownerHeader);contentType(ContentType.Application.Json);setBody("""{"expectedVersion":1,"approved":true}""") }
            assertEquals(HttpStatusCode.OK,special.status);assertTrue(special.bodyAsText().contains("\"status\":\"AGREED_AWAITING_PAYMENT\""))
            assertFalse(special.bodyAsText().contains("PAID_CONFIRMED"))
        } } finally { cleanup(f) }
    }
}
