package edu.msrit.canteen.backend.features.management

import edu.msrit.canteen.backend.api.requireAuthenticatedUser
import edu.msrit.canteen.backend.api.requireSellerCapability
import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.features.engagement.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Serializable internal data class PromotionWrite(
    val title: String, val description: String = "", val kind: String,
    val audience: String = "GENERAL", val discountPercent: Int? = null,
    val discountPaise: Long? = null, val startsAt: String, val endsAt: String,
    val published: Boolean = false, val itemIds: List<String> = emptyList(),
    val categoryIds: List<String> = emptyList(), val expectedVersion: Long? = null,
)

@Serializable internal data class PromotionDto(
    val id: String, val sellerId: String, val title: String, val description: String,
    val kind: String, val audience: String, val discountPercent: Int?, val discountPaise: Long?,
    val startsAt: String, val endsAt: String, val published: Boolean,
    val itemIds: List<String>, val categoryIds: List<String>, val version: Long,
)

@Serializable internal data class PromotionsDto(val items: List<PromotionDto>)

internal class PromotionService(private val context: BackendContext) {
    private val tx = ManagementTransactions(context)

    fun sellerList(seller: String): PromotionsDto = context.read { db ->
        PromotionsDto(db.query("SELECT * FROM promotions WHERE seller_id=? ORDER BY created_at DESC,id", seller) {
            db.promotion(it)
        })
    }

    fun customerList(principal: AuthenticatedPrincipal, seller: String?): PromotionsDto = context.read { db ->
        val consent = db.queryOne(
            "SELECT personalization_consent FROM customer_preferences WHERE user_id=?", principal.userId,
        ) { it.getBoolean(1) } == true
        PromotionsDto(db.query(
            """SELECT p.* FROM promotions p JOIN sellers s ON s.id=p.seller_id
               WHERE s.active AND p.published AND p.starts_at<=? AND p.ends_at>?
                 AND (?::text IS NULL OR p.seller_id=?)
                 AND (p.audience='GENERAL' OR ?)
               ORDER BY p.ends_at,p.id LIMIT 100""",
            context.clock.instant(), context.clock.instant(), seller, seller, consent,
        ) { db.promotion(it) })
    }

