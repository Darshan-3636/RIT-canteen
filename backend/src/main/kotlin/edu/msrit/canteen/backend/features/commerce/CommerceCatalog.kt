package edu.msrit.canteen.backend.features.commerce

import java.sql.Connection

internal fun sellers(connection: Connection): List<SellerDto> = connection.prepareStatement(
    """SELECT s.id,s.name,s.description,s.active,s.is_main_canteen,
              COALESCE(p.bulk_minimum_units,8) AS bulk_minimum_units
       FROM sellers s LEFT JOIN seller_ordering_policies p ON p.seller_id=s.id
       WHERE s.active ORDER BY s.is_main_canteen DESC,s.name"""
).use { statement -> statement.executeQuery().use { rows ->
    buildList {
        while (rows.next()) {
            val id=rows.getString("id"); val main=rows.getBoolean("is_main_canteen")
            val categories=connection.prepareStatement("SELECT category_id FROM seller_categories WHERE seller_id=? ORDER BY category_id").use { cat ->
                cat.setString(1,id); cat.executeQuery().use { result -> buildList { while(result.next()) add(result.getString(1)) } }
            }
            add(SellerDto(id,rows.getString("name"),rows.getString("description"),true,main,
                bulkMinimumUnits=rows.getInt("bulk_minimum_units"),
                orderingModes=if(main) listOf("INDIVIDUAL_PREPARED_DEMO","BULK_REQUEST") else listOf("BULK_REQUEST"),
                categoryIds=categories))
        }
    }
} }

internal fun menuItems(connection: Connection, sellerId: String, query: String?, categoryId: String?): List<MenuItemDto> {
    val sql = """SELECT i.id,i.seller_id,i.name,i.description,i.price_paise,i.kind,i.dietary,i.availability,
                       i.published,i.image_key,i.category_id,s.is_main_canteen
                FROM menu_items i JOIN sellers s ON s.id=i.seller_id
                WHERE i.seller_id=? AND s.active AND i.published
                  AND (i.availability='AVAILABLE' OR i.unavailable_visibility='SHOW')
                  AND (? IS NULL OR i.category_id=?)
                  AND (? IS NULL OR i.name ILIKE ? OR i.description ILIKE ?)
                ORDER BY i.category_id,i.name"""
    return connection.prepareStatement(sql).use { statement ->
        statement.setString(1,sellerId); statement.setString(2,categoryId); statement.setString(3,categoryId)
        val term=query?.trim()?.takeIf(String::isNotEmpty)?.let { "%$it%" }
        statement.setString(4,term); statement.setString(5,term); statement.setString(6,term)
        statement.executeQuery().use { rows -> buildList {
            while(rows.next()) {
                val main=rows.getBoolean("is_main_canteen"); val kind=rows.getString("kind")
                add(MenuItemDto(rows.getString("id"),rows.getString("seller_id"),rows.getString("name"),
                    rows.getString("description"),rows.getLong("price_paise"),kind,rows.getString("dietary"),
                    rows.getString("availability"),rows.getBoolean("published"),
                    if(main && kind=="PREPARED") listOf("INDIVIDUAL_PREPARED_DEMO","BULK_REQUEST") else listOf("BULK_REQUEST"),
                    rows.getString("image_key"),rows.getString("category_id")))
            }
        } }
    }
}

/** Seller-owned item lookup includes unpublished/hidden rows so a successful edit can return its result. */
internal fun menuItem(connection: Connection, sellerId: String, itemId: String): MenuItemDto? = connection.prepareStatement(
    """SELECT i.id,i.seller_id,i.name,i.description,i.price_paise,i.kind,i.dietary,i.availability,
              i.published,i.image_key,i.category_id,s.is_main_canteen
       FROM menu_items i JOIN sellers s ON s.id=i.seller_id
       WHERE i.seller_id=? AND i.id=? AND s.active"""
).use { statement ->
    statement.setString(1,sellerId);statement.setString(2,itemId)
    statement.executeQuery().use { rows ->
        if(!rows.next()) null else {
            val main=rows.getBoolean("is_main_canteen");val kind=rows.getString("kind")
            MenuItemDto(rows.getString("id"),rows.getString("seller_id"),rows.getString("name"),
                rows.getString("description"),rows.getLong("price_paise"),kind,rows.getString("dietary"),
                rows.getString("availability"),rows.getBoolean("published"),
                if(main && kind=="PREPARED") listOf("INDIVIDUAL_PREPARED_DEMO","BULK_REQUEST") else listOf("BULK_REQUEST"),
                rows.getString("image_key"),rows.getString("category_id"))
        }
    }
}
