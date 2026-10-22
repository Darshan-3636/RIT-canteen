package edu.msrit.canteen.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerRouteTest {
    @Test fun catalogDestinationsAreDebugOnly() {
        listOf(Route.CustomerPreview, Route.OrderFood, Route.Sellers, Route.SellerProfile,
            Route.SellerMenu, Route.FoodDetails, Route.CustomerUnavailable, Route.Search,
            Route.Favorites, Route.Promotions, Route.PromotionDetail, Route.QuickReorderEmpty,
            Route.QuickReorderSample).forEach {
            assertTrue(it in Route.debugOnly)
        }
        assertFalse(Route.Welcome in Route.debugOnly)
        assertFalse(PresentationAccess.allows(null, ProtectedArea.Customer))
    }

    @Test fun sellerAndFoodDestinationsResolveWithinTheCatalog() {
        assertEquals("debug/sellers/main-canteen", Route.sellerProfile("main-canteen"))
        assertEquals("debug/sellers/main-canteen/menu", Route.sellerMenu("main-canteen"))
        assertEquals("debug/items/masala-dosa", Route.foodDetails("masala-dosa"))
        assertEquals("debug/search?categoryId=rice&filters=false", Route.search("rice"))
        assertEquals("debug/promotions/dosa-spotlight", Route.promotionDetail("dosa-spotlight"))
    }

    @Test fun sellerBusinessRoutesAreDebugOnly() {
        listOf(Route.SellerManagement, Route.SellerManageMenu, Route.SellerManageItem,
            Route.SellerManageAvailability, Route.SellerManageCategories, Route.SellerManageSettings,
            Route.SellerManageProfile, Route.SellerManageHours, Route.SellerManageClosures,
            Route.SellerManageMinimum, Route.SellerManageEmployees, Route.SellerManageEmployee,
            Route.SellerManagePromotions, Route.SellerManagePromotion).forEach { assertTrue(it in Route.debugOnly) }
        assertEquals("debug/seller/management/items/new", Route.manageItem(null))
    }
}
