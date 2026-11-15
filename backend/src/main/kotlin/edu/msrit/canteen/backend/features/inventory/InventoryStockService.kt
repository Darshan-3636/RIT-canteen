package edu.msrit.canteen.backend.features.inventory

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.features.engagement.*
import edu.msrit.canteen.backend.features.management.ManagementTransactions
import edu.msrit.canteen.backend.features.management.text
import java.math.BigDecimal
import java.security.MessageDigest
import java.sql.Connection
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class InventoryStockService(private val context: BackendContext) {
    private val tx=ManagementTransactions(context)
    private val json=Json { encodeDefaults=true }
    private fun digest(value: String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

    fun receipts(seller: String): List<ReceiptDto> = context.read { db ->
        db.query("SELECT id FROM inventory_stock_receipts WHERE seller_id=? ORDER BY received_at DESC LIMIT 100",seller) {
            it.getObject(1,UUID::class.java) }.map { db.receipt(seller,it) }
    }

    fun batches(seller: String): List<BatchDto> = context.read { db ->
        db.query("""SELECT id,material_id,batch_code,expires_on,initial_base,remaining_base
            FROM inventory_stock_batches WHERE seller_id=? ORDER BY created_at DESC LIMIT 200""",seller) {
            BatchDto(it.getObject(1,UUID::class.java).toString(),it.getObject(2,UUID::class.java).toString(),
                it.getString(3),it.getDate(4)?.toLocalDate()?.toString(),
                it.getBigDecimal(5).toPlainString(),it.getBigDecimal(6).toPlainString()) }
    }

    fun receive(actor: AuthenticatedPrincipal, seller: String, request: ReceiptWrite): ReceiptDto {
        val id=inventoryId(request.requestId)
        val poId=request.purchaseOrderId?.let(::inventoryId)
        val supplied=request.supplierId?.let(::inventoryId)
        if(request.lines.isEmpty() || request.lines.size>100 ||
            request.lines.map { it.materialId to it.batchCode }.toSet().size!=request.lines.size)
            throw badRequest("INVALID_RECEIPT","Provide 1 to 100 distinct material batches")
        if(request.supplierReference?.length ?: 0 > 100) throw badRequest("INVALID_REFERENCE","Supplier reference is too long")
        val hash=digest(json.encodeToString(request))
        return tx.write(actor,seller,"inventory.manage") { db ->
            db.lockInventoryRequest(id)
            val previous=db.queryOne("SELECT request_hash FROM inventory_stock_receipts WHERE seller_id=? AND id=?",seller,id) { it.getBytes(1) }
            if(previous!=null) {
                if(!MessageDigest.isEqual(previous,hash)) throw conflict("IDEMPOTENCY_KEY_REUSED","Receipt ID belongs to another request")
                return@write db.receipt(seller,id)
            }
            val poSupplier=poId?.let { po ->
                db.queryOne("SELECT supplier_id,status FROM inventory_purchase_orders WHERE seller_id=? AND id=? FOR UPDATE",seller,po) {
                    it.getObject(1,UUID::class.java) to it.getString(2)
                } ?: throw notFound()
            }
            if(poSupplier!=null && poSupplier.second !in setOf("PLACED","PARTIALLY_RECEIVED"))
                throw conflict("INVALID_PO_STATUS","Purchase order is not open for receiving")
            if(supplied!=null && poSupplier!=null && supplied!=poSupplier.first)
                throw badRequest("SUPPLIER_MISMATCH","Receipt supplier differs from the purchase order")
            val supplier=supplied ?: poSupplier?.first
            if(supplier!=null && db.queryOne("SELECT active FROM inventory_suppliers WHERE seller_id=? AND id=?",seller,supplier) { it.getBoolean(1) }!=true)
                throw badRequest("INVALID_SUPPLIER","Supplier is missing or inactive")
            db.update("""INSERT INTO inventory_stock_receipts
                (id,seller_id,purchase_order_id,supplier_id,supplier_reference,created_by,request_hash)
                VALUES (?,?,?,?,?,?,?)""",id,seller,poId,supplier,request.supplierReference?.trim(),actor.userId,hash)
            request.lines.sortedWith(compareBy<ReceiptLineWrite> { it.materialId }.thenBy { it.batchCode }).forEach { line ->
                val material=inventoryId(line.materialId)
                val quantity=inventoryQuantity(line.quantity)
                val base=db.convertQuantity(seller,material,line.unitCode,quantity)
                val batchCode=text(line.batchCode,80,"batchCode")
                val expiry=line.expiresOn?.let { runCatching { LocalDate.parse(it) }.getOrNull()
                    ?: throw badRequest("INVALID_EXPIRY","expiresOn must be an ISO date") }
                if(expiry!=null && expiry.isBefore(LocalDate.now(context.clock)))
                    throw badRequest("EXPIRED_BATCH","Cannot receive a batch past its expiry date")
                if((poId==null)!=(line.purchaseOrderLineNo==null))
                    throw badRequest("INVALID_PO_LINE","A purchase order receipt must identify its line")
                if(poId!=null) {
                    val poLine=db.queryOne("""SELECT material_id,unit_code,quantity,received_base FROM inventory_purchase_order_items
                        WHERE seller_id=? AND purchase_order_id=? AND line_no=? FOR UPDATE""",
                        seller,poId,line.purchaseOrderLineNo) {
                        listOf(it.getObject(1,UUID::class.java),it.getString(2),it.getBigDecimal(3),it.getBigDecimal(4))
                    } ?: throw badRequest("INVALID_PO_LINE","Purchase order line does not exist")
                    if(poLine[0]!=material) throw badRequest("INVALID_PO_LINE","Receipt material differs from the purchase order line")
                    val ordered=db.convertQuantity(seller,material,poLine[1] as String,poLine[2] as BigDecimal)
                    if((poLine[3] as BigDecimal)+base>ordered)
                        throw conflict("OVER_RECEIPT","Received quantity exceeds the ordered quantity")
                    db.update("""UPDATE inventory_purchase_order_items SET received_base=received_base+?
                        WHERE seller_id=? AND purchase_order_id=? AND line_no=?""",base,seller,poId,line.purchaseOrderLineNo)
                }
                val batchId=UUID.randomUUID()
                val lineId=UUID.randomUUID()
                db.update("""INSERT INTO inventory_stock_batches
                    (id,seller_id,material_id,receipt_id,batch_code,expires_on,initial_base,remaining_base)
                    VALUES (?,?,?,?,?,?,?,0)""",batchId,seller,material,id,batchCode,expiry,base)
                db.update("""INSERT INTO inventory_stock_receipt_items
                    (id,seller_id,receipt_id,material_id,batch_id,unit_code,quantity,base_quantity,purchase_order_id,purchase_order_line_no)
                    VALUES (?,?,?,?,?,?,?,?,?,?)""",lineId,seller,id,material,batchId,line.unitCode,quantity,base,poId,line.purchaseOrderLineNo)
                db.moveStock(seller,material,batchId,base,"RECEIPT",lineId,"Stock receipt",actor.userId)
                db.syncExpiryAlert(seller,material,batchId,expiry,context.clock.instant().atZone(context.config.sellerZone).toLocalDate())
            }
            if(poId!=null) {
                val lines=db.query("SELECT material_id,unit_code,quantity,received_base FROM inventory_purchase_order_items WHERE seller_id=? AND purchase_order_id=?",seller,poId) {
                    listOf(it.getObject(1,UUID::class.java),it.getString(2),it.getBigDecimal(3),it.getBigDecimal(4)) }
                val complete=lines.all { row ->
                    val ordered=db.convertQuantity(seller,row[0] as UUID,row[1] as String,row[2] as BigDecimal)
                    (row[3] as BigDecimal)>=ordered
                }
                db.update("UPDATE inventory_purchase_orders SET status=?,version=version+1,updated_at=now() WHERE seller_id=? AND id=?",
                    if(complete) "RECEIVED" else "PARTIALLY_RECEIVED",seller,poId)
            }
            recordAudit(db,actor.userId,"SELLER","inventory.receipt.post","INVENTORY_RECEIPT",id.toString(),seller)
            db.receipt(seller,id)
        }
    }

    fun wastage(actor: AuthenticatedPrincipal, seller: String, input: StockChangeWrite): StockChangeDto {
        val id=inventoryId(input.requestId);val material=inventoryId(input.materialId);val batch=inventoryId(input.batchId)
        val quantity=inventoryQuantity(input.quantityBase)
        val reason=text(input.reason,300,"reason")
        val hash=digest(json.encodeToString(input))
        return tx.write(actor,seller,"inventory.manage") { db ->
            db.lockInventoryRequest(id)
            val prior=db.queryOne("SELECT request_hash FROM inventory_wastage WHERE seller_id=? AND id=?",seller,id) { it.getBytes(1) }
            if(prior!=null) {
                if(!MessageDigest.isEqual(prior,hash)) throw conflict("IDEMPOTENCY_KEY_REUSED","Wastage ID belongs to another request")
                return@write db.stockChange(seller,"WASTAGE",id)
            }
            db.update("""INSERT INTO inventory_wastage
                (id,seller_id,material_id,batch_id,quantity_base,reason,actor_user_id,request_hash)
                VALUES (?,?,?,?,?,?,?,?)""",id,seller,material,batch,quantity,reason,actor.userId,hash)
            val result=db.moveStock(seller,material,batch,quantity.negate(),"WASTAGE",id,reason,actor.userId)
            recordAudit(db,actor.userId,"SELLER","inventory.wastage.record","INVENTORY_WASTAGE",id.toString(),seller)
            result
        }
    }

    fun adjust(actor: AuthenticatedPrincipal, seller: String, input: StockAdjustmentWrite): StockChangeDto {
        val id=inventoryId(input.requestId);val material=inventoryId(input.materialId);val batch=inventoryId(input.batchId)
        val delta=inventoryQuantity(input.deltaBase,false)
        val reason=text(input.reason,300,"reason")
        val hash=digest(json.encodeToString(input))
        return tx.write(actor,seller,"inventory.adjust") { db ->
            db.lockInventoryRequest(id)
            val prior=db.queryOne("SELECT request_hash FROM inventory_stock_adjustments WHERE seller_id=? AND id=?",seller,id) { it.getBytes(1) }
            if(prior!=null) {
                if(!MessageDigest.isEqual(prior,hash)) throw conflict("IDEMPOTENCY_KEY_REUSED","Adjustment ID belongs to another request")
                return@write db.stockChange(seller,"ADJUSTMENT",id)
            }
            db.update("""INSERT INTO inventory_stock_adjustments
                (id,seller_id,material_id,batch_id,delta_base,reason,actor_user_id,request_hash)
                VALUES (?,?,?,?,?,?,?,?)""",id,seller,material,batch,delta,reason,actor.userId,hash)
            val result=db.moveStock(seller,material,batch,delta,"ADJUSTMENT",id,reason,actor.userId)
            recordAudit(db,actor.userId,"SELLER","inventory.adjustment.record","INVENTORY_ADJUSTMENT",id.toString(),seller)
            result
        }
    }

    fun reorder(actor: AuthenticatedPrincipal, seller: String, material: UUID, input: ReorderRuleWrite): List<AlertDto> {
        val threshold=(if(input.thresholdBase.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO)==0)
            BigDecimal.ZERO.setScale(6) else inventoryQuantity(input.thresholdBase,false)).takeIf { it>=BigDecimal.ZERO }
            ?: throw badRequest("INVALID_THRESHOLD","Threshold must be nonnegative")
        val target=inventoryQuantity(input.targetBase)
        if(target<=threshold) throw badRequest("INVALID_TARGET","Target must exceed the threshold")
        return tx.write(actor,seller,"inventory.manage") { db ->
            if(db.queryOne("SELECT 1 FROM inventory_materials WHERE seller_id=? AND id=?",seller,material) { true }==null) throw notFound()
            db.update("""INSERT INTO inventory_reorder_rules(seller_id,material_id,threshold_base,target_base,active)
                VALUES (?,?,?,?,?) ON CONFLICT(seller_id,material_id) DO UPDATE SET
                threshold_base=EXCLUDED.threshold_base,target_base=EXCLUDED.target_base,active=EXCLUDED.active""",
                seller,material,threshold,target,input.active)
            db.refreshLowStockAlert(seller,material)
            recordAudit(db,actor.userId,"SELLER","inventory.reorder_rule.save","INVENTORY_MATERIAL",material.toString(),seller)
            db.alerts(seller)
        }
    }

    fun alerts(actor: AuthenticatedPrincipal, seller: String): List<AlertDto> =
        tx.write(actor,seller,"inventory.view") { db ->
            db.refreshExpiryAlerts(seller,context.clock.instant().atZone(context.config.sellerZone).toLocalDate())
            db.alerts(seller)
        }
    fun report(seller: String): String = context.read { db ->
        val rows=db.query("""SELECT m.name,c.name,m.base_unit_code,m.on_hand_base,
            r.threshold_base,r.target_base,
            (SELECT count(*) FROM inventory_stock_batches b WHERE b.seller_id=m.seller_id
                AND b.material_id=m.id AND b.remaining_base>0 AND b.expires_on<=?)
            FROM inventory_materials m JOIN inventory_material_categories c
                ON c.seller_id=m.seller_id AND c.id=m.category_id
            LEFT JOIN inventory_reorder_rules r ON r.seller_id=m.seller_id AND r.material_id=m.id
            WHERE m.seller_id=? ORDER BY c.name,m.name""",
            context.clock.instant().atZone(context.config.sellerZone).toLocalDate().plusDays(7),seller) { row ->
            listOf(row.getString(1),row.getString(2),row.getString(3),
                row.getBigDecimal(4).toPlainString(),row.getBigDecimal(5)?.toPlainString().orEmpty(),
                row.getBigDecimal(6)?.toPlainString().orEmpty(),row.getLong(7).toString())
        }
        buildString {
            appendLine("Material,Category,Base unit,On hand,Reorder threshold,Reorder target,Expiring batches")
            rows.forEach { row -> appendLine(row.joinToString(",",transform=::inventoryCsvCell)) }
        }
    }
    fun acknowledge(actor: AuthenticatedPrincipal, seller: String, alertId: UUID): AlertDto =
        tx.write(actor,seller,"inventory.manage") { db ->
            val row=db.queryOne("SELECT status FROM inventory_alerts WHERE seller_id=? AND id=? FOR UPDATE",seller,alertId) {
                it.getString(1) } ?: throw notFound()
            if(row=="CLOSED") throw conflict("ALERT_CLOSED","This alert is no longer active")
            db.update("UPDATE inventory_alerts SET status='ACKNOWLEDGED',updated_at=now() WHERE seller_id=? AND id=?",seller,alertId)
            recordAudit(db,actor.userId,"SELLER","inventory.alert.acknowledge","INVENTORY_ALERT",alertId.toString(),seller)
            db.queryOne("SELECT material_id,batch_id,kind,status FROM inventory_alerts WHERE id=?",alertId) {
                AlertDto(alertId.toString(),it.getObject(1,UUID::class.java).toString(),
                    it.getObject(2,UUID::class.java)?.toString(),it.getString(3),it.getString(4)) }!!
        }
    fun ledger(seller: String, limit: Int, offset: Int): List<StockLedgerDto> = context.read { db ->
        db.query("""SELECT * FROM inventory_stock_transactions WHERE seller_id=?
            ORDER BY occurred_at DESC,id LIMIT ? OFFSET ?""",seller,limit,offset) {
            StockLedgerDto(it.getObject("id",UUID::class.java).toString(),it.getObject("material_id",UUID::class.java).toString(),
                it.getObject("batch_id",UUID::class.java)?.toString(),it.getString("source_kind"),
                it.getObject("source_id",UUID::class.java).toString(),it.getBigDecimal("delta_base").toPlainString(),
                it.getBigDecimal("material_balance_after").toPlainString(),it.getString("reason"),
                it.getTimestamp("occurred_at").toInstant().toString()) }
    }
}

