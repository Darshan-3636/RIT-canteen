package edu.msrit.canteen.backend.features.engagement

import edu.msrit.canteen.backend.api.requireAuthenticatedUser
import edu.msrit.canteen.backend.api.requirePlatformAdmin
import edu.msrit.canteen.backend.api.requireSellerCapability
import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.auth.SellerCapabilities
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.transaction
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

private val referencePattern = Regex("^[A-Za-z0-9._:-]{1,64}$")
private val clientIdPattern = Regex("^[A-Za-z0-9_-]{8,64}$")

/** Admin may move a ticket only along these edges; customers and sellers may only escalate OPEN tickets. */
private val adminTransitions = mapOf(
    TicketStatus.OPEN to setOf(TicketStatus.ESCALATED, TicketStatus.RESOLVED, TicketStatus.CLOSED),
    TicketStatus.ESCALATED to setOf(TicketStatus.RESOLVED, TicketStatus.CLOSED),
    TicketStatus.RESOLVED to setOf(TicketStatus.OPEN, TicketStatus.CLOSED),
    TicketStatus.CLOSED to emptySet(),
)

/** Which tickets a viewer may see: own (customer), routed to the seller, or any (platform admin). */
private sealed interface TicketScope {
    val actorRole: String
    data class Customer(val userId: UUID) : TicketScope { override val actorRole = "CUSTOMER" }
    data class Seller(val sellerId: String) : TicketScope { override val actorRole = "SELLER" }
    data object Admin : TicketScope { override val actorRole = "ADMIN" }
}

