package edu.msrit.canteen.customer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SellerBusinessTest {
    private val repo = DevelopmentSellerBusiness
    private val seller = "north-counter"
    private val owner = "debug-owner"
    private val manager = "debug-manager"
    private val staff = "debug-staff"
    private val ops = "debug-staff-ops"

    @Before fun setUp() = repo.reset()
    @After fun tearDown() = repo.reset()

    private fun item(id: String = "test-item") = MenuItem(id, seller, "combos", "Test lunch", "Sample",
        Price(50), FoodKind.PREPARED, Dietary.VEGETARIAN)

    @Test fun itemCreateEditKeepsStableIdAndUpdatesSharedCatalog() {
        assertTrue(repo.saveItem(owner, item()))
        assertTrue(repo.saveItem(owner, item().copy(name = "Revised lunch", price = Price(70))))
        assertEquals(1, PreviewCatalog.items.count { it.id == "test-item" })
        assertEquals("Revised lunch", PreviewCatalog.visibleItems(seller).first { it.id == "test-item" }.name)
    }

    @Test fun itemValidationBlocksBlankNameAndForeignCategory() {
        assertFalse(repo.saveItem(owner, item().copy(name = " ")))
        assertFalse(repo.saveItem(owner, item().copy(categoryId = "dosa")))
        assertFalse(repo.saveItem(owner, item().copy(sellerId = PreviewCatalog.MAIN_ID)))
    }

    @Test fun priceRejectsNegativeAtModelBoundary() {
        assertTrue(runCatching { Price(-1) }.isFailure)
    }

    @Test fun ownerCannotChangeOtherSellerCatalog() {
        val foreign = PreviewCatalog.items.first { it.sellerId == PreviewCatalog.MAIN_ID }
        assertFalse(repo.saveItem(owner, foreign.copy(name = "Changed")))
        assertEquals("Masala Dosa", PreviewCatalog.items.first { it.id == foreign.id }.name)
    }

    @Test fun availabilityVisibleHiddenAndUnpublishedPropagate() {
        val original = PreviewCatalog.items.first { it.id == "north-lunch" }
        assertTrue(repo.setItemState(owner, seller, original.id, false, true, true))
        assertTrue(original.id in PreviewCatalog.visibleItems(seller).map { it.id })
        assertEquals(ItemEligibility.UNAVAILABLE, itemEligibility(PreviewCatalog.sellers.first { it.id == seller },
            PreviewCatalog.items.first { it.id == original.id }))
        assertTrue(repo.setItemState(owner, seller, original.id, false, true, false))
        assertFalse(original.id in PreviewCatalog.visibleItems(seller).map { it.id })
        assertTrue(repo.setItemState(owner, seller, original.id, true, false, true))
        assertFalse(original.id in PreviewCatalog.visibleItems(seller).map { it.id })
    }

    @Test fun staffCannotMutateCatalogButDelegatedStaffCanChangeAvailability() {
        assertFalse(repo.saveItem(staff, item()))
        assertFalse(repo.setItemState(staff, seller, "north-lunch", false, true, true))
        assertTrue(repo.setItemState(ops, seller, "north-lunch", false, true, true))
    }

    @Test fun sellerCategoryRenameDoesNotAffectOtherSeller() {
        assertTrue(repo.saveCategory(owner, seller, "combos", "North Combos"))
        val north = PreviewCatalog.items.first { it.id == "north-lunch" }
        val main = PreviewCatalog.items.first { it.id == "veg-biryani" }
        assertEquals("combos", north.categoryId)
        assertEquals("North Combos", PreviewCatalog.categories.first { it.id == north.categoryId }.name)
        assertEquals("rice", main.categoryId)
        assertEquals("Rice Dishes", PreviewCatalog.categories.first { it.id == "rice" }.name)
        assertTrue("combos" in DevelopmentPromotions.promotions.first { it.id == "north-lunch-demo" }.categoryIds)
    }

    @Test fun categoryCreateHideShowAndSafeRemoval() {
        assertTrue(repo.saveCategory(owner, seller, null, "Group specials"))
        val category = PreviewCatalog.categories.first { it.name == "Group specials" }
        assertTrue(repo.saveItem(owner, item().copy(categoryId = category.id)))
        assertFalse(repo.removeCategory(owner, seller, category.id))
        assertTrue(repo.hideCategory(owner, seller, category.id, true))
        assertFalse(PreviewCatalog.visibleItems(seller).any { it.id == "test-item" })
        assertTrue(repo.hideCategory(owner, seller, category.id, false))
        assertTrue(PreviewCatalog.visibleItems(seller).any { it.id == "test-item" })
    }

    @Test fun categoryReorderIsSellerScoped() {
        val old = PreviewCatalog.sellers.first { it.id == seller }.categoryIds.toList()
        assertTrue(repo.moveCategory(owner, seller, old[1], -1))
        assertEquals(old[1], PreviewCatalog.sellers.first { it.id == seller }.categoryIds.first())
        assertEquals(listOf("dosa", "breakfast"), PreviewCatalog.sellers.first { it.id == PreviewCatalog.MAIN_ID }.categoryIds.take(2))
    }

    @Test fun bulkMinimumAndHoursUseSamePolicySource() {
        val policy = DevelopmentBulkPreview.forSeller(seller)!!
        val changed = policy.copy(minimumOverride = 11,
            weeklyHours = mapOf(DayOfWeek.MONDAY to listOf(BulkTimeWindow(LocalTime.of(10, 0), LocalTime.of(15, 0)))))
        assertTrue(repo.savePolicy(owner, changed, emptyList()))
        assertEquals(11, DevelopmentBulkPreview.forSeller(seller)?.minimum)
        assertEquals(changed.weeklyHours, DevelopmentBulkPreview.forSeller(seller)?.weeklyHours)
        val monday = (1L..21L).map { LocalDate.now(RIT_TIME_ZONE).plusDays(it) }.first {
            it.dayOfWeek == DayOfWeek.MONDAY && it !in changed.holidays && it !in changed.temporaryClosures
        }
        assertEquals(PickupWindow.REGULAR, pickupWindow(DevelopmentBulkPreview.forSeller(seller),
            monday.atTime(11, 0), LocalDateTime.now(RIT_TIME_ZONE)))
    }

    @Test fun editedMinimumChangesExistingCustomerCartCheck() {
        val draft = BulkDraft(sellerId = seller, lines = listOf(BulkLine("north-lunch", 10)))
        val now = LocalDateTime.now(RIT_TIME_ZONE)
        assertFalse(BulkDraftIssue.BELOW_MINIMUM in checkBulkDraft(draft, PreviewCatalog, DevelopmentBulkPreview, now).issues)
        assertTrue(repo.savePolicy(owner, DevelopmentBulkPreview.forSeller(seller)!!.copy(minimumOverride = 12), emptyList()))
        assertTrue(BulkDraftIssue.BELOW_MINIMUM in checkBulkDraft(draft, PreviewCatalog, DevelopmentBulkPreview, now).issues)
    }

    @Test fun packagedItemsRemainBulkOnlyDespiteSellerCatalogEditing() {
        val packaged = item("packaged-demo").copy(kind = FoodKind.PACKAGED)
        assertTrue(repo.saveItem(owner, packaged))
        assertEquals(ItemEligibility.BULK_ONLY,
            itemEligibility(PreviewCatalog.sellers.first { it.id == seller },
                PreviewCatalog.items.first { it.id == packaged.id }))
    }

    @Test fun closuresRequireSpecialPickupAndCanBeRestored() {
        val policy = DevelopmentBulkPreview.forSeller(seller)!!
        val monday = LocalDate.now(RIT_TIME_ZONE).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
        val closed = policy.copy(holidays = policy.holidays + monday)
        assertTrue(repo.savePolicy(owner, closed, listOf(SellerClosure(monday, "Demo holiday", true))))
        assertEquals(PickupWindow.SPECIAL_REQUIRED, pickupWindow(DevelopmentBulkPreview.forSeller(seller),
            monday.atTime(11, 0), LocalDateTime.now(RIT_TIME_ZONE)))
        assertTrue(repo.savePolicy(owner, closed.copy(holidays = closed.holidays - monday), emptyList()))
        assertFalse(monday in DevelopmentBulkPreview.forSeller(seller)!!.holidays)
    }

    @Test fun invalidHoursAndUnauthorizedMinimumChangeAreRejected() {
        val policy = DevelopmentBulkPreview.forSeller(seller)!!
        assertFalse(repo.savePolicy(staff, policy.copy(minimumOverride = 20), emptyList()))
        assertFalse(repo.savePolicy(owner, policy.copy(weeklyHours = mapOf(DayOfWeek.MONDAY to listOf(
            BulkTimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0)),
            BulkTimeWindow(LocalTime.of(11, 0), LocalTime.of(14, 0))))), emptyList()))
    }

    @Test fun scheduleConflictListsRequestWithoutRewritingQuote() {
        val record = DevelopmentSellerOperations.records.first { it.sellerId == seller &&
            it.thread.stage == QuoteStage.SUBMITTED }
        val before = record.latest
        val proposed = DevelopmentBulkPreview.forSeller(seller)!!.copy(
            temporaryClosures = setOf(before.pickup.toLocalDate()))
        assertTrue(record.reference in sellerPickupConflicts(listOf(record), seller, proposed,
            before.pickup.minusDays(1)))
        assertEquals(before, record.latest)
    }

    @Test fun managerAndStaffCapabilitiesAreNestedAndRevokeCascades() {
        assertTrue(SellerCapability.RESPOND in repo.capabilities(ops, seller))
        val managerRecord = repo.employees.first { it.id == manager }
        assertTrue(repo.saveEmployee(owner, managerRecord.copy(granted = managerRecord.granted - SellerCapability.RESPOND)))
        assertFalse(SellerCapability.RESPOND in repo.capabilities(ops, seller))
        assertTrue(SellerCapability.VIEW_REQUESTS in repo.capabilities(ops, seller))
    }

    @Test fun staffCannotSelfElevateAndManagerCannotGrantBeyondOwnScope() {
        val staffRecord = repo.employees.first { it.id == staff }
        assertFalse(repo.saveEmployee(staff, staffRecord.copy(granted = SellerCapability.entries.toSet())))
        assertFalse(repo.saveEmployee(manager, staffRecord.copy(granted = staffRecord.granted + SellerCapability.REPORTS)))
        assertFalse(repo.saveEmployee(manager, staffRecord.copy(role = SellerEmployeeRole.MANAGER)))
    }

    @Test fun managerCanAddDelegatedStaffAndOwnerCanDeactivate() {
        val added = SellerEmployee("new-staff", seller, "Demo helper", SellerEmployeeRole.STAFF, manager,
            setOf(SellerCapability.VIEW_REQUESTS, SellerCapability.AVAILABILITY))
        assertTrue(repo.saveEmployee(manager, added))
        assertTrue(SellerCapability.AVAILABILITY in repo.capabilities(added.id, seller))
        assertTrue(repo.removeEmployee(owner, seller, added.id))
        assertTrue(repo.capabilities(added.id, seller).isEmpty())
    }

    @Test fun permissionBoundaryRejectsCrossSellerAndOwnerMutation() {
        val ownerRecord = repo.employees.first { it.id == owner }
        assertFalse(repo.saveEmployee(owner, ownerRecord.copy(name = "Elevated")))
        assertFalse(repo.saveEmployee("debug-customer", repo.employees.first { it.id == staff }))
        assertFalse(repo.saveEmployee(owner, SellerEmployee("foreign", PreviewCatalog.MAIN_ID,
            "Foreign", SellerEmployeeRole.MANAGER, owner, setOf(SellerCapability.VIEW_REQUESTS))))
    }

    @Test fun promotionsAreSellerScopedAndCustomerPreviewUpdates() {
        val promotion = Promotion("north-test", seller, "Sample group lunch", "Demo only", PromotionKind.FEATURED,
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"), true,
            itemIds = setOf("north-lunch"))
        assertTrue(repo.savePromotion(owner, promotion))
        assertTrue(DevelopmentPromotions.promotions.any { it.id == promotion.id && it.published })
        assertFalse(repo.savePromotion(staff, promotion.copy(title = "Unauthorized")))
        assertFalse(repo.savePromotion(owner, promotion.copy(itemIds = setOf("masala-dosa"))))
        assertTrue(repo.savePromotion(owner, promotion.copy(published = false)))
        assertFalse(DevelopmentPromotions.presentable(now = Instant.parse("2026-10-22T00:00:00Z"))
            .any { it.id == promotion.id })
    }

    @Test fun promotionalDiscountValidationAndExpiry() {
        assertTrue(runCatching { Promotion("bad", seller, "Bad", "", PromotionKind.PERCENT_DISCOUNT,
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), true,
            discountValue = 101) }.isFailure)
        val offer = Promotion("fixed", seller, "Fixed", "", PromotionKind.FIXED_DISCOUNT,
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-05T00:00:00Z"), true,
            discountValue = 10)
        assertEquals(PromotionStatus.EXPIRED, offer.statusAt(Instant.parse("2026-10-22T00:00:00Z")))
    }

    @Test fun resetRestoresAllIntegratedFixtures() {
        assertTrue(repo.saveItem(owner, item()))
        assertTrue(repo.savePolicy(owner, DevelopmentBulkPreview.forSeller(seller)!!.copy(minimumOverride = 18), emptyList()))
        repo.reset()
        assertFalse(PreviewCatalog.items.any { it.id == "test-item" })
        assertEquals(8, DevelopmentBulkPreview.forSeller(seller)?.minimum)
        assertEquals(8, repo.employees.size)
        assertTrue(repo.employees.any { it.id == "demo-owner-main" && it.sellerId == "main-canteen" })
        assertTrue(repo.employees.any { it.id == "demo-owner-north" && it.sellerId == "north-counter" })
    }
}
