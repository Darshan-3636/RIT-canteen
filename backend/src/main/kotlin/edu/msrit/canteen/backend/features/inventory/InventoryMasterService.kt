package edu.msrit.canteen.backend.features.inventory

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.features.engagement.*
import edu.msrit.canteen.backend.features.management.ManagementTransactions
import edu.msrit.canteen.backend.features.management.text
import edu.msrit.canteen.backend.features.management.version
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

internal class InventoryMasterService(private val context: BackendContext) {
    private val tx = ManagementTransactions(context)

    fun overview(actor: AuthenticatedPrincipal, seller: String): InventoryOverviewDto =
        tx.write(actor,seller,"inventory.view") { db ->
        db.refreshExpiryAlerts(seller,context.clock.instant().atZone(context.config.sellerZone).toLocalDate())
        InventoryOverviewDto(
            db.query("SELECT * FROM inventory_units ORDER BY dimension,base_factor") { r ->
                UnitDto(r.getString("code"),r.getString("name"),r.getString("dimension"),r.getBigDecimal("base_factor").toPlainString()) },
            db.query("SELECT * FROM inventory_material_categories WHERE seller_id=? ORDER BY name",seller, map=::categoryRow),
            db.query("SELECT * FROM inventory_materials WHERE seller_id=? ORDER BY name",seller, map=::materialRow),
            db.query("SELECT * FROM inventory_suppliers WHERE seller_id=? ORDER BY name",seller, map=::supplierRow),
            db.alerts(seller),
        )
    }

    fun category(actor: AuthenticatedPrincipal, seller: String, id: UUID?, input: CategoryWrite): CategoryDto {
        val name = text(input.name,80,"name")
        return tx.write(actor,seller,"inventory.manage") { db ->
            val key=id ?: UUID.randomUUID()
            if (id==null) db.update("INSERT INTO inventory_material_categories(id,seller_id,name,active) VALUES (?,?,?,?)",
                key,seller,name,input.active)
            else {
                val current=db.queryOne("SELECT version FROM inventory_material_categories WHERE id=? AND seller_id=? FOR UPDATE",key,seller) { it.getLong(1) }
                    ?: throw notFound()
                version(current,input.expectedVersion ?: throw badRequest("EXPECTED_VERSION_REQUIRED","expectedVersion is required"))
                db.update("UPDATE inventory_material_categories SET name=?,active=?,version=version+1 WHERE id=? AND seller_id=?",
                    name,input.active,key,seller)
            }
            recordAudit(db,actor.userId,"SELLER",if(id==null) "inventory.category.create" else "inventory.category.update",
                "INVENTORY_CATEGORY",key.toString(),seller)
            db.queryOne("SELECT * FROM inventory_material_categories WHERE id=? AND seller_id=?",key,seller,map=::categoryRow)!!
        }
    }

    fun material(actor: AuthenticatedPrincipal, seller: String, id: UUID?, input: MaterialWrite): MaterialDto {
        val name=text(input.name,120,"name")
        val category=inventoryId(input.categoryId)
        return tx.write(actor,seller,"inventory.manage") { db ->
            if (db.queryOne("SELECT active FROM inventory_material_categories WHERE seller_id=? AND id=?",seller,category) { it.getBoolean(1) } != true)
                throw badRequest("INVALID_CATEGORY","Choose an active category from this seller")
            if (db.queryOne("SELECT 1 FROM inventory_units WHERE code=?",input.baseUnitCode) { true } == null)
                throw badRequest("INVALID_UNIT","Choose a supported base unit")
            val key=id ?: UUID.randomUUID()
            if (id==null) db.update("""INSERT INTO inventory_materials(id,seller_id,category_id,name,base_unit_code,active)
                VALUES (?,?,?,?,?,?)""",key,seller,category,name,input.baseUnitCode,input.active)
            else {
                val old=db.queryOne("SELECT version,base_unit_code FROM inventory_materials WHERE id=? AND seller_id=? FOR UPDATE",key,seller) {
                    it.getLong(1) to it.getString(2) } ?: throw notFound()
                version(old.first,input.expectedVersion ?: throw badRequest("EXPECTED_VERSION_REQUIRED","expectedVersion is required"))
                if (old.second!=input.baseUnitCode) throw conflict("BASE_UNIT_IMMUTABLE","Create a new material for a different base unit")
                db.update("""UPDATE inventory_materials SET category_id=?,name=?,active=?,version=version+1
                    WHERE id=? AND seller_id=?""",category,name,input.active,key,seller)
            }
            recordAudit(db,actor.userId,"SELLER",if(id==null) "inventory.material.create" else "inventory.material.update",
                "INVENTORY_MATERIAL",key.toString(),seller)
            db.queryOne("SELECT * FROM inventory_materials WHERE id=? AND seller_id=?",key,seller,map=::materialRow)!!
        }
    }