internal fun Route.registerSupportRoutes(context: BackendContext) {
    get("/me/support/tickets") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@get
            call.respond(context.read { it.listTickets(TicketScope.Customer(principal.userId), call) })
        }
    }

    post("/me/support/tickets") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@post
            val request = call.receiveBody<CreateTicketRequest>()
            val subject = request.subject.trim()
            if (subject.length !in 3..120) throw badRequest("INVALID_SUBJECT", "Subject must be 3 to 120 characters")
            val body = validBody(request.message)
            request.sellerId?.let { if (!isSlug(it)) throw badRequest("INVALID_SELLER_ID", "sellerId must be a stable seller ID") }
            request.requestReference?.let {
                if (!referencePattern.matches(it)) throw badRequest("INVALID_REQUEST_REFERENCE", "requestReference must be 1 to 64 ID characters")
            }
            request.clientRequestId?.let(::validClientId)
            val (detail, created) = context.transaction { db ->
                context.requireLiveSession(db, principal)
                request.sellerId?.let(db::requireActiveSeller)
                val id = UUID.randomUUID()
                val inserted = db.queryOne(
                    """INSERT INTO support_tickets (id, user_id, seller_id, category, subject, request_reference, client_request_id)
                       VALUES (?, ?, ?, ?, ?, ?, ?)
                       ON CONFLICT (user_id, client_request_id) WHERE client_request_id IS NOT NULL DO NOTHING
                       RETURNING id""",
                    id, principal.userId, request.sellerId, request.category.name, subject,
                    request.requestReference, request.clientRequestId,
                ) { it.uuid("id") }
                if (inserted == null) {
                    // Idempotent retry: return the ticket first created with this clientRequestId, unchanged.
                    val existing = db.queryOne("SELECT id FROM support_tickets WHERE user_id = ? AND client_request_id = ?",
                        principal.userId, request.clientRequestId) { it.uuid("id") }!!
                    return@transaction db.ticketDetail(existing, TicketScope.Customer(principal.userId), principal.userId)!! to false
                }
                db.insertMessage(id, principal.userId, "CUSTOMER", body, null)
                db.recordStatus(id, null, TicketStatus.OPEN, principal.userId, "CUSTOMER")
                db.ticketDetail(id, TicketScope.Customer(principal.userId), principal.userId)!! to true
            }
            call.respond(if (created) HttpStatusCode.Created else HttpStatusCode.OK, detail)
        }
    }

    get("/me/support/tickets/{id}") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@get
            val id = call.pathUuid("id")
            call.respond(context.read { it.ticketDetail(id, TicketScope.Customer(principal.userId), principal.userId) }
                ?: throw notFound())
        }
    }

    post("/me/support/tickets/{id}/messages") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@post
            postMessage(context, call, principal, TicketScope.Customer(principal.userId)) {
                context.requireLiveSession(it, principal)
            }
        }
    }

    post("/me/support/tickets/{id}/escalate") {
        call.handled {
            val principal = call.requireAuthenticatedUser(context) ?: return@post
            escalate(context, call, principal, TicketScope.Customer(principal.userId)) {
                context.requireLiveSession(it, principal)
            }
        }
    }

    get("/sellers/{sellerId}/support/tickets") {
        call.handled {
            val sellerId = call.parameters["sellerId"]!!
            call.requireSellerCapability(context, sellerId, SellerCapabilities.BULK_VIEW) ?: return@get
            call.respond(context.read { it.listTickets(TicketScope.Seller(sellerId), call) })
        }
    }

    get("/sellers/{sellerId}/support/tickets/{id}") {
        call.handled {
            val sellerId = call.parameters["sellerId"]!!
            val principal = call.requireSellerCapability(context, sellerId, SellerCapabilities.BULK_VIEW) ?: return@get
            val id = call.pathUuid("id")
            call.respond(context.read { it.ticketDetail(id, TicketScope.Seller(sellerId), principal.userId) } ?: throw notFound())
        }
    }

    post("/sellers/{sellerId}/support/tickets/{id}/messages") {
        call.handled {
            val sellerId = call.parameters["sellerId"]!!
            val principal = call.requireSellerCapability(context, sellerId, SellerCapabilities.BULK_RESPOND) ?: return@post
            postMessage(context, call, principal, TicketScope.Seller(sellerId)) {
                context.requireSellerInTx(it, principal, sellerId, SellerCapabilities.BULK_RESPOND)
            }
        }
    }

    post("/sellers/{sellerId}/support/tickets/{id}/escalate") {
        call.handled {
            val sellerId = call.parameters["sellerId"]!!
            val principal = call.requireSellerCapability(context, sellerId, SellerCapabilities.BULK_RESPOND) ?: return@post
            escalate(context, call, principal, TicketScope.Seller(sellerId)) {
                context.requireSellerInTx(it, principal, sellerId, SellerCapabilities.BULK_RESPOND)
            }
        }
    }

    get("/admin/support/tickets") {
        call.handled {
            call.requirePlatformAdmin(context) ?: return@get
            call.respond(context.read { it.listTickets(TicketScope.Admin, call) })
        }
    }

    get("/admin/support/tickets/{id}") {
        call.handled {
            val principal = call.requirePlatformAdmin(context) ?: return@get
            val id = call.pathUuid("id")
            call.respond(context.read { it.ticketDetail(id, TicketScope.Admin, principal.userId) } ?: throw notFound())
        }
    }

    post("/admin/support/tickets/{id}/messages") {
        call.handled {
            val principal = call.requirePlatformAdmin(context) ?: return@post
            postMessage(context, call, principal, TicketScope.Admin) { context.requireAdminInTx(it, principal) }
        }
    }

    patch("/admin/support/tickets/{id}") {
        call.handled {
            val principal = call.requirePlatformAdmin(context) ?: return@patch
            val id = call.pathUuid("id")
            val target = call.receiveBody<UpdateTicketStatusRequest>().status
            val detail = context.transaction { db ->
                context.requireAdminInTx(db, principal)
                val ticket = db.lockTicket(id, TicketScope.Admin) ?: throw notFound()
                if (target == ticket.status) return@transaction db.ticketDetail(id, TicketScope.Admin, principal.userId)!!
                if (target !in adminTransitions.getValue(ticket.status)) {
                    throw conflict("INVALID_TICKET_TRANSITION", "A ${ticket.status} ticket cannot move to $target")
                }
                db.changeStatus(ticket, target, principal.userId, "ADMIN")
                db.notifyCustomer(ticket, "SUPPORT_STATUS_CHANGED", "Support ticket updated",
                    "Your ticket is now ${target.name.lowercase()}: ${ticket.subject}")
                recordAudit(db, principal.userId, "ADMIN", "support.status_changed", "SUPPORT_TICKET", id.toString(),
                    ticket.sellerId, mapOf("from" to ticket.status.name, "to" to target.name))
                db.ticketDetail(id, TicketScope.Admin, principal.userId)!!
            }
            call.respond(detail)
        }
    }
}

