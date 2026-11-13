package edu.msrit.canteen.backend

import edu.msrit.canteen.backend.core.*
import edu.msrit.canteen.backend.features.engagement.*
import edu.msrit.canteen.backend.features.management.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlin.test.*

/** Every scenario owns a generated schema in the explicitly disposable loopback fresh database. */
class ManagementIntegrationTest {
    private data class Actor(val id: UUID,val token: String)
    private class Fixture(val context: BackendContext) {
        val admin=actor("admin",true)
        val owner=actor("owner")
        val manager=actor("manager")
        val staff=actor("staff")
        val customer=actor("customer")
        val other=actor("other")
        val ownerId=UUID.randomUUID()
        val managerId=UUID.randomUUID()
        val staffId=UUID.randomUUID()
        init {
            sql("INSERT INTO sellers(id,name,description,is_main_canteen) VALUES ('main-canteen','Main','test',true),('other-counter','Other','test',false)")
            sql("INSERT INTO seller_ordering_policies(seller_id,individual_enabled) VALUES ('main-canteen',true),('other-counter',false)")
            sql("INSERT INTO menu_categories(id,name) VALUES ('meals','Meals')")
            sql("INSERT INTO seller_categories(seller_id,category_id) VALUES ('main-canteen','meals'),('other-counter','meals')")
            sql("INSERT INTO seller_memberships(id,user_id,seller_id,role) VALUES (?,?,'main-canteen','OWNER')",ownerId,owner.id)
            sql("INSERT INTO seller_memberships(id,user_id,seller_id,role,supervisor_membership_id) VALUES (?,?,'main-canteen','MANAGER',?)",managerId,manager.id,ownerId)
            sql("INSERT INTO seller_memberships(id,user_id,seller_id,role,supervisor_membership_id) VALUES (?,?,'main-canteen','STAFF',?)",staffId,staff.id,managerId)
            sql("INSERT INTO seller_memberships(id,user_id,seller_id,role) VALUES (?,?,'other-counter','OWNER')",UUID.randomUUID(),other.id)
            listOf("employees.manage","catalog.edit","bulk.view","bulk.respond").forEach {
                sql("INSERT INTO seller_membership_capabilities(membership_id,capability) VALUES (?,?)",managerId,it)
            }
            listOf("employees.manage","catalog.edit","bulk.view").forEach {
                sql("INSERT INTO seller_membership_capabilities(membership_id,capability) VALUES (?,?)",staffId,it)
            }
        }
        fun actor(name: String,admin: Boolean=false): Actor {
            val id=UUID.randomUUID(); val token="management-${UUID.randomUUID()}-${UUID.randomUUID()}"
            sql("INSERT INTO users(id,email,password_hash,full_name,affiliation,is_platform_admin) VALUES (?,?,? ,?,'STUDENT',?)",
                id,"$name-$id@example.test","test-password-hash-long-enough",name,admin)
            sql("INSERT INTO sessions(id,user_id,token_hash,expires_at) VALUES (?,?,?,?)",UUID.randomUUID(),id,
                MessageDigest.getInstance("SHA-256").digest(token.toByteArray()),Instant.now().plusSeconds(3600))
            return Actor(id,token)
        }
        fun sql(sql: String,vararg args: Any?)=context.read { it.update(sql,*args) }
        fun count(sql: String,vararg args: Any?)=context.read { it.count(sql,*args) }
        fun bulk(): UUID {
            val id=UUID.randomUUID()
            sql("""INSERT INTO bulk_requests(id,customer_user_id,seller_id,current_version,status,idempotency_key,idempotency_hash)
                VALUES (?,?,'main-canteen',1,'SUBMITTED',?,?)""",id,customer.id,id.toString(),ByteArray(32))
            sql("""INSERT INTO bulk_quote_revisions(request_id,version,status,pickup_at,pickup_kind,subtotal_paise,expires_at,created_by)
                VALUES (?,1,'SUBMITTED',?,'REGULAR',800,?,?)""",id,Instant.now().plusSeconds(7200),Instant.now().plusSeconds(3600),customer.id)
            return id
        }
    }

