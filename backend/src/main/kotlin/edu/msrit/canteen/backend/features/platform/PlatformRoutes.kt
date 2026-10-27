package edu.msrit.canteen.backend.features.platform

import edu.msrit.canteen.backend.api.requirePlatformAdmin
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.transaction
import edu.msrit.canteen.backend.features.engagement.CommerceReadPort
import edu.msrit.canteen.backend.features.engagement.PageDto
import edu.msrit.canteen.backend.features.engagement.SupportCountsDto
import edu.msrit.canteen.backend.features.engagement.analyticsSummary
import edu.msrit.canteen.backend.features.engagement.badRequest
import edu.msrit.canteen.backend.features.engagement.conflict
import edu.msrit.canteen.backend.features.engagement.count
import edu.msrit.canteen.backend.features.engagement.handled
import edu.msrit.canteen.backend.features.engagement.instant
import edu.msrit.canteen.backend.features.engagement.isSlug
import edu.msrit.canteen.backend.features.engagement.likePattern
import edu.msrit.canteen.backend.features.engagement.notFound
import edu.msrit.canteen.backend.features.engagement.page
import edu.msrit.canteen.backend.features.engagement.pathUuid
import edu.msrit.canteen.backend.features.engagement.query
import edu.msrit.canteen.backend.features.engagement.queryOne
import edu.msrit.canteen.backend.features.engagement.read
import edu.msrit.canteen.backend.features.engagement.receiveBody
import edu.msrit.canteen.backend.features.engagement.recordAudit
import edu.msrit.canteen.backend.features.engagement.requireAdminInTx
import edu.msrit.canteen.backend.features.engagement.supportCounts
import edu.msrit.canteen.backend.features.engagement.update
import edu.msrit.canteen.backend.features.engagement.uuid
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

@Serializable
data class UserCountsDto(val total: Long, val active: Long, val suspended: Long, val disabled: Long, val platformAdmins: Long)

@Serializable
data class SellerCountsDto(val total: Long, val active: Long)

@Serializable
data class AdminOverviewDto(
    val users: UserCountsDto,
    val sellers: SellerCountsDto,
    val supportTickets: SupportCountsDto,
    val openSetupDrafts: Long,
    val source: String = "CORE_DATABASE",
)

@Serializable
data class AdminSellerDto(
    val id: String, val name: String, val description: String, val active: Boolean, val isMainCanteen: Boolean,
    val activeMemberCount: Long, val createdAt: String,
)

/** Directory row: no password hash, session, department or other profile detail. */
@Serializable
data class AdminUserDto(
    val id: String, val email: String, val fullName: String, val affiliation: String? = null, val status: String,
    val platformAdmin: Boolean, val activeMembershipCount: Long, val createdAt: String,
)

@Serializable
data class AuditEventDto(
    val id: String, val actorUserId: String, val actorRole: String, val action: String, val targetType: String,
    val targetId: String, val sellerId: String? = null, val detail: JsonObject, val occurredAt: String,
)

@Serializable
data class CreateSetupDraftRequest(val proposedSellerId: String, val sellerName: String, val notes: String? = null)

@Serializable
data class SetupDraftDto(
    val id: String, val proposedSellerId: String, val sellerName: String, val notes: String? = null, val status: String,
    /** Drafts never create sellers or send invitations. */
    val sellerCreated: Boolean = false,
    val invitationSent: Boolean = false,
    val createdBy: String, val createdAt: String, val updatedAt: String,
)

