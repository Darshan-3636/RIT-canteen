package edu.msrit.canteen.customer

import edu.msrit.canteen.BuildConfig
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Fictional scheduling and quote data; never seller-confirmed business rules or orders. */
object DevelopmentBulkPreview : BulkPolicySource {
    override fun forSeller(sellerId: String): SellerBulkPolicy? {
        if (PreviewCatalog.sellers.none { it.id == sellerId }) return null
        DevelopmentSellerBusiness.policyOverrides[sellerId]?.let { return it }
        if (BuildConfig.RIT_CLOUD_DEMO) return null // Never invent operating hours for a connected seller.
        return defaultForSeller(sellerId)
    }

    private fun defaultForSeller(sellerId: String): SellerBulkPolicy {
        val today = LocalDate.now(RIT_TIME_ZONE)
        val weekdays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).associateWith {
            listOf(BulkTimeWindow(LocalTime.of(9, 0), LocalTime.of(17, 0)))
        }
        return SellerBulkPolicy(
            sellerId = sellerId,
            minimumOverride = PreviewCatalog.sellers.find { it.id == sellerId }?.bulkMinimumUnits,
            weeklyHours = weekdays + (DayOfWeek.SATURDAY to listOf(BulkTimeWindow(LocalTime.of(10, 0), LocalTime.of(14, 0)))),
            holidays = setOf(today.plusDays(4)),
            temporaryClosures = setOf(today.plusDays(6)),
        )
    }
}
