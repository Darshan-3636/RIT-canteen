package edu.msrit.canteen.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerCatalogTest {
    private val catalog = PreviewCatalog
    private val main = catalog.sellers.first { it.id == PreviewCatalog.MAIN_ID }

    @Test fun mainPreparedFoodIsFutureEligibleButPackagedIsBulkOnly() {
        assertEquals(ItemEligibility.FUTURE_INDIVIDUAL_AND_BULK, itemEligibility(main, catalog.items.first { it.id == "masala-dosa" }))
        assertEquals(ItemEligibility.BULK_ONLY, itemEligibility(main, catalog.items.first { it.id == "water-bottle" }))
    }

    @Test fun everyOtherSellerIsBulkOnlyForPreparedAndPackagedItems() {
        catalog.sellers.filterNot { it.isMainCanteen }.forEach { seller ->
            catalog.items.filter { it.sellerId == seller.id }.forEach { item ->
                assertEquals(ItemEligibility.BULK_ONLY, itemEligibility(seller, item))
            }
        }
    }

    @Test fun categoryFilterIsScopedToSellerAndSupportsEmptyResults() {
        assertTrue(catalog.visibleItems(PreviewCatalog.MAIN_ID, "dosa").all { it.sellerId == PreviewCatalog.MAIN_ID && it.categoryId == "dosa" })
        assertTrue(catalog.visibleItems("campus-pantry", "dosa").isEmpty())
    }

    @Test fun unavailableVisibilityAndPublicationAreRespected() {
        assertTrue(catalog.visibleItems().any { it.id == "lemon-rice" })
        val hidden = object : CustomerCatalogSource by catalog {
            override val items = catalog.items + catalog.items.first().copy(id = "hidden", availability = ItemAvailability.UNAVAILABLE, unavailableVisibility = UnavailableVisibility.HIDE) + catalog.items.first().copy(id = "draft", published = false)
        }
        assertFalse(hidden.visibleItems().any { it.id == "hidden" || it.id == "draft" })
        assertEquals(ItemEligibility.UNAVAILABLE, itemEligibility(main, catalog.items.first { it.id == "lemon-rice" }))
    }

    @Test fun allVisibleItemsBelongToTheirSellerAndHaveCategory() {
        catalog.visibleItems().forEach { item ->
            val seller = catalog.sellers.single { it.id == item.sellerId }
            assertTrue(item.categoryId in seller.categoryIds)
            assertTrue(catalog.categories.any { it.id == item.categoryId })
        }
    }

    @Test fun detailsCanResolveEachVisibleItemAndSeller() {
        catalog.visibleItems().forEach { item ->
            assertEquals(item, catalog.items.single { it.id == item.id })
            assertEquals(1, catalog.sellers.count { it.id == item.sellerId })
        }
    }

    @Test fun sellerSearchAndCategorySelectionUseFixtureData() {
        assertEquals(listOf(PreviewCatalog.MAIN_ID), catalog.visibleSellers("Main Canteen").map { it.id })
        assertTrue(catalog.visibleSellers(categoryId = "dosa").all { "dosa" in it.categoryIds })
        assertTrue(catalog.visibleSellers("does not exist").isEmpty())
    }

    @Test fun everyOrderingActionIsDisabledAndUnavailableIsExplicit() {
        ItemEligibility.entries.forEach { assertFalse(orderingAction(it).enabled) }
        assertEquals("Item unavailable", orderingAction(ItemEligibility.UNAVAILABLE).label)
    }

    @Test fun everyCurrentSellerAndFoodHasDedicatedArtworkWithUnknownFallback() {
        assertEquals(4, catalog.sellers.size)
        assertEquals(72, catalog.items.size)
        assertEquals(catalog.sellers.size, catalog.sellers.mapNotNull { CatalogArtwork.sellerPhoto(it.id) }.distinct().size)
        assertEquals(catalog.items.size, catalog.items.mapNotNull { CatalogArtwork.itemPhoto(it.id) }.distinct().size)
        catalog.sellers.forEach { assertEquals(CatalogArtwork.sellerPhoto(it.id), it.photo) }
        catalog.items.forEach { assertEquals(CatalogArtwork.itemPhoto(it.id), it.photo) }
        assertEquals(null, CatalogArtwork.sellerPhoto("future-seller"))
        assertEquals(null, CatalogArtwork.itemPhoto("future-food"))
    }
}
