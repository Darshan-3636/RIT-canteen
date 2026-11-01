package edu.msrit.canteen.backend.features.engagement

import edu.msrit.canteen.backend.api.requireSellerCapability
import edu.msrit.canteen.backend.api.respondApiError
import edu.msrit.canteen.backend.auth.SellerCapabilities
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.transaction
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.postgresql.util.PSQLException
import java.sql.ResultSet
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

private val reportMetrics = setOf("DEMO_REQUEST_COUNTS", "SUPPORT_TICKETS")
private val metricsSerializer = ListSerializer(String.serializer())

/** Analytics never invent revenue: there is no verified payment source, so revenue is always null. */
internal fun Route.registerAnalyticsRoutes(context: BackendContext, commerce: CommerceReadPort) {
    get("/sellers/{sellerId}/dashboard-summary") {
        val sellerId = call.parameters["sellerId"]!!
        call.requireSellerCapability(context, sellerId, SellerCapabilities.ANALYTICS_VIEW) ?: return@get
        val counts = commerce.demoRequestCounts(sellerId, null, null)
        call.respond(DashboardSummaryDto(
            sellerId = sellerId,
            insufficientData = counts == null,
            demoRequestCounts = counts?.toDto(),
            demoRequestCountsStatus = countsStatus(counts),
            simulatedSales = commerce.simulatedSales(sellerId, null, null)?.toDto(),
            sandboxSales = commerce.sandboxSales(sellerId, null, null)?.toSandboxDto(),
            supportTickets = context.read { it.supportCounts(sellerId) },
        ))
    }

    get("/sellers/{sellerId}/analytics/summary") {
        call.handled {
            val sellerId = call.parameters["sellerId"]!!
            call.requireSellerCapability(context, sellerId, SellerCapabilities.ANALYTICS_VIEW) ?: return@get
            call.respond(analyticsSummary(call, commerce, sellerId))
        }
    }

    get("/sellers/{sellerId}/reports/selections") {
        val sellerId = call.parameters["sellerId"]!!
        call.requireSellerCapability(context, sellerId, SellerCapabilities.REPORTS_EXPORT) ?: return@get
        val items = context.read { db ->
            db.query("SELECT * FROM report_selections WHERE seller_id = ? ORDER BY created_at DESC, id", sellerId,
                map = ::selectionRow)
        }
        call.respond(ReportSelectionsResponse(items))
    }

    post("/sellers/{sellerId}/reports/selections") {
        call.handled {
            val sellerId = call.parameters["sellerId"]!!
            val principal = call.requireSellerCapability(context, sellerId, SellerCapabilities.REPORTS_EXPORT) ?: return@post
            val request = call.receiveBody<CreateReportSelectionRequest>()
            val name = request.name.trim()
            if (name.length !in 1..80) throw badRequest("INVALID_REPORT_NAME", "Report name must be 1 to 80 characters")
            if (request.metrics.isEmpty() || request.metrics.size > 10 || !reportMetrics.containsAll(request.metrics) ||
                request.metrics.toSet().size != request.metrics.size) {
                throw badRequest("INVALID_REPORT_METRICS", "metrics must be distinct values from $reportMetrics")
            }
            val from = request.from?.let(::parseDate)
            val to = request.to?.let(::parseDate)
            validateRange(from, to)
            val created = context.transaction { db ->
                context.requireSellerInTx(db, principal, sellerId, SellerCapabilities.REPORTS_EXPORT)
                val id = UUID.randomUUID()
                try {
                    db.update(
                        """INSERT INTO report_selections (id, seller_id, created_by, name, metrics, from_date, to_date)
                           VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)""",
                        id, sellerId, principal.userId, name, Json.encodeToString(metricsSerializer, request.metrics), from, to,
                    )
                } catch (failure: PSQLException) {
                    if (failure.sqlState == "23505") throw conflict("REPORT_NAME_TAKEN", "A report with this name already exists")
                    throw failure
                }
                recordAudit(db, principal.userId, "SELLER", "report.selection_saved", "REPORT_SELECTION", id.toString(), sellerId)
                db.queryOne("SELECT * FROM report_selections WHERE id = ?", id, map = ::selectionRow)!!
            }
            call.respond(HttpStatusCode.Created, created)
        }
    }

    get("/sellers/{sellerId}/reports/export") {
        call.handled {
            val sellerId=call.parameters["sellerId"]!!
            val actor=call.requireSellerCapability(context,sellerId,SellerCapabilities.REPORTS_EXPORT) ?: return@handled
            val selection=call.request.queryParameters["selectionId"]?.let { raw ->
                val id=runCatching { UUID.fromString(raw) }.getOrNull() ?: throw badRequest("INVALID_ID","Invalid selectionId")
                context.read { it.queryOne("SELECT * FROM report_selections WHERE id=? AND seller_id=?",id,sellerId,map=::selectionRow) } ?: throw notFound()
            }
            val to=call.optionalDate("to") ?: selection?.to?.let(::parseDate) ?: context.clock.instant().atZone(context.config.sellerZone).toLocalDate()
            val from=call.optionalDate("from") ?: selection?.from?.let(::parseDate) ?: to.minusDays(29)
            validateRange(from,to)
            val metrics=selection?.metrics ?: reportMetrics.toList()
            val lines=mutableListOf("metric,value,unit,label,from,to")
            if("DEMO_REQUEST_COUNTS" in metrics) {
                val counts=commerce.demoRequestCounts(sellerId,from,to)
                if(counts==null) lines += "bulk_requests,,count,UNAVAILABLE,$from,$to"
                else {
                    lines += "bulk_requests,${counts.total},count,DEMO_UNVERIFIED,$from,$to"
                    counts.byStatus.toSortedMap().forEach { (status,count) ->
                        // Status originates in checked enums; CSV quoting still handles an injected read port.
                        val label=status.replace("\"","\"\"").replace("\r","").replace("\n","")
                        lines += "\"bulk_status_$label\",$count,count,DEMO_UNVERIFIED,$from,$to"
                    }
                }
            }
            if("SUPPORT_TICKETS" in metrics) context.read { db ->
                db.query("""SELECT status,count(*) FROM support_tickets WHERE seller_id=?
                    AND (created_at AT TIME ZONE 'Asia/Kolkata')::date BETWEEN ? AND ? GROUP BY status ORDER BY status""",sellerId,from,to) {
                    lines += "support_${it.getString(1)},${it.getLong(2)},count,STORED_TICKETS,$from,$to"
                }
            }
            context.transaction { db ->
                context.requireSellerInTx(db,actor,sellerId,SellerCapabilities.REPORTS_EXPORT)
                recordAudit(db,actor.userId,"SELLER","report.export","SELLER",sellerId,sellerId)
            }
            call.response.headers.append("Content-Disposition","attachment; filename=\"seller-report.csv\"")
            call.respondText(lines.joinToString("\r\n",postfix="\r\n"),ContentType.Text.CSV)
        }
    }

}

