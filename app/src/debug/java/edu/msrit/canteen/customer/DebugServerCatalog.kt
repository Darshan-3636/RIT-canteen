package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Read-only catalog snapshot from PostgreSQL; local images remain keyed by stable IDs. */
object DebugServerCatalog {
    data class Snapshot(val sellers: List<Seller>, val categories: List<MenuCategory>, val items: List<MenuItem>,
                        val policies: List<Pair<SellerBulkPolicy, List<SellerClosure>>> = emptyList())
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
        val policies = sellers.mapNotNull { seller ->
            runCatching { fetchPolicy(seller.id) }.getOrNull()
        }
        return Snapshot(sellers, categoryIds.map { MenuCategory(it, categoryNames[it] ?: it
            .replace('-', ' ').replaceFirstChar(Char::uppercase)) }, items, policies)
    }

    private fun fetchPolicy(sellerId: String): Pair<SellerBulkPolicy, List<SellerClosure>> {
        val response = DebugBackendApi.request("GET", "/api/v1/sellers/$sellerId/operating-policy")
        check(response.status == 200) { response.error ?: "Could not load operating policy" }
        val row = requireNotNull(response.body)
        val hours = row.getJSONArray("hours")
        val weekly = (0 until hours.length()).map { hours.getJSONObject(it) }.groupBy {
            DayOfWeek.of(it.getInt("dayOfWeek"))
        }.mapValues { (_, windows) -> windows.map {
            BulkTimeWindow(LocalTime.parse(it.getString("opensAt")), LocalTime.parse(it.getString("closesAt")))
        } }
        val closures = row.getJSONArray("closures").let { values ->
            (0 until values.length()).map { values.getJSONObject(it) }.map {
                SellerClosure(LocalDate.parse(it.getString("localDate")), it.getString("reason"), false)
            }
        }
        return SellerBulkPolicy(sellerId, row.getInt("minimumUnits"), weekly,
            temporaryClosures = closures.map { it.date }.toSet(),
            specialPickupEnabled = row.getBoolean("specialPickupEnabled")) to closures
    }

    fun apply(snapshot: Snapshot) {
        DevelopmentSellerBusiness.clearServerPolicies()
        PreviewCatalog.applyDemoSnapshot(snapshot.sellers, snapshot.categories, snapshot.items)
        snapshot.policies.forEach { (policy, closures) ->
            DevelopmentSellerBusiness.applyServerPolicy(policy, closures)
        }
    }

    fun clear() {
        DevelopmentSellerBusiness.clearServerPolicies()
        PreviewCatalog.applyDemoSnapshot(emptyList(), emptyList(), emptyList())
    }

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
