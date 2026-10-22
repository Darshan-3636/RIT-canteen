package edu.msrit.canteen.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {
    private class FakePreference : OnboardingPreference {
        var completed = false
        override fun hasCompleted() = completed
        override fun markCompleted(): Boolean { completed = true; return true }
    }

    @Test fun firstLaunchAndRepeatLaunch() {
        val saved = FakePreference()
        val flow = OnboardingFlow(saved)
        assertTrue(flow.shouldShowOnboarding())
        assertTrue(flow.finish())
        assertTrue(saved.completed)
        assertFalse(OnboardingFlow(saved).shouldShowOnboarding())
    }

    @Test fun skipAndCompletionShareTheSamePersistedOutcome() {
        val skipped = FakePreference()
        assertTrue(OnboardingFlow(skipped).finish())
        assertFalse(OnboardingFlow(skipped).shouldShowOnboarding())
    }

    @Test fun threeFeaturePagesAndBoundedProgression() {
        assertEquals(listOf(SlideId.OrderFood, SlideId.BulkOrders, SlideId.ExploreSellers), OnboardingManifest.pages.map { it.id })
        assertEquals(listOf(ThemeId.Minecraft, ThemeId.Valorant, ThemeId.AmongUs), OnboardingManifest.pages.map { it.theme.id })
        assertTrue(OnboardingManifest.pages.all { it.theme.cleanFullScreen })
        assertEquals(1, OnboardingManifest.nextIndex(0))
        assertEquals(0, OnboardingManifest.previousIndex(0))
        assertEquals(0, OnboardingManifest.previousIndex(1))
        assertEquals(2, OnboardingManifest.nextIndex(2))
    }

    @Test fun allTenThemesRemainAvailableWithoutExpandingMandatoryFlow() {
        assertEquals(10, ThemeCatalog.all.size)
        assertEquals(5, ThemeCatalog.all.count { it.family == ThemeFamily.Game })
        assertEquals(5, ThemeCatalog.all.count { it.family == ThemeFamily.Education })
        assertEquals(3, OnboardingManifest.pages.size)
    }
}
