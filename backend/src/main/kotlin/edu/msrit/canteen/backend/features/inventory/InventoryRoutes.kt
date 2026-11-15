package edu.msrit.canteen.backend.features.inventory

import edu.msrit.canteen.backend.api.requireSellerCapability
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.features.engagement.handled
import edu.msrit.canteen.backend.features.engagement.page
import edu.msrit.canteen.backend.features.engagement.receiveBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.header
import io.ktor.server.routing.*

internal fun Route.registerInventoryRoutes(context: BackendContext) {
    val masters=InventoryMasterService(context)
    val stock=InventoryStockService(context)
    get("/sellers/{sellerId}/inventory/overview") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"inventory.view") ?: return@handled
        call.respond(masters.overview(actor,seller))
    } }
    for(edit in listOf(false,true)) {
        val suffix=if(edit) "/{id}" else ""
        val methodCategory: Route.() -> Unit = {
            val path="/sellers/{sellerId}/inventory/categories$suffix"
            if(edit) patch(path) { call.handled {
                val seller=call.parameters["sellerId"]!!
                val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
                call.respond(masters.category(actor,seller,inventoryId(call.parameters["id"]!!),call.receiveBody()))
            } } else post(path) { call.handled {
                val seller=call.parameters["sellerId"]!!
                val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
                call.respond(HttpStatusCode.Created,masters.category(actor,seller,null,call.receiveBody()))
            } }
        }
        methodCategory()
        val materialPath="/sellers/{sellerId}/inventory/materials$suffix"
        if(edit) patch(materialPath) { call.handled {
            val seller=call.parameters["sellerId"]!!
            val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
            call.respond(masters.material(actor,seller,inventoryId(call.parameters["id"]!!),call.receiveBody()))
        } } else post(materialPath) { call.handled {
            val seller=call.parameters["sellerId"]!!
            val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
            call.respond(HttpStatusCode.Created,masters.material(actor,seller,null,call.receiveBody()))
        } }
        val supplierPath="/sellers/{sellerId}/inventory/suppliers$suffix"
        if(edit) patch(supplierPath) { call.handled {
            val seller=call.parameters["sellerId"]!!
            val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
            call.respond(masters.supplier(actor,seller,inventoryId(call.parameters["id"]!!),call.receiveBody()))
        } } else post(supplierPath) { call.handled {
            val seller=call.parameters["sellerId"]!!
            val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
            call.respond(HttpStatusCode.Created,masters.supplier(actor,seller,null,call.receiveBody()))
        } }
    }
    get("/sellers/{sellerId}/inventory/purchase-orders") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"inventory.view") ?: return@handled
        call.respond(masters.purchaseOrders(seller))
    } }
    post("/sellers/{sellerId}/inventory/purchase-orders") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
        call.respond(HttpStatusCode.Created,masters.purchaseOrder(actor,seller,call.receiveBody()))
    } }
    for(next in listOf("PLACED","CANCELLED")) post("/sellers/{sellerId}/inventory/purchase-orders/{id}/${next.lowercase()}") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
        call.respond(masters.purchaseOrderStatus(actor,seller,inventoryId(call.parameters["id"]!!),next,
            call.receiveBody<VersionWrite>().expectedVersion))
    } }
    get("/sellers/{sellerId}/inventory/receipts") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"inventory.view") ?: return@handled
        call.respond(stock.receipts(seller))
    } }
    post("/sellers/{sellerId}/inventory/receipts") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
        call.respond(HttpStatusCode.Created,stock.receive(actor,seller,call.receiveBody()))
    } }
    get("/sellers/{sellerId}/inventory/batches") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"inventory.view") ?: return@handled
        call.respond(stock.batches(seller))
    } }
    post("/sellers/{sellerId}/inventory/wastage") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
        call.respond(HttpStatusCode.Created,stock.wastage(actor,seller,call.receiveBody()))
    } }
    post("/sellers/{sellerId}/inventory/adjustments") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"inventory.adjust") ?: return@handled
        call.respond(HttpStatusCode.Created,stock.adjust(actor,seller,call.receiveBody()))
    } }
    put("/sellers/{sellerId}/inventory/reorder-rules/{materialId}") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
        call.respond(stock.reorder(actor,seller,inventoryId(call.parameters["materialId"]!!),call.receiveBody()))
    } }
    get("/sellers/{sellerId}/inventory/alerts") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"inventory.view") ?: return@handled
        call.respond(stock.alerts(actor,seller))
    } }
    get("/sellers/{sellerId}/inventory/report.csv") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"inventory.view") ?: return@handled
        call.response.header(HttpHeaders.ContentDisposition,"attachment; filename=inventory-report.csv")
        call.respondText(stock.report(seller),ContentType.parse("text/csv; charset=utf-8"))
    } }
    post("/sellers/{sellerId}/inventory/alerts/{id}/acknowledge") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
        call.respond(stock.acknowledge(actor,seller,inventoryId(call.parameters["id"]!!)))
    } }
    get("/sellers/{sellerId}/inventory/ledger") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"inventory.view") ?: return@handled
        val page=call.page()
        call.respond(stock.ledger(seller,page.limit,page.offset))
    } }
    get("/sellers/{sellerId}/inventory/recipes/{itemId}") { call.handled {
        val seller=call.parameters["sellerId"]!!
        call.requireSellerCapability(context,seller,"inventory.view") ?: return@handled
        call.respond(masters.recipe(seller,call.parameters["itemId"]!!))
    } }
    put("/sellers/{sellerId}/inventory/recipes/{itemId}") { call.handled {
        val seller=call.parameters["sellerId"]!!
        val actor=call.requireSellerCapability(context,seller,"inventory.manage") ?: return@handled
        call.respond(masters.saveRecipe(actor,seller,call.parameters["itemId"]!!,call.receiveBody()))
    } }
}
