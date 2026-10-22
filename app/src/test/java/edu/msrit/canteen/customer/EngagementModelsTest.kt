package edu.msrit.canteen.customer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EngagementModelsTest {
    private val now = Instant.parse("2026-10-21T00:00:00Z")

    @Test fun promotionsRespectPublicationScheduleAndBehavioralConsent() {
        val visible = DevelopmentPromotions.presentable(now = now)
        assertFalse(visible.any { it.id == "unpublished-demo" })
        assertEquals(PromotionStatus.ACTIVE, visible.first { it.id == "dosa-spotlight" }.statusAt(now))
        assertEquals(PromotionStatus.EXPIRED, visible.first { it.id == "expired-coffee-demo" }.statusAt(now))
        val targeted = Promotion("targeted", PreviewCatalog.MAIN_ID, "Private demo", "Not public", PromotionKind.BANNER,
            now.minusSeconds(60), now.plusSeconds(60), true, audience = PromotionAudience.BEHAVIORAL)
        val source = object : PromotionSource { override val promotions = listOf(targeted) }
        assertTrue(source.presentable(now = now).isEmpty())
        assertEquals(1, source.presentable(PromotionPrivacy(behavioralConsentGranted = true, optedOut = false), now).size)
    }

    @Test fun sampleHistoryNeverConfersCurrentEligibility() {
        assertTrue(EmptyOrderHistory.orders.isEmpty())
        val lines = DevelopmentSampleHistory.orders.single().lines
        assertEquals(CurrentItemState.AVAILABLE_FOR_REVIEW, currentItemState(PreviewCatalog, lines.first()))
        assertEquals(CurrentItemState.REMOVED, currentItemState(PreviewCatalog, lines.last()))
        assertEquals(Price(45), lines.first().priceSnapshot)
        assertEquals(Price(50), PreviewCatalog.items.first { it.id == "masala-dosa" }.price)
    }
}