    private fun isolated(block: (String,String,String)->Unit) {
        val env=System.getenv(); val url=env["RIT_TEST_FRESH_DATABASE_URL"]
        assumeTrue(url!=null && env["RIT_INTEGRATION_DISPOSABLE_FRESH_DB"]=="YES","Disposable PostgreSQL required")
        require(Regex("^jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/rit_canteen_fresh$").matches(url!!))
        val user=env.getValue("RIT_TEST_DATABASE_USER");val password=env.getValue("RIT_TEST_DATABASE_PASSWORD")
        val schema="management_${UUID.randomUUID().toString().replace("-","")}"
        DriverManager.getConnection(url,user,password).use { it.createStatement().use { q -> q.execute("CREATE SCHEMA $schema") } }
        try { block("$url?currentSchema=$schema",user,password) }
        finally {
            // Only the exact schema created above, in the guarded local disposable database.
            DriverManager.getConnection(url,user,password).use { it.createStatement().use { q -> q.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
    private fun scenario(block: suspend ApplicationTestBuilder.(Fixture)->Unit)=isolated { url,user,password ->
        val context=BackendBootstrap.open(BackendConfig(RuntimeEnvironment.LOCAL,"127.0.0.1",8080,url,user,password,false))
        try {
            val fixture=Fixture(context)
            testApplication { application { backendModule(context) }; block(fixture) }
        } finally { context.close() }
    }
    private suspend fun ApplicationTestBuilder.api(method: HttpMethod,path: String,actor: Actor?,body: String?=null,match: String?=null): HttpResponse =
        client.request("/api/v1$path") {
            this.method=method
            actor?.let { bearerAuth(it.token) }
            body?.let { contentType(ContentType.Application.Json);setBody(it) }
            match?.let { header(HttpHeaders.IfMatch,it) }
        }
    private suspend fun HttpResponse.obj()=Json.parseToJsonElement(bodyAsText()).jsonObject
    private fun JsonObject.str(key: String)=getValue(key).jsonPrimitive.content
    private suspend fun ApplicationTestBuilder.createItem(f: Fixture,id: String="managed-meal"): HttpResponse =
        api(HttpMethod.Post,"/sellers/main-canteen/catalog/items",f.owner,
            """{"id":"$id","categoryId":"meals","name":"Meal","pricePaise":100,"kind":"PREPARED","imageKey":"meal","published":true,"orderEligibility":"BOTH"}""")

    @Test fun sellerProfileReadProvidesVersionForAndroidEdits()=scenario { f ->
        val path="/sellers/main-canteen/profile"
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,path,f.other).status)
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,path,f.staff).status)
        val before=api(HttpMethod.Get,path,f.owner)
        assertEquals(HttpStatusCode.OK,before.status)
        assertEquals("0",before.obj().str("version"))
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Patch,path,f.owner,
            """{"name":"Main Canteen","description":"Updated","expectedVersion":0}""").status)
        val after=api(HttpMethod.Get,path,f.owner)
        assertEquals("1",after.obj().str("version"))
        assertEquals("Updated",after.obj().str("description"))
    }

    @Test fun catalogLifecycleAndCategoryVisibilityAffectCheckout()=scenario { f ->
        assertEquals(HttpStatusCode.Created,createItem(f).status)
        assertEquals(HttpStatusCode.Conflict,createItem(f).status)
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,"/sellers/main-canteen/catalog/items",f.other).status)
        assertEquals(HttpStatusCode.NotFound,api(HttpMethod.Get,"/sellers/other-counter/catalog/items/managed-meal",f.other).status)
        assertEquals(HttpStatusCode.BadRequest,api(HttpMethod.Patch,"/sellers/main-canteen/menu/items/managed-meal",f.owner,"""{"pricePaise":-1}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Put,"/me/cart/individual",f.customer,"""{"expectedVersion":0,"lines":[{"itemId":"managed-meal","quantity":1}]}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,"/sellers/main-canteen/catalog/items/managed-meal/archive",f.owner,"""{"expectedVersion":0}""").status)
        assertEquals(HttpStatusCode.NotFound,api(HttpMethod.Get,"/sellers/main-canteen/menu/managed-meal",null).status)
        assertFalse(api(HttpMethod.Get,"/me/cart/individual",f.customer).obj().getValue("readyForCheckout").jsonPrimitive.boolean)
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Patch,"/sellers/main-canteen/menu/items/managed-meal",f.owner,"""{"name":"Again","expectedVersion":0}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,"/sellers/main-canteen/catalog/items/managed-meal/restore",f.owner,"""{"expectedVersion":1}""").status)
        assertEquals(HttpStatusCode.NotFound,api(HttpMethod.Get,"/sellers/main-canteen/menu/managed-meal",null).status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Patch,"/sellers/main-canteen/menu/items/managed-meal",f.owner,"""{"published":true,"expectedVersion":2}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Put,"/sellers/main-canteen/catalog/categories/meals",f.owner,"""{"expectedVersion":0,"active":false,"displayName":"Lunch"}""").status)
        assertEquals(HttpStatusCode.NotFound,api(HttpMethod.Get,"/sellers/main-canteen/menu/managed-meal",null).status)
        assertFalse(api(HttpMethod.Get,"/me/cart/individual",f.customer).obj().getValue("readyForCheckout").jsonPrimitive.boolean)
        assertEquals(1L,f.count("SELECT count(*) FROM menu_items WHERE id='managed-meal'"))
    }

    @Test fun itemAndSellerEligibilityAreEnforcedInBothCarts()=scenario { f ->
        assertEquals(HttpStatusCode.Created,createItem(f).status)
        val individual = """{"expectedVersion":0,"lines":[{"itemId":"managed-meal","quantity":1}]}"""
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Put,"/me/cart/individual",f.customer,individual).status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Patch,"/sellers/main-canteen/menu/items/managed-meal",f.owner,
            """{"orderEligibility":"BULK_ONLY","expectedVersion":0}""").status)
        assertFalse(api(HttpMethod.Get,"/me/cart/individual",f.customer).obj().getValue("readyForCheckout").jsonPrimitive.boolean)
        assertEquals(HttpStatusCode.UnprocessableEntity,api(HttpMethod.Put,"/me/cart/individual",f.customer,
            """{"expectedVersion":1,"lines":[{"itemId":"managed-meal","quantity":1}]}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Put,"/me/draft/bulk",f.customer,
            """{"expectedVersion":0,"sellerId":"main-canteen","lines":[{"itemId":"managed-meal","quantity":1}]}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Patch,"/sellers/main-canteen/menu/items/managed-meal",f.owner,
            """{"orderEligibility":"INDIVIDUAL_ONLY","expectedVersion":1}""").status)
        assertFalse(api(HttpMethod.Get,"/me/draft/bulk",f.customer).obj().getValue("lines").jsonArray[0]
            .jsonObject.getValue("available").jsonPrimitive.boolean)
        assertEquals(HttpStatusCode.UnprocessableEntity,api(HttpMethod.Put,"/me/draft/bulk",f.customer,
            """{"expectedVersion":1,"sellerId":"main-canteen","lines":[{"itemId":"managed-meal","quantity":1}]}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Put,"/me/cart/individual",f.customer,
            """{"expectedVersion":1,"lines":[{"itemId":"managed-meal","quantity":1}]}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Patch,"/sellers/main-canteen/bulk-policy",f.owner,
            """{"individualEnabled":false}""").status)
        assertFalse(api(HttpMethod.Get,"/me/cart/individual",f.customer).obj().getValue("readyForCheckout").jsonPrimitive.boolean)
    }

    @Test fun membershipDelegationCannotEscalateOrCrossSellerAndRevocationIsLive()=scenario { f ->
        val path="/sellers/main-canteen/memberships/${f.staffId}/capabilities"
        assertEquals(HttpStatusCode.BadRequest,api(HttpMethod.Patch,"/sellers/main-canteen/memberships/${f.staffId}",f.owner,
            """{"role":"STAFF","supervisorMembershipId":"${f.managerId}","reason":"missing version"}""").status)
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Put,path,f.staff,"""{"expectedVersion":0,"reason":"self","capabilities":["catalog.edit"]}""").status)
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Put,path,f.manager,"""{"expectedVersion":0,"reason":"escalate","capabilities":["reports.export"]}""").status)
        assertEquals(HttpStatusCode.BadRequest,api(HttpMethod.Put,path,f.owner,"""{"expectedVersion":0,"reason":"unknown","capabilities":["root.all"]}""").status)
        assertEquals(HttpStatusCode.NotFound,api(HttpMethod.Put,"/sellers/other-counter/memberships/${f.staffId}/capabilities",f.other,"""{"expectedVersion":0,"reason":"wrong seller","capabilities":[]}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Put,path,f.manager,"""{"expectedVersion":0,"reason":"revoke","capabilities":[]}""").status)
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,"/sellers/main-canteen/catalog/items",f.staff).status)
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Put,path,f.manager,"""{"expectedVersion":0,"reason":"stale","capabilities":[]}""").status)
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Patch,"/sellers/main-canteen/memberships/${f.managerId}",f.manager,
            """{"role":"OWNER","expectedVersion":0,"reason":"promote self"}""").status)
        val before=f.count("SELECT count(*) FROM seller_memberships")
        assertEquals(HttpStatusCode.BadRequest,api(HttpMethod.Post,"/sellers/main-canteen/memberships",f.owner,
            """{"userId":"${f.customer.id}","role":"STAFF","supervisorMembershipId":"${f.ownerId}","reason":"wrong parent"}""").status)
        assertEquals(before,f.count("SELECT count(*) FROM seller_memberships"))
    }

    @Test fun supervisorRoleChangesAndLastOwnerAreGuarded()=scenario { f ->
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Patch,"/admin/sellers/main-canteen/memberships/${f.managerId}",f.admin,
            """{"role":"OWNER","expectedVersion":0,"reason":"has children"}""").status)
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Patch,"/admin/sellers/main-canteen/memberships/${f.ownerId}",f.admin,
            """{"role":"OWNER","active":false,"expectedVersion":0,"reason":"last owner"}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,"/admin/sellers/main-canteen/suspend",f.admin,"""{"expectedVersion":0,"reason":"pause"}""").status)
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,"/sellers/main-canteen/catalog/items",f.owner).status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Patch,"/admin/sellers/main-canteen/memberships/${f.ownerId}",f.admin,
            """{"role":"OWNER","active":false,"expectedVersion":0,"reason":"owner leaving"}""").status)
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Post,"/admin/sellers/main-canteen/activate",f.admin,"""{"expectedVersion":1,"reason":"no owner"}""").status)
        assertEquals(0L,f.count("SELECT count(*) FROM sellers WHERE id='main-canteen' AND active"))
    }

    @Test fun onboardingCreatesStableInactiveSellerAndTruthfulDraftWithAudit()=scenario { f ->
        val draft=api(HttpMethod.Post,"/admin/setup-drafts",f.admin,"""{"proposedSellerId":"approved-kiosk","sellerName":"Approved"}""")
        assertEquals(HttpStatusCode.Created,draft.status);val id=draft.obj().str("id")
        val body="""{"expectedVersion":0,"ownerUserId":"${f.customer.id}","reason":"approved review"}"""
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Post,"/admin/setup-drafts/$id/approve",f.owner,body).status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,"/admin/setup-drafts/$id/approve",f.admin,body).status)
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Post,"/admin/setup-drafts/$id/approve",f.admin,body).status)
        assertEquals(1L,f.count("SELECT count(*) FROM sellers WHERE id='approved-kiosk' AND NOT active"))
        assertEquals(1L,f.count("SELECT count(*) FROM seller_memberships WHERE seller_id='approved-kiosk' AND user_id=? AND role='OWNER'",f.customer.id))
        val listed=api(HttpMethod.Get,"/admin/setup-drafts",f.admin).obj().getValue("items").jsonArray.single().jsonObject
        assertTrue(listed.getValue("sellerCreated").jsonPrimitive.boolean)
        assertFalse(listed.getValue("invitationSent").jsonPrimitive.boolean)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,"/admin/sellers/approved-kiosk/activate",f.admin,"""{"expectedVersion":0,"reason":"ready"}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Get,"/sellers/approved-kiosk/catalog/items",f.customer).status)
        assertEquals(1L,f.count("SELECT count(*) FROM admin_audit_events WHERE action='admin.setup_draft.approve' AND target_id=?",id))
    }

    @Test fun accountSuspensionRevokesSessionsAndLastAdminOwnerChecksRollback()=scenario { f ->
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Post,"/admin/users/${f.owner.id}/status",f.admin,"""{"status":"SUSPENDED","expectedVersion":0,"reason":"sole owner"}""").status)
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Put,"/admin/users/${f.admin.id}/platform-admin",f.admin,"""{"enabled":false,"expectedVersion":0,"reason":"last admin"}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,"/admin/users/${f.customer.id}/status",f.admin,"""{"status":"SUSPENDED","expectedVersion":0,"reason":"review"}""").status)
        assertEquals(HttpStatusCode.Unauthorized,api(HttpMethod.Get,"/me/cart/individual",f.customer).status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,"/admin/users/${f.customer.id}/status",f.admin,"""{"status":"ACTIVE","expectedVersion":1,"reason":"review complete"}""").status)
        assertEquals(HttpStatusCode.Unauthorized,api(HttpMethod.Get,"/me/cart/individual",f.customer).status)
        assertEquals(1L,f.count("SELECT count(*) FROM users WHERE id=? AND status='ACTIVE'",f.owner.id))
        assertEquals(0L,f.count("SELECT count(*) FROM admin_audit_events WHERE action='admin.user.admin_flag'"))
    }

    @Test fun hoursPoliciesAndClosuresShareVersionAndAudit()=scenario { f ->
        assertEquals(HttpStatusCode.NoContent,api(HttpMethod.Put,"/sellers/main-canteen/hours",f.owner,
            """{"hours":[{"dayOfWeek":1,"opensAt":"08:00","closesAt":"18:00"}]}""","0").status)
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Patch,"/sellers/main-canteen/bulk-policy",f.owner,"""{"minimumUnits":12}""","0").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Patch,"/sellers/main-canteen/bulk-policy",f.owner,"""{"minimumUnits":12}""","1").status)
        assertEquals(HttpStatusCode.NoContent,api(HttpMethod.Put,"/sellers/main-canteen/closures/2027-01-01",f.owner,"""{"reason":"Holiday"}""","2").status)
        assertEquals("3",api(HttpMethod.Get,"/sellers/main-canteen/operations/version",f.owner).obj().str("version"))
        assertEquals(HttpStatusCode.BadRequest,api(HttpMethod.Put,"/sellers/main-canteen/hours",f.owner,
            """{"hours":[{"dayOfWeek":1,"opensAt":"18:00","closesAt":"08:00"}]}""").status)
        assertEquals(3L,f.count("SELECT count(*) FROM admin_audit_events WHERE action='seller.operations.update'"))
    }

    @Test fun bulkRejectionAndIndividualTransitionsEnforceSellerAndState()=scenario { f ->
        val id=f.bulk();val body="""{"expectedVersion":1,"reason":"Cannot supply"}"""
        val sellerList=api(HttpMethod.Get,"/sellers/main-canteen/bulk/requests",f.owner).obj()
        val sellerRequest=sellerList.getValue("requests").jsonArray.single().jsonObject
        assertEquals("customer",sellerRequest.str("customerName"))
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Post,"/sellers/main-canteen/bulk/requests/$id/reject",f.other,body).status)
        assertEquals(HttpStatusCode.NotFound,api(HttpMethod.Post,"/sellers/other-counter/bulk/requests/$id/reject",f.other,body).status)
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Post,"/sellers/main-canteen/bulk/requests/$id/reject",f.owner,"""{"expectedVersion":2,"reason":"stale"}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,"/sellers/main-canteen/bulk/requests/$id/reject",f.owner,body).status)
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Post,"/sellers/main-canteen/bulk/requests/$id/seller-approval",f.owner,"""{"expectedVersion":1}""").status)
        val order=UUID.randomUUID()
        f.sql("INSERT INTO individual_demo_orders(id,customer_user_id,seller_id,subtotal_paise,status,demo_payment_outcome) VALUES (?,?,'main-canteen',100,'DEMO_PAYMENT_SIMULATED','SUCCESS')",order,f.customer.id)
        val path="/sellers/main-canteen/individual-demo-orders/$order/demo-status"
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Post,path,f.owner,"""{"status":"DEMO_COMPLETED"}""").status)
        for(state in listOf("DEMO_ACCEPTED","DEMO_ACCEPTED","DEMO_READY"))
            assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,path,f.owner,"""{"status":"$state"}""").status)
        val customerOrder=api(HttpMethod.Get,"/me/individual-demo-orders",f.customer).obj()
            .getValue("orders").jsonArray.first().jsonObject
        val code=customerOrder.str("pickupCode")
        assertTrue(customerOrder.str("pickupReference").startsWith("RIT-"))
        assertFalse(api(HttpMethod.Get,"/sellers/main-canteen/individual-demo-orders",f.owner)
            .bodyAsText().contains(code))
        assertEquals(HttpStatusCode.UnprocessableEntity,api(HttpMethod.Post,path,f.owner,
            """{"status":"DEMO_COMPLETED","pickupCode":"WRONG-CODE12"}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,path,f.owner,
            """{"status":"DEMO_COMPLETED","pickupCode":"$code"}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,path,f.owner,
            """{"status":"DEMO_COMPLETED","pickupCode":"$code"}""").status)
        assertEquals(1L,f.count("SELECT count(*) FROM individual_demo_orders WHERE id=? AND NOT payment_verified AND demo_only",order))
        assertEquals(3L,f.count("SELECT count(*) FROM admin_audit_events WHERE action='seller.individual.status' AND target_id=?",order.toString()))
    }

    @Test fun reportsAreBoundedCsvAndSelectionsCannotCrossSellers()=scenario { f ->
        f.bulk()
        val export=api(HttpMethod.Get,"/sellers/main-canteen/reports/export",f.owner)
        assertEquals(HttpStatusCode.OK,export.status)
        assertTrue(export.bodyAsText().contains("DEMO_UNVERIFIED"))
        assertTrue(export.headers[HttpHeaders.ContentType]!!.contains("text/csv"))
        assertEquals(HttpStatusCode.BadRequest,api(HttpMethod.Get,"/sellers/main-canteen/reports/export?from=2020-01-01&to=2026-10-31",f.owner).status)
        val selection=api(HttpMethod.Post,"/sellers/other-counter/reports/selections",f.other,"""{"name":"Other","metrics":["SUPPORT_TICKETS"]}""").obj().str("id")
        assertEquals(HttpStatusCode.NotFound,api(HttpMethod.Get,"/sellers/main-canteen/reports/export?selectionId=$selection",f.owner).status)
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,"/sellers/main-canteen/reports/export",f.manager).status)
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,"/admin/reports/export",f.owner).status)
        val platform=api(HttpMethod.Get,"/admin/reports/export",f.admin)
        assertEquals(HttpStatusCode.OK,platform.status)
        assertTrue(platform.headers[HttpHeaders.ContentType]!!.contains("text/csv"))
        assertTrue(platform.bodyAsText().contains("STORED_OPERATIONS_DEMO_ORDERS_UNVERIFIED"))
        assertEquals(1L,f.count("SELECT count(*) FROM admin_audit_events WHERE action='admin.report.export'"))
    }

    @Test fun concurrentCatalogVersionsAndLastOwnerRemovalAllowOnlyOneWinner()=scenario { f ->
        createItem(f)
        val edits=coroutineScope { listOf("One","Two").map { name -> async {
            api(HttpMethod.Patch,"/sellers/main-canteen/menu/items/managed-meal",f.owner,"""{"name":"$name","expectedVersion":0}""").status
        } }.map { it.await() } }
        assertEquals(setOf(HttpStatusCode.OK,HttpStatusCode.Conflict),edits.toSet())
        val second=f.actor("second-owner")
        val secondId=UUID.randomUUID()
        f.sql("INSERT INTO seller_memberships(id,user_id,seller_id,role) VALUES (?,?,'main-canteen','OWNER')",secondId,second.id)
        val removals=coroutineScope { listOf(f.ownerId,secondId).map { id -> async {
            api(HttpMethod.Patch,"/admin/sellers/main-canteen/memberships/$id",f.admin,
                """{"role":"OWNER","active":false,"expectedVersion":0,"reason":"concurrent removal"}""").status
        } }.map { it.await() } }
        assertEquals(setOf(HttpStatusCode.OK,HttpStatusCode.Conflict),removals.toSet())
        assertEquals(1L,f.count("SELECT count(*) FROM seller_memberships WHERE seller_id='main-canteen' AND role='OWNER' AND active"))
    }

    @Test fun globalCategoriesAreAdminOnlyAndSellerAssignmentsRemainIndependent()=scenario { f ->
        val body="""{"id":"drinks","name":"Drinks"}"""
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Post,"/admin/catalog/categories",f.owner,body).status)
        assertEquals(HttpStatusCode.Created,api(HttpMethod.Post,"/admin/catalog/categories",f.admin,body).status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Put,"/sellers/main-canteen/catalog/categories/drinks",f.owner,
            """{"expectedVersion":0,"displayName":"Cold drinks","sortOrder":2}""").status)
        assertEquals(0L,f.count("SELECT count(*) FROM seller_categories WHERE seller_id='other-counter' AND category_id='drinks'"))
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Put,"/sellers/main-canteen/catalog/categories/drinks",f.owner,
            """{"expectedVersion":0,"active":false}""").status)
        createItem(f)
        assertEquals(HttpStatusCode.NoContent,api(HttpMethod.Put,"/admin/catalog/categories/meals",f.admin,
            """{"id":"meals","name":"Meals","active":false}""").status)
        assertEquals(HttpStatusCode.NotFound,api(HttpMethod.Get,"/sellers/main-canteen/menu/managed-meal",null).status)
    }

    @Test fun metricsAreScopedAndRejectionDoesNotCreateSeller()=scenario { f ->
        f.bulk();createItem(f)
        val seller=api(HttpMethod.Get,"/sellers/main-canteen/analytics/operations",f.owner)
        assertEquals(HttpStatusCode.OK,seller.status)
        val metrics=seller.obj()
        assertEquals("1",metrics.getValue("bulkRequestsByStatus").jsonObject.str("SUBMITTED"))
        assertEquals(JsonNull,metrics.getValue("verifiedRevenuePaise"))
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,"/admin/dashboard/metrics",f.owner).status)
        assertEquals("PLATFORM",api(HttpMethod.Get,"/admin/dashboard/metrics",f.admin).obj().str("scope"))
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,"/sellers/main-canteen/analytics/operations",f.other).status)
        val id=api(HttpMethod.Post,"/admin/setup-drafts",f.admin,"""{"proposedSellerId":"rejected-kiosk","sellerName":"Rejected"}""").obj().str("id")
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,"/admin/setup-drafts/$id/reject",f.admin,"""{"expectedVersion":0,"reason":"incomplete application"}""").status)
        assertEquals(0L,f.count("SELECT count(*) FROM sellers WHERE id='rejected-kiosk'"))
        assertEquals("REJECTED",api(HttpMethod.Get,"/admin/setup-drafts/$id",f.admin).obj().str("decision"))
    }

    @Test fun validEmployeeCreationReassignmentAndDeactivationKeepIdentity()=scenario { f ->
        val employee=f.actor("new-staff")
        val created=api(HttpMethod.Post,"/sellers/main-canteen/memberships",f.manager,
            """{"userId":"${employee.id}","role":"STAFF","supervisorMembershipId":"${f.managerId}","capabilities":["catalog.edit"],"reason":"hired"}""")
        assertEquals(HttpStatusCode.Created,created.status)
        val id=created.obj().str("id")
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Get,"/sellers/main-canteen/catalog/items",employee).status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Patch,"/sellers/main-canteen/memberships/$id",f.manager,
            """{"role":"STAFF","supervisorMembershipId":"${f.managerId}","active":false,"capabilities":[],"expectedVersion":0,"reason":"left"}""").status)
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,"/sellers/main-canteen/catalog/items",employee).status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Patch,"/sellers/main-canteen/memberships/$id",f.owner,
            """{"role":"MANAGER","supervisorMembershipId":"${f.ownerId}","active":true,"capabilities":["catalog.edit"],"expectedVersion":1,"reason":"promoted"}""").status)
        assertEquals(1L,f.count("SELECT count(*) FROM seller_memberships WHERE id=? AND user_id=? AND role='MANAGER'",UUID.fromString(id),employee.id))
    }

    @Test fun managedCategoryAndArchivedItemSurviveDemoRestart()=isolated { url,user,password ->
        val config=BackendConfig(RuntimeEnvironment.LOCAL,"127.0.0.1",8080,url,user,password,true)
        val first=BackendBootstrap.open(config)
        try {
            edu.msrit.canteen.backend.features.commerce.seedCommerceFixtures(first)
            first.read { db ->
                db.update("""INSERT INTO seller_categories(seller_id,category_id,display_name,active,version)
                    VALUES ('main-canteen','packs','Custom category',false,1)""")
                db.update("UPDATE menu_items SET archived_at=now(),published=false,version=1 WHERE id='masala-dosa'")
            }
        } finally { first.close() }
        val restarted=BackendBootstrap.open(config)
        try {
            edu.msrit.canteen.backend.features.commerce.seedCommerceFixtures(restarted)
            restarted.read { db ->
                assertEquals(1L,db.count("SELECT count(*) FROM seller_categories WHERE seller_id='main-canteen' AND category_id='packs' AND display_name='Custom category' AND NOT active AND version=1"))
                assertEquals(1L,db.count("SELECT count(*) FROM menu_items WHERE id='masala-dosa' AND archived_at IS NOT NULL AND NOT published AND version=1"))
            }
        } finally { restarted.close() }
    }

    @Test fun individualNoticesStayScopedAndDoNotDuplicateOnStatusRetry()=scenario { f ->
        createItem(f)
        val created=api(HttpMethod.Post,"/me/individual-demo-orders",f.customer,
            """{"lines":[{"itemId":"managed-meal","quantity":1}]}""")
        assertEquals(HttpStatusCode.Created,created.status)
        val orderId=created.obj().str("id")
        // Orders are prepaid: the seller hears about an order only once it is paid.
        assertEquals(0L,f.count("SELECT count(*) FROM notification_inbox WHERE user_id=? AND event_type='INDIVIDUAL_ORDER_UPDATE'",f.owner.id))
        f.sql("UPDATE individual_demo_orders SET status='DEMO_PAYMENT_SIMULATED',demo_payment_outcome='SUCCESS' WHERE id=?",UUID.fromString(orderId))
        f.context.transaction { db -> edu.msrit.canteen.backend.features.commerce.notifyIndividualEvent(db,UUID.fromString(orderId),"DEMO_PAYMENT_SIMULATED") }
        assertEquals(1L,f.count("SELECT count(*) FROM notification_inbox WHERE user_id=? AND event_key=?",
            f.owner.id,"individual:$orderId:DEMO_PAYMENT_SIMULATED"))
        assertEquals(0L,f.count("SELECT count(*) FROM notification_inbox WHERE user_id=?",f.other.id))
        val path="/sellers/main-canteen/individual-demo-orders/$orderId/demo-status"
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,path,f.owner,"""{"status":"DEMO_ACCEPTED"}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,path,f.owner,"""{"status":"DEMO_ACCEPTED"}""").status)
        assertEquals(1L,f.count("SELECT count(*) FROM notification_inbox WHERE user_id=? AND event_key=?",
            f.customer.id,"individual:$orderId:DEMO_ACCEPTED"))
        assertEquals(0L,f.count("SELECT count(*) FROM notification_inbox WHERE user_id=?",f.other.id))
    }

    @Test fun promotionDiscountIsScopedQuotedAndSnapshottedInPaise()=scenario { f ->
        createItem(f)
        val start=Instant.now().minusSeconds(60).toString()
        val end=Instant.now().plusSeconds(3600).toString()
        val body="""{"title":"Lunch offer","kind":"PERCENT_DISCOUNT","discountPercent":25,
            "startsAt":"$start","endsAt":"$end","published":true,"itemIds":["managed-meal"]}"""
        val path="/sellers/main-canteen/promotions"
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Post,path,f.customer,body).status)
        assertEquals(HttpStatusCode.BadRequest,api(HttpMethod.Post,"/sellers/other-counter/promotions",f.other,body).status)
        val created=api(HttpMethod.Post,path,f.owner,body)
        assertEquals(HttpStatusCode.Created,created.status)
        val id=created.obj().str("id")
        assertEquals(1,f.count("SELECT count(*) FROM promotions WHERE id=? AND seller_id='main-canteen'",UUID.fromString(id)).toInt())
        assertEquals(1,api(HttpMethod.Get,"/me/offers?sellerId=main-canteen",f.customer).obj()
            .getValue("items").jsonArray.size)
        val cart=api(HttpMethod.Put,"/me/cart/individual",f.customer,
            """{"expectedVersion":0,"lines":[{"itemId":"managed-meal","quantity":2}]}""")
        assertEquals(HttpStatusCode.OK,cart.status)
        val quote=api(HttpMethod.Post,"/me/cart/individual/offer-quote",f.customer,"""{"promotionId":"$id"}""")
        assertEquals(HttpStatusCode.OK,quote.status)
        assertEquals("50",quote.obj().str("discountPaise"))
        val checkoutBody="""{"expectedVersion":1,"promotionId":"$id"}"""
        val order=api(HttpMethod.Post,"/me/cart/individual/checkout",f.customer,checkoutBody,match=null)
        // This helper does not send Idempotency-Key; use the client request below.
        assertEquals(HttpStatusCode.BadRequest,order.status)
        val saved=client.post("/api/v1/me/cart/individual/checkout") {
            bearerAuth(f.customer.token);contentType(ContentType.Application.Json)
            header("Idempotency-Key","promotion-${UUID.randomUUID()}")
            setBody(checkoutBody)
        }
        assertEquals(HttpStatusCode.Created,saved.status)
        val savedId=saved.obj().str("id")
        assertEquals("200",saved.obj().str("subtotalPaise"))
        assertEquals("50",saved.obj().str("discountPaise"))
        assertEquals("150",saved.obj().str("totalPaise"))
        assertEquals(150L,f.context.read { db -> db.queryOne(
            "SELECT subtotal_paise-discount_paise FROM individual_demo_orders WHERE id=?",UUID.fromString(savedId)) { it.getLong(1) }!! })
        val crossSellerUpdate="""{"title":"Lunch offer","kind":"PERCENT_DISCOUNT","discountPercent":25,
            "startsAt":"$start","endsAt":"$end","published":true,"itemIds":["managed-meal"],"expectedVersion":0}"""
        assertEquals(HttpStatusCode.NotFound,api(HttpMethod.Put,"/sellers/other-counter/promotions/$id",f.other,
            crossSellerUpdate).status)
    }

    @Test fun inventoryLedgerIsSellerScopedIdempotentAndNeverNegative()=scenario { f ->
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,"/sellers/main-canteen/inventory/overview",f.other).status)
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,"/sellers/main-canteen/inventory/overview",f.staff).status)
        val base="/sellers/main-canteen/inventory"
        val category=api(HttpMethod.Post,"$base/categories",f.owner,"""{"name":"Kitchen"}""")
        assertEquals(HttpStatusCode.Created,category.status)
        val categoryId=category.obj().str("id")
        val material=api(HttpMethod.Post,"$base/materials",f.owner,
            """{"categoryId":"$categoryId","name":"Rice","baseUnitCode":"g"}""")
        assertEquals(HttpStatusCode.Created,material.status)
        val materialId=material.obj().str("id")
        val supplier=api(HttpMethod.Post,"$base/suppliers",f.owner,"""{"name":"Local supplier"}""")
        assertEquals(HttpStatusCode.Created,supplier.status)
        val supplierId=supplier.obj().str("id")
        val po=api(HttpMethod.Post,"$base/purchase-orders",f.owner,
            """{"supplierId":"$supplierId","lines":[{"materialId":"$materialId","unitCode":"kg","quantity":"2","unitPricePaise":10000}]}""")
        assertEquals(HttpStatusCode.Created,po.status)
        val poId=po.obj().str("id")
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Post,"$base/purchase-orders/$poId/placed",f.owner,
            """{"expectedVersion":0}""").status)
        val receiptId=UUID.randomUUID()
        val receiptBody="""{"requestId":"$receiptId","purchaseOrderId":"$poId","lines":[{"materialId":"$materialId",
            "unitCode":"kg","quantity":"1","batchCode":"RICE-1","purchaseOrderLineNo":1}]}"""
        val concurrentReceipts=coroutineScope { List(2) { async {
            api(HttpMethod.Post,"$base/receipts",f.owner,receiptBody)
        } }.map { it.await() } }
        assertTrue(concurrentReceipts.all { it.status==HttpStatusCode.Created })
        val first=concurrentReceipts.first()
        val batchId=first.obj().getValue("lines").jsonArray.first().jsonObject.str("batchId")
        assertEquals(HttpStatusCode.Created,api(HttpMethod.Post,"$base/receipts",f.owner,receiptBody).status)
        assertEquals(1L,f.count("SELECT count(*) FROM inventory_stock_transactions WHERE seller_id='main-canteen' AND source_kind='RECEIPT'"))
        assertEquals(0L,f.count("SELECT count(*) FROM inventory_stock_transactions WHERE seller_id='other-counter'"))
        val wasteId=UUID.randomUUID()
        val waste="""{"requestId":"$wasteId","materialId":"$materialId","batchId":"$batchId","quantityBase":"100","reason":"Spoiled"}"""
        assertEquals(HttpStatusCode.Created,api(HttpMethod.Post,"$base/wastage",f.owner,waste).status)
        assertEquals(HttpStatusCode.Created,api(HttpMethod.Post,"$base/wastage",f.owner,waste).status)
        assertEquals(1L,f.count("SELECT count(*) FROM inventory_stock_transactions WHERE seller_id='main-canteen' AND source_kind='WASTAGE'"))
        assertEquals(HttpStatusCode.Conflict,api(HttpMethod.Post,"$base/adjustments",f.owner,
            """{"requestId":"${UUID.randomUUID()}","materialId":"$materialId","batchId":"$batchId","deltaBase":"-1000","reason":"Count"}""").status)
        assertEquals("900.000000",f.context.read { db -> db.queryOne("SELECT on_hand_base FROM inventory_materials WHERE id=?",
            UUID.fromString(materialId)) { it.getBigDecimal(1).toPlainString() }!! })
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Put,"$base/reorder-rules/$materialId",f.owner,
            """{"thresholdBase":"1000","targetBase":"2000"}""").status)
        val overview=api(HttpMethod.Get,"$base/overview",f.owner)
        assertEquals(HttpStatusCode.OK,overview.status)
        assertEquals("LOW_STOCK",overview.obj().getValue("alerts").jsonArray.first().jsonObject.str("kind"))
        f.context.transaction { db -> db.update("UPDATE inventory_stock_batches SET expires_on=CURRENT_DATE+3 WHERE id=?",
            UUID.fromString(batchId)) }
        val expiring=api(HttpMethod.Get,"$base/alerts",f.owner)
        assertEquals(HttpStatusCode.OK,expiring.status)
        assertTrue(Json.parseToJsonElement(expiring.bodyAsText()).jsonArray.any {
            it.jsonObject.str("kind")=="EXPIRY" })
        assertEquals(HttpStatusCode.Forbidden,api(HttpMethod.Get,"$base/report.csv",f.other).status)
        val report=api(HttpMethod.Get,"$base/report.csv",f.owner)
        assertEquals(HttpStatusCode.OK,report.status)
        assertTrue(report.bodyAsText().contains("\"Rice\",\"Kitchen\",\"g\",\"900.000000\""))
        createItem(f)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Put,"$base/recipes/managed-meal",f.owner,
            """{"ingredients":[{"materialId":"$materialId","quantityBase":"50"}]}""").status)
        assertEquals(HttpStatusCode.OK,api(HttpMethod.Get,"$base/recipes/managed-meal",f.owner).status)
    }

    @Test fun v047UpgradePreservesIdentitiesOrdersAndUatAttempts()=isolated { url,user,password ->
        fun migration()=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration")
        migration().target("047").load().migrate()
        val customer=UUID.randomUUID();val order=UUID.randomUUID();val payment=UUID.randomUUID()
        DriverManager.getConnection(url,user,password).use { db ->
            db.update("INSERT INTO users(id,email,password_hash,full_name,affiliation) VALUES (?,'upgrade@example.test','historical-password-hash','Customer','STUDENT')",customer)
            db.update("INSERT INTO sellers(id,name,description) VALUES ('historic-counter','Historic','existing')")
            db.update("INSERT INTO individual_demo_orders(id,customer_user_id,seller_id,subtotal_paise) VALUES (?,?,'historic-counter',1200)",order,customer)
            db.update("""INSERT INTO sandbox_payment_attempts(id,customer_user_id,seller_id,source_type,source_id,amount_paise,
                merchant_order_id,idempotency_key,state,expires_at) VALUES (?,?,'historic-counter','INDIVIDUAL',?,1200,'historical-merchant','historical-key','CANCELLED',now()+interval '1 hour')""",payment,customer,order)
            val before=db.queryOne("SELECT row_to_json(p)::text FROM sandbox_payment_attempts p WHERE id=?",payment) { it.getString(1) }
            migration().load().migrate();migration().load().migrate()
            assertEquals(before,db.queryOne("SELECT row_to_json(p)::text FROM sandbox_payment_attempts p WHERE id=?",payment) { it.getString(1) })
            assertEquals("historical-password-hash",db.queryOne("SELECT password_hash FROM users WHERE id=?",customer) { it.getString(1) })
            assertEquals(1200L,db.queryOne("SELECT subtotal_paise FROM individual_demo_orders WHERE id=?",order) { it.getLong(1) })
            assertEquals(23L,db.count("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL"))
            // V054: the uncollected order now has a 4-digit PIN, and new orders get distinct PINs.
            val pins=mutableListOf(db.queryOne("SELECT pickup_code FROM individual_demo_orders WHERE id=?",order) { it.getString(1) }!!)
            repeat(30) {
                val next=UUID.randomUUID()
                db.update("INSERT INTO individual_demo_orders(id,customer_user_id,seller_id,subtotal_paise) VALUES (?,?,'historic-counter',100)",next,customer)
                pins+=db.queryOne("SELECT pickup_code FROM individual_demo_orders WHERE id=?",next) { it.getString(1) }!!
            }
            assertTrue(pins.all { Regex("[0-9]{4}").matches(it) }, pins.toString())
            assertEquals(pins.size,pins.toSet().size)
            assertFailsWith<java.sql.SQLException> {
                db.update("UPDATE individual_demo_orders SET pickup_code=? WHERE id<>? AND seller_id='historic-counter' AND pickup_code<>?",pins[0],order,pins[0])
            }
        }
    }
}