    fun supplier(actor: AuthenticatedPrincipal, seller: String, id: UUID?, input: SupplierWrite): SupplierDto {
        val name=text(input.name,120,"name")
        val phone=input.phone?.trim()?.takeIf(String::isNotEmpty)
        if (phone!=null && (phone.length !in 5..30 || phone.any { it !in "+0123456789 -()" }))
            throw badRequest("INVALID_PHONE","Use a valid supplier phone number")
        return tx.write(actor,seller,"inventory.manage") { db ->
            val key=id ?: UUID.randomUUID()
            if (id==null) db.update("INSERT INTO inventory_suppliers(id,seller_id,name,phone,active) VALUES (?,?,?,?,?)",
                key,seller,name,phone,input.active)
            else {
                val current=db.queryOne("SELECT version FROM inventory_suppliers WHERE id=? AND seller_id=? FOR UPDATE",key,seller) { it.getLong(1) }
                    ?: throw notFound()
                version(current,input.expectedVersion ?: throw badRequest("EXPECTED_VERSION_REQUIRED","expectedVersion is required"))
                db.update("UPDATE inventory_suppliers SET name=?,phone=?,active=?,version=version+1 WHERE id=? AND seller_id=?",
                    name,phone,input.active,key,seller)
            }
            recordAudit(db,actor.userId,"SELLER",if(id==null) "inventory.supplier.create" else "inventory.supplier.update",
                "INVENTORY_SUPPLIER",key.toString(),seller)
            db.queryOne("SELECT * FROM inventory_suppliers WHERE id=? AND seller_id=?",key,seller,map=::supplierRow)!!
        }
    }

    fun purchaseOrder(actor: AuthenticatedPrincipal, seller: String, request: PurchaseOrderWrite): PurchaseOrderDto {
        val supplier=inventoryId(request.supplierId)
        if (request.note.length>500 || request.lines.isEmpty() || request.lines.size>200 ||
            request.lines.map { it.materialId }.toSet().size!=request.lines.size)
            throw badRequest("INVALID_PURCHASE_ORDER","Provide 1 to 200 distinct material lines and a short note")
        return tx.write(actor,seller,"inventory.manage") { db ->
            if (db.queryOne("SELECT active FROM inventory_suppliers WHERE id=? AND seller_id=?",supplier,seller) { it.getBoolean(1) } != true)
                throw badRequest("INVALID_SUPPLIER","Choose an active supplier from this seller")
            val id=UUID.randomUUID()
            db.update("INSERT INTO inventory_purchase_orders(id,seller_id,supplier_id,note,created_by) VALUES (?,?,?,?,?)",
                id,seller,supplier,request.note.trim(),actor.userId)
            request.lines.forEachIndexed { index,line ->
                val material=inventoryId(line.materialId)
                val quantity=inventoryQuantity(line.quantity)
                db.convertQuantity(seller,material,line.unitCode,quantity)
                if (line.unitPricePaise<0) throw badRequest("INVALID_PRICE","Price must be nonnegative integer paise")
                db.update("""INSERT INTO inventory_purchase_order_items
                    (seller_id,purchase_order_id,line_no,material_id,unit_code,quantity,unit_price_paise)
                    VALUES (?,?,?,?,?,?,?)""",seller,id,index+1,material,line.unitCode,quantity,line.unitPricePaise)
            }
            recordAudit(db,actor.userId,"SELLER","inventory.purchase_order.create","INVENTORY_PURCHASE_ORDER",id.toString(),seller)
            db.purchaseOrder(seller,id)
        }
    }

    fun purchaseOrders(seller: String): List<PurchaseOrderDto> = context.read { db ->
        db.query("SELECT id FROM inventory_purchase_orders WHERE seller_id=? ORDER BY created_at DESC LIMIT 100",seller) {
            it.getObject(1,UUID::class.java) }.map { db.purchaseOrder(seller,it) }
    }

    fun purchaseOrderStatus(actor: AuthenticatedPrincipal, seller: String, id: UUID,
                            next: String, expectedVersion: Long): PurchaseOrderDto = tx.write(actor,seller,"inventory.manage") { db ->
        val old=db.queryOne("SELECT status,version FROM inventory_purchase_orders WHERE id=? AND seller_id=? FOR UPDATE",id,seller) {
            it.getString(1) to it.getLong(2) } ?: throw notFound()
        version(old.second,expectedVersion)
        val allowed = when(next) {
            "PLACED" -> old.first=="DRAFT"
            "CANCELLED" -> old.first in setOf("DRAFT","PLACED") &&
                db.count("SELECT count(*) FROM inventory_stock_receipts WHERE seller_id=? AND purchase_order_id=?",seller,id)==0L
            else -> false
        }
        if(!allowed) throw conflict("INVALID_PO_TRANSITION","Purchase order cannot move from ${old.first} to $next")
        db.update("UPDATE inventory_purchase_orders SET status=?,version=version+1,updated_at=now() WHERE id=? AND seller_id=?",
            next,id,seller)
        recordAudit(db,actor.userId,"SELLER","inventory.purchase_order.status","INVENTORY_PURCHASE_ORDER",id.toString(),seller,
            mapOf("status" to next))
        db.purchaseOrder(seller,id)
    }