    fun save(actor: AuthenticatedPrincipal, seller: String, id: UUID?, request: PromotionWrite): PromotionDto {
        val title = text(request.title,120,"title")
        if (request.description.length > 1000 || request.description.any(Char::isISOControl))
            throw badRequest("INVALID_DESCRIPTION","description must be at most 1000 readable characters")
        if (request.kind !in setOf("FEATURED","TODAYS_SPECIAL","NEW_ARRIVAL","BANNER","PERCENT_DISCOUNT","FIXED_DISCOUNT"))
            throw badRequest("INVALID_KIND","Unknown promotion type")
        if (request.audience !in setOf("GENERAL","BEHAVIORAL")) throw badRequest("INVALID_AUDIENCE","Unknown audience")
        if (when (request.kind) {
            "PERCENT_DISCOUNT" -> request.discountPercent !in 1..100 || request.discountPaise != null
            "FIXED_DISCOUNT" -> request.discountPaise == null || request.discountPaise !in 1..1_000_000_000L || request.discountPercent != null
            else -> request.discountPercent != null || request.discountPaise != null
        }) throw badRequest("INVALID_DISCOUNT","Provide a valid percent or integer paise amount for this offer")
        val start = runCatching { Instant.parse(request.startsAt) }.getOrNull()
            ?: throw badRequest("INVALID_DATE","startsAt must be an ISO instant")
        val end = runCatching { Instant.parse(request.endsAt) }.getOrNull()
            ?: throw badRequest("INVALID_DATE","endsAt must be an ISO instant")
        if (!end.isAfter(start)) throw badRequest("INVALID_DATE","endsAt must be after startsAt")
        if (request.itemIds.size > 100 || request.categoryIds.size > 100 ||
            request.itemIds.size != request.itemIds.toSet().size || request.categoryIds.size != request.categoryIds.toSet().size)
            throw badRequest("INVALID_TARGETS","Targets must be distinct and limited to 100 of each kind")
        return tx.write(actor,seller,"promotions.manage") { db ->
            if (id != null) {
                val current = db.queryOne("SELECT version FROM promotions WHERE id=? AND seller_id=? FOR UPDATE",id,seller) { it.getLong(1) }
                    ?: throw notFound()
                version(current,request.expectedVersion ?: throw badRequest("EXPECTED_VERSION_REQUIRED","expectedVersion is required"))
                db.update("""UPDATE promotions SET title=?,description=?,kind=?,audience=?,discount_percent=?,discount_paise=?,
                    starts_at=?,ends_at=?,published=?,version=version+1,updated_at=now() WHERE id=? AND seller_id=?""",
                    title,request.description.trim(),request.kind,request.audience,request.discountPercent,request.discountPaise,
                    start,end,request.published,id,seller)
                db.update("DELETE FROM promotion_items WHERE promotion_id=?",id)
                db.update("DELETE FROM promotion_categories WHERE promotion_id=?",id)
            } else {
                if (request.expectedVersion != null) throw badRequest("INVALID_VERSION","Creation does not accept expectedVersion")
            }
            val promotionId = id ?: UUID.randomUUID().also { newId ->
                db.update("""INSERT INTO promotions(id,seller_id,title,description,kind,audience,discount_percent,
                    discount_paise,starts_at,ends_at,published) VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                    newId,seller,title,request.description.trim(),request.kind,request.audience,
                    request.discountPercent,request.discountPaise,start,end,request.published)
            }
            request.itemIds.forEach { item ->
                if (db.queryOne("SELECT 1 FROM menu_items WHERE seller_id=? AND id=?",seller,item) { true } == null)
                    throw badRequest("INVALID_TARGETS","An item does not belong to this seller")
                db.update("INSERT INTO promotion_items(seller_id,promotion_id,item_id) VALUES (?,?,?)",seller,promotionId,item)
            }
            request.categoryIds.forEach { category ->
                if (db.queryOne("SELECT 1 FROM seller_categories WHERE seller_id=? AND category_id=?",seller,category) { true } == null)
                    throw badRequest("INVALID_TARGETS","A category does not belong to this seller")
                db.update("INSERT INTO promotion_categories(seller_id,promotion_id,category_id) VALUES (?,?,?)",seller,promotionId,category)
            }
            audit(db,actor,false,if(id==null) "seller.promotion.create" else "seller.promotion.update",
                "PROMOTION",promotionId.toString(),seller)
            db.queryOne("SELECT * FROM promotions WHERE id=? AND seller_id=?",promotionId,seller) { db.promotion(it) }!!
        }
    }
}

private fun Connection.promotion(row: ResultSet): PromotionDto {
    val id = row.getObject("id",UUID::class.java)
    return PromotionDto(id.toString(),row.getString("seller_id"),row.getString("title"),
        row.getString("description"),row.getString("kind"),row.getString("audience"),
        row.getInt("discount_percent").takeUnless { row.wasNull() },
        row.getLong("discount_paise").takeUnless { row.wasNull() },
        row.getTimestamp("starts_at").toInstant().toString(),row.getTimestamp("ends_at").toInstant().toString(),
        row.getBoolean("published"),
        query("SELECT item_id FROM promotion_items WHERE promotion_id=? ORDER BY item_id",id) { it.getString(1) },
        query("SELECT category_id FROM promotion_categories WHERE promotion_id=? ORDER BY category_id",id) { it.getString(1) },
        row.getLong("version"))
}

internal fun Route.registerPromotionRoutes(context: BackendContext) {
    val service = PromotionService(context)
    get("/me/offers") { call.handled {
        val actor = call.requireAuthenticatedUser(context) ?: return@handled
        val seller = call.request.queryParameters["sellerId"]
        call.respond(service.customerList(actor,seller))
    } }
    get("/sellers/{sellerId}/promotions") { call.handled {
        val seller = call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"promotions.manage") ?: return@handled
        call.respond(service.sellerList(seller))
    } }
    post("/sellers/{sellerId}/promotions") { call.handled {
        val seller = call.parameters["sellerId"]!!
        val actor = call.requireSellerCapability(context,seller,"promotions.manage") ?: return@handled
        call.respond(HttpStatusCode.Created,service.save(actor,seller,null,call.receiveBody()))
    } }
    put("/sellers/{sellerId}/promotions/{promotionId}") { call.handled {
        val seller = call.parameters["sellerId"]!!
        val actor = call.requireSellerCapability(context,seller,"promotions.manage") ?: return@handled
        call.respond(service.save(actor,seller,call.pathUuid("promotionId"),call.receiveBody()))
    } }
}
