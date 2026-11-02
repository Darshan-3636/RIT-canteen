package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import org.json.JSONArray
import org.json.JSONObject

/** Seller inventory transport. Call only from Dispatchers.IO. */
object DebugInventoryApi {
    data class Result<T>(val value: T? = null, val error: String? = null)
    data class Category(val id: String, val name: String)
    data class Material(val id: String, val categoryId: String, val name: String,
                        val unit: String, val onHand: String, val active: Boolean)
    data class Supplier(val id: String, val name: String)
    data class Batch(val id: String, val materialId: String, val code: String,
                     val remaining: String, val expiresOn: String?)
    data class Alert(val id: String, val materialId: String, val kind: String,
                     val recommendation: String?)
    data class PurchaseLine(val lineNo: Int, val materialId: String)
    data class PurchaseOrder(val id: String, val supplierId: String, val status: String,
                             val version: Long, val lines: List<PurchaseLine>)
    data class LedgerEntry(val kind: String, val materialId: String, val delta: String,
                           val reason: String, val occurredAt: String)
    data class Snapshot(val categories: List<Category>, val materials: List<Material>,
                        val suppliers: List<Supplier>, val alerts: List<Alert>)

    private fun JSONArray.objects()=(0 until length()).map(::getJSONObject)
    private fun request(method: String, path: String, body: JSONObject?=null): Result<JSONObject> {
        val token=DevelopmentDemoAccounts.token() ?: return Result(error="Sign in again.")
        return try {
            val response=DebugBackendApi.request(method,path,body,token)
            if(response.status !in 200..299) Result(error=response.error ?: "Inventory request failed (${response.status}).")
            else Result(value=response.body ?: JSONObject())
        } catch (_: Exception) { Result(error="The service did not confirm this inventory request. Refresh before retrying.") }
    }
    private fun path(sellerId: String)="/api/v1/sellers/$sellerId/inventory"
    fun overview(sellerId: String): Result<Snapshot> {
        val result=request("GET","${path(sellerId)}/overview")
        val row=result.value ?: return Result(error=result.error)
        return try { Result(value=Snapshot(
            row.getJSONArray("categories").objects().filter { it.getBoolean("active") }.map { Category(it.getString("id"),it.getString("name")) },
            row.getJSONArray("materials").objects().map { Material(it.getString("id"),it.getString("categoryId"),
                it.getString("name"),it.getString("baseUnitCode"),it.getString("onHandBase"),it.getBoolean("active")) },
            row.getJSONArray("suppliers").objects().filter { it.getBoolean("active") }.map { Supplier(it.getString("id"),it.getString("name")) },
            row.getJSONArray("alerts").objects().map { Alert(it.getString("id"),it.getString("materialId"),
                it.getString("kind"),it.optString("recommendationBase").takeUnless { value -> value.isBlank() || value=="null" }) }
        )) } catch (_: Exception) { Result(error="Inventory data could not be read.") }
    }
    fun purchaseOrders(sellerId: String): Result<List<PurchaseOrder>> {
        val token=DevelopmentDemoAccounts.token() ?: return Result(error="Sign in again.")
        return try {
            val response=DebugBackendApi.request("GET","${path(sellerId)}/purchase-orders",token=token)
            if(response.status!=200) Result(error=response.error ?: "Purchase orders unavailable.")
            else Result(value=response.array!!.objects().map { PurchaseOrder(it.getString("id"),
                it.getString("supplierId"),it.getString("status"),it.getLong("version"),
                it.getJSONArray("lines").objects().map { line ->
                    PurchaseLine(line.getInt("lineNo"),line.getString("materialId")) }) })
        } catch (_: Exception) { Result(error="Purchase orders could not be read.") }
    }
    fun loadBatches(sellerId: String): Result<List<Batch>> {
        val token=DevelopmentDemoAccounts.token() ?: return Result(error="Sign in again.")
        return try {
            val response=DebugBackendApi.request("GET","${path(sellerId)}/batches",token=token)
            if(response.status!=200) Result(error=response.error ?: "Batches unavailable.")
            else Result(value=response.array!!.objects().map { Batch(it.getString("id"),it.getString("materialId"),
                it.getString("batchCode"),it.getString("remainingBase"),
                it.optString("expiresOn").takeUnless { date -> date.isBlank() || date=="null" }) })
        } catch (_: Exception) { Result(error="Batch data could not be read.") }
    }
    fun ledger(sellerId: String): Result<List<LedgerEntry>> {
        val token=DevelopmentDemoAccounts.token() ?: return Result(error="Sign in again.")
        return try {
            val response=DebugBackendApi.request("GET","${path(sellerId)}/ledger?limit=50",token=token)
            if(response.status!=200) Result(error=response.error ?: "Stock ledger unavailable.")
            else Result(value=response.array!!.objects().map { LedgerEntry(it.getString("sourceKind"),
                it.getString("materialId"),it.getString("deltaBase"),it.getString("reason"),
                it.getString("occurredAt")) })
        } catch (_: Exception) { Result(error="Stock ledger could not be read.") }
    }
    fun report(sellerId: String): Result<String> {
        val token=DevelopmentDemoAccounts.token() ?: return Result(error="Sign in again.")
        return try {
            val response=DebugBackendApi.request("GET","${path(sellerId)}/report.csv",token=token)
            if(response.status!=200) Result(error=response.error ?: "Inventory report unavailable.")
            else Result(value=response.raw.orEmpty())
        } catch (_: Exception) { Result(error="Inventory report could not be downloaded.") }
    }
    fun createCategory(sellerId: String, name: String): Result<Boolean> =
        request("POST","${path(sellerId)}/categories",JSONObject().put("name",name)).let { Result(it.value!=null,it.error) }
    fun createMaterial(sellerId: String, categoryId: String, name: String, unit: String): Result<Boolean> =
        request("POST","${path(sellerId)}/materials",JSONObject().put("categoryId",categoryId)
            .put("name",name).put("baseUnitCode",unit)).let { Result(it.value!=null,it.error) }
    fun createSupplier(sellerId: String, name: String): Result<Boolean> =
        request("POST","${path(sellerId)}/suppliers",JSONObject().put("name",name)).let { Result(it.value!=null,it.error) }
    fun createPurchaseOrder(sellerId: String, supplierId: String, materialId: String,
                            unit: String, quantity: String, pricePaise: Long): Result<Boolean> =
        request("POST","${path(sellerId)}/purchase-orders",JSONObject().put("supplierId",supplierId)
            .put("lines",JSONArray().put(JSONObject().put("materialId",materialId).put("unitCode",unit)
                .put("quantity",quantity).put("unitPricePaise",pricePaise))))
            .let { Result(it.value!=null,it.error) }
    fun placePurchaseOrder(sellerId: String, order: PurchaseOrder): Result<Boolean> =
        request("POST","${path(sellerId)}/purchase-orders/${order.id}/placed",
            JSONObject().put("expectedVersion",order.version)).let { Result(it.value!=null,it.error) }
    fun receive(sellerId: String, requestId: String, materialId: String, unit: String,
                quantity: String, batchCode: String, expiresOn: String?,
                supplierId: String?=null, purchaseOrderId: String?=null,
                lineNo: Int?=null): Result<Boolean> {
        val line=JSONObject().put("materialId",materialId).put("unitCode",unit).put("quantity",quantity)
            .put("batchCode",batchCode).put("expiresOn",expiresOn ?: JSONObject.NULL)
            .put("purchaseOrderLineNo",lineNo ?: JSONObject.NULL)
        val body=JSONObject().put("requestId",requestId).put("purchaseOrderId",purchaseOrderId ?: JSONObject.NULL)
            .put("supplierId",supplierId ?: JSONObject.NULL).put("lines",JSONArray().put(line))
        return request("POST","${path(sellerId)}/receipts",body).let { Result(it.value!=null,it.error) }
    }
    fun wastage(sellerId: String, requestId: String, materialId: String, batchId: String,
                quantity: String, reason: String): Result<Boolean> =
        request("POST","${path(sellerId)}/wastage",JSONObject().put("requestId",requestId)
            .put("materialId",materialId).put("batchId",batchId).put("quantityBase",quantity)
            .put("reason",reason)).let { Result(it.value!=null,it.error) }
    fun adjust(sellerId: String, requestId: String, materialId: String, batchId: String,
               delta: String, reason: String): Result<Boolean> =
        request("POST","${path(sellerId)}/adjustments",JSONObject().put("requestId",requestId)
            .put("materialId",materialId).put("batchId",batchId).put("deltaBase",delta)
            .put("reason",reason)).let { Result(it.value!=null,it.error) }
    fun reorder(sellerId: String, materialId: String, threshold: String, target: String): Result<Boolean> =
        request("PUT","${path(sellerId)}/reorder-rules/$materialId",JSONObject()
            .put("thresholdBase",threshold).put("targetBase",target)).let { Result(it.value!=null,it.error) }
    fun acknowledge(sellerId: String, alertId: String): Result<Boolean> =
        request("POST","${path(sellerId)}/alerts/$alertId/acknowledge").let { Result(it.value!=null,it.error) }
    fun saveRecipe(sellerId: String, itemId: String, materialId: String, quantity: String): Result<Boolean> {
        val token=DevelopmentDemoAccounts.token() ?: return Result(error="Sign in again.")
        return try {
            val path="${path(sellerId)}/recipes/$itemId"
            val old=DebugBackendApi.request("GET",path,token=token)
            if(old.status !in setOf(200,404)) return Result(error=old.error ?: "Could not load recipe version.")
            val ingredients=JSONArray()
            if(old.status==200) old.body!!.getJSONArray("ingredients").objects()
                .filter { it.getString("materialId")!=materialId }
                .forEach { ingredients.put(JSONObject().put("materialId",it.getString("materialId"))
                    .put("quantityBase",it.getString("quantityBase"))) }
            ingredients.put(JSONObject().put("materialId",materialId).put("quantityBase",quantity))
            val body=JSONObject().put("ingredients",ingredients)
            if(old.status==200) body.put("expectedVersion",old.body!!.getLong("version"))
            val response=DebugBackendApi.request("PUT",path,body,token)
            if(response.status==200) Result(value=true)
            else Result(error=response.error ?: "Recipe save failed.")
        } catch (_: Exception) { Result(error="The service did not confirm the recipe save.") }
    }
}
