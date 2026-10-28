package edu.msrit.canteen.customer

import edu.msrit.canteen.BuildConfig
import org.junit.Assume.assumeFalse
import edu.msrit.canteen.navigation.DevelopmentPreviewAccess
import edu.msrit.canteen.navigation.DevelopmentRole
import edu.msrit.canteen.navigation.ProtectedArea
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class DevelopmentInsightsTest {
    private val range = InsightRange(LocalDate.now(RIT_TIME_ZONE).minusDays(30), LocalDate.now(RIT_TIME_ZONE))
    // Exercise the isolated analytics fixture without granting a runtime session.
    private fun role(role: DevelopmentRole) =
        DevelopmentPreviewAccess.identities.first { it.role == role }.authorization.let { fixture ->
            fixture.copy(capabilitiesBySeller = fixture.capabilitiesBySeller.mapValues { (seller, grants) ->
                grants + DevelopmentSellerBusiness.capabilities(fixture.userId, seller).map { it.wire }
            })
        }
    @Before fun before() {
        assumeFalse("Synthetic insights are disabled in cloud mode", BuildConfig.RIT_CLOUD_DEMO)
        DevelopmentSellerBusiness.reset(); DevelopmentInsights.reset(); DevelopmentPreviewAccess.reset()
    }
    @After fun after() {
        if (!BuildConfig.RIT_CLOUD_DEMO) {
            DevelopmentSellerBusiness.reset(); DevelopmentInsights.reset(); DevelopmentPreviewAccess.reset()
        }
    }

    @Test fun ownerSeesOwnSyntheticSalesButNotAnotherSeller() {
        val owner = role(DevelopmentRole.SellerOwner)
        assertTrue(DevelopmentInsights.sales(owner, "north-counter", range, true).orders > 0)
        assertEquals(0, DevelopmentInsights.sales(owner, PreviewCatalog.MAIN_ID, range, true).orders)
        assertEquals(0, DevelopmentInsights.sales(owner, null, range, true).orders)
    }
    @Test fun adminSeesAggregateAndSellerBreakdown() {
        val admin = role(DevelopmentRole.PlatformAdministrator)
        val all = DevelopmentInsights.sales(admin, null, range, true)
        val sellers = PreviewCatalog.sellers.map { DevelopmentInsights.sales(admin, it.id, range, true) }
        assertTrue(all.orders > 40)
        assertEquals(sellers.sumOf { it.orders }, all.orders)
        assertEquals(sellers.sumOf { it.netPaise }, all.netPaise)
        assertEquals(all.netPaise / all.orders, all.averagePaise)
    }
    @Test fun demoDateWindowsAndBreakdownsAreInternallyConsistent() {
        val owner = role(DevelopmentRole.SellerOwner)
        val today = LocalDate.now(RIT_TIME_ZONE)
        val one = DevelopmentInsights.sales(owner, "north-counter", InsightRange(today, today), true)
        val seven = DevelopmentInsights.sales(owner, "north-counter", InsightRange(today.minusDays(6), today), true)
        val thirty = DevelopmentInsights.sales(owner, "north-counter", InsightRange(today.minusDays(29), today), true)
        assertTrue(one.orders > 0)
        assertTrue(seven.orders > one.orders)
        assertTrue(thirty.orders > seven.orders)
        assertEquals(thirty.netPaise, thirty.daily.sumOf { it.netPaise })
        assertEquals(thirty.netPaise, thirty.products.sumOf { it.netPaise })
        assertEquals(thirty.netPaise, thirty.categories.sumOf { it.netPaise })
        assertEquals(thirty.netPaise / thirty.orders, thirty.averagePaise)
        assertTrue(thirty.returningCustomers > 0)
    }
    @Test fun managerAndStaffNeedDelegatedAnalytics() {
        val manager = role(DevelopmentRole.SellerManager)
        val staff = role(DevelopmentRole.SellerStaff)
        assertEquals(0, DevelopmentInsights.sales(manager, "north-counter", range, true).orders)
        assertEquals(0, DevelopmentInsights.sales(staff, "north-counter", range, true).orders)
        val record = DevelopmentSellerBusiness.employees.first { it.id == "debug-manager" }
        assertTrue(DevelopmentSellerBusiness.saveEmployee("debug-owner", record.copy(
            granted = record.granted + SellerCapability.ANALYTICS)))
        assertTrue(DevelopmentInsights.sales(role(DevelopmentRole.SellerManager), "north-counter", range, true).orders > 0)
    }
    @Test fun noSampleAndUnpaidOperationsNeverBecomeSales() {
        val owner = role(DevelopmentRole.SellerOwner)
        assertEquals(0, DevelopmentInsights.sales(owner, "north-counter", range, false).orders)
        val before = DevelopmentInsights.sales(owner, "north-counter", range, true).netPaise
        assertTrue(DevelopmentSellerOperations.records.isNotEmpty())
        assertEquals(before, DevelopmentInsights.sales(owner, "north-counter", range, true).netPaise)
    }
    @Test fun catalogRenameFlowsIntoAnalyticsLabels() {
        val owner = role(DevelopmentRole.SellerOwner)
        val item = PreviewCatalog.items.first { it.id == "north-lunch" }
        assertTrue(DevelopmentSellerBusiness.saveItem("debug-owner", item.copy(name = "Renamed sample lunch")))
        assertEquals("Renamed sample lunch", DevelopmentInsights.sales(owner, "north-counter", range, true)
            .products.first { it.id == item.id }.label)
    }
    @Test fun reportsAreOwnerScopedAndNeverDownloadFiles() {
        val owner = role(DevelopmentRole.SellerOwner)
        val manager = role(DevelopmentRole.SellerManager)
        val selection = ReportSelection("north-counter", ReportType.SALES, range)
        assertFalse(DevelopmentInsights.saveReportPreview(manager, selection))
        assertTrue(DevelopmentInsights.saveReportPreview(owner, selection))
        assertEquals(2, DevelopmentInsights.reports(owner, "north-counter").size)
        assertTrue(DevelopmentInsights.reports(owner, "north-counter").all { it.sampleOnly })
        assertTrue(DevelopmentInsights.reports(manager, "north-counter").isEmpty())
    }
    @Test fun sellerCannotReadAdminDirectoriesOrCreateSeller() {
        val owner = role(DevelopmentRole.SellerOwner)
        assertTrue(DevelopmentInsights.sellers(owner).isEmpty())
        assertTrue(DevelopmentInsights.users(owner).isEmpty())
        assertFalse(DevelopmentInsights.createSellerDraft(owner, "Foreign seller"))
    }
    @Test fun adminSellerDraftAndAuditAreResettable() {
        val admin = role(DevelopmentRole.PlatformAdministrator)
        assertTrue(DevelopmentInsights.createSellerDraft(admin, "Sample new seller"))
        assertNotNull(DevelopmentInsights.sellers(admin).find { it.name == "Sample new seller" })
        assertTrue(DevelopmentInsights.audit(admin).any { it.action == "Seller setup draft" })
        DevelopmentInsights.reset()
        assertNull(DevelopmentInsights.sellers(admin).find { it.name == "Sample new seller" })
        assertEquals(1, DevelopmentInsights.audit(admin).size)
    }
    @Test fun supportAccessFiltersSellerAndAdminCanChangeLocalStatus() {
        val owner = role(DevelopmentRole.SellerOwner)
        val admin = role(DevelopmentRole.PlatformAdministrator)
        assertEquals(2, DevelopmentInsights.tickets(owner, "north-counter").size)
        assertNull(DevelopmentInsights.ticket(owner, "SAMPLE-T-103", "north-counter"))
        assertTrue(DevelopmentInsights.setTicketStatus(admin, "SAMPLE-T-103", TicketStatus.IN_PROGRESS))
        assertEquals(TicketStatus.IN_PROGRESS, DevelopmentInsights.ticket(admin, "SAMPLE-T-103")?.status)
    }
    @Test fun moderationAndNotesRequireAdmin() {
        val owner = role(DevelopmentRole.SellerOwner)
        val admin = role(DevelopmentRole.PlatformAdministrator)
        assertFalse(DevelopmentInsights.markModerationReviewed(owner, "SAMPLE-M-1"))
        assertFalse(DevelopmentInsights.saveInternalNote(owner, "SAMPLE-T-103", "not allowed"))
        assertTrue(DevelopmentInsights.markModerationReviewed(admin, "SAMPLE-M-1"))
        assertTrue(DevelopmentInsights.saveInternalNote(admin, "SAMPLE-T-103", "Local review"))
        assertTrue(DevelopmentInsights.audit(admin).size >= 2)
    }
    @Test fun localSupportCreationIsSellerScoped() {
        val owner = role(DevelopmentRole.SellerOwner)
        assertFalse(DevelopmentInsights.createSellerTicket(owner, PreviewCatalog.MAIN_ID, TicketCategory.FOOD, "Issue", "Detail"))
        assertTrue(DevelopmentInsights.createSellerTicket(owner, "north-counter", TicketCategory.FOOD, "Issue", "Detail"))
        assertEquals(3, DevelopmentInsights.tickets(owner, "north-counter").size)
    }
    @Test fun activeMockRoleSeparationStillHolds() {
        role(DevelopmentRole.PlatformAdministrator)
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Administrator))
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Seller("north-counter", "catalog.edit")))
        role(DevelopmentRole.Customer)
        assertFalse(DevelopmentPreviewAccess.allows(ProtectedArea.Administrator))
    }
}
