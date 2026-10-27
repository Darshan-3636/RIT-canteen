package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import org.json.JSONObject

/** Read-only catalog snapshot from PostgreSQL; local images remain keyed by stable IDs. */
object DebugServerCatalog {
    data class Snapshot(val sellers: List<Seller>, val categories: List<MenuCategory>, val items: List<MenuItem>)
    private val categoryNames = PreviewCatalog.categories.associate { it.id to it.name }

    /** Blocking transport. Fetch on Dispatchers.IO, then apply on the UI thread. */
    fun fetch(): Snapshot {
        val response = DebugBackendApi.request("GET", "/api/v1/sellers")
        check(response.status == 200) { response.error ?: "Could not load sellers" }
        val sellersJson = response.body!!.getJSONArray("sellers")
        val sellers = (0 until sellersJson.length()).map { index ->
            val row = sellersJson.getJSONObject(index)
            val id = row.getString("id")
            val modes = row.getJSONArray("orderingModes")
            val categories = row.getJSONArray("categoryIds")
            Seller(id = id, name = row.getString("name"), description = row.getString("description"),
                location = null,
                capabilities = OrderingCapabilities(
                    futureIndividualPrepared = (0 until modes.length()).any {
                        modes.getString(it) == "INDIVIDUAL_PREPARED_DEMO" },
                    bulk = (0 until modes.length()).any { modes.getString(it) == "BULK_REQUEST" }),
                categoryIds = (0 until categories.length()).map(categories::getString).toSet(),
                availability = when (row.optString("openStatus")) {
                    "OPEN" -> SellerAvailability.AVAILABLE
                    "CLOSED" -> SellerAvailability.UNAVAILABLE
                    else -> SellerAvailability.UNKNOWN
                }, photo = CatalogArtwork.sellerPhoto(id),
                isMainCanteen = row.optBoolean("isMainCanteen"),
                bulkMinimumUnits = row.getInt("bulkMinimumUnits"))
        }
        val items = sellers.flatMap { seller ->
            val menu = DebugBackendApi.request("GET", "/api/v1/sellers/" + seller.id + "/menu")
            check(menu.status == 200) { menu.error ?: "Could not load menu" }
            val rows = menu.body!!.getJSONArray("items")
            (0 until rows.length()).map { index -> toMenuItem(rows.getJSONObject(index)) }
        }
        val categoryIds = items.map(MenuItem::categoryId).distinct()
        return Snapshot(sellers, categoryIds.map { MenuCategory(it, categoryNames[it] ?: it
            .replace('-', ' ').replaceFirstChar(Char::uppercase)) }, items)
    }

    fun apply(snapshot: Snapshot) =
        PreviewCatalog.applyDemoSnapshot(snapshot.sellers, snapshot.categories, snapshot.items)

    fun clear() = PreviewCatalog.applyDemoSnapshot(emptyList(), emptyList(), emptyList())

    private fun toMenuItem(row: JSONObject): MenuItem {
        val paise = row.getLong("pricePaise")
        require(paise >= 0 && paise % 100L == 0L && paise / 100L <= Int.MAX_VALUE) {
            "Catalog price cannot be represented in this UI"
        }
        val id = row.getString("id")
        return MenuItem(id = id, sellerId = row.getString("sellerId"),
            categoryId = row.getString("categoryId"), name = row.getString("name"),
            description = row.getString("description"), price = Price((paise / 100L).toInt()),
            kind = FoodKind.valueOf(row.getString("kind")),
            dietary = Dietary.valueOf(row.getString("dietary")),
            availability = ItemAvailability.valueOf(row.getString("availability")),
            published = row.getBoolean("published"), photo = CatalogArtwork.itemPhoto(id))
    }
}
