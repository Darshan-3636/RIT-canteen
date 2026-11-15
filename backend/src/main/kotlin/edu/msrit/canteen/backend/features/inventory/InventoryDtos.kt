package edu.msrit.canteen.backend.features.inventory

import kotlinx.serialization.Serializable

@Serializable internal data class UnitDto(val code: String, val name: String, val dimension: String,
                                          val baseFactor: String)
@Serializable internal data class CategoryWrite(val name: String, val active: Boolean = true,
                                                val expectedVersion: Long? = null)
@Serializable internal data class CategoryDto(val id: String, val sellerId: String, val name: String,
                                              val active: Boolean, val version: Long)
@Serializable internal data class MaterialWrite(val categoryId: String, val name: String,
                                                val baseUnitCode: String, val active: Boolean = true,
                                                val expectedVersion: Long? = null)
@Serializable internal data class MaterialDto(val id: String, val sellerId: String, val categoryId: String,
                                              val name: String, val baseUnitCode: String,
                                              val active: Boolean, val onHandBase: String, val version: Long)
@Serializable internal data class SupplierWrite(val name: String, val phone: String? = null,
                                                val active: Boolean = true, val expectedVersion: Long? = null)
@Serializable internal data class SupplierDto(val id: String, val sellerId: String, val name: String,
                                              val phone: String?, val active: Boolean, val version: Long)
@Serializable internal data class PurchaseLineWrite(val materialId: String, val unitCode: String,
                                                    val quantity: String, val unitPricePaise: Long)
@Serializable internal data class PurchaseOrderWrite(val supplierId: String, val note: String = "",
                                                    val lines: List<PurchaseLineWrite>)
@Serializable internal data class PurchaseOrderDto(val id: String, val sellerId: String,
    val supplierId: String, val status: String, val version: Long, val note: String,
    val lines: List<PurchaseLineDto>)
@Serializable internal data class PurchaseLineDto(val lineNo: Int, val materialId: String,
    val unitCode: String, val quantity: String, val receivedBase: String, val unitPricePaise: Long)
@Serializable internal data class ReceiptLineWrite(val materialId: String, val unitCode: String,
    val quantity: String, val batchCode: String, val expiresOn: String? = null,
    val purchaseOrderLineNo: Int? = null)
@Serializable internal data class ReceiptWrite(val requestId: String, val purchaseOrderId: String? = null,
    val supplierId: String? = null, val supplierReference: String? = null, val lines: List<ReceiptLineWrite>)
@Serializable internal data class ReceiptLineDto(val materialId: String, val batchId: String,
    val unitCode: String, val quantity: String, val baseQuantity: String, val batchCode: String,
    val expiresOn: String?)
@Serializable internal data class ReceiptDto(val id: String, val sellerId: String,
    val purchaseOrderId: String?, val lines: List<ReceiptLineDto>)
@Serializable internal data class BatchDto(val id: String, val materialId: String, val batchCode: String,
    val expiresOn: String?, val initialBase: String, val remainingBase: String)
@Serializable internal data class StockChangeWrite(val requestId: String, val materialId: String,
    val batchId: String, val quantityBase: String, val reason: String)
@Serializable internal data class StockAdjustmentWrite(val requestId: String, val materialId: String,
    val batchId: String, val deltaBase: String, val reason: String)
@Serializable internal data class StockChangeDto(val id: String, val materialId: String,
    val batchId: String, val deltaBase: String, val materialBalanceAfter: String)
@Serializable internal data class ReorderRuleWrite(val thresholdBase: String, val targetBase: String,
                                                  val active: Boolean = true)
@Serializable internal data class AlertDto(val id: String, val materialId: String, val batchId: String?,
    val kind: String, val status: String, val recommendationBase: String? = null)
@Serializable internal data class RecipeIngredientWrite(val materialId: String, val quantityBase: String)
@Serializable internal data class RecipeWrite(val expectedVersion: Long? = null, val active: Boolean = true,
                                             val ingredients: List<RecipeIngredientWrite>)
@Serializable internal data class RecipeDto(val id: String, val itemId: String, val version: Long,
    val active: Boolean, val ingredients: List<RecipeIngredientWrite>)
@Serializable internal data class StockLedgerDto(val id: String, val materialId: String,
    val batchId: String?, val sourceKind: String, val sourceId: String,
    val deltaBase: String, val materialBalanceAfter: String, val reason: String, val occurredAt: String)
@Serializable internal data class InventoryOverviewDto(val units: List<UnitDto>,
    val categories: List<CategoryDto>, val materials: List<MaterialDto>, val suppliers: List<SupplierDto>,
    val alerts: List<AlertDto>)
@Serializable internal data class VersionWrite(val expectedVersion: Long)
