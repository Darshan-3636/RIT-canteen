package edu.msrit.canteen.backend.features.management

import edu.msrit.canteen.backend.api.requirePlatformAdmin
import edu.msrit.canteen.backend.api.requireSellerCapability
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.features.engagement.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun Route.registerManagementRoutes(context: BackendContext) {
    registerManagementAnalytics(context)
    registerPromotionRoutes(context)
    val catalog=CatalogService(context)
    val imageStorage=MenuImageStorage()
    val members=MembershipService(context)
    val admin=AdministrationService(context)
    post("/sellers/{sellerId}/catalog/items/{itemId}/image") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"catalog.edit") ?: return@handled
        val itemId=call.parameters["itemId"].orEmpty()
        if (!itemId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
            throw badRequest("INVALID_ITEM_ID","Invalid menu item identifier")
        val exists=context.read { db -> db.queryOne("SELECT 1 FROM menu_items WHERE seller_id=? AND id=?",seller,itemId) { true } ?: false }
        if (!exists && !itemId.matches(Regex("item-[0-9a-fA-F-]{36}"))) throw notFound()
        val declaredMime=call.request.contentType().withoutParameters().toString()
        val bytes=withContext(Dispatchers.IO) { call.receiveChannel().toInputStream().use { it.readNBytes(MAX_MENU_IMAGE_UPLOAD_BYTES+1) } }
        val normalized=try { normalizeMenuImage(bytes,declaredMime) }
            catch (failure: InvalidMenuImage) { throw badRequest("INVALID_MENU_IMAGE",failure.message ?: "Invalid menu image") }
        val stored=try { withContext(Dispatchers.IO) { imageStorage.upload(seller,normalized) } }
            catch (_: MenuImageStorageUnavailable) { throw ApiFailure(HttpStatusCode.ServiceUnavailable,"IMAGE_STORAGE_UNAVAILABLE",
                "Menu image storage is not configured or is unavailable. The item was not saved.") }
        call.respond(HttpStatusCode.Created,mapOf("imageKey" to stored.key,"width" to stored.width,"height" to stored.height))
    } }
    get("/catalog/images/{imageKey}") { call.handled {
        val key=call.parameters["imageKey"].orEmpty()
        if (!key.matches(Regex("custom-[0-9a-fA-F-]{36}"))) throw notFound()
        val seller=context.read { db -> db.queryOne("""SELECT seller_id FROM menu_items WHERE image_key=?
            AND published=TRUE AND archived_at IS NULL AND
            (availability='AVAILABLE' OR unavailable_visibility='SHOW') LIMIT 1""",key) { it.getString(1) } } ?: throw notFound()
        val bytes=try { withContext(Dispatchers.IO) { imageStorage.download(seller,key) } }
            catch (_: MenuImageStorageUnavailable) { throw ApiFailure(HttpStatusCode.ServiceUnavailable,"IMAGE_STORAGE_UNAVAILABLE",
                "Menu image storage is temporarily unavailable") }
        call.respondBytes(bytes,ContentType.Image.JPEG,HttpStatusCode.OK)
    } }
    get("/sellers/{sellerId}/catalog/items") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"catalog.edit") ?: return@handled
        call.respond(catalog.items(seller,call.page()))
    } }
    get("/sellers/{sellerId}/catalog/items/{itemId}") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"catalog.edit") ?: return@handled
        call.respond(catalog.item(seller,call.parameters["itemId"]!!))
    } }
    post("/sellers/{sellerId}/catalog/items") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"catalog.edit") ?: return@handled
        call.respond(HttpStatusCode.Created,catalog.create(actor,seller,call.receiveBody()))
    } }
    for(archive in listOf(true,false)) post("/sellers/{sellerId}/catalog/items/{itemId}/${if(archive) "archive" else "restore"}") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"catalog.edit") ?: return@handled
        call.respond(catalog.archive(actor,seller,call.parameters["itemId"]!!,call.receiveBody<VersionRequest>().expectedVersion,archive))
    } }
    get("/sellers/{sellerId}/catalog/categories") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"catalog.edit") ?: return@handled
        call.respond(catalog.categories(seller))
    } }
    put("/sellers/{sellerId}/catalog/categories/{categoryId}") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"catalog.edit") ?: return@handled
        catalog.category(actor,seller,call.parameters["categoryId"]!!,call.receiveBody())
        call.respond(catalog.categories(seller))
    } }
    patch("/sellers/{sellerId}/profile") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"profile.edit") ?: return@handled
        call.respond(catalog.profile(actor,seller,call.receiveBody()))
    } }
    get("/sellers/{sellerId}/profile") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"profile.edit") ?: return@handled
        call.respond(context.read { it.sellerState(seller) })
    } }
    get("/sellers/{sellerId}/operations/version") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"hours.edit") ?: return@handled
        call.respond(PolicyVersion(context.read { it.queryOne("SELECT version FROM seller_ordering_policies WHERE seller_id=?",seller) { row -> row.getLong(1) } ?: 0 }))
    } }
    for(administrative in listOf(false,true)) {
        val prefix=if(administrative) "/admin/sellers/{sellerId}/memberships" else "/sellers/{sellerId}/memberships"
        get(prefix) { call.handled {
            val seller=call.parameters["sellerId"]!!
            if(administrative) call.requirePlatformAdmin(context) ?: return@handled
            else {
                val actor=call.requireSellerCapability(context,seller,"employees.manage") ?: return@handled
                if(context.read { it.queryOne("SELECT role FROM seller_memberships WHERE seller_id=? AND user_id=?",seller,actor.userId) { r -> r.getString(1) } } !in setOf("OWNER","MANAGER")) denied()
            }
            call.respond(members.list(seller,call.page()))
        } }
        post(prefix) { call.handled {
            val seller=call.parameters["sellerId"]!!
            val actor=(if(administrative) call.requirePlatformAdmin(context) else call.requireSellerCapability(context,seller,"employees.manage")) ?: return@handled
            call.respond(HttpStatusCode.Created,members.save(actor,seller,null,call.receiveBody(),administrative))
        } }
        patch("$prefix/{membershipId}") { call.handled {
            val seller=call.parameters["sellerId"]!!
            val actor=(if(administrative) call.requirePlatformAdmin(context) else call.requireSellerCapability(context,seller,"employees.manage")) ?: return@handled
            call.respond(members.save(actor,seller,call.pathUuid("membershipId"),call.receiveBody(),administrative))
        } }
        put("$prefix/{membershipId}/capabilities") { call.handled {
            val seller=call.parameters["sellerId"]!!
            val actor=(if(administrative) call.requirePlatformAdmin(context) else call.requireSellerCapability(context,seller,"employees.manage")) ?: return@handled
            call.respond(members.grants(actor,seller,call.pathUuid("membershipId"),call.receiveBody(),administrative))
        } }
    }
    get("/admin/sellers/{sellerId}") { call.handled {
        call.requirePlatformAdmin(context) ?: return@handled
        call.respond(admin.seller(call.parameters["sellerId"]!!))
    } }
    get("/admin/users/{userId}") { call.handled {
        call.requirePlatformAdmin(context) ?: return@handled
        call.respond(admin.user(call.pathUuid("userId")))
    } }
    get("/admin/setup-drafts/{id}") { call.handled {
        call.requirePlatformAdmin(context) ?: return@handled
        call.respond(admin.draft(call.pathUuid("id")))
    } }
    for(approve in listOf(true,false)) post("/admin/setup-drafts/{id}/${if(approve) "approve" else "reject"}") { call.handled {
        val actor=call.requirePlatformAdmin(context) ?: return@handled
        call.respond(admin.decide(actor,call.pathUuid("id"),approve,call.receiveBody()))
    } }
    for(active in listOf(true,false)) post("/admin/sellers/{sellerId}/${if(active) "activate" else "suspend"}") { call.handled {
        val actor=call.requirePlatformAdmin(context) ?: return@handled
        call.respond(admin.sellerStatus(actor,call.parameters["sellerId"]!!,active,call.receiveBody()))
    } }
    post("/admin/users/{userId}/status") { call.handled {
        val actor=call.requirePlatformAdmin(context) ?: return@handled
        call.respond(admin.userStatus(actor,call.pathUuid("userId"),call.receiveBody()))
    } }
    put("/admin/users/{userId}/platform-admin") { call.handled {
        val actor=call.requirePlatformAdmin(context) ?: return@handled
        call.respond(admin.adminFlag(actor,call.pathUuid("userId"),call.receiveBody()))
    } }
    get("/admin/catalog/categories") { call.handled {
        call.requirePlatformAdmin(context) ?: return@handled
        call.respond(context.read { db -> buildJsonArray { db.query("SELECT * FROM menu_categories ORDER BY sort_order,id") { r ->
            buildJsonObject { put("id",r.getString("id"));put("name",r.getString("name"));put("active",r.getBoolean("active"));put("sortOrder",r.getInt("sort_order")) }
        }.forEach { add(it) } } })
    } }
    post("/admin/catalog/categories") { call.handled {
        val actor=call.requirePlatformAdmin(context) ?: return@handled
        catalog.globalCategory(actor,call.receiveBody(),true); call.respond(HttpStatusCode.Created)
    } }
    put("/admin/catalog/categories/{categoryId}") { call.handled {
        val actor=call.requirePlatformAdmin(context) ?: return@handled
        val body=call.receiveBody<GlobalCategoryWrite>()
        if(body.id!=call.parameters["categoryId"]) throw badRequest("ID_MISMATCH","Category ID cannot change")
        catalog.globalCategory(actor,body,false); call.respond(HttpStatusCode.NoContent)
    } }
}
