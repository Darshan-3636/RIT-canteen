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
    private val cacheLock = Any()
    private var cachedPublic: Snapshot? = null
    private var cachedAtMillis = 0L
    private val cachedPolicies = mutableMapOf<String, Pair<Long, Pair<SellerBulkPolicy, List<SellerClosure>>>>()
    private const val PUBLIC_CACHE_MILLIS = 30_000L

    /** Blocking transport. Fetch on Dispatchers.IO, then apply on the UI thread. */
    fun fetch(policySellerId: String? = null, force: Boolean = false): Snapshot {
        val public = synchronized(cacheLock) {
            val now = android.os.SystemClock.elapsedRealtime()
            cachedPublic?.takeIf { !force && now - cachedAtMillis < PUBLIC_CACHE_MILLIS } ?: fetchPublic().also {
                cachedPublic = it
                cachedAtMillis = android.os.SystemClock.elapsedRealtime()
            }
        }
        val policies = policySellerId?.let { id -> listOf(policy(id, force)) }.orEmpty()
        return public.copy(policies = policies)
    }

    /** Blocking read; cache only short-lived scheduling data. The server still validates requests. */
    fun policy(sellerId: String, force: Boolean = false): Pair<SellerBulkPolicy, List<SellerClosure>> =
        synchronized(cacheLock) {
            val now = android.os.SystemClock.elapsedRealtime()
            cachedPolicies[sellerId]?.takeIf { !force && now - it.first < PUBLIC_CACHE_MILLIS }?.second
                ?: fetchPolicy(sellerId).also { cachedPolicies[sellerId] =
                    android.os.SystemClock.elapsedRealtime() to it }
        }

    fun applyPolicy(value: Pair<SellerBulkPolicy, List<SellerClosure>>) {
        DevelopmentSellerBusiness.applyServerPolicy(value.first, value.second)
    }

    private fun fetchPublic(): Snapshot {
        val snapshot = DebugBackendApi.request("GET", "/api/v1/catalog/snapshot")
        val response = if (snapshot.status == 404) DebugBackendApi.request("GET", "/api/v1/sellers") else snapshot
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
                    // Closed now still permits a future bulk pickup; the backend lists only active sellers.
                    "CLOSED" -> SellerAvailability.UNKNOWN
                    else -> SellerAvailability.UNKNOWN
                }, photo = CatalogArtwork.sellerPhoto(id),
                isMainCanteen = row.optBoolean("isMainCanteen"),
                bulkMinimumUnits = row.getInt("bulkMinimumUnits"))
        }
        val items = if (snapshot.status == 200) {
            val rows=snapshot.body!!.getJSONArray("items")
            (0 until rows.length()).map { index -> toMenuItem(rows.getJSONObject(index)) }
        } else sellers.flatMap { seller ->
            val menu = DebugBackendApi.request("GET", "/api/v1/sellers/" + seller.id + "/menu")
            check(menu.status == 200) { menu.error ?: "Could not load menu" }
            val rows = menu.body!!.getJSONArray("items")
            (0 until rows.length()).map { index -> toMenuItem(rows.getJSONObject(index)) }
        }
        val categoryIds = items.map(MenuItem::categoryId).distinct()
        return Snapshot(sellers, categoryIds.map { MenuCategory(it, categoryNames[it] ?: it
            .replace('-', ' ').replaceFirstChar(Char::uppercase)) }, items)
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
            specialPickupEnabled = row.getBoolean("specialPickupEnabled"),
            individualEnabled = row.optBoolean("individualEnabled")) to closures
    }

    fun apply(snapshot: Snapshot) {
        PreviewCatalog.applyDemoSnapshot(snapshot.sellers, snapshot.categories, snapshot.items)
        snapshot.policies.forEach(::applyPolicy)
    }

    fun clear() {
        synchronized(cacheLock) { cachedPublic = null; cachedAtMillis = 0L; cachedPolicies.clear() }
        DevelopmentSellerBusiness.clearServerPolicies()
        PreviewCatalog.applyDemoSnapshot(emptyList(), emptyList(), emptyList())
    }

    private fun toMenuItem(row: JSONObject): MenuItem {
        val paise = row.getLong("pricePaise")
        require(paise >= 0 && paise % 100L == 0L && paise / 100L <= Int.MAX_VALUE) {
            "Catalog price cannot be represented in this UI"
        }
        val id = row.getString("id")
        val imageKey = row.getString("imageKey")
        return MenuItem(id = id, sellerId = row.getString("sellerId"),
            categoryId = row.getString("categoryId"), name = row.getString("name"),
            description = row.getString("description"), price = Price((paise / 100L).toInt()),
            kind = FoodKind.valueOf(row.getString("kind")),
            dietary = Dietary.valueOf(row.getString("dietary")),
            availability = ItemAvailability.valueOf(row.getString("availability")),
            published = row.getBoolean("published"), photo = CatalogArtwork.itemPhoto(imageKey),
            orderEligibility = OrderEligibility.valueOf(row.optString("orderEligibility", "BULK_ONLY")),
            remoteImageUrl = imageKey.takeIf { it.startsWith("custom-") }
                ?.let { DebugBackendApi.publicUrl("/api/v1/catalog/images/$it") },
            imageKey = imageKey)
    }
}
