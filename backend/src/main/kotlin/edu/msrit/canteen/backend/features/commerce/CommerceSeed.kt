package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.transaction
import java.sql.Connection

/** Explicit local/cloud-demo catalog fixture. Seller edits and policy/hours are never overwritten. */
fun seedCommerceFixtures(context: BackendContext) {
    check(context.config.environment != RuntimeEnvironment.PRODUCTION) { "Commerce demo seed is forbidden in production" }
    if (!context.config.demoSeedEnabled && !context.config.cloudDemoSeedEnabled) return
    check(context.config.environment == RuntimeEnvironment.LOCAL ||
        (context.config.environment == RuntimeEnvironment.CLOUD_DEMO && context.config.cloudDemoSeedEnabled)) {
        "Commerce demo seed requires explicit local or cloud-demo mode"
    }

    val categories = fixtureRows("catalog-categories-v2.tsv", 2)
    val items = fixtureRows("catalog-items-v2.tsv", 12).map(::fixtureItem)
    val legacy = fixtureRows("catalog-items-v1-legacy.tsv", 12).map(::fixtureItem).associateBy { it.id }
    check(categories.size == 15 && items.size == 72 && legacy.size == 12)
    check(items.map { it.id }.toSet().size == 72)
    check(items.groupingBy { it.sellerId }.eachCount() == mapOf(
        "main-canteen" to 18, "north-counter" to 18, "coffee-corner" to 18, "campus-pantry" to 18,
    ))
    check(items.all { it.imageKey == it.id && it.categoryId in categories.map { row -> row[0] } })

    context.transaction { db ->
        db.prepareStatement("INSERT INTO menu_categories(id,name,sort_order) VALUES (?,?,?) ON CONFLICT(id) DO NOTHING").use { sql ->
            categories.forEachIndexed { index, row ->
                sql.setString(1, row[0]); sql.setString(2, row[1]); sql.setInt(3, index); sql.addBatch()
            }
            sql.executeBatch()
        }
        // These two labels changed in the approved Android fixture; preserve any non-fixture custom label.
        db.prepareStatement("UPDATE menu_categories SET name=? WHERE id=? AND name=?").use { sql ->
            listOf(Triple("rice", "Rice Dishes", "Rice"), Triple("packs", "Packaged Snacks", "Packaged"))
                .forEach { (id, current, old) ->
                    sql.setString(1, current); sql.setString(2, id); sql.setString(3, old); sql.addBatch()
                }
            sql.executeBatch()
        }
        val mappings = items.map { it.sellerId to it.categoryId }.distinct()
        db.prepareStatement("INSERT INTO seller_categories(seller_id,category_id) VALUES (?,?) ON CONFLICT DO NOTHING").use { sql ->
            mappings.forEach { (seller, category) -> sql.setString(1, seller); sql.setString(2, category); sql.addBatch() }
            sql.executeBatch()
        }
        db.prepareStatement("INSERT INTO seller_ordering_policies(seller_id) VALUES (?) ON CONFLICT(seller_id) DO NOTHING").use { sql ->
            items.map { it.sellerId }.distinct().forEach { sql.setString(1, it); sql.addBatch() }
            sql.executeBatch()
        }
        // Upgrade only pristine 12-item seed rows. Any changed field prevents an update, so seller edits survive.
        db.prepareStatement("""UPDATE menu_items SET category_id=?,name=?,description=?,price_paise=?,kind=?,dietary=?,
            availability=?,published=?,unavailable_visibility=?,image_key=?,updated_at=now()
            WHERE id=? AND seller_id=? AND category_id=? AND name=? AND description=? AND price_paise=?
              AND kind=? AND dietary=? AND availability=? AND published=? AND unavailable_visibility=? AND image_key=?""").use { sql ->
            items.forEach { current ->
                val old = legacy[current.id] ?: return@forEach
                var n = 1
                n = current.bindWithoutIdentity(sql, n)
                sql.setString(n++, current.id); sql.setString(n++, current.sellerId)
                old.bindWithoutIdentity(sql, n)
                sql.addBatch()
            }
            sql.executeBatch()
        }
        db.prepareStatement("""INSERT INTO menu_items(id,seller_id,category_id,name,description,price_paise,kind,dietary,
            availability,published,unavailable_visibility,image_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO NOTHING""").use { sql ->
            items.forEach { item ->
                sql.setString(1, item.id); sql.setString(2, item.sellerId)
                item.bindWithoutIdentity(sql, 3)
                sql.addBatch()
            }
            sql.executeBatch()
        }
        // Drop only obsolete V1 fixture mappings after their last item has moved; never detach an edited item.
        val currentMappings = mappings.toSet()
        val legacyMappings = legacy.values.map { it.sellerId to it.categoryId }.distinct()
        db.prepareStatement("""DELETE FROM seller_categories sc WHERE sc.seller_id=? AND sc.category_id=?
            AND NOT EXISTS (SELECT 1 FROM menu_items i WHERE i.seller_id=sc.seller_id AND i.category_id=sc.category_id)""").use { sql ->
            legacyMappings.filter { it !in currentMappings }.forEach { (seller, category) ->
                sql.setString(1, seller); sql.setString(2, category); sql.addBatch()
            }
            sql.executeBatch()
        }
    }
}

private data class FixtureItem(
    val id: String, val sellerId: String, val categoryId: String, val name: String, val description: String,
    val pricePaise: Long, val kind: String, val dietary: String, val availability: String,
    val published: Boolean, val unavailableVisibility: String, val imageKey: String,
) {
    fun bindWithoutIdentity(sql: java.sql.PreparedStatement, start: Int): Int {
        var n = start
        sql.setString(n++, categoryId); sql.setString(n++, name); sql.setString(n++, description)
        sql.setLong(n++, pricePaise); sql.setString(n++, kind); sql.setString(n++, dietary)
        sql.setString(n++, availability); sql.setBoolean(n++, published)
        sql.setString(n++, unavailableVisibility); sql.setString(n++, imageKey)
        return n
    }
}

private fun fixtureItem(row: List<String>) = FixtureItem(
    row[0], row[1], row[2], row[3], row[4], row[5].toLong(), row[6], row[7], row[8],
    row[9].toBooleanStrict(), row[10], row[11],
)

private fun fixtureRows(name: String, columns: Int): List<List<String>> {
    val resource = checkNotNull(CommerceSeedResource::class.java.getResourceAsStream("/demo/$name")) {
        "Missing demo catalog fixture: $name"
    }
    return resource.bufferedReader(Charsets.UTF_8).use { reader ->
        reader.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            line.split('\t').also { check(it.size == columns) { "Invalid $name fixture row" } }
        }.toList()
    }
}

private object CommerceSeedResource
