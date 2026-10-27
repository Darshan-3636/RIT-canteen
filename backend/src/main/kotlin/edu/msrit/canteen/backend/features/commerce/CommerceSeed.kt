package edu.msrit.canteen.backend.features.commerce

import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.RuntimeEnvironment
import edu.msrit.canteen.backend.core.transaction

/** Idempotent demo catalog fixture; call only after BackendBootstrap.open() has seeded sellers. */
fun seedCommerceFixtures(context: BackendContext) {
    check(context.config.environment != RuntimeEnvironment.PRODUCTION) { "Commerce demo seed is forbidden in production" }
    if (!context.config.demoSeedEnabled) return
    context.transaction { connection ->
        connection.prepareStatement("INSERT INTO menu_categories(id,name,sort_order) VALUES (?,?,?) ON CONFLICT(id) DO NOTHING").use { statement ->
            listOf("dosa" to "Dosa", "rice" to "Rice", "snacks" to "Snacks", "drinks" to "Beverages", "packs" to "Packaged")
                .forEachIndexed { index, pair -> statement.setString(1, pair.first); statement.setString(2, pair.second); statement.setInt(3, index); statement.addBatch() }
            statement.executeBatch()
        }
        connection.prepareStatement("INSERT INTO seller_categories(seller_id,category_id) VALUES (?,?) ON CONFLICT DO NOTHING").use { statement ->
            val mappings = mapOf(
                "main-canteen" to listOf("dosa", "rice", "snacks", "drinks", "packs"),
                "north-counter" to listOf("rice", "snacks"),
                "coffee-corner" to listOf("drinks", "snacks", "packs"),
                "campus-pantry" to listOf("packs", "drinks"),
            )
            mappings.forEach { (seller, categories) -> categories.forEach { category -> statement.setString(1, seller); statement.setString(2, category); statement.addBatch() } }
            statement.executeBatch()
        }
        connection.prepareStatement("INSERT INTO seller_ordering_policies(seller_id) VALUES (?) ON CONFLICT(seller_id) DO NOTHING").use { statement ->
            listOf("main-canteen", "north-counter", "coffee-corner", "campus-pantry").forEach { statement.setString(1, it); statement.addBatch() }
            statement.executeBatch()
        }
        val items = listOf(
            FixtureItem("masala-dosa","main-canteen","dosa","Masala Dosa","Crisp dosa with spiced potato filling, sambar and chutney.",5000,"PREPARED","VEGETARIAN"),
            FixtureItem("veg-biryani","main-canteen","rice","Veg Biryani","Fragrant rice with vegetables and warm spices.",8500,"PREPARED","VEGETARIAN"),
            FixtureItem("filter-coffee","main-canteen","drinks","Filter Coffee","South Indian-style coffee served hot.",3000,"PREPARED","VEGETARIAN"),
            FixtureItem("idli","main-canteen","snacks","Idli","Steamed rice cakes served with sambar and chutney.",3500,"PREPARED","VEGETARIAN"),
            FixtureItem("lemon-rice","main-canteen","rice","Lemon Rice","Rice tempered with lemon, curry leaves and peanuts.",4500,"PREPARED","VEGETARIAN","UNAVAILABLE"),
            FixtureItem("water-bottle","main-canteen","packs","Bottled Water","Sealed drinking water for group requests.",2000,"PACKAGED","VEGETARIAN"),
            FixtureItem("north-lunch","north-counter","rice","North Counter Lunch","Illustrative prepared lunch plate.",9000,"PREPARED","NOT_SPECIFIED"),
            FixtureItem("north-snack","north-counter","snacks","Savoury Snack Box","Assorted prepared savoury bites for a group.",6500,"PREPARED","VEGETARIAN"),
            FixtureItem("corner-coffee","coffee-corner","drinks","Group Coffee","Freshly prepared coffee for a group request.",3500,"PREPARED","VEGETARIAN"),
            FixtureItem("corner-biscuits","coffee-corner","packs","Biscuit Pack","Sealed biscuit pack.",2500,"PACKAGED","VEGETARIAN"),
            FixtureItem("pantry-juice","campus-pantry","packs","Juice Carton","Packaged fruit drink for group requests.",3000,"PACKAGED","VEGETARIAN"),
            FixtureItem("pantry-water","campus-pantry","drinks","Water Bottle","Sealed drinking water.",2000,"PACKAGED","VEGETARIAN"),
        )
        connection.prepareStatement("""INSERT INTO menu_items(id,seller_id,category_id,name,description,price_paise,kind,dietary,availability,published,unavailable_visibility,image_key)
            VALUES (?,?,?,?,?,?,?,?,?,TRUE,'SHOW',?) ON CONFLICT(id) DO NOTHING""").use { statement ->
            items.forEach { item ->
                statement.setString(1,item.id); statement.setString(2,item.sellerId); statement.setString(3,item.categoryId)
                statement.setString(4,item.name); statement.setString(5,item.description); statement.setLong(6,item.pricePaise)
                statement.setString(7,item.kind); statement.setString(8,item.dietary); statement.setString(9,item.availability)
                statement.setString(10,item.id); statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}

private data class FixtureItem(val id:String,val sellerId:String,val categoryId:String,val name:String,val description:String,
                               val pricePaise:Long,val kind:String,val dietary:String,val availability:String="AVAILABLE")