internal fun Connection.receipt(seller: String, id: UUID): ReceiptDto {
    val po=queryOne("SELECT purchase_order_id FROM inventory_stock_receipts WHERE seller_id=? AND id=?",seller,id) {
        it.getObject(1,UUID::class.java) } ?: run {
            if(count("SELECT count(*) FROM inventory_stock_receipts WHERE seller_id=? AND id=?",seller,id)==0L) throw notFound()
            null
        }
    val lines=query("""SELECT i.material_id,i.batch_id,i.unit_code,i.quantity,i.base_quantity,b.batch_code,b.expires_on
        FROM inventory_stock_receipt_items i JOIN inventory_stock_batches b ON b.id=i.batch_id
        WHERE i.seller_id=? AND i.receipt_id=? ORDER BY i.material_id,b.batch_code""",seller,id) {
        ReceiptLineDto(it.getObject(1,UUID::class.java).toString(),it.getObject(2,UUID::class.java).toString(),
            it.getString(3),it.getBigDecimal(4).toPlainString(),it.getBigDecimal(5).toPlainString(),
            it.getString(6),it.getDate(7)?.toLocalDate()?.toString()) }
    return ReceiptDto(id.toString(),seller,po?.toString(),lines)
}

internal fun Connection.stockChange(seller: String, kind: String, source: UUID): StockChangeDto = queryOne(
    """SELECT id,material_id,batch_id,delta_base,material_balance_after FROM inventory_stock_transactions
        WHERE seller_id=? AND source_kind=? AND source_id=?""",seller,kind,source) {
        StockChangeDto(it.getObject(1,UUID::class.java).toString(),it.getObject(2,UUID::class.java).toString(),
            it.getObject(3,UUID::class.java).toString(),it.getBigDecimal(4).toPlainString(),it.getBigDecimal(5).toPlainString())
    } ?: throw notFound()

