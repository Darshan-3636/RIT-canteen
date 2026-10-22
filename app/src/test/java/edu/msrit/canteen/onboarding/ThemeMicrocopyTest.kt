package edu.msrit.canteen.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeMicrocopyTest {
    @Test fun bankCoversEveryThemeWithCuratedMetadata() {
        assertTrue(ThemeMicrocopy.lines.size in 30..40)
        assertEquals(ThemeId.entries.toSet(), ThemeMicrocopy.lines.map { it.theme }.toSet())
        assertEquals(ThemeMicrocopy.lines.size, ThemeMicrocopy.lines.map { it.id }.toSet().size)
        assertTrue(ThemeMicrocopy.lines.all { it.frequency > 0 })
    }

    @Test fun contextAndRecentUseConstrainSelection() {
        val line = ThemeMicrocopy.select(ThemeId.Minecraft, CopyContext.EmptySearch, CopyTrigger.NoResults, CopySurface.EmptyState, 10)!!
        assertEquals("mc2", line.id)
        assertNull(ThemeMicrocopy.select(ThemeId.Minecraft, CopyContext.EmptySearch, CopyTrigger.NoResults,
            CopySurface.EmptyState, 11, mapOf(line.id to 10)))
        assertEquals(line.id, ThemeMicrocopy.select(ThemeId.Minecraft, CopyContext.EmptySearch, CopyTrigger.NoResults,
            CopySurface.EmptyState, 15, mapOf(line.id to 10))?.id)
    }

    @Test fun criticalActionsHaveNoHumorousCopy() {
        assertTrue(ThemeMicrocopy.lines.none { it.context == CopyContext.Transaction ||
            it.trigger == CopyTrigger.Payment || it.trigger == CopyTrigger.Auth })
        assertNull(ThemeMicrocopy.select(ThemeId.Pubg, CopyContext.Transaction, CopyTrigger.Payment, CopySurface.Hint, 5))
        assertNull(ThemeMicrocopy.select(ThemeId.Turing, CopyContext.Discovery, CopyTrigger.Auth, CopySurface.Hint, 5))
    }
}