/** Platform-administrator APIs. Every handler checks the live admin flag; none grants seller membership. */
internal fun Route.registerPlatformRoutes(context: BackendContext, commerce: CommerceReadPort) {
    get("/admin/overview") {
        call.requirePlatformAdmin(context) ?: return@get
        val overview = context.read { db ->
            val users = db.queryOne(
                """SELECT count(*), count(*) FILTER (WHERE status = 'ACTIVE'), count(*) FILTER (WHERE status = 'SUSPENDED'),
                          count(*) FILTER (WHERE status = 'DISABLED'), count(*) FILTER (WHERE is_platform_admin)
                   FROM users""") { UserCountsDto(it.getLong(1), it.getLong(2), it.getLong(3), it.getLong(4), it.getLong(5)) }!!
            val sellers = db.queryOne("SELECT count(*), count(*) FILTER (WHERE active) FROM sellers") {
                SellerCountsDto(it.getLong(1), it.getLong(2))
            }!!
            AdminOverviewDto(users, sellers, db.supportCounts(null),
                db.count("SELECT count(*) FROM admin_setup_drafts WHERE status = 'DRAFT'"))
        }
        call.respond(overview)
    }

    get("/admin/sellers") {
        call.handled {
            call.requirePlatformAdmin(context) ?: return@get
            val page = call.page()
            val search = call.request.queryParameters["q"]?.trim()?.takeIf { it.isNotEmpty() }?.let(::likePattern)
            val filter = "(?::text IS NULL OR s.id ILIKE ?::text OR s.name ILIKE ?::text)"
            val result = context.read { db ->
                val items = db.query(
                    """SELECT s.*, (SELECT count(*) FROM seller_memberships m WHERE m.seller_id = s.id AND m.active) AS members
                       FROM sellers s WHERE $filter ORDER BY s.is_main_canteen DESC, s.id LIMIT ? OFFSET ?""",
                    search, search, search, page.limit, page.offset,
                ) {
                    AdminSellerDto(it.getString("id"), it.getString("name"), it.getString("description"),
                        it.getBoolean("active"), it.getBoolean("is_main_canteen"), it.getLong("members"), it.instant("created_at")!!)
                }
                PageDto(items, page.limit, page.offset, db.count("SELECT count(*) FROM sellers s WHERE $filter", search, search, search))
            }
            call.respond(result)
        }
    }

    get("/admin/users") {
        call.handled {
            call.requirePlatformAdmin(context) ?: return@get
            val page = call.page()
            val search = call.request.queryParameters["q"]?.trim()?.takeIf { it.isNotEmpty() }?.let(::likePattern)
            val status = call.request.queryParameters["status"]?.also {
                if (it !in setOf("ACTIVE", "SUSPENDED", "DISABLED")) throw badRequest("INVALID_STATUS", "Unknown user status")
            }
            val filter = "(?::text IS NULL OR u.email ILIKE ?::text OR u.full_name ILIKE ?::text) AND (?::text IS NULL OR u.status = ?::text)"
            val args = arrayOf<Any?>(search, search, search, status, status)
            val result = context.read { db ->
                val items = db.query(
                    """SELECT u.id, u.email, u.full_name, u.affiliation, u.status, u.is_platform_admin, u.created_at,
                              (SELECT count(*) FROM seller_memberships m WHERE m.user_id = u.id AND m.active) AS memberships
                       FROM users u WHERE $filter ORDER BY u.created_at, u.id LIMIT ? OFFSET ?""",
                    *args, page.limit, page.offset,
                ) {
                    AdminUserDto(it.uuid("id").toString(), it.getString("email"), it.getString("full_name"),
                        it.getString("affiliation"), it.getString("status"), it.getBoolean("is_platform_admin"),
                        it.getLong("memberships"), it.instant("created_at")!!)
                }
                PageDto(items, page.limit, page.offset, db.count("SELECT count(*) FROM users u WHERE $filter", *args))
            }
            call.respond(result)
        }
    }

    get("/admin/audit") {
        call.handled {
            call.requirePlatformAdmin(context) ?: return@get
            val page = call.page()
            val action = call.request.queryParameters["action"]
            val targetType = call.request.queryParameters["targetType"]
            val filter = "(?::text IS NULL OR action = ?::text) AND (?::text IS NULL OR target_type = ?::text)"
            val args = arrayOf<Any?>(action, action, targetType, targetType)
            val result = context.read { db ->
                val items = db.query(
                    "SELECT * FROM admin_audit_events WHERE $filter ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?",
                    *args, page.limit, page.offset,
                ) {
                    AuditEventDto(it.uuid("id").toString(), it.uuid("actor_user_id").toString(), it.getString("actor_role"),
                        it.getString("action"), it.getString("target_type"), it.getString("target_id"),
                        it.getString("seller_id"), Json.parseToJsonElement(it.getString("detail")) as JsonObject,
                        it.instant("occurred_at")!!)
                }
                PageDto(items, page.limit, page.offset, db.count("SELECT count(*) FROM admin_audit_events WHERE $filter", *args))
            }
            call.respond(result)
        }
    }

    get("/admin/setup-drafts") {
        call.handled {
            call.requirePlatformAdmin(context) ?: return@get
            val page = call.page()
            val result = context.read { db ->
                val items = db.query("SELECT * FROM admin_setup_drafts ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
                    page.limit, page.offset, map = ::draftRow)
                PageDto(items, page.limit, page.offset, db.count("SELECT count(*) FROM admin_setup_drafts"))
            }
            call.respond(result)
        }
    }

    post("/admin/setup-drafts") {
        call.handled {
            val principal = call.requirePlatformAdmin(context) ?: return@post
            val request = call.receiveBody<CreateSetupDraftRequest>()
            val name = request.sellerName.trim()
            if (!isSlug(request.proposedSellerId)) throw badRequest("INVALID_SELLER_ID", "proposedSellerId must be a stable lowercase ID")
            if (name.length !in 1..200) throw badRequest("INVALID_SELLER_NAME", "sellerName must be 1 to 200 characters")
            val notes = request.notes?.trim()?.takeIf { it.isNotEmpty() }
            if (notes != null && notes.length > 1000) throw badRequest("INVALID_NOTES", "notes must be at most 1000 characters")
            val draft = context.transaction { db ->
                context.requireAdminInTx(db, principal)
                if (db.queryOne("SELECT 1 FROM sellers WHERE id = ?", request.proposedSellerId) { true } != null) {
                    throw conflict("SELLER_ID_TAKEN", "A seller with this ID already exists")
                }
                val id = UUID.randomUUID()
                try {
                    db.queryOne(
                        """INSERT INTO admin_setup_drafts (id, proposed_seller_id, seller_name, notes, created_by)
                           VALUES (?, ?, ?, ?, ?) RETURNING id""",
                        id, request.proposedSellerId, name, notes, principal.userId) { true }
                } catch (failure: PSQLException) {
                    if (failure.sqlState == "23505") throw conflict("DRAFT_EXISTS", "An open draft already uses this seller ID")
                    throw failure
                }
                recordAudit(db, principal.userId, "ADMIN", "admin.setup_draft_created", "SETUP_DRAFT", id.toString(),
                    detail = mapOf("proposedSellerId" to request.proposedSellerId))
                db.draft(id)!!
            }
            call.respond(HttpStatusCode.Created, draft)
        }
    }

    post("/admin/setup-drafts/{id}/archive") {
        call.handled {
            val principal = call.requirePlatformAdmin(context) ?: return@post
            val id = call.pathUuid("id")
            val draft = context.transaction { db ->
                context.requireAdminInTx(db, principal)
                val changed = db.update(
                    "UPDATE admin_setup_drafts SET status = 'ARCHIVED', updated_at = now() WHERE id = ? AND status = 'DRAFT'", id)
                if (changed == 1) {
                    recordAudit(db, principal.userId, "ADMIN", "admin.setup_draft_archived", "SETUP_DRAFT", id.toString())
                }
                db.draft(id) ?: throw notFound()
            }
            call.respond(draft)
        }
    }

    get("/admin/analytics/summary") {
        call.handled {
            call.requirePlatformAdmin(context) ?: return@get
            call.respond(analyticsSummary(call, commerce, null))
        }
    }
}

private fun Connection.draft(id: UUID): SetupDraftDto? =
    queryOne("SELECT * FROM admin_setup_drafts WHERE id = ?", id, map = ::draftRow)

private fun draftRow(rows: ResultSet) = SetupDraftDto(
    id = rows.uuid("id").toString(),
    proposedSellerId = rows.getString("proposed_seller_id"),
    sellerName = rows.getString("seller_name"),
    notes = rows.getString("notes"),
    status = rows.getString("status"),
    createdBy = rows.uuid("created_by").toString(),
    createdAt = rows.instant("created_at")!!,
    updatedAt = rows.instant("updated_at")!!,
)