internal fun Connection.moveStock(seller: String, material: UUID, batch: UUID, delta: BigDecimal,
                                  kind: String, source: UUID, reason: String, actor: UUID): StockChangeDto {
    val materialBefore=queryOne("SELECT on_hand_base FROM inventory_materials WHERE seller_id=? AND id=? FOR UPDATE",seller,material) {
        it.getBigDecimal(1) } ?: throw notFound()
    val batchBefore=queryOne("SELECT remaining_base FROM inventory_stock_batches WHERE seller_id=? AND material_id=? AND id=? FOR UPDATE",
        seller,material,batch) { it.getBigDecimal(1) } ?: throw notFound()
    val materialAfter=materialBefore+delta
    val batchAfter=batchBefore+delta
    if(materialAfter<BigDecimal.ZERO || batchAfter<BigDecimal.ZERO)
        throw conflict("INSUFFICIENT_STOCK","Stock cannot become negative")
    if(materialAfter.precision()>20 || batchAfter.precision()>20)
        throw badRequest("STOCK_TOO_LARGE","Stock amount exceeds the supported range")
    update("UPDATE inventory_stock_batches SET remaining_base=? WHERE seller_id=? AND material_id=? AND id=?",
        batchAfter,seller,material,batch)
    update("UPDATE inventory_materials SET on_hand_base=?,version=version+1 WHERE seller_id=? AND id=?",
        materialAfter,seller,material)
    val id=UUID.randomUUID()
    update("""INSERT INTO inventory_stock_transactions
        (id,seller_id,material_id,batch_id,source_kind,source_id,delta_base,material_balance_after,reason,actor_user_id)
        VALUES (?,?,?,?,?,?,?,?,?,?)""",id,seller,material,batch,kind,source,delta,materialAfter,reason,actor)
    refreshLowStockAlert(seller,material)
    return StockChangeDto(id.toString(),material.toString(),batch.toString(),delta.toPlainString(),materialAfter.toPlainString())
}

