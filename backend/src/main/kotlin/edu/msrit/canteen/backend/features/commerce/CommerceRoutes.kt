package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.api.requireAuthenticatedUser
import edu.msrit.canteen.backend.api.requireSellerCapability
import edu.msrit.canteen.backend.api.respondApiError
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.transaction
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.delete
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID
import java.sql.Connection

/** Commerce feature seam. The application owner mounts this below `/api/v1` after branch integration. */
fun Route.registerCommerceRoutes(context: BackendContext) {
    get("/sellers") { call.commerce { call.respond(context.dataSource.connection.use { SellerListDto(sellers(it)) }) } }
    get("/sellers/{sellerId}") {
        call.commerce {
            val id=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val found=context.dataSource.connection.use { c -> sellers(c).firstOrNull { it.id==id } }
                ?: throw CommerceFault(404,"SELLER_NOT_FOUND","Seller not found")
            call.respond(found)
        }
    }
    get("/sellers/{sellerId}/menu") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val category=call.request.queryParameters["categoryId"]
            val result=context.dataSource.connection.use { c ->
                if(sellers(c).none { it.id==seller }) throw CommerceFault(404,"SELLER_NOT_FOUND","Seller not found")
                val categories=c.prepareStatement("SELECT category_id FROM seller_categories WHERE seller_id=? ORDER BY category_id").use { s -> s.setString(1,seller);s.executeQuery().use { r -> buildList { while(r.next()) add(r.getString(1)) } } }
                if(category!=null && category !in categories) throw CommerceFault(404,"CATEGORY_NOT_FOUND","Category is not offered by this seller")
                MenuDto(seller,categories,menuItems(c,seller,call.request.queryParameters["query"],category))
            }
            call.respond(result)
        }
    }
    get("/sellers/{sellerId}/menu/{itemId}") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val item=call.parameters["itemId"] ?: throw CommerceFault(400,"ITEM_ID_REQUIRED","Item id is required")
            val result=context.dataSource.connection.use { c -> menuItems(c,seller,null,null).firstOrNull { it.id==item } }
                ?: throw CommerceFault(404,"ITEM_NOT_FOUND","Menu item not found")
            call.respond(result)
        }
    }
    get("/sellers/{sellerId}/operating-policy") {
        call.commerce {
            val seller = call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val policy = context.dataSource.connection.use { db ->
                val row = db.prepareStatement(
                    """SELECT p.bulk_minimum_units,p.special_pickup_enabled,p.timezone
                       FROM seller_ordering_policies p JOIN sellers s ON s.id=p.seller_id
                       WHERE p.seller_id=? AND s.active""").use { stmt ->
                    stmt.setString(1,seller)
                    stmt.executeQuery().use { result ->
                        if (result.next()) Triple(result.getInt(1), result.getBoolean(2), result.getString(3)) else null
                    }
                } ?: throw CommerceFault(404,"SELLER_NOT_FOUND","Seller not found")
                val hours = db.prepareStatement(
                    "SELECT day_of_week,opens_at,closes_at FROM seller_hours WHERE seller_id=? ORDER BY day_of_week").use { stmt ->
                    stmt.setString(1,seller)
                    stmt.executeQuery().use { result -> buildList {
                        while (result.next()) add(SellerHourEntry(result.getInt(1),
                            result.getObject(2, java.time.LocalTime::class.java).toString(),
                            result.getObject(3, java.time.LocalTime::class.java).toString()))
                    } }
                }
                val closures = db.prepareStatement(
                    "SELECT local_date,reason FROM seller_closures WHERE seller_id=? AND local_date>=CURRENT_DATE ORDER BY local_date").use { stmt ->
                    stmt.setString(1,seller)
                    stmt.executeQuery().use { result -> buildList {
                        while (result.next()) add(SellerClosureDto(
                            result.getObject(1, java.time.LocalDate::class.java).toString(), result.getString(2)))
                    } }
                }
                SellerOperatingPolicyDto(seller,row.first,row.second,row.third,hours,closures)
            }
            call.respond(policy)
        }
    }
    patch("/sellers/{sellerId}/menu/items/{itemId}/availability") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val item=call.parameters["itemId"] ?: throw CommerceFault(400,"ITEM_ID_REQUIRED","Item id is required")
            val principal=call.requireSellerCapability(context,seller,"availability.edit") ?: return@commerce
            val body=call.receive<AvailabilityPatch>()
            if(body.availability !in setOf("AVAILABLE","UNAVAILABLE")) throw CommerceFault(400,"INVALID_AVAILABILITY","availability must be AVAILABLE or UNAVAILABLE")
            context.transaction { c ->
                if(!context.authorization.hasSellerCapability(c,principal,seller,"availability.edit")) throw CommerceFault(403,"SELLER_CAPABILITY_REQUIRED","This seller action is not permitted")
                c.prepareStatement("UPDATE menu_items SET availability=?,updated_at=now() WHERE seller_id=? AND id=?").use { s -> s.setString(1,body.availability);s.setString(2,seller);s.setString(3,item);if(s.executeUpdate()!=1) throw CommerceFault(404,"ITEM_NOT_FOUND","Menu item not found") }
            }
            call.respond(context.dataSource.connection.use { menuItem(it,seller,item) }
                ?: throw CommerceFault(404,"ITEM_NOT_FOUND","Menu item not found"))
        }
    }
    patch("/sellers/{sellerId}/menu/items/{itemId}") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val item=call.parameters["itemId"] ?: throw CommerceFault(400,"ITEM_ID_REQUIRED","Item id is required")
            val principal=call.requireSellerCapability(context,seller,"catalog.edit") ?: return@commerce
            val body=call.receive<CatalogItemPatch>()
            if(listOf(body.name,body.description,body.pricePaise,body.published,body.dietary,body.unavailableVisibility).all { it==null }) throw CommerceFault(400,"EMPTY_PATCH","Provide at least one catalog field")
            body.name?.let { if(it.isBlank() || it.length>120) throw CommerceFault(400,"INVALID_NAME","Name must contain 1 to 120 characters") }
            body.description?.let { if(it.length>1200) throw CommerceFault(400,"INVALID_DESCRIPTION","Description is too long") }
            body.pricePaise?.let { if(it<0) throw CommerceFault(400,"INVALID_PRICE","pricePaise cannot be negative") }
            body.dietary?.let { if(it !in setOf("VEGETARIAN","NON_VEGETARIAN","NOT_SPECIFIED")) throw CommerceFault(400,"INVALID_DIETARY","Unknown dietary value") }
            body.unavailableVisibility?.let { if(it !in setOf("SHOW","HIDE")) throw CommerceFault(400,"INVALID_VISIBILITY","unavailableVisibility must be SHOW or HIDE") }
            context.transaction { c ->
                if(!context.authorization.hasSellerCapability(c,principal,seller,"catalog.edit")) throw CommerceFault(403,"SELLER_CAPABILITY_REQUIRED","This seller action is not permitted")
                c.prepareStatement("""UPDATE menu_items SET name=COALESCE(?,name),description=COALESCE(?,description),price_paise=COALESCE(?,price_paise),published=COALESCE(?,published),dietary=COALESCE(?,dietary),unavailable_visibility=COALESCE(?,unavailable_visibility),updated_at=now() WHERE seller_id=? AND id=?""").use { s ->
                    s.setString(1,body.name?.trim());s.setString(2,body.description);if(body.pricePaise==null)s.setNull(3,java.sql.Types.BIGINT) else s.setLong(3,body.pricePaise)
                    if(body.published==null)s.setNull(4,java.sql.Types.BOOLEAN) else s.setBoolean(4,body.published)
                    s.setString(5,body.dietary);s.setString(6,body.unavailableVisibility);s.setString(7,seller);s.setString(8,item)
                    if(s.executeUpdate()!=1) throw CommerceFault(404,"ITEM_NOT_FOUND","Menu item not found")
                }
            }
            call.respond(context.dataSource.connection.use { c -> menuItem(c,seller,item) }
                ?: throw CommerceFault(404,"ITEM_NOT_FOUND","Menu item not found"))
        }
    }
    patch("/sellers/{sellerId}/bulk-policy") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val principal=call.requireSellerCapability(context,seller,"hours.edit") ?: return@commerce
            val body=call.receive<BulkPolicyPatch>()
            if(body.minimumUnits==null && body.specialPickupEnabled==null) throw CommerceFault(400,"EMPTY_PATCH","Provide a minimum or special-pickup setting")
            body.minimumUnits?.let { if(it !in 1..999) throw CommerceFault(400,"INVALID_MINIMUM","minimumUnits must be between 1 and 999") }
            context.transaction { c ->
                if(!context.authorization.hasSellerCapability(c,principal,seller,"hours.edit")) throw CommerceFault(403,"SELLER_CAPABILITY_REQUIRED","This seller action is not permitted")
                c.prepareStatement("INSERT INTO seller_ordering_policies(seller_id,bulk_minimum_units,special_pickup_enabled) VALUES (?,?,?) ON CONFLICT(seller_id) DO UPDATE SET bulk_minimum_units=COALESCE(?,seller_ordering_policies.bulk_minimum_units),special_pickup_enabled=COALESCE(?,seller_ordering_policies.special_pickup_enabled),updated_at=now()").use { s ->
                    s.setString(1,seller);s.setInt(2,body.minimumUnits ?: 8);s.setBoolean(3,body.specialPickupEnabled ?: false)
                    if(body.minimumUnits==null)s.setNull(4,java.sql.Types.INTEGER) else s.setInt(4,body.minimumUnits)
                    if(body.specialPickupEnabled==null)s.setNull(5,java.sql.Types.BOOLEAN) else s.setBoolean(5,body.specialPickupEnabled)
                    s.executeUpdate()
                }
            }
            call.respond(context.dataSource.connection.use { c -> sellers(c).firstOrNull { it.id==seller } ?: throw CommerceFault(404,"SELLER_NOT_FOUND","Seller not found") })
        }
    }
    put("/sellers/{sellerId}/hours") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val principal=call.requireSellerCapability(context,seller,"hours.edit") ?: return@commerce
            val body=call.receive<SellerHoursRequest>()
            if(body.hours.size>7 || body.hours.map { it.dayOfWeek }.toSet().size!=body.hours.size || body.hours.any { it.dayOfWeek !in 1..7 }) throw CommerceFault(400,"INVALID_HOURS","Hours must have unique ISO weekdays from 1 through 7")
            val parsed=body.hours.map { entry ->
                val open=try { java.time.LocalTime.parse(entry.opensAt) } catch (_:Exception) { throw CommerceFault(400,"INVALID_HOURS","Hours must use ISO local times") }
                val close=try { java.time.LocalTime.parse(entry.closesAt) } catch (_:Exception) { throw CommerceFault(400,"INVALID_HOURS","Hours must use ISO local times") }
                if(!open.isBefore(close)) throw CommerceFault(400,"INVALID_HOURS","Closing time must be after opening time")
                Triple(entry.dayOfWeek,open,close)
            }
            context.transaction { c ->
                if(!context.authorization.hasSellerCapability(c,principal,seller,"hours.edit")) throw CommerceFault(403,"SELLER_CAPABILITY_REQUIRED","This seller action is not permitted")
                c.lockSellerPolicy(seller)
                c.prepareStatement("DELETE FROM seller_hours WHERE seller_id=?").use { it.setString(1,seller);it.executeUpdate() }
                c.prepareStatement("INSERT INTO seller_hours(seller_id,day_of_week,opens_at,closes_at) VALUES (?,?,?,?)").use { s -> parsed.forEach { (day,open,close) -> s.setString(1,seller);s.setInt(2,day);s.setObject(3,open);s.setObject(4,close);s.addBatch() };s.executeBatch() }
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
    put("/sellers/{sellerId}/closures/{localDate}") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val principal=call.requireSellerCapability(context,seller,"hours.edit") ?: return@commerce
            val date=try { java.time.LocalDate.parse(call.parameters["localDate"] ?: "") } catch (_:Exception) { throw CommerceFault(400,"INVALID_DATE","localDate must be YYYY-MM-DD") }
            val body=call.receive<SellerClosureRequest>();if(body.reason.length>300) throw CommerceFault(400,"INVALID_REASON","Closure reason is too long")
            context.transaction { c ->
                if(!context.authorization.hasSellerCapability(c,principal,seller,"hours.edit")) throw CommerceFault(403,"SELLER_CAPABILITY_REQUIRED","This seller action is not permitted")
                c.lockSellerPolicy(seller)
                c.prepareStatement("INSERT INTO seller_closures(seller_id,local_date,reason) VALUES (?,?,?) ON CONFLICT(seller_id,local_date) DO UPDATE SET reason=EXCLUDED.reason").use { s -> s.setString(1,seller);s.setObject(2,date);s.setString(3,body.reason);s.executeUpdate() }
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
    delete("/sellers/{sellerId}/closures/{localDate}") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val principal=call.requireSellerCapability(context,seller,"hours.edit") ?: return@commerce
            val date=try { java.time.LocalDate.parse(call.parameters["localDate"] ?: "") } catch (_:Exception) { throw CommerceFault(400,"INVALID_DATE","localDate must be YYYY-MM-DD") }
            context.transaction { c ->
                if(!context.authorization.hasSellerCapability(c,principal,seller,"hours.edit")) throw CommerceFault(403,"SELLER_CAPABILITY_REQUIRED","This seller action is not permitted")
                c.lockSellerPolicy(seller)
                c.prepareStatement("DELETE FROM seller_closures WHERE seller_id=? AND local_date=?").use { s -> s.setString(1,seller);s.setObject(2,date);s.executeUpdate() }
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    post("/bulk/requests") {
        call.commerce {
            val principal=call.requireAuthenticatedUser(context) ?: return@commerce
            val body=call.receive<BulkCreateRequest>()
            val key=call.request.headers["Idempotency-Key"] ?: throw CommerceFault(400,"IDEMPOTENCY_KEY_REQUIRED","Idempotency-Key header is required")
            call.respond(HttpStatusCode.Created,createBulk(context,principal,body.sellerId,body.lines,body.pickupAt,
                body.specialPickupReason,body.instructions,key))
        }
    }
    get("/me/bulk/requests") {
        call.commerce { val p=call.requireAuthenticatedUser(context) ?: return@commerce;call.respond(customerBulk(context,p.userId)) }
    }
    get("/me/bulk/requests/{requestId}") {
        call.commerce {
            val p=call.requireAuthenticatedUser(context) ?: return@commerce;val id=call.uuidParameter("requestId");expireDue(context)
            val value=context.dataSource.connection.use { c ->
                c.prepareStatement("SELECT 1 FROM bulk_requests WHERE id=? AND customer_user_id=?").use { s -> s.setObject(1,id);s.setObject(2,p.userId);s.executeQuery().use { it.next() } }
                    .let { owned -> if(owned) loadBulk(c,id) else null }
            } ?: throw CommerceFault(404,"REQUEST_NOT_FOUND","Bulk request not found")
            call.respond(value)
        }
    }
    get("/sellers/{sellerId}/bulk/requests") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            if(call.requireSellerCapability(context,seller,"bulk.view")==null) return@commerce
            val status=call.request.queryParameters["status"]
            if(status!=null && status !in setOf("SUBMITTED","COUNTEROFFER","AGREED_AWAITING_PAYMENT","EXPIRED","REJECTED","WITHDRAWN")) throw CommerceFault(400,"INVALID_STATUS","Unknown request status")
            call.respond(sellerBulk(context,seller,status))
        }
    }
    get("/sellers/{sellerId}/bulk/requests/{requestId}") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            if(call.requireSellerCapability(context,seller,"bulk.view")==null) return@commerce
            val id=call.uuidParameter("requestId");expireDue(context)
            val value=context.dataSource.connection.use { c -> c.prepareStatement("SELECT 1 FROM bulk_requests WHERE id=? AND seller_id=?").use { s -> s.setObject(1,id);s.setString(2,seller);s.executeQuery().use { if(it.next()) loadBulk(c,id) else null } } }
                ?: throw CommerceFault(404,"REQUEST_NOT_FOUND","Bulk request not found")
            call.respond(value)
        }
    }
    post("/sellers/{sellerId}/bulk/requests/{requestId}/counteroffers") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val p=call.requireSellerCapability(context,seller,"bulk.respond") ?: return@commerce
            val body=call.receive<QuoteTermsRequest>();call.respond(counteroffer(context,p,seller,call.uuidParameter("requestId"),body))
        }
    }
    post("/bulk/requests/{requestId}/customer-approval") {
        call.commerce { val p=call.requireAuthenticatedUser(context) ?: return@commerce;val b=call.receive<ApprovalRequest>();call.respond(approveBulk(context,p,call.uuidParameter("requestId"),b.expectedVersion)) }
    }
    post("/sellers/{sellerId}/bulk/requests/{requestId}/seller-approval") {
        call.commerce { val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required");val p=call.requireSellerCapability(context,seller,"bulk.respond") ?: return@commerce;val b=call.receive<ApprovalRequest>();call.respond(approveBulk(context,p,call.uuidParameter("requestId"),b.expectedVersion,seller)) }
    }
    post("/sellers/{sellerId}/bulk/requests/{requestId}/special-pickup-decision") {
        call.commerce { val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required");val p=call.requireSellerCapability(context,seller,"bulk.respond") ?: return@commerce;val b=call.receive<SpecialPickupDecisionRequest>();call.respond(decideSpecialPickup(context,p,seller,call.uuidParameter("requestId"),b.expectedVersion,b.approved)) }
    }
    post("/bulk/requests/{requestId}/withdraw") {
        call.commerce { val p=call.requireAuthenticatedUser(context) ?: return@commerce;val b=call.receive<ApprovalRequest>();call.respond(withdrawBulk(context,p,call.uuidParameter("requestId"),b.expectedVersion)) }
    }
    get("/sellers/{sellerId}/pickup-schedule") {
        call.commerce {
            val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            if(call.requireSellerCapability(context,seller,"bulk.view")==null) return@commerce
            // No payment provider or fulfillment policy exists, so the backend has no confirmed pickups to expose.
            call.respond(PickupScheduleDto(emptyList(),"No paid or operationally confirmed pickups are available in this demo backend."))
        }
    }
    get("/me/cart/individual") {
        call.commerce { val p=call.requireAuthenticatedUser(context) ?: return@commerce;call.respond(readIndividualCart(context,p)) }
    }
    get("/me/draft/bulk") {
        call.commerce { val p=call.requireAuthenticatedUser(context) ?: return@commerce
            call.respond(readBulkDraft(context,p)) }
    }
    put("/me/draft/bulk") {
        call.commerce { val p=call.requireAuthenticatedUser(context) ?: return@commerce
            call.respond(replaceBulkDraft(context,p,call.receive<BulkDraftReplaceRequest>())) }
    }
    put("/me/cart/individual") {
        call.commerce { val p=call.requireAuthenticatedUser(context) ?: return@commerce
            call.respond(replaceIndividualCart(context,p,call.receive<IndividualCartReplaceRequest>())) }
    }
    post("/me/cart/individual/checkout") {
        call.commerce { val p=call.requireAuthenticatedUser(context) ?: return@commerce
            val key=call.request.headers["Idempotency-Key"] ?: throw CommerceFault(400,"IDEMPOTENCY_KEY_REQUIRED","Idempotency-Key header is required")
            call.respond(HttpStatusCode.Created,checkoutIndividualCart(context,p,call.receive<IndividualCartCheckoutRequest>(),key)) }
    }
    post("/me/individual-demo-orders") {
        call.commerce { val p=call.requireAuthenticatedUser(context) ?: return@commerce;val b=call.receive<CustomerDemoOrderRequest>();call.respond(HttpStatusCode.Created,createIndividual(context,p,b.lines)) }
    }
    get("/me/individual-demo-orders") {
        call.commerce { val p=call.requireAuthenticatedUser(context) ?: return@commerce;call.respond(customerIndividual(context,p.userId)) }
    }
    post("/me/individual-demo-orders/{orderId}/simulate-payment") {
        call.commerce { val p=call.requireAuthenticatedUser(context) ?: return@commerce
            val body=call.receive<DemoPaymentRequest>()
            call.respond(simulateIndividualPayment(context,p,call.uuidParameter("orderId"),body.outcome)) }
    }
    get("/sellers/{sellerId}/individual-demo-orders") {
        call.commerce { val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            if(call.requireSellerCapability(context,seller,"bulk.view")==null) return@commerce
            call.respond(sellerIndividual(context,seller)) }
    }
    post("/sellers/{sellerId}/individual-demo-orders/{orderId}/demo-status") {
        call.commerce { val seller=call.parameters["sellerId"] ?: throw CommerceFault(400,"SELLER_ID_REQUIRED","Seller id is required")
            val p=call.requireSellerCapability(context,seller,"bulk.respond") ?: return@commerce
            val body=call.receive<DemoSellerStatusRequest>()
            call.respond(transitionIndividualDemo(context,p,seller,call.uuidParameter("orderId"),body.status)) }
    }
}

private fun Connection.lockSellerPolicy(sellerId:String) {
    prepareStatement("INSERT INTO seller_ordering_policies(seller_id) VALUES (?) ON CONFLICT(seller_id) DO NOTHING").use { it.setString(1,sellerId);it.executeUpdate() }
    prepareStatement("SELECT seller_id FROM seller_ordering_policies WHERE seller_id=? FOR UPDATE").use { it.setString(1,sellerId);it.executeQuery().use { rows -> if(!rows.next()) throw CommerceFault(404,"SELLER_NOT_FOUND","Seller not found") } }
}

private suspend inline fun ApplicationCall.commerce(crossinline action:suspend ()->Unit) {
    try { action() }
    catch (failure:CommerceFault) { respondApiError(HttpStatusCode.fromValue(failure.http),failure.code,failure.message) }
    catch (failure:io.ktor.server.plugins.BadRequestException) { respondApiError(HttpStatusCode.BadRequest,"INVALID_REQUEST","Request body is invalid") }
}

private fun ApplicationCall.uuidParameter(name:String):UUID = try { UUID.fromString(parameters[name] ?: throw IllegalArgumentException()) }
catch (_:IllegalArgumentException) { throw CommerceFault(400,"INVALID_ID","$name must be a UUID") }
