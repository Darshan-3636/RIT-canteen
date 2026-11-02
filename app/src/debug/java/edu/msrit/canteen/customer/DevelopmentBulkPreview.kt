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

    fun sample(stage: QuoteStage, now: LocalDateTime = LocalDateTime.now(RIT_TIME_ZONE)): QuoteThread? {
        if (BuildConfig.RIT_CLOUD_DEMO) return null
        val pickup = (1L..14L).asSequence()
            .map { now.plusDays(it).withHour(11).withMinute(30).withSecond(0).withNano(0) }
            .first { pickupWindow(forSeller(PreviewCatalog.MAIN_ID), it, now) == PickupWindow.REGULAR }
        val original = QuoteRevision(1, QuoteActor.CUSTOMER, listOf(
            QuoteLineSnapshot("masala-dosa", "Masala Dosa", 3, 5_000),
            QuoteLineSnapshot("water-bottle", "Bottled Water", 5, 2_000),
        ), pickup, false, 25_000, "Group lunch request · demonstration", now.minusHours(2))
        val sellerCounter = QuoteRevision(2, QuoteActor.SELLER, listOf(
            QuoteLineSnapshot("veg-biryani", "Veg Biryani", 3, 8_000),
            QuoteLineSnapshot("water-bottle", "Bottled Water", 6, 2_000),
        ), pickup.plusMinutes(30), false, 36_000,
            "Demo seller suggests biryani instead of dosa, one extra water and a later pickup.", now.minusHours(1))
        val revisions = if (stage == QuoteStage.SUBMITTED) listOf(original) else listOf(original, sellerCounter)
        return QuoteThread(
            sellerId = PreviewCatalog.MAIN_ID,
            reference = "DEMO-BULK-01",
            revisions = revisions,
            stage = stage,
            sellerAcceptedVersion = if (stage == QuoteStage.AGREED_AWAITING_PAYMENT) 2 else null,
            customerAcceptedVersion = if (stage == QuoteStage.AGREED_AWAITING_PAYMENT) 2 else null,
            expiresAt = when (stage) {
                QuoteStage.AGREED_AWAITING_PAYMENT -> now.plusHours(2)
                QuoteStage.EXPIRED -> now.minusMinutes(1)
                else -> null
            },
        )
    }
}