internal fun Connection.refreshLowStockAlert(seller: String, material: UUID) {
    val row=queryOne("""SELECT r.threshold_base,r.active,m.on_hand_base FROM inventory_reorder_rules r
        JOIN inventory_materials m ON m.seller_id=r.seller_id AND m.id=r.material_id
        WHERE r.seller_id=? AND r.material_id=?""",seller,material) {
        Triple(it.getBigDecimal(1),it.getBoolean(2),it.getBigDecimal(3)) }
    val low=row!=null && row.second && row.third<=row.first
    val prior=queryOne("""SELECT id,status FROM inventory_alerts WHERE seller_id=? AND material_id=?
        AND kind='LOW_STOCK' AND batch_id IS NULL FOR UPDATE""",seller,material) {
        it.getObject(1,UUID::class.java) to it.getString(2) }
    if(low && prior==null) update("""INSERT INTO inventory_alerts(id,seller_id,material_id,kind)
        VALUES (?,?,?,'LOW_STOCK')""",UUID.randomUUID(),seller,material)
    else if(low && prior?.second=="CLOSED") update("UPDATE inventory_alerts SET status='OPEN',updated_at=now() WHERE id=?",prior.first)
    else if(!low && prior!=null && prior.second!="CLOSED") update("UPDATE inventory_alerts SET status='CLOSED',updated_at=now() WHERE id=?",prior.first)
}

