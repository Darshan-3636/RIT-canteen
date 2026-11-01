package edu.msrit.canteen.backend.features.management

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.features.commerce.CatalogItemPatch
import edu.msrit.canteen.backend.features.engagement.*
import java.sql.Connection
import java.sql.ResultSet

internal class CatalogService(private val context: BackendContext) {
    private val tx = ManagementTransactions(context)

    fun items(seller: String, page: Page): PageDto<ManagedItem> = context.read { db ->
        PageDto(db.query("SELECT * FROM menu_items WHERE seller_id=? ORDER BY name,id LIMIT ? OFFSET ?",
            seller, page.limit, page.offset, map = ::itemRow), page.limit, page.offset,
            db.count("SELECT count(*) FROM menu_items WHERE seller_id=?", seller))
    }
    fun item(seller: String, id: String): ManagedItem = context.read { it.item(seller, id) }

    fun create(actor: AuthenticatedPrincipal, seller: String, request: ItemCreate): ManagedItem {
        if (!Regex("[a-z0-9][a-z0-9-]{0,95}").matches(request.id)) throw badRequest("INVALID_ITEM_ID", "Invalid item ID")
        val name = text(request.name, 120, "name")
        validate(request.description, request.pricePaise, request.dietary, request.unavailableVisibility)
        if (request.kind !in setOf("PREPARED", "PACKAGED") || request.availability !in setOf("AVAILABLE", "UNAVAILABLE"))
            throw badRequest("INVALID_ITEM", "Unknown item kind or availability")
        val image = text(request.imageKey, 120, "imageKey")
        return tx.write(actor, seller, "catalog.edit") { db ->
            db.category(seller, request.categoryId)
            db.update("""INSERT INTO menu_items(id,seller_id,category_id,name,description,price_paise,kind,dietary,
                availability,published,unavailable_visibility,image_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
                request.id, seller, request.categoryId, name, request.description, request.pricePaise, request.kind,
                request.dietary, request.availability, request.published, request.unavailableVisibility, image)
            audit(db, actor, false, "seller.catalog.create", "MENU_ITEM", request.id, seller)
            db.item(seller, request.id)
        }
    }

    fun update(actor: AuthenticatedPrincipal, seller: String, id: String, request: CatalogItemPatch): ManagedItem {
        if (listOf(request.name,request.description,request.pricePaise,request.published,request.dietary,
                request.unavailableVisibility,request.categoryId,request.kind,request.imageKey).all { it == null })
            throw badRequest("EMPTY_PATCH", "Provide at least one catalog field")
        val name = request.name?.let { text(it, 120, "name") }
        val image = request.imageKey?.let { text(it, 120, "imageKey") }
        validate(request.description, request.pricePaise, request.dietary, request.unavailableVisibility)
        if (request.kind != null && request.kind !in setOf("PREPARED", "PACKAGED")) throw badRequest("INVALID_KIND", "Unknown kind")
        return tx.write(actor, seller, "catalog.edit") { db ->
            val old = db.item(seller, id, true)
            version(old.version, request.expectedVersion)
            if (old.archivedAt != null) throw conflict("ITEM_ARCHIVED", "Restore the item before editing")
            request.categoryId?.let { db.category(seller, it) }
            if (request.kind != null && request.kind != old.kind &&
                (db.count("SELECT count(*) FROM individual_demo_order_lines WHERE seller_id=? AND item_id=?", seller,id) > 0 ||
                 db.count("SELECT count(*) FROM bulk_quote_lines WHERE seller_id=? AND item_id=?", seller,id) > 0))
                throw conflict("ITEM_KIND_IN_USE", "Historical items cannot change kind")
            db.update("""UPDATE menu_items SET name=COALESCE(?,name),description=COALESCE(?,description),
                price_paise=COALESCE(?,price_paise),published=COALESCE(?,published),dietary=COALESCE(?,dietary),
                unavailable_visibility=COALESCE(?,unavailable_visibility),category_id=COALESCE(?,category_id),
                kind=COALESCE(?,kind),image_key=COALESCE(?,image_key),version=version+1,updated_at=now()
                WHERE seller_id=? AND id=?""", name,request.description,request.pricePaise,request.published,
                request.dietary,request.unavailableVisibility,request.categoryId,request.kind,image,seller,id)
            audit(db,actor,false,"seller.catalog.update","MENU_ITEM",id,seller)
            db.item(seller,id)
        }
    }

    fun availability(actor: AuthenticatedPrincipal, seller: String, id: String, value: String, expected: Long?): ManagedItem {
        if (value !in setOf("AVAILABLE", "UNAVAILABLE")) throw badRequest("INVALID_AVAILABILITY", "Unknown availability")
        return tx.write(actor,seller,"availability.edit") { db ->
            val old=db.item(seller,id,true); version(old.version,expected)
            if (old.archivedAt != null) throw conflict("ITEM_ARCHIVED", "Restore the item before editing")
            db.update("UPDATE menu_items SET availability=?,version=version+1,updated_at=now() WHERE seller_id=? AND id=?",value,seller,id)
            audit(db,actor,false,"seller.catalog.availability","MENU_ITEM",id,seller)
            db.item(seller,id)
        }
    }

    fun archive(actor: AuthenticatedPrincipal, seller: String, id: String, expected: Long, archived: Boolean): ManagedItem =
        tx.write(actor,seller,"catalog.edit") { db ->
            val old=db.item(seller,id,true); version(old.version,expected)
            db.update("""UPDATE menu_items SET archived_at=CASE WHEN ? THEN now() ELSE NULL END,published=false,
                version=version+1,updated_at=now() WHERE seller_id=? AND id=?""",archived,seller,id)
            audit(db,actor,false,if(archived) "seller.catalog.archive" else "seller.catalog.restore","MENU_ITEM",id,seller)
            db.item(seller,id)
        }

    fun categories(seller: String): List<CategoryDto> = context.read { db ->
        db.query("""SELECT c.id,c.name,c.active AS globally_active,s.display_name,s.sort_order,s.active,s.version
            FROM seller_categories s JOIN menu_categories c ON c.id=s.category_id WHERE s.seller_id=?
            ORDER BY s.sort_order,c.sort_order,c.id""",seller) {
            CategoryDto(it.getString("id"),it.getString("name"),it.getString("display_name"),it.getInt("sort_order"),
                it.getBoolean("active"),it.getBoolean("globally_active"),it.getLong("version"))
        }
    }

    fun category(actor: AuthenticatedPrincipal,seller: String,id: String,request: CategoryWrite) {
        val label=request.displayName?.let { text(it,80,"displayName") }
        tx.write(actor,seller,"catalog.edit") { db ->
            db.queryOne("SELECT id FROM sellers WHERE id=? FOR UPDATE",seller) { it.getString(1) } ?: throw notFound()
            db.queryOne("SELECT id FROM menu_categories WHERE id=? FOR SHARE",id) { it.getString(1) } ?: throw notFound("Global category not found")
            val old=db.queryOne("SELECT version FROM seller_categories WHERE seller_id=? AND category_id=? FOR UPDATE",seller,id) { it.getLong(1) }
            version(old ?: 0,request.expectedVersion)
            db.update("""INSERT INTO seller_categories(seller_id,category_id,display_name,sort_order,active,version)
                VALUES (?,?,?,?,?,1) ON CONFLICT(seller_id,category_id) DO UPDATE SET display_name=EXCLUDED.display_name,
                sort_order=EXCLUDED.sort_order,active=EXCLUDED.active,version=seller_categories.version+1""",seller,id,label,request.sortOrder,request.active)
            audit(db,actor,false,"seller.category.update","CATEGORY",id,seller)
        }
    }

    fun globalCategory(actor: AuthenticatedPrincipal, request: GlobalCategoryWrite, create: Boolean) {
        if (!Regex("[a-z0-9][a-z0-9-]{0,63}").matches(request.id)) throw badRequest("INVALID_CATEGORY_ID","Invalid category ID")
        val name=text(request.name,80,"name")
        tx.write(actor,admin=true,lifecycle=true) { db ->
            if (create) db.update("INSERT INTO menu_categories(id,name,sort_order,active) VALUES (?,?,?,?)",request.id,name,request.sortOrder,request.active)
            else if(db.update("UPDATE menu_categories SET name=?,sort_order=?,active=? WHERE id=?",name,request.sortOrder,request.active,request.id)!=1) throw notFound()
            audit(db,actor,true,"admin.category.update","CATEGORY",request.id)
        }
    }

    fun profile(actor: AuthenticatedPrincipal,seller: String,request: ProfileWrite): SellerState {
        val name=text(request.name,200,"name")
        if(request.description.length>2000) throw badRequest("INVALID_DESCRIPTION","Description is too long")
        return tx.write(actor,seller,"profile.edit") { db ->
            db.queryOne("SELECT id FROM sellers WHERE id=? FOR UPDATE",seller) { true } ?: throw notFound()
            version(db.sellerState(seller).version,request.expectedVersion)
            db.update("UPDATE sellers SET name=?,description=?,version=version+1,updated_at=now() WHERE id=?",name,request.description,seller)
            audit(db,actor,false,"seller.profile.update","SELLER",seller,seller)
            db.sellerState(seller)
        }
    }

    private fun validate(description: String?, price: Long?, dietary: String?, visibility: String?) {
        if(description != null && description.length>1200) throw badRequest("INVALID_DESCRIPTION","Description is too long")
        if(price != null && price<0) throw badRequest("INVALID_PRICE","pricePaise cannot be negative")
        if(dietary != null && dietary !in setOf("VEGETARIAN","NON_VEGETARIAN","NOT_SPECIFIED")) throw badRequest("INVALID_DIETARY","Unknown dietary value")
        if(visibility != null && visibility !in setOf("SHOW","HIDE")) throw badRequest("INVALID_VISIBILITY","Unknown visibility")
    }
    private fun Connection.category(seller: String,id: String) {
        queryOne("""SELECT c.id FROM seller_categories s JOIN menu_categories c ON c.id=s.category_id
            WHERE s.seller_id=? AND s.category_id=? AND s.active AND c.active FOR SHARE OF s,c""",seller,id) { true }
            ?: throw badRequest("INVALID_CATEGORY","Choose an active category assigned to this seller")
    }
    private fun Connection.item(seller: String,id: String,lock: Boolean=false): ManagedItem =
        queryOne("SELECT * FROM menu_items WHERE seller_id=? AND id=?" + if(lock) " FOR UPDATE" else "",seller,id,map=::itemRow) ?: throw notFound()
    private fun itemRow(r: ResultSet)=ManagedItem(r.getString("id"),r.getString("seller_id"),r.getString("category_id"),
        r.getString("name"),r.getString("description"),r.getLong("price_paise"),r.getString("kind"),r.getString("dietary"),
        r.getString("availability"),r.getBoolean("published"),r.getString("unavailable_visibility"),r.getString("image_key"),
        r.instant("archived_at"),r.getLong("version"))
}
