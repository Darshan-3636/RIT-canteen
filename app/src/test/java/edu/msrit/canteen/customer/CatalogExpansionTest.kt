package edu.msrit.canteen.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogExpansionTest {
    private val catalog = PreviewCatalog

    @Test fun fourSellersHaveExactlyEighteenUniqueItemsEach() {
        catalog.resetDemoSnapshot()
        assertEquals(4, catalog.sellers.size)
        assertEquals(72, catalog.items.size)
        assertEquals(72, catalog.items.map { it.id }.toSet().size)
        catalog.sellers.forEach { seller ->
            assertEquals(seller.id, 18, catalog.items.count { it.sellerId == seller.id })
        }
        catalog.items.forEach { item ->
            assertTrue(item.name.isNotBlank() && item.description.isNotBlank())
            assertTrue(item.price.rupees > 0)
            assertTrue(catalog.categories.any { it.id == item.categoryId })
            assertTrue(catalog.sellers.single { it.id == item.sellerId }.categoryIds.contains(item.categoryId))
            assertNotNull(item.photo)
        }
    }

    @Test fun legacyIdsKeepOriginalSellers() {
        val legacy = mapOf(
            "masala-dosa" to "main-canteen", "veg-biryani" to "main-canteen",
            "filter-coffee" to "main-canteen", "idli" to "main-canteen",
            "lemon-rice" to "main-canteen", "water-bottle" to "main-canteen",
            "north-lunch" to "north-counter", "north-snack" to "north-counter",
            "corner-coffee" to "coffee-corner", "corner-biscuits" to "coffee-corner",
            "pantry-juice" to "campus-pantry", "pantry-water" to "campus-pantry",
        )
        legacy.forEach { (id, seller) -> assertEquals(seller, catalog.items.single { it.id == id }.sellerId) }
        assertEquals(ItemAvailability.UNAVAILABLE, catalog.items.single { it.id == "lemon-rice" }.availability)
    }

    @Test fun pantryBeveragesAndPackagedSnacksAreDistinctFromFoodKind() {
        val drinks = catalog.visibleItems("campus-pantry", "drinks")
        assertEquals(10, drinks.size)
        assertTrue(drinks.all { it.kind == FoodKind.PACKAGED })
        assertTrue(drinks.map { it.id }.containsAll(listOf("pantry-juice", "pantry-water")))
        val snacks = catalog.visibleItems("campus-pantry", "packs")
        assertEquals(8, snacks.size)
        assertTrue(snacks.map { it.id }.containsAll(listOf("potato-chips", "granola-bar", "cracker-pack")))
        assertFalse(snacks.any { it.id == "pantry-juice" || it.id == "pantry-water" })
        assertEquals(drinks.map { it.id }.toSet(), catalog.discover(filters = DiscoveryFilters(
            sellerId = "campus-pantry", categoryId = "drinks", kind = FoodKind.PACKAGED)).items.map { it.id }.toSet())
    }

    @Test fun coffeeAndWaterShareBeverageCategoryButNotKind() {
        val mainDrinks = catalog.visibleItems("main-canteen", "drinks")
        assertEquals(setOf("filter-coffee", "water-bottle"), mainDrinks.map { it.id }.toSet())
        assertEquals(FoodKind.PREPARED, mainDrinks.single { it.id == "filter-coffee" }.kind)
        assertEquals(FoodKind.PACKAGED, mainDrinks.single { it.id == "water-bottle" }.kind)
        assertEquals("breakfast", catalog.items.single { it.id == "idli" }.categoryId)
        assertEquals("cookies", catalog.items.single { it.id == "corner-biscuits" }.categoryId)
    }

    @Test fun searchAndFiltersStaySellerScoped() {
        val juice = catalog.discover("juice", DiscoveryFilters(sellerId = "campus-pantry")).items
        assertTrue(juice.map { it.id }.containsAll(listOf("pantry-juice", "orange-juice-bottle", "mango-juice-bottle")))
        assertTrue(juice.all { it.sellerId == "campus-pantry" && it.categoryId == "drinks" })
        val water = catalog.discover("water", DiscoveryFilters(sellerId = "campus-pantry")).items
        assertTrue(water.map { it.id }.containsAll(listOf("pantry-water", "sparkling-water", "coconut-water-pack")))
        catalog.sellers.forEach { seller ->
            assertEquals(18, catalog.visibleItems(seller.id).size)
            catalog.categories.forEach { category ->
                assertTrue(catalog.visibleItems(seller.id, category.id).all {
                    it.sellerId == seller.id && it.categoryId == category.id
                })
            }
        }
    }

    @Test fun resetRestoresCatalogAndAvailabilityAfterLocalChanges() {
        val original = catalog.items.single { it.id == "potato-chips" }
        catalog.applyDemoSnapshot(catalog.sellers, catalog.categories,
            catalog.items.map { if (it.id == original.id) it.copy(name = "Edited chips", published = false) else it })
        assertFalse(catalog.visibleItems("campus-pantry").any { it.id == original.id })
        catalog.resetDemoSnapshot()
        assertEquals(original, catalog.items.single { it.id == original.id })
        assertEquals(72, catalog.items.size)
    }

    @Test fun newItemsRespectExistingOrderModeRules() {
        val main = catalog.sellers.single { it.id == "main-canteen" }
        val pantry = catalog.sellers.single { it.id == "campus-pantry" }
        val north = catalog.sellers.single { it.id == "north-counter" }
        assertEquals(ItemEligibility.FUTURE_INDIVIDUAL_AND_BULK,
            itemEligibility(main, catalog.items.single { it.id == "plain-dosa" }))
        assertEquals(ItemEligibility.BULK_ONLY,
            itemEligibility(main, catalog.items.single { it.id == "water-bottle" }))
        assertEquals(ItemEligibility.BULK_ONLY,
            itemEligibility(north, catalog.items.single { it.id == "veg-samosa" }))
        assertEquals(ItemEligibility.BULK_ONLY,
            itemEligibility(pantry, catalog.items.single { it.id == "orange-juice-bottle" }))
    }
}
