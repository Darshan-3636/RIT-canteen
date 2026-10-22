package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.customer.CustomerCatalogSource
import edu.msrit.canteen.customer.Dietary
import edu.msrit.canteen.customer.DiscoveryFilters
import edu.msrit.canteen.customer.DiscoverySort
import edu.msrit.canteen.customer.FavoritesRepository
import edu.msrit.canteen.customer.FoodKind
import edu.msrit.canteen.customer.OrderEligibilityFilter
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.discover
import edu.msrit.canteen.ui.components.CatalogFoodCard
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.CatalogSellerCard
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSearchField
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun CustomerSearchScreen(
    initialCategoryId: String? = null,
    initiallyShowFilters: Boolean = false,
    onBack: () -> Unit,
    onFood: (String) -> Unit,
    onSeller: (String) -> Unit,
    favorites: FavoritesRepository? = null,
    customerId: String? = null,
    catalog: CustomerCatalogSource = PreviewCatalog,
) {
    val colors = RitThemeTokens.colors
    val focus = androidx.compose.runtime.remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable(initialCategoryId) { mutableStateOf(DiscoveryFilters(categoryId = initialCategoryId)) }
    var sort by rememberSaveable { mutableStateOf(DiscoverySort.NAME) }
    var showFilters by rememberSaveable { mutableStateOf(initiallyShowFilters) }
    val results = catalog.discover(query, filters, sort)

    LaunchedEffect(Unit) {
        if (!initiallyShowFilters) {
            focus.requestFocus()
            keyboard?.show()
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background).testTag("customer_search")) {
        CatalogHeader("Search", onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            RitStatusIndicator("Preview catalog · not a live menu")
            Spacer(Modifier.height(12.dp))
            RitSearchField(query, { query = it }, Modifier.fillMaxWidth(),
                hint = "Search food, categories or sellers…",
                onFilterClick = { showFilters = !showFilters; keyboard?.hide() },
                onClearClick = { query = "" }, inputModifier = Modifier.focusRequester(focus),
                onSubmit = { keyboard?.hide() })
            Spacer(Modifier.height(10.dp))
        }
        LazyColumn(Modifier.weight(1f).testTag("search_results_list"),
            contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RitText("${results.items.size} ${if (results.items.size == 1) "food" else "foods"} · " +
                            "${results.sellers.size} ${if (results.sellers.size == 1) "seller" else "sellers"}", RitType.small,
                            colors.muted, Modifier.weight(1f).testTag("search_result_count"))
                        if (!filters.isEmpty) RitChip("Reset filters", false, { filters = DiscoveryFilters() },
                            Modifier.testTag("search_reset_filters"))
                    }
                    if (!filters.isEmpty) {
                        Spacer(Modifier.height(4.dp))
                        ActiveFilterChips(filters, catalog, { filters = it })
                    }
                    Spacer(Modifier.height(8.dp))
                    RitText("Sort by", RitType.small, colors.muted)
                    Spacer(Modifier.height(6.dp))
                }
            }
            item {
                LazyRow(modifier = Modifier.testTag("search_sort_choices"),
                    contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { RitChip("Name", sort == DiscoverySort.NAME, { sort = DiscoverySort.NAME }, Modifier.testTag("sort_name")) }
                    item { RitChip("Price: low to high", sort == DiscoverySort.PRICE_LOW_TO_HIGH,
                        { sort = DiscoverySort.PRICE_LOW_TO_HIGH }, Modifier.testTag("sort_price_low")) }
                    item { RitChip("Price: high to low", sort == DiscoverySort.PRICE_HIGH_TO_LOW,
                        { sort = DiscoverySort.PRICE_HIGH_TO_LOW }, Modifier.testTag("sort_price_high")) }
                }
            }
            if (showFilters) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).testTag("search_filters_panel")) {
                        RitText("Advanced filters", RitType.title)
                        RitText("Combine catalog facts; item availability is a demo flag, not live stock.", RitType.small, colors.muted)
                    }
                }
                item { FilterChoices("Seller", catalog.sellers.map { it.id to it.name }, filters.sellerId, "seller") {
                    filters = filters.copy(sellerId = it)
                } }
                item { FilterChoices("Food category", catalog.categories.filter { category ->
                    catalog.sellers.any { category.id in it.categoryIds } }.map { it.id to it.name }, filters.categoryId, "category") {
                    filters = filters.copy(categoryId = it)
                } }
                item { FilterChoices("Food type", listOf("PREPARED" to "Prepared", "PACKAGED" to "Packaged"), filters.kind?.name, "kind") {
                    filters = filters.copy(kind = it?.let(FoodKind::valueOf))
                } }
                val knownDietary = catalog.items.map { it.dietary }.filter { it != Dietary.NOT_SPECIFIED }.distinct()
                if (knownDietary.isNotEmpty()) item {
                    FilterChoices("Dietary information", knownDietary.map { it.name to if (it == Dietary.VEGETARIAN) "Vegetarian" else "Non-vegetarian" },
                        filters.dietary?.name, "dietary") { filters = filters.copy(dietary = it?.let(Dietary::valueOf)) }
                }
                item { FilterChoices("Ordering eligibility", listOf(
                    OrderEligibilityFilter.INDIVIDUAL_PLANNED.name to "Individual planned",
                    OrderEligibilityFilter.BULK_REQUEST.name to "Bulk request"), filters.orderEligibility?.name, "mode") {
                    filters = filters.copy(orderEligibility = it?.let(OrderEligibilityFilter::valueOf))
                } }
                item { FilterChoices("Illustrative price", listOf("up-to-50" to "₹0–₹50", "over-50" to "₹51+"),
                    when { filters.maxPrice == 50 -> "up-to-50"; filters.minPrice == 51 -> "over-50"; else -> null }, "price") {
                    filters = when (it) {
                        "up-to-50" -> filters.copy(minPrice = null, maxPrice = 50)
                        "over-50" -> filters.copy(minPrice = 51, maxPrice = null)
                        else -> filters.copy(minPrice = null, maxPrice = null)
                    }
                } }
                item { FilterChoices("Item status", listOf("marked" to "Marked available"),
                    if (filters.itemMarkedAvailableOnly) "marked" else null, "availability") {
                    filters = filters.copy(itemMarkedAvailableOnly = it != null)
                } }
                item { RitPrimaryButton("Reset All advanced filters", { filters = DiscoveryFilters() },
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("filters_reset_all")) }
            }
            if (results.items.isEmpty() && results.sellers.isEmpty()) {
                item {
                    Column {
                        RitEmptyState("No matching food found", "Try another keyword, adjust filters or browse a seller.")
                        RitPrimaryButton("Clear search and filters", { query = ""; filters = DiscoveryFilters() },
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("search_clear_all"))
                    }
                }
            } else {
                if (results.items.isNotEmpty()) {
                    item { RitText("Food", RitType.title, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
                    items(results.items, key = { "food:${it.id}" }) { food ->
                        val seller = catalog.sellers.first { it.id == food.sellerId }
                        CatalogFoodCard(food, seller, { onFood(food.id) },
                            Modifier.padding(horizontal = 20.dp, vertical = 5.dp).testTag("search_item_${food.id}"),
                            isFavorite = favorites != null && customerId != null && food.id in favorites.itemIds(customerId),
                            onFavoriteClick = if (favorites != null && customerId != null) ({ favorites.toggleItem(customerId, food.id) }) else null)
                    }
                }
                if (results.sellers.isNotEmpty()) {
                    item { RitText("Sellers", RitType.title, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
                    items(results.sellers, key = { "seller:${it.id}" }) { seller ->
                        val names = catalog.categories.filter { it.id in seller.categoryIds }.joinToString(" · ") { it.name }
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).testTag("search_seller_${seller.id}")) {
                            CatalogSellerCard(seller, names, { onSeller(seller.id) },
                                isFavorite = favorites != null && customerId != null && seller.id in favorites.sellerIds(customerId),
                                onFavoriteClick = if (favorites != null && customerId != null) ({ favorites.toggleSeller(customerId, seller.id) }) else null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChoices(title: String, choices: List<Pair<String, String>>, selected: String?, tagPrefix: String,
                          onSelect: (String?) -> Unit) {
    val colors = RitThemeTokens.colors
    Column(Modifier.padding(vertical = 7.dp)) {
        RitText(title, RitType.label, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(7.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { RitChip("Any", selected == null, { onSelect(null) }, Modifier.testTag("filter_${tagPrefix}_any")) }
            items(choices, key = { it.first }) { (id, label) ->
                RitChip(label, selected == id, { onSelect(id) }, Modifier.testTag("filter_${tagPrefix}_$id"))
            }
        }
    }
}

@Composable
private fun ActiveFilterChips(filters: DiscoveryFilters, catalog: CustomerCatalogSource,
                              onChange: (DiscoveryFilters) -> Unit) {
    val chips = buildList<Pair<String, () -> Unit>> {
        filters.sellerId?.let { id -> add((catalog.sellers.find { it.id == id }?.name ?: id) to { onChange(filters.copy(sellerId = null)) }) }
        filters.categoryId?.let { id -> add((catalog.categories.find { it.id == id }?.name ?: id) to { onChange(filters.copy(categoryId = null)) }) }
        filters.kind?.let { add(it.name.lowercase().replaceFirstChar(Char::uppercase) to { onChange(filters.copy(kind = null)) }) }
        filters.dietary?.let { add(it.name.lowercase().replace('_', ' ') to { onChange(filters.copy(dietary = null)) }) }
        filters.orderEligibility?.let { add(it.name.lowercase().replace('_', ' ') to { onChange(filters.copy(orderEligibility = null)) }) }
        if (filters.minPrice != null || filters.maxPrice != null) add("Price range" to { onChange(filters.copy(minPrice = null, maxPrice = null)) })
        if (filters.itemMarkedAvailableOnly) add("Marked available" to { onChange(filters.copy(itemMarkedAvailableOnly = false)) })
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips) { (label, clear) -> RitChip("$label ×", true, clear) }
    }
}