private suspend fun postMessage(context: BackendContext, call: ApplicationCall, principal: AuthenticatedPrincipal,
                                scope: TicketScope, recheck: (Connection) -> Unit) {
    val id = call.pathUuid("id")
    val request = call.receiveBody<PostMessageRequest>()
    val body = validBody(request.body)
    request.clientMessageId?.let(::validClientId)
    val (message, created) = context.transaction { db ->
        recheck(db)
        val ticket = db.lockTicket(id, scope) ?: throw notFound()
        if (ticket.status == TicketStatus.CLOSED) throw conflict("TICKET_CLOSED", "A closed ticket cannot receive messages")
        val messageId = db.insertMessage(id, principal.userId, scope.actorRole, body, request.clientMessageId)
        if (messageId == null) {
            val existing = db.queryOne(
                "SELECT * FROM support_ticket_messages WHERE ticket_id = ? AND author_user_id = ? AND client_message_id = ?",
                id, principal.userId, request.clientMessageId) { messageRow(it, principal.userId) }!!
            return@transaction existing to false
        }
        db.update("UPDATE support_tickets SET updated_at = now() WHERE id = ?", id)
        if (scope !is TicketScope.Customer) {
            db.notifyCustomer(ticket, "SUPPORT_REPLY", "New support reply", "There is a new reply on: ${ticket.subject}")
            recordAudit(db, principal.userId, scope.actorRole, "support.reply_posted", "SUPPORT_TICKET", id.toString(),
                ticket.sellerId, mapOf("messageId" to messageId.toString()))
        }
        db.queryOne("SELECT * FROM support_ticket_messages WHERE id = ?", messageId) { messageRow(it, principal.userId) }!! to true
    }
    call.respond(if (created) HttpStatusCode.Created else HttpStatusCode.OK, message)
}

private suspend fun escalate(context: BackendContext, call: ApplicationCall, principal: AuthenticatedPrincipal,
                             scope: TicketScope, recheck: (Connection) -> Unit) {
    val id = call.pathUuid("id")
    val detail = context.transaction { db ->
        recheck(db)
        val ticket = db.lockTicket(id, scope) ?: throw notFound()
        if (ticket.status != TicketStatus.ESCALATED) {
            if (ticket.status != TicketStatus.OPEN) {
                throw conflict("INVALID_TICKET_TRANSITION", "Only an open ticket can be escalated")
            }
            db.changeStatus(ticket, TicketStatus.ESCALATED, principal.userId, scope.actorRole)
            recordAudit(db, principal.userId, scope.actorRole, "support.ticket_escalated", "SUPPORT_TICKET", id.toString(),
                ticket.sellerId, mapOf("from" to ticket.status.name, "to" to TicketStatus.ESCALATED.name))
        }
        db.ticketDetail(id, scope, principal.userId)!!
    }
    call.respond(detail)
}

private data class TicketRow(val id: UUID, val userId: UUID, val sellerId: String?, val subject: String, val status: TicketStatus)

private fun scopeClause(scope: TicketScope): Pair<String, Any?> = when (scope) {
    is TicketScope.Customer -> "user_id = ?" to scope.userId
    is TicketScope.Seller -> "seller_id = ?" to scope.sellerId
    TicketScope.Admin -> "?::text IS NULL" to null
}

private fun Connection.lockTicket(id: UUID, scope: TicketScope): TicketRow? {
    val (clause, arg) = scopeClause(scope)
    return queryOne("SELECT id, user_id, seller_id, subject, status FROM support_tickets WHERE id = ? AND $clause FOR UPDATE",
        id, arg) {
        TicketRow(it.uuid("id"), it.uuid("user_id"), it.getString("seller_id"), it.getString("subject"),
            TicketStatus.valueOf(it.getString("status")))
    }
}

private fun Connection.listTickets(scope: TicketScope, call: ApplicationCall): PageDto<TicketSummaryDto> {
    val page = call.page()
    val status = call.request.queryParameters["status"]?.let { raw ->
        TicketStatus.entries.firstOrNull { it.name == raw } ?: throw badRequest("INVALID_STATUS", "Unknown ticket status")
    }
    val (clause, arg) = scopeClause(scope)
    val filter = "$clause AND (?::text IS NULL OR status = ?::text)"
    val items = query("SELECT * FROM support_tickets WHERE $filter ORDER BY updated_at DESC, id LIMIT ? OFFSET ?",
        arg, status?.name, status?.name, page.limit, page.offset) { summaryRow(it, scope == TicketScope.Admin) }
    val total = count("SELECT count(*) FROM support_tickets WHERE $filter", arg, status?.name, status?.name)
    return PageDto(items, page.limit, page.offset, total)
}

