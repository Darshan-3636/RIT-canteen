package edu.msrit.canteen.customer

import java.io.Serializable
import java.util.Locale

enum class OrderEligibilityFilter { INDIVIDUAL_PLANNED, BULK_REQUEST }
enum class DiscoverySort { NAME, PRICE_LOW_TO_HIGH, PRICE_HIGH_TO_LOW }

/** Catalog-backed filters only. This is presentation logic, not server authorization. */
data class DiscoveryFilters(
    val sellerId: String? = null,
    val categoryId: String? = null,
    val kind: FoodKind? = null,
    val dietary: Dietary? = null,
    val orderEligibility: OrderEligibilityFilter? = null,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val itemMarkedAvailableOnly: Boolean = false,
) : Serializable {
    val isEmpty: Boolean get() = sellerId == null && categoryId == null && kind == null && dietary == null &&
        orderEligibility == null && minPrice == null && maxPrice == null && !itemMarkedAvailableOnly
}

data class DiscoveryResults(val items: List<MenuItem>, val sellers: List<Seller>)

private fun words(query: String): List<String> = query.trim().lowercase(Locale.ROOT)
    .split(Regex("\\s+")).filter { it.isNotEmpty() }

private fun matchesAll(tokens: List<String>, vararg values: String): Boolean {
    if (tokens.isEmpty()) return true
    val text = values.joinToString(" ").lowercase(Locale.ROOT)
    return tokens.all(text::contains)
}

fun CustomerCatalogSource.discover(
    query: String = "",
    filters: DiscoveryFilters = DiscoveryFilters(),
    sort: DiscoverySort = DiscoverySort.NAME,
): DiscoveryResults {
    val tokens = words(query)
    val sellersById = sellers.associateBy(Seller::id)
    val categoriesById = categories.associateBy(MenuCategory::id)
    val matchingItems = visibleItems().filter { item ->
        val seller = sellersById[item.sellerId] ?: return@filter false
        val eligibility = itemEligibility(seller, item)
        (filters.sellerId == null || item.sellerId == filters.sellerId) &&
            (filters.categoryId == null || item.categoryId == filters.categoryId) &&
            (filters.kind == null || item.kind == filters.kind) &&
            (filters.dietary == null || item.dietary == filters.dietary) &&
            (filters.minPrice == null || item.price.rupees >= filters.minPrice) &&
            (filters.maxPrice == null || item.price.rupees <= filters.maxPrice) &&
            (!filters.itemMarkedAvailableOnly || item.availability == ItemAvailability.AVAILABLE) &&
            when (filters.orderEligibility) {
                null -> true
                OrderEligibilityFilter.INDIVIDUAL_PLANNED -> eligibility == ItemEligibility.FUTURE_INDIVIDUAL_AND_BULK
                OrderEligibilityFilter.BULK_REQUEST -> seller.capabilities.bulk && eligibility != ItemEligibility.UNAVAILABLE
            } && matchesAll(tokens, item.name, item.description, categoriesById[item.categoryId]?.name.orEmpty(),
                seller.name, seller.description, item.tags.joinToString(" "))
    }
    val orderedItems = when (sort) {
        DiscoverySort.NAME -> matchingItems.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        DiscoverySort.PRICE_LOW_TO_HIGH -> matchingItems.sortedWith(compareBy<MenuItem> { it.price.rupees }.thenBy { it.name })
        DiscoverySort.PRICE_HIGH_TO_LOW -> matchingItems.sortedWith(compareByDescending<MenuItem> { it.price.rupees }.thenBy { it.name })
    }
    val matchingSellers = sellers.filter { seller ->
        (filters.sellerId == null || seller.id == filters.sellerId) &&
            (filters.categoryId == null || filters.categoryId in seller.categoryIds) &&
            (filters.isEmpty || matchingItems.any { it.sellerId == seller.id }) &&
            matchesAll(tokens, seller.name, seller.description,
                categories.filter { it.id in seller.categoryIds }.joinToString(" ") { it.name })
    }.sortedBy(Seller::name)
    return DiscoveryResults(orderedItems, matchingSellers)
}
