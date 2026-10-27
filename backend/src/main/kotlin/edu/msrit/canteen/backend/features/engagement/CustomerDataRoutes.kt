package edu.msrit.canteen.backend.features.engagement

import edu.msrit.canteen.backend.api.requireAuthenticatedUser
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.transaction
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

private const val MAX_FAVORITES = 200
private const val MAX_TEMPLATES = 50
private val linesSerializer = ListSerializer(TemplateLineDto.serializer())

/** Customer-owned extras. Every query is scoped by the authenticated principal, never a client user ID. */
internal fun Route.registerCustomerDataRoutes(context: BackendContext, commerce: CommerceReadPort) {
    get("/me/preferences") {
        val principal = call.requireAuthenticatedUser(context) ?: return@get
        val preferences = context.read { it.loadPreferences(principal.userId) }
        call.respond(preferences ?: PreferencesDto(true, true, false, false, null))
    }

    patch("/me/preferences") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@patch
            val request = call.receiveBody<UpdatePreferencesRequest>()
            val values = arrayOf(request.operationalNotifications, request.transactionalNotifications,
                request.promotionalNotifications, request.personalizationConsent)
            val updated = context.transaction { db ->
                context.requireLiveSession(db, principal)
                db.queryOne(
                    """INSERT INTO customer_preferences (user_id, operational_notifications, transactional_notifications,
                                                         promotional_notifications, personalization_consent)
                       VALUES (?, COALESCE(?::boolean, TRUE), COALESCE(?::boolean, TRUE),
                               COALESCE(?::boolean, FALSE), COALESCE(?::boolean, FALSE))
                       ON CONFLICT (user_id) DO UPDATE SET
                         operational_notifications = COALESCE(?::boolean, customer_preferences.operational_notifications),
                         transactional_notifications = COALESCE(?::boolean, customer_preferences.transactional_notifications),
                         promotional_notifications = COALESCE(?::boolean, customer_preferences.promotional_notifications),
                         personalization_consent = COALESCE(?::boolean, customer_preferences.personalization_consent),
                         updated_at = now()
                       RETURNING *""",
                    principal.userId, *values, *values, map = ::preferencesRow,
                )!!
            }
            call.respond(updated)
        }
    }

    get("/me/favorites") {
        val principal = call.requireAuthenticatedUser(context) ?: return@get
        val items = context.read { db ->
            db.query("SELECT * FROM favorites WHERE user_id = ? ORDER BY created_at DESC, target_id",
                principal.userId, map = ::favoriteRow)
        }
        call.respond(FavoritesResponse(items))
    }

    put("/me/favorites/{type}/{targetId}") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@put
            val type = favoriteType(call.parameters["type"])
            val targetId = call.parameters["targetId"]!!.also {
                if (!isSlug(it)) throw badRequest("INVALID_TARGET_ID", "targetId must be a stable lowercase ID")
            }
            val sellerId = when (type) {
                FavoriteType.SELLER -> targetId
                FavoriteType.ITEM -> when (val lookup = commerce.lookupItem(targetId)) {
                    is ItemLookup.Found -> lookup.sellerId
                    ItemLookup.Missing -> throw notFound("Item not found")
                    ItemLookup.Unavailable -> throw ApiFailure(HttpStatusCode.ServiceUnavailable, "CATALOG_UNAVAILABLE",
                        "Item favorites need the catalogue service, which is not connected")
                }
            }
            val favorite = context.transaction { db ->
                context.requireLiveSession(db, principal)
                db.requireActiveSeller(sellerId)
                if (db.count("SELECT count(*) FROM favorites WHERE user_id = ?", principal.userId) >= MAX_FAVORITES &&
                    db.queryOne("SELECT 1 FROM favorites WHERE user_id = ? AND target_type = ? AND target_id = ?",
                        principal.userId, type.name, targetId) { true } == null) {
                    throw ApiFailure(HttpStatusCode.UnprocessableEntity, "FAVORITES_LIMIT", "At most $MAX_FAVORITES favorites are allowed")
                }
                db.update(
                    """INSERT INTO favorites (user_id, target_type, target_id, seller_id) VALUES (?, ?, ?, ?)
                       ON CONFLICT (user_id, target_type, target_id) DO NOTHING""",
                    principal.userId, type.name, targetId, sellerId,
                )
                db.queryOne("SELECT * FROM favorites WHERE user_id = ? AND target_type = ? AND target_id = ?",
                    principal.userId, type.name, targetId, map = ::favoriteRow)!!
            }
            call.respond(favorite)
        }
    }

    delete("/me/favorites/{type}/{targetId}") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@delete
            val type = favoriteType(call.parameters["type"])
            context.transaction { db ->
                context.requireLiveSession(db, principal)
                db.update("DELETE FROM favorites WHERE user_id = ? AND target_type = ? AND target_id = ?",
                    principal.userId, type.name, call.parameters["targetId"])
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    get("/me/bulk-templates") {
        val principal = call.requireAuthenticatedUser(context) ?: return@get
        val items = context.read { db ->
            db.query("SELECT * FROM bulk_templates WHERE user_id = ? ORDER BY updated_at DESC, id",
                principal.userId, map = ::templateRow)
        }
        call.respond(BulkTemplatesResponse(items))
    }

    get("/me/bulk-templates/{id}") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@get
            val id = call.pathUuid("id")
            val template = context.read { it.ownTemplate(id, principal.userId) } ?: throw notFound()
            call.respond(template)
        }
    }

    post("/me/bulk-templates") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@post
            val request = call.receiveBody<CreateBulkTemplateRequest>()
            val name = validTemplateName(request.name)
            validateLines(request.lines)
            val created = context.transaction { db ->
                context.requireLiveSession(db, principal)
                db.requireActiveSeller(request.sellerId)
                if (db.count("SELECT count(*) FROM bulk_templates WHERE user_id = ?", principal.userId) >= MAX_TEMPLATES) {
                    throw ApiFailure(HttpStatusCode.UnprocessableEntity, "TEMPLATES_LIMIT", "At most $MAX_TEMPLATES templates are allowed")
                }
                val id = UUID.randomUUID()
                uniqueName {
                    db.update("INSERT INTO bulk_templates (id, user_id, seller_id, name, lines) VALUES (?, ?, ?, ?, ?::jsonb)",
                        id, principal.userId, request.sellerId, name, Json.encodeToString(linesSerializer, request.lines))
                }
                db.ownTemplate(id, principal.userId)!!
            }
            call.respond(HttpStatusCode.Created, created)
        }
    }

    patch("/me/bulk-templates/{id}") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@patch
            val id = call.pathUuid("id")
            val request = call.receiveBody<UpdateBulkTemplateRequest>()
            val name = request.name?.let(::validTemplateName)
            request.lines?.let(::validateLines)
            val updated = context.transaction { db ->
                context.requireLiveSession(db, principal)
                db.ownTemplate(id, principal.userId) ?: throw notFound()
                uniqueName {
                    db.update(
                        """UPDATE bulk_templates SET name = COALESCE(?, name), lines = COALESCE(?::jsonb, lines), updated_at = now()
                           WHERE id = ? AND user_id = ?""",
                        name, request.lines?.let { Json.encodeToString(linesSerializer, it) }, id, principal.userId,
                    )
                }
                db.ownTemplate(id, principal.userId)!!
            }
            call.respond(updated)
        }
    }

    delete("/me/bulk-templates/{id}") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@delete
            val id = call.pathUuid("id")
            val deleted = context.transaction { db ->
                context.requireLiveSession(db, principal)
                db.update("DELETE FROM bulk_templates WHERE id = ? AND user_id = ?", id, principal.userId)
            }
            if (deleted == 0) throw notFound()
            call.respond(HttpStatusCode.NoContent)
        }
    }

    get("/me/notifications") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@get
            val page = call.page()
            val unreadOnly = call.request.queryParameters["unreadOnly"] == "true"
            val response = context.read { db ->
                val items = db.query(
                    """SELECT * FROM notification_inbox WHERE user_id = ? AND (NOT ? OR read_at IS NULL)
                       ORDER BY created_at DESC, id LIMIT ? OFFSET ?""",
                    principal.userId, unreadOnly, page.limit, page.offset, map = ::notificationRow,
                )
                val unread = db.count("SELECT count(*) FROM notification_inbox WHERE user_id = ? AND read_at IS NULL",
                    principal.userId)
                NotificationsResponse(items, unread, page.limit, page.offset)
            }
            call.respond(response)
        }
    }

    patch("/me/notifications/{id}/read") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@patch
            val id = call.pathUuid("id")
            val notification = context.transaction { db ->
                context.requireLiveSession(db, principal)
                db.queryOne(
                    """UPDATE notification_inbox SET read_at = COALESCE(read_at, now())
                       WHERE id = ? AND user_id = ? RETURNING *""",
                    id, principal.userId, map = ::notificationRow,
                )
            } ?: throw notFound()
            call.respond(notification)
        }
    }
}

