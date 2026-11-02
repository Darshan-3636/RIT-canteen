package edu.msrit.canteen.customer

/** Release has no debug inventory transport. */
object DebugInventoryApi {
    data class Result<T>(val value: T? = null, val error: String? = "Unavailable in release")
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
    fun overview(sellerId: String): Result<Snapshot> = Result()
    fun loadBatches(sellerId: String): Result<List<Batch>> = Result()
    fun purchaseOrders(sellerId: String): Result<List<PurchaseOrder>> = Result()
    fun ledger(sellerId: String): Result<List<LedgerEntry>> = Result()
    fun report(sellerId: String): Result<String> = Result()
    fun createCategory(sellerId: String, name: String): Result<Boolean> = Result()
    fun createMaterial(sellerId: String, categoryId: String, name: String, unit: String): Result<Boolean> = Result()
    fun createSupplier(sellerId: String, name: String): Result<Boolean> = Result()
    fun createPurchaseOrder(sellerId: String, supplierId: String, materialId: String,
                            unit: String, quantity: String, pricePaise: Long): Result<Boolean> = Result()
    fun placePurchaseOrder(sellerId: String, order: PurchaseOrder): Result<Boolean> = Result()
    fun receive(sellerId: String, requestId: String, materialId: String, unit: String,
                quantity: String, batchCode: String, expiresOn: String?, supplierId: String?=null,
                purchaseOrderId: String?=null, lineNo: Int?=null): Result<Boolean> = Result()
    fun wastage(sellerId: String, requestId: String, materialId: String, batchId: String,
                quantity: String, reason: String): Result<Boolean> = Result()
    fun adjust(sellerId: String, requestId: String, materialId: String, batchId: String,
               delta: String, reason: String): Result<Boolean> = Result()
    fun reorder(sellerId: String, materialId: String, threshold: String, target: String): Result<Boolean> = Result()
    fun acknowledge(sellerId: String, alertId: String): Result<Boolean> = Result()
    fun saveRecipe(sellerId: String, itemId: String, materialId: String, quantity: String): Result<Boolean> = Result()
}
