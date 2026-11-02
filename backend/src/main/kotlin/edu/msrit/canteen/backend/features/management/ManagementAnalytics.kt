package edu.msrit.canteen.backend.features.management

import edu.msrit.canteen.backend.api.requirePlatformAdmin
import edu.msrit.canteen.backend.api.requireSellerCapability
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.transaction
import edu.msrit.canteen.backend.features.engagement.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.*
import java.time.temporal.ChronoUnit
import java.util.UUID

internal fun Route.registerManagementAnalytics(context: BackendContext) {
    get("/admin/dashboard/metrics") { call.handled {
        call.requirePlatformAdmin(context) ?: return@handled
        call.respond(managementMetrics(context,call,null))
    } }
    get("/admin/reports/export") { call.handled {
        val actor=call.requirePlatformAdmin(context) ?: return@handled
        val metrics=managementMetrics(context,call,null)
        val rows=buildList {
            add("Metric,Value")
            for(key in listOf("scope","from","to","timezone","source","activeSellers",
                "activeMemberships","catalogItems","archivedItems","suspendedUsers","pendingSetupDrafts"))
                add("${reportCell(key)},${reportCell(metrics[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content.orEmpty())}")
            for(key in listOf("individualDemoOrdersByStatus","bulkRequestsByStatus"))
                metrics[key]?.jsonObject?.forEach { (status,value) ->
                    add("${reportCell("$key.$status")},${reportCell(value.jsonPrimitive.content)}")
                }
        }
        context.transaction { db ->
            context.requireAdminInTx(db,actor)
            recordAudit(db,actor.userId,"ADMIN","admin.report.export",
                "PLATFORM_REPORT",UUID.randomUUID().toString(),detail=mapOf(
                    "from" to (metrics["from"]?.toString() ?: ""),"to" to (metrics["to"]?.toString() ?: "")))
        }
        call.response.header(HttpHeaders.ContentDisposition,"attachment; filename=platform-report.csv")
        call.respondText(rows.joinToString("\n",postfix="\n"),ContentType.parse("text/csv; charset=utf-8"))
    } }
    get("/sellers/{sellerId}/analytics/operations") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"analytics.view") ?: return@handled
        call.respond(managementMetrics(context,call,seller))
    } }
}

private fun reportCell(value: String): String {
    val safe=if(value.firstOrNull() in listOf('=','+','-','@')) "'"+value else value
    return "\""+safe.replace("\"","\"\"")+"\""
}

/** Stock, forecasts and verified revenue are not inferred from demo or sandbox orders. */
private fun managementMetrics(context: BackendContext,call: ApplicationCall,seller: String?): JsonObject {
    val from=call.optionalDate("from");val to=call.optionalDate("to")
    if(from!=null && to!=null && (from>to || ChronoUnit.DAYS.between(from,to)>366))
        throw badRequest("INVALID_DATE_RANGE","Date range must be ordered and at most 366 days")
    return context.read { db -> buildJsonObject {
        put("scope",if(seller==null) "PLATFORM" else "SELLER")
        put("sellerId",seller?.let(::JsonPrimitive) ?: JsonNull)
        put("from",from?.toString()?.let(::JsonPrimitive) ?: JsonNull)
        put("to",to?.toString()?.let(::JsonPrimitive) ?: JsonNull)
        put("timezone","Asia/Kolkata")
        put("source","STORED_OPERATIONS_DEMO_ORDERS_UNVERIFIED")
        put("verifiedRevenuePaise",JsonNull)
        put("activeMemberships",db.count("""SELECT count(*) FROM seller_memberships m JOIN users u ON u.id=m.user_id
            JOIN sellers s ON s.id=m.seller_id WHERE m.active AND u.status='ACTIVE' AND s.active
            AND (?::text IS NULL OR m.seller_id=?)""",seller,seller))
        put("catalogItems",db.count("SELECT count(*) FROM menu_items WHERE (?::text IS NULL OR seller_id=?)",seller,seller))
        put("archivedItems",db.count("SELECT count(*) FROM menu_items WHERE archived_at IS NOT NULL AND (?::text IS NULL OR seller_id=?)",seller,seller))
        for((table,key) in listOf("individual_demo_orders" to "individualDemoOrdersByStatus","bulk_requests" to "bulkRequestsByStatus")) {
            put(key,buildJsonObject {
                // Table names are fixed constants, never request input.
                db.query("""SELECT status,count(*) FROM $table WHERE (?::text IS NULL OR seller_id=?)
                    AND (?::date IS NULL OR (created_at AT TIME ZONE 'Asia/Kolkata')::date>=?)
                    AND (?::date IS NULL OR (created_at AT TIME ZONE 'Asia/Kolkata')::date<=?) GROUP BY status ORDER BY status""",
                    seller,seller,from,from,to,to) { put(it.getString(1),it.getLong(2)) }
            })
        }
        if(seller==null) {
            put("activeSellers",db.count("SELECT count(*) FROM sellers WHERE active"))
            put("suspendedUsers",db.count("SELECT count(*) FROM users WHERE status='SUSPENDED'"))
            put("pendingSetupDrafts",db.count("SELECT count(*) FROM admin_setup_drafts WHERE status='DRAFT'"))
        }
    } }
}