internal fun Connection.requireActiveSeller(sellerId: String) {
    queryOne("SELECT 1 FROM sellers WHERE id = ? AND active", sellerId) { true } ?: throw notFound("Seller not found")
}

private fun favoriteType(raw: String?): FavoriteType =
    FavoriteType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: throw badRequest("INVALID_FAVORITE_TYPE", "Favorite type must be seller or item")

private fun validTemplateName(raw: String): String = raw.trim().also {
    if (it.length !in 1..80) throw badRequest("INVALID_TEMPLATE_NAME", "Template name must be 1 to 80 characters")
}

private fun validateLines(lines: List<TemplateLineDto>) {
    if (lines.size !in 1..50) throw badRequest("INVALID_TEMPLATE_LINES", "A template needs 1 to 50 lines")
    if (lines.any { !isSlug(it.itemId) || it.quantity !in 1..999 }) {
        throw badRequest("INVALID_TEMPLATE_LINES", "Each line needs a stable item ID and a quantity of 1 to 999")
    }
    if (lines.map { it.itemId }.toSet().size != lines.size) {
        throw badRequest("INVALID_TEMPLATE_LINES", "Each item may appear only once in a template")
    }
}

private inline fun uniqueName(block: () -> Unit) {
    try {
        block()
    } catch (failure: PSQLException) {
        if (failure.sqlState == "23505") throw conflict("TEMPLATE_NAME_TAKEN", "A template with this name already exists for this seller")
        throw failure
    }
}