internal fun Connection.syncExpiryAlert(seller: String, material: UUID, batch: UUID,
                                        expiry: LocalDate?, today: LocalDate) {
    if(expiry==null || expiry.isAfter(today.plusDays(7))) return
    update("""INSERT INTO inventory_alerts(id,seller_id,material_id,batch_id,kind)
        VALUES (?,?,?,?,'EXPIRY') ON CONFLICT DO NOTHING""",UUID.randomUUID(),seller,material,batch)
}

internal fun Connection.refreshExpiryAlerts(seller: String, today: LocalDate) {
    update("""INSERT INTO inventory_alerts(id,seller_id,material_id,batch_id,kind)
        SELECT gen_random_uuid(),b.seller_id,b.material_id,b.id,'EXPIRY'
        FROM inventory_stock_batches b WHERE b.seller_id=? AND b.remaining_base>0
            AND b.expires_on IS NOT NULL AND b.expires_on<=?
        ON CONFLICT DO NOTHING""",seller,today.plusDays(7))
    update("""UPDATE inventory_alerts a SET status='CLOSED',updated_at=now()
        FROM inventory_stock_batches b WHERE a.seller_id=? AND a.seller_id=b.seller_id
            AND a.batch_id=b.id AND a.kind='EXPIRY' AND a.status<>'CLOSED'
            AND b.remaining_base=0""",seller)
}

private fun inventoryCsvCell(value: String): String {
    val safe=if(value.firstOrNull() in listOf('=','+','-','@')) "'"+value else value
    return "\""+safe.replace("\"","\"\"")+"\""
}

internal fun Connection.alerts(seller: String): List<AlertDto> = query("""SELECT a.id,a.material_id,a.batch_id,a.kind,a.status,
    CASE WHEN a.kind='LOW_STOCK' AND r.active THEN GREATEST(r.target_base-m.on_hand_base,0) ELSE NULL END
    FROM inventory_alerts a JOIN inventory_materials m ON m.seller_id=a.seller_id AND m.id=a.material_id
    LEFT JOIN inventory_reorder_rules r ON r.seller_id=a.seller_id AND r.material_id=a.material_id
    WHERE a.seller_id=? AND a.status<>'CLOSED' ORDER BY a.created_at DESC LIMIT 100""",seller) {
    AlertDto(it.getObject(1,UUID::class.java).toString(),it.getObject(2,UUID::class.java).toString(),
        it.getObject(3,UUID::class.java)?.toString(),it.getString(4),it.getString(5),it.getBigDecimal(6)?.toPlainString()) }