private fun Connection.ticketDetail(id: UUID, scope: TicketScope, viewer: UUID): TicketDetailDto? {
    val (clause, arg) = scopeClause(scope)
    val ticket = queryOne("SELECT * FROM support_tickets WHERE id = ? AND $clause", id, arg) {
        summaryRow(it, scope == TicketScope.Admin)
    } ?: return null
    val messages = query("SELECT * FROM support_ticket_messages WHERE ticket_id = ? ORDER BY created_at, id", id) {
        messageRow(it, viewer)
    }
    val history = query("SELECT * FROM support_ticket_status_history WHERE ticket_id = ? ORDER BY changed_at, id", id) {
        StatusChangeDto(it.getString("from_status")?.let(TicketStatus::valueOf), TicketStatus.valueOf(it.getString("to_status")),
            it.getString("actor_role"), it.instant("changed_at")!!)
    }
    return TicketDetailDto(ticket, messages, history)
}

/** Returns null when [clientMessageId] was already used by this author on this ticket. */
private fun Connection.insertMessage(ticketId: UUID, author: UUID, role: String, body: String, clientMessageId: String?): UUID? =
    queryOne(
        """INSERT INTO support_ticket_messages (id, ticket_id, author_user_id, author_role, body, client_message_id)
           VALUES (?, ?, ?, ?, ?, ?)
           ON CONFLICT (ticket_id, author_user_id, client_message_id) WHERE client_message_id IS NOT NULL DO NOTHING
           RETURNING id""",
        UUID.randomUUID(), ticketId, author, role, body, clientMessageId,
    ) { it.uuid("id") }

private fun Connection.recordStatus(ticketId: UUID, from: TicketStatus?, to: TicketStatus, actor: UUID, role: String) {
    update("""INSERT INTO support_ticket_status_history (id, ticket_id, from_status, to_status, actor_user_id, actor_role)
              VALUES (?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), ticketId, from?.name, to.name, actor, role)
}

private fun Connection.changeStatus(ticket: TicketRow, to: TicketStatus, actor: UUID, role: String) {
    update(
        """UPDATE support_tickets SET status = ?, updated_at = now(),
               escalated_at = CASE WHEN ? = 'ESCALATED' THEN COALESCE(escalated_at, now()) ELSE escalated_at END
           WHERE id = ?""",
        to.name, to.name, ticket.id,
    )
    recordStatus(ticket.id, ticket.status, to, actor, role)
}

/** In-app inbox row only; nothing is pushed, texted or emailed. */
private fun Connection.notifyCustomer(ticket: TicketRow, eventType: String, title: String, body: String) {
    update("INSERT INTO notification_inbox (id, user_id, event_type, title, body, ticket_id) VALUES (?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(), ticket.userId, eventType, title, body.take(500), ticket.id)
}

private fun validBody(raw: String): String = raw.trim().also {
    if (it.length !in 1..4000) throw badRequest("INVALID_MESSAGE", "Message must be 1 to 4000 characters")
}

private fun validClientId(raw: String) {
    if (!clientIdPattern.matches(raw)) throw badRequest("INVALID_CLIENT_ID", "Client IDs must be 8 to 64 URL-safe characters")
}

private fun summaryRow(rows: ResultSet, includeCustomer: Boolean) = TicketSummaryDto(
    id = rows.uuid("id").toString(),
    category = SupportCategory.valueOf(rows.getString("category")),
    subject = rows.getString("subject"),
    status = TicketStatus.valueOf(rows.getString("status")),
    sellerId = rows.getString("seller_id"),
    requestReference = rows.getString("request_reference"),
    customerUserId = if (includeCustomer) rows.uuid("user_id").toString() else null,
    escalatedAt = rows.instant("escalated_at"),
    createdAt = rows.instant("created_at")!!,
    updatedAt = rows.instant("updated_at")!!,
)

private fun messageRow(rows: ResultSet, viewer: UUID) = TicketMessageDto(
    id = rows.uuid("id").toString(),
    authorRole = rows.getString("author_role"),
    mine = rows.uuid("author_user_id") == viewer,
    body = rows.getString("body"),
    createdAt = rows.instant("created_at")!!,
)

internal fun Connection.supportCounts(sellerId: String?): SupportCountsDto {
    val counts = query(
        "SELECT status, count(*) FROM support_tickets WHERE (?::text IS NULL OR seller_id = ?::text) GROUP BY status",
        sellerId, sellerId) { it.getString(1) to it.getLong(2) }.toMap()
    return SupportCountsDto(counts["OPEN"] ?: 0, counts["ESCALATED"] ?: 0, counts["RESOLVED"] ?: 0, counts["CLOSED"] ?: 0)
}