private fun Connection.ownTemplate(id: UUID, userId: UUID): BulkTemplateDto? =
    queryOne("SELECT * FROM bulk_templates WHERE id = ? AND user_id = ?", id, userId, map = ::templateRow)

private fun Connection.loadPreferences(userId: UUID): PreferencesDto? =
    queryOne("SELECT * FROM customer_preferences WHERE user_id = ?", userId, map = ::preferencesRow)

private fun preferencesRow(rows: ResultSet) = PreferencesDto(
    operationalNotifications = rows.getBoolean("operational_notifications"),
    transactionalNotifications = rows.getBoolean("transactional_notifications"),
    promotionalNotifications = rows.getBoolean("promotional_notifications"),
    personalizationConsent = rows.getBoolean("personalization_consent"),
    updatedAt = rows.instant("updated_at"),
)

private fun favoriteRow(rows: ResultSet) = FavoriteDto(
    FavoriteType.valueOf(rows.getString("target_type")), rows.getString("target_id"),
    rows.getString("seller_id"), rows.instant("created_at")!!,
)

private fun templateRow(rows: ResultSet) = BulkTemplateDto(
    id = rows.uuid("id").toString(),
    sellerId = rows.getString("seller_id"),
    name = rows.getString("name"),
    lines = Json.decodeFromString(linesSerializer, rows.getString("lines")),
    createdAt = rows.instant("created_at")!!,
    updatedAt = rows.instant("updated_at")!!,
)

internal fun notificationRow(rows: ResultSet) = NotificationDto(
    id = rows.uuid("id").toString(),
    eventType = rows.getString("event_type"),
    title = rows.getString("title"),
    body = rows.getString("body"),
    ticketId = rows.getObject("ticket_id", UUID::class.java)?.toString(),
    deliveryChannel = rows.getString("delivery_channel"),
    read = rows.getTimestamp("read_at") != null,
    readAt = rows.instant("read_at"),
    createdAt = rows.instant("created_at")!!,
)
