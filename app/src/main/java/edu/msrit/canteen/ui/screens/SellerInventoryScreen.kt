package edu.msrit.canteen.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.DebugInventoryApi
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Every displayed balance and action result comes from the seller-scoped backend. */
@Composable
fun SellerInventoryScreen(sellerId: String, onBack: () -> Unit,
                          canManage: Boolean, canAdjust: Boolean) {
    val colors=RitThemeTokens.colors
    val androidContext=LocalContext.current
    val scope=rememberCoroutineScope()
    var refresh by remember(sellerId) { mutableStateOf(0) }
    var loading by remember(sellerId) { mutableStateOf(true) }
    var busy by remember(sellerId) { mutableStateOf(false) }
    var message by remember(sellerId) { mutableStateOf<String?>(null) }
    var data by remember(sellerId) { mutableStateOf<DebugInventoryApi.Snapshot?>(null) }
    var batches by remember(sellerId) { mutableStateOf<List<DebugInventoryApi.Batch>>(emptyList()) }
    var orders by remember(sellerId) { mutableStateOf<List<DebugInventoryApi.PurchaseOrder>>(emptyList()) }
    var ledger by remember(sellerId) { mutableStateOf<List<DebugInventoryApi.LedgerEntry>>(emptyList()) }
    var categoryName by remember(sellerId) { mutableStateOf("") }
    var materialName by remember(sellerId) { mutableStateOf("") }
    var supplierName by remember(sellerId) { mutableStateOf("") }
    var categoryId by remember(sellerId) { mutableStateOf<String?>(null) }
    var materialId by remember(sellerId) { mutableStateOf<String?>(null) }
    var supplierId by remember(sellerId) { mutableStateOf<String?>(null) }
    var orderId by remember(sellerId) { mutableStateOf<String?>(null) }
    var batchId by remember(sellerId) { mutableStateOf<String?>(null) }
    var baseUnit by remember(sellerId) { mutableStateOf("g") }
    var quantity by remember(sellerId) { mutableStateOf("") }
    var batchCode by remember(sellerId) { mutableStateOf("") }
    var expiresOn by remember(sellerId) { mutableStateOf("") }
    var reason by remember(sellerId) { mutableStateOf("") }
    var threshold by remember(sellerId) { mutableStateOf("") }
    var target by remember(sellerId) { mutableStateOf("") }
    var unitPrice by remember(sellerId) { mutableStateOf("") }
    var recipeItem by remember(sellerId) { mutableStateOf<String?>(null) }
    var recipeQty by remember(sellerId) { mutableStateOf("") }
    var receiptKey by remember(sellerId) { mutableStateOf(UUID.randomUUID().toString()) }
    var wasteKey by remember(sellerId) { mutableStateOf(UUID.randomUUID().toString()) }
    var adjustmentKey by remember(sellerId) { mutableStateOf(UUID.randomUUID().toString()) }

    LaunchedEffect(sellerId,refresh) {
        loading=true
        val overview=withContext(Dispatchers.IO) { DebugInventoryApi.overview(sellerId) }
        val batchResult=withContext(Dispatchers.IO) { DebugInventoryApi.loadBatches(sellerId) }
        val poResult=withContext(Dispatchers.IO) { DebugInventoryApi.purchaseOrders(sellerId) }
        val ledgerResult=withContext(Dispatchers.IO) { DebugInventoryApi.ledger(sellerId) }
        data=overview.value
        batches=batchResult.value.orEmpty()
        orders=poResult.value.orEmpty()
        ledger=ledgerResult.value.orEmpty()
        message=overview.error ?: batchResult.error ?: poResult.error ?: ledgerResult.error
        loading=false
    }

    fun perform(success: String, command: () -> DebugInventoryApi.Result<Boolean>, onSuccess: () -> Unit = {}) {
        if(busy) return
        busy=true
        message="Saving changes…"
        scope.launch {
            val result=withContext(Dispatchers.IO) { command() }
            if(result.value==true) {
                onSuccess()
                refresh++
                message=success
            } else message=result.error ?: "The server did not confirm this change."
            busy=false
        }
    }

    val selectedMaterial=data?.materials?.find { it.id==materialId }
    val selectedBatch=batches.find { it.id==batchId }
    val selectedOrder=orders.find { it.id==orderId }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("seller_inventory")) {
        CatalogHeader("Inventory",onBack)
        if(loading) RitText("Loading stock and supplies…",RitType.body,colors.muted,
            Modifier.padding(20.dp).testTag("inventory_loading"))
        else LazyColumn(Modifier.fillMaxSize(), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.padding(horizontal=20.dp)) {
                    RitText("Stock overview",RitType.heading)
                    RitText("Balances are tracked in each material's base unit.",RitType.small,colors.muted)
                    message?.let { RitText(it,RitType.small,colors.accent,Modifier.testTag("inventory_message")) }
                    if(data==null) RitSecondaryButton("Retry",{ refresh++ },Modifier.fillMaxWidth())
                    if(data!=null) RitSecondaryButton("Export inventory report",{
                        if(!busy) {
                            busy=true
                            message="Preparing report…"
                            scope.launch {
                                val result=withContext(Dispatchers.IO) { DebugInventoryApi.report(sellerId) }
                                if(result.value!=null) {
                                    runCatching {
                                        androidContext.startActivity(Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).setType("text/csv")
                                                .putExtra(Intent.EXTRA_TEXT,result.value),
                                            "Share inventory report"))
                                    }.onFailure { message="No app is available to share the report." }
                                    if(message=="Preparing report…") message="Report ready to share."
                                } else message=result.error ?: "Report unavailable."
                                busy=false
                            }
                        }
                    },Modifier.fillMaxWidth(),enabled=!busy)
                }
            }
            items(data?.materials.orEmpty(),key={ it.id }) { material ->
                Column(Modifier.padding(horizontal=20.dp,vertical=4.dp)) {
                    RitText(material.name,RitType.label)
                    RitText("${material.onHand} ${material.unit} on hand${if(material.active) "" else " · archived"}",
                        RitType.body,colors.muted)
                }
            }
            if(data?.materials.isNullOrEmpty()) item {
                RitText("No materials yet. Add a category and material to start tracking stock.",
                    RitType.body,colors.muted,Modifier.padding(horizontal=20.dp))
            }
            if(data?.alerts?.isNotEmpty()==true) item {
                Column(Modifier.padding(horizontal=20.dp)) {
                    RitText("Attention",RitType.title)
                    data!!.alerts.forEach { alert ->
                        val material=data!!.materials.find { it.id==alert.materialId }
                        RitText("${material?.name ?: "Material"}: ${alert.kind.replace('_',' ').lowercase()}"+
                            (alert.recommendation?.let { " · reorder $it ${material?.unit.orEmpty()}" } ?: ""),
                            RitType.body,colors.accent)
                        if(canManage) RitSecondaryButton("Acknowledge",{
                            perform("Alert acknowledged.",{ DebugInventoryApi.acknowledge(sellerId,alert.id) })
                        })
                    }
                }
            }
            if(canManage && data!=null) item {
                Column(Modifier.padding(horizontal=20.dp)) {
                    Spacer(Modifier.height(10.dp))
                    RitText("Materials and suppliers",RitType.title)
                    RitTextField(categoryName,{categoryName=it},"New material category","Category name")
                    RitPrimaryButton("Add category",{
                        perform("Category saved.",{ DebugInventoryApi.createCategory(sellerId,categoryName.trim()) }) {
                            categoryName="" }
                    },Modifier.fillMaxWidth(),enabled=!busy && categoryName.isNotBlank())
                    Picker("Category",data!!.categories.map { it.id to it.name },categoryId,{categoryId=it})
                    RitTextField(materialName,{materialName=it},"New material","Material name")
                    Picker("Base unit",listOf("g" to "g","kg" to "kg","ml" to "ml","l" to "l","each" to "each"),baseUnit,{baseUnit=it})
                    RitPrimaryButton("Add material",{
                        perform("Material saved.",{ DebugInventoryApi.createMaterial(sellerId,categoryId!!,materialName.trim(),baseUnit) }) {
                            materialName="" }
                    },Modifier.fillMaxWidth(),enabled=!busy && categoryId!=null && materialName.isNotBlank())
                    RitTextField(supplierName,{supplierName=it},"New supplier","Supplier name")
                    RitPrimaryButton("Add supplier",{
                        perform("Supplier saved.",{ DebugInventoryApi.createSupplier(sellerId,supplierName.trim()) }) {
                            supplierName="" }
                    },Modifier.fillMaxWidth(),enabled=!busy && supplierName.isNotBlank())
                }
            }
            if(canManage && data?.materials?.isNotEmpty()==true) item {
                Column(Modifier.padding(horizontal=20.dp)) {
                    Spacer(Modifier.height(10.dp))
                    RitText("Purchase and receive",RitType.title)
                    Picker("Material",data!!.materials.filter { it.active }.map { it.id to it.name },materialId,{materialId=it})
                    Picker("Supplier",data!!.suppliers.map { it.id to it.name },supplierId,{supplierId=it})
                    RitTextField(quantity,{quantity=it},"Quantity","Number",keyboardType=KeyboardType.Decimal)
                    RitTextField(unitPrice,{unitPrice=it},"Unit price in paise for purchase order","Optional",keyboardType=KeyboardType.Number)
                    RitPrimaryButton("Create purchase order",{
                        perform("Purchase order saved as draft.",{ DebugInventoryApi.createPurchaseOrder(sellerId,supplierId!!,
                            materialId!!,selectedMaterial?.unit ?: baseUnit,quantity,unitPrice.toLong()) })
                    },Modifier.fillMaxWidth(),enabled=!busy && materialId!=null && supplierId!=null &&
                        quantity.toBigDecimalOrNull()?.signum()==1 && unitPrice.toLongOrNull()!=null)
                    orders.forEach { order ->
                        val supplier=data!!.suppliers.find { it.id==order.supplierId }
                        RitText("${supplier?.name ?: "Supplier"} · ${order.status.replace('_',' ')}",RitType.body)
                        if(order.status=="DRAFT") RitSecondaryButton("Place purchase order",{
                            perform("Purchase order placed.",{ DebugInventoryApi.placePurchaseOrder(sellerId,order) })
                        },enabled=!busy)
                    }
                    Picker("Receive against order (optional)",orders.filter {
                        it.status in setOf("PLACED","PARTIALLY_RECEIVED") &&
                            it.lines.any { line -> line.materialId==materialId } }.map { it.id to
                        (data!!.suppliers.find { supplier -> supplier.id==it.supplierId }?.name ?: "Purchase order") },
                        orderId,{orderId=if(orderId==it) null else it})
                    RitTextField(batchCode,{batchCode=it},"Batch code","Supplier lot or delivery code")
                    RitTextField(expiresOn,{expiresOn=it},"Expiry date","yyyy-MM-dd (optional)")
                    RitPrimaryButton("Record stock receipt",{
                        perform("Receipt posted to stock ledger.",{ DebugInventoryApi.receive(sellerId,receiptKey,
                            materialId!!,selectedMaterial?.unit ?: baseUnit,quantity,batchCode.trim(),
                            expiresOn.trim().takeIf(String::isNotEmpty),supplierId,selectedOrder?.id,
                            selectedOrder?.lines?.find { it.materialId==materialId }?.lineNo) }) {
                            receiptKey=UUID.randomUUID().toString();batchCode="";expiresOn="" }
                    },Modifier.fillMaxWidth(),enabled=!busy && materialId!=null && batchCode.isNotBlank() &&
                        quantity.toBigDecimalOrNull()?.signum()==1)
                }
            }
            if((canManage || canAdjust) && batches.isNotEmpty()) item {
                Column(Modifier.padding(horizontal=20.dp)) {
                    Spacer(Modifier.height(10.dp))
                    RitText("Batches and stock changes",RitType.title)
                    Picker("Batch",batches.map { batch -> batch.id to
                        "${data?.materials?.find { it.id==batch.materialId }?.name ?: "Material"} · ${batch.code} (${batch.remaining})" },
                        batchId,{batchId=it})
                    RitTextField(reason,{reason=it},"Reason","Required for wastage or adjustment")
                    RitTextField(quantity,{quantity=it},"Base unit quantity","Use a negative number only for adjustment",
                        keyboardType=KeyboardType.Decimal)
                    if(canManage) RitPrimaryButton("Record wastage",{
                        perform("Wastage posted to stock ledger.",{ DebugInventoryApi.wastage(sellerId,wasteKey,
                            selectedBatch!!.materialId,selectedBatch.id,quantity,reason.trim()) }) {
                            wasteKey=UUID.randomUUID().toString();reason="" }
                    },Modifier.fillMaxWidth(),enabled=!busy && selectedBatch!=null && reason.isNotBlank() &&
                        quantity.toBigDecimalOrNull()?.signum()==1)
                    if(canAdjust) RitSecondaryButton("Post audited adjustment",{
                        perform("Adjustment posted to stock ledger.",{ DebugInventoryApi.adjust(sellerId,adjustmentKey,
                            selectedBatch!!.materialId,selectedBatch.id,quantity,reason.trim()) }) {
                            adjustmentKey=UUID.randomUUID().toString();reason="" }
                    },Modifier.fillMaxWidth(),enabled=!busy && selectedBatch!=null && reason.isNotBlank() &&
                        quantity.toBigDecimalOrNull()?.signum()!=0 && quantity.toBigDecimalOrNull()!=null)
                }
            }
            if(canManage && selectedMaterial!=null) item {
                Column(Modifier.padding(horizontal=20.dp)) {
                    Spacer(Modifier.height(10.dp))
                    RitText("Reorder rule",RitType.title)
                    RitTextField(threshold,{threshold=it},"Low stock threshold","Base unit quantity",keyboardType=KeyboardType.Decimal)
                    RitTextField(target,{target=it},"Restock target","Base unit quantity",keyboardType=KeyboardType.Decimal)
                    RitPrimaryButton("Save reorder rule",{
                        perform("Reorder rule saved.",{ DebugInventoryApi.reorder(sellerId,selectedMaterial.id,threshold,target) })
                    },Modifier.fillMaxWidth(),enabled=!busy && threshold.toBigDecimalOrNull()!=null &&
                        target.toBigDecimalOrNull()!=null)
                }
            }
            if(canManage && selectedMaterial!=null) item {
                Column(Modifier.padding(horizontal=20.dp)) {
                    Spacer(Modifier.height(10.dp))
                    RitText("Menu recipe",RitType.title)
                    Picker("Menu item",PreviewCatalog.items.filter { it.sellerId==sellerId }
                        .map { it.id to it.name },recipeItem,{recipeItem=it})
                    RitTextField(recipeQty,{recipeQty=it},"Material per item","Base unit quantity",keyboardType=KeyboardType.Decimal)
                    RitPrimaryButton("Save recipe ingredient",{
                        perform("Recipe saved.",{ DebugInventoryApi.saveRecipe(sellerId,recipeItem!!,
                            selectedMaterial.id,recipeQty) })
                    },Modifier.fillMaxWidth(),enabled=!busy && recipeItem!=null &&
                        recipeQty.toBigDecimalOrNull()?.signum()==1)
                }
            }
            if(ledger.isNotEmpty()) item {
                Column(Modifier.padding(horizontal=20.dp)) {
                    Spacer(Modifier.height(10.dp))
                    RitText("Recent stock movements",RitType.title)
                    ledger.forEach { entry ->
                        val material=data?.materials?.find { it.id==entry.materialId }
                        RitText("${material?.name ?: "Material"}: ${entry.delta} · ${entry.kind.replace('_',' ')}",
                            RitType.body)
                        RitText(entry.reason,RitType.small,colors.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Picker(label: String, options: List<Pair<String,String>>, selected: String?,
                   onSelect: (String) -> Unit) {
    RitText(label,RitType.label)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        options.forEach { (id,name) -> RitChip(name,selected==id,{ onSelect(id) }) }
    }
}
