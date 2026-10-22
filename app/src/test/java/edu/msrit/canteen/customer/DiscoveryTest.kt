package edu.msrit.canteen.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryTest {
    @Test fun matchesFoodSellerCategoryAndDescriptionIgnoringCaseAndWhitespace() {
        assertTrue(PreviewCatalog.discover("  MASALA   dosa ").items.any { it.id == "masala-dosa" })
        assertTrue(PreviewCatalog.discover("campus meals").sellers.any { it.id == PreviewCatalog.MAIN_ID })
        assertTrue(PreviewCatalog.discover("rice").items.any { it.id == "veg-biryani" })
        assertTrue(PreviewCatalog.discover("group requests").items.any { it.id == "north-lunch" })
        assertTrue(PreviewCatalog.discover("never-listed-food").items.isEmpty())
    }

    @Test fun combinedFiltersAndResetArePredictable() {
        val filters = DiscoveryFilters(sellerId = PreviewCatalog.MAIN_ID, categoryId = "rice",
            kind = FoodKind.PREPARED, dietary = Dietary.VEGETARIAN, maxPrice = 85,
            orderEligibility = OrderEligibilityFilter.INDIVIDUAL_PLANNED, itemMarkedAvailableOnly = true)
        assertEquals(listOf("veg-biryani"), PreviewCatalog.discover(filters = filters).items.map { it.id })
        assertTrue(PreviewCatalog.discover(filters = filters.copy(maxPrice = 50)).items.isEmpty())
        assertEquals(PreviewCatalog.visibleItems().size, PreviewCatalog.discover(filters = DiscoveryFilters()).items.size)
    }

    @Test fun sortingAndEligibilityDoNotInventPopularityOrLiveAvailability() {
        val low = PreviewCatalog.discover(sort = DiscoverySort.PRICE_LOW_TO_HIGH).items.map { it.price.rupees }
        val high = PreviewCatalog.discover(sort = DiscoverySort.PRICE_HIGH_TO_LOW).items.map { it.price.rupees }
        assertEquals(low.sorted(), low)
        assertEquals(high.sortedDescending(), high)
        val individual = PreviewCatalog.discover(filters = DiscoveryFilters(orderEligibility = OrderEligibilityFilter.INDIVIDUAL_PLANNED)).items
        assertTrue(individual.all { it.sellerId == PreviewCatalog.MAIN_ID && it.kind == FoodKind.PREPARED && it.availability == ItemAvailability.AVAILABLE })
        assertFalse(individual.any { it.id == "lemon-rice" || it.kind == FoodKind.PACKAGED })
        val bulk = PreviewCatalog.discover(filters = DiscoveryFilters(orderEligibility = OrderEligibilityFilter.BULK_REQUEST)).items
        assertTrue(bulk.any { it.id == "north-lunch" })
        assertFalse(bulk.any { it.id == "lemon-rice" })
    }
}