    fun recipe(seller: String, item: String): RecipeDto = context.read { db -> db.recipe(seller,item) }

    fun saveRecipe(actor: AuthenticatedPrincipal, seller: String, item: String, input: RecipeWrite): RecipeDto {
        if(input.ingredients.isEmpty() || input.ingredients.size>100 ||
            input.ingredients.map { it.materialId }.toSet().size!=input.ingredients.size)
            throw badRequest("INVALID_RECIPE","Recipe needs 1 to 100 distinct materials")
        return tx.write(actor,seller,"inventory.manage") { db ->
            if(db.queryOne("SELECT 1 FROM menu_items WHERE seller_id=? AND id=?",seller,item) { true }==null) throw notFound()
            val old=db.queryOne("SELECT id,version FROM inventory_recipes WHERE seller_id=? AND item_id=? FOR UPDATE",seller,item) {
                it.getObject(1,UUID::class.java) to it.getLong(2) }
            val id=old?.first ?: UUID.randomUUID()
            if(old==null) {
                if(input.expectedVersion!=null) throw badRequest("INVALID_VERSION","New recipe does not accept expectedVersion")
                db.update("INSERT INTO inventory_recipes(id,seller_id,item_id,active) VALUES (?,?,?,?)",id,seller,item,input.active)
            } else {
                version(old.second,input.expectedVersion ?: throw badRequest("EXPECTED_VERSION_REQUIRED","expectedVersion is required"))
                db.update("UPDATE inventory_recipes SET active=?,version=version+1,updated_at=now() WHERE id=?",input.active,id)
                db.update("DELETE FROM inventory_recipe_ingredients WHERE recipe_id=?",id)
            }
            input.ingredients.forEach { ingredient ->
                val material=inventoryId(ingredient.materialId)
                inventoryQuantity(ingredient.quantityBase)
                if(db.queryOne("SELECT active FROM inventory_materials WHERE seller_id=? AND id=?",seller,material) { it.getBoolean(1) }!=true)
                    throw badRequest("INVALID_MATERIAL","Recipe material must be active and seller-owned")
                db.update("INSERT INTO inventory_recipe_ingredients(seller_id,recipe_id,material_id,quantity_base) VALUES (?,?,?,?)",
                    seller,id,material,inventoryQuantity(ingredient.quantityBase))
            }
            recordAudit(db,actor.userId,"SELLER","inventory.recipe.save","INVENTORY_RECIPE",id.toString(),seller)
            db.recipe(seller,item)
        }
    }
}

internal fun categoryRow(r: ResultSet)=CategoryDto(r.getObject("id",UUID::class.java).toString(),
    r.getString("seller_id"),r.getString("name"),r.getBoolean("active"),r.getLong("version"))
internal fun materialRow(r: ResultSet)=MaterialDto(r.getObject("id",UUID::class.java).toString(),
    r.getString("seller_id"),r.getObject("category_id",UUID::class.java).toString(),r.getString("name"),
    r.getString("base_unit_code"),r.getBoolean("active"),r.getBigDecimal("on_hand_base").toPlainString(),r.getLong("version"))
internal fun supplierRow(r: ResultSet)=SupplierDto(r.getObject("id",UUID::class.java).toString(),
    r.getString("seller_id"),r.getString("name"),r.getString("phone"),r.getBoolean("active"),r.getLong("version"))

internal fun Connection.purchaseOrder(seller: String, id: UUID): PurchaseOrderDto {
    val row=queryOne("SELECT * FROM inventory_purchase_orders WHERE seller_id=? AND id=?",seller,id) {
        Triple(it.getObject("supplier_id",UUID::class.java),it.getString("status"),it.getLong("version") to it.getString("note"))
    } ?: throw notFound()
    val lines=query("SELECT * FROM inventory_purchase_order_items WHERE seller_id=? AND purchase_order_id=? ORDER BY line_no",seller,id) {
        PurchaseLineDto(it.getInt("line_no"),it.getObject("material_id",UUID::class.java).toString(),
            it.getString("unit_code"),it.getBigDecimal("quantity").toPlainString(),
            it.getBigDecimal("received_base").toPlainString(),it.getLong("unit_price_paise")) }
    return PurchaseOrderDto(id.toString(),seller,row.first.toString(),row.second,row.third.first,row.third.second,lines)
}

internal fun Connection.recipe(seller: String, item: String): RecipeDto {
    val row=queryOne("SELECT * FROM inventory_recipes WHERE seller_id=? AND item_id=?",seller,item) {
        Triple(it.getObject("id",UUID::class.java),it.getLong("version"),it.getBoolean("active"))
    } ?: throw notFound()
    return RecipeDto(row.first.toString(),item,row.second,row.third,
        query("SELECT material_id,quantity_base FROM inventory_recipe_ingredients WHERE seller_id=? AND recipe_id=? ORDER BY material_id",seller,row.first) {
            RecipeIngredientWrite(it.getObject(1,UUID::class.java).toString(),it.getBigDecimal(2).toPlainString()) })
}