/** Shared by the seller and platform summaries; [sellerId] null means platform-wide. */
internal fun analyticsSummary(call: ApplicationCall, commerce: CommerceReadPort, sellerId: String?): AnalyticsSummaryDto {
    val from = call.optionalDate("from")
    val to = call.optionalDate("to")
    validateRange(from, to)
    val counts = commerce.demoRequestCounts(sellerId, from, to)
    return AnalyticsSummaryDto(
        scope = if (sellerId == null) "PLATFORM" else "SELLER",
        sellerId = sellerId,
        from = from?.toString(),
        to = to?.toString(),
        demoRequestCounts = counts?.toDto(),
        demoRequestCountsStatus = countsStatus(counts),
        simulatedSales = commerce.simulatedSales(sellerId, from, to)?.toDto(),
        sandboxSales = commerce.sandboxSales(sellerId, from, to)?.toSandboxDto(),
    )
}

private fun countsStatus(counts: DemoRequestCounts?) =
    if (counts == null) "UNAVAILABLE_NO_COMMERCE_SOURCE" else "DEMO_UNVERIFIED"

private fun DemoRequestCounts.toDto() = DemoRequestCountsDto(total, byStatus)
private fun SimulatedSales.toDto() = SimulatedSalesDto(orderCount, totalPaise)
private fun SimulatedSales.toSandboxDto() = SimulatedSalesDto(orderCount, totalPaise, "PHONEPE_UAT_NO_REAL_MONEY")

private fun parseDate(raw: String): LocalDate = runCatching { LocalDate.parse(raw) }.getOrNull()
    ?: throw badRequest("INVALID_DATE", "Dates must be ISO dates such as 2026-10-27")

private fun validateRange(from: LocalDate?, to: LocalDate?) {
    if (from != null && to != null && (from > to || ChronoUnit.DAYS.between(from, to) > 366)) {
        throw badRequest("INVALID_DATE_RANGE", "from must not be after to, and the range must be at most 366 days")
    }
}

private fun selectionRow(rows: ResultSet) = ReportSelectionDto(
    id = rows.uuid("id").toString(),
    sellerId = rows.getString("seller_id"),
    name = rows.getString("name"),
    metrics = Json.decodeFromString(metricsSerializer, rows.getString("metrics")),
    from = rows.getObject("from_date", LocalDate::class.java)?.toString(),
    to = rows.getObject("to_date", LocalDate::class.java)?.toString(),
    createdAt = rows.instant("created_at")!!,
)
