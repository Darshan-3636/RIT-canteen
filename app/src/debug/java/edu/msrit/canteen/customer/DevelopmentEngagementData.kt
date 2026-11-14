package edu.msrit.canteen.customer

import edu.msrit.canteen.BuildConfig
import java.time.Instant
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Explicit demo fixtures, never verified campus offers or real purchases. */
object DevelopmentPromotions : PromotionSource {
    private val initial = listOf(
        Promotion("dosa-spotlight", PreviewCatalog.MAIN_ID, "Masala Dosa moments · demo",
            "Illustrative 20% off selected dosas. Not applied to any real or demo checkout.", PromotionKind.PERCENT_DISCOUNT,
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z"), true,
            itemIds = setOf("masala-dosa"), discountValue = 20),
        Promotion("north-lunch-demo", "north-counter", "Group lunch feature · demo",
            "Illustrative seller banner for bulk-request discovery. No order or redemption is available.", PromotionKind.BANNER,
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-30T23:59:59Z"), true,
            itemIds = setOf("north-lunch"), categoryIds = setOf("combos")),
        Promotion("corner-coffee-demo", "coffee-corner", "Coffee break for groups · demo",
            "Illustrative ₹30 off an eligible bulk request. No automatic pricing or redemption.", PromotionKind.FIXED_DISCOUNT,
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z"), true,
            itemIds = setOf("corner-coffee"), discountValue = 30),
        Promotion("expired-coffee-demo", "coffee-corner", "Coffee feature · expired demo",
            "Past demonstration placement; it is not a current offer.", PromotionKind.FEATURED,
            Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"), true,
            itemIds = setOf("corner-coffee")),
        Promotion("unpublished-demo", PreviewCatalog.MAIN_ID, "Hidden demo", "Unpublished.", PromotionKind.BANNER,
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"), false),
    )
    override var promotions by mutableStateOf(if (BuildConfig.RIT_CLOUD_DEMO) emptyList() else initial)
        private set
    fun replaceDemo(value: List<Promotion>) { promotions = value }
    fun resetDemo() { promotions = if (BuildConfig.RIT_CLOUD_DEMO) emptyList() else initial }
}

object DevelopmentSampleHistory : OrderHistorySource {
    override val orders = listOf(PastOrderSnapshot("DEMO-HISTORY-01", Instant.parse("2026-09-20T12:00:00Z"), listOf(
        PastOrderLine("masala-dosa", PreviewCatalog.MAIN_ID, "Masala Dosa", Price(45)),
        PastOrderLine("retired-snack", "north-counter", "Retired Snack", Price(40)),
    )))
}
