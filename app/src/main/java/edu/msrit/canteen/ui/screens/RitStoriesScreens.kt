package edu.msrit.canteen.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.onboarding.ThemeCatalog
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
private fun StoryLink(label: String, onClick: () -> Unit, enabled: Boolean = true, tag: String? = null) {
    val colors = RitThemeTokens.colors
    RitText(label, RitType.label, if (enabled) colors.foreground else colors.muted,
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .background(colors.warmCard).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .padding(horizontal = 18.dp, vertical = 17.dp))
}

@Composable
fun RitStoriesPreviewScreen(onBack: () -> Unit, onTheme: (edu.msrit.canteen.onboarding.ThemeId) -> Unit) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(20.dp)) {
        RitText("RIT Stories", RitType.heading)
        Spacer(Modifier.height(5.dp))
        RitText("Optional games and ideas gallery · development artwork", RitType.body, colors.muted)
        Spacer(Modifier.height(12.dp))
        StoryLink("Back to customer home", onBack)
        ThemeCatalog.all.forEach { theme ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { onTheme(theme.id) }.testTag("stories_${theme.id.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(theme.artwork), theme.description,
                    Modifier.size(width = 80.dp, height = 106.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    RitText(theme.title, RitType.title)
                    RitText(theme.family.name, RitType.small, colors.muted)
                    RitText(if (theme.cleanFullScreen) "Clean artwork" else "Legacy reference · not full-screen ready",
                        RitType.small, colors.muted)
                }
            }
        }
        RitText("Some legacy references retain embedded text or game HUD. They are shown uncropped for review, not cleared for publication.",
            RitType.small, colors.muted)
    }
}

@Composable
fun RitStoryDetailScreen(id: edu.msrit.canteen.onboarding.ThemeId, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    val artwork = ThemeCatalog.get(id)
    val note = edu.msrit.canteen.onboarding.ThemeMicrocopy.lines.firstOrNull {
        it.theme == id && it.context == edu.msrit.canteen.onboarding.CopyContext.Discovery
    }?.text
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())
        .padding(20.dp).testTag("story_detail_${id.name.lowercase()}")) {
        RitText(artwork.title, RitType.heading)
        Spacer(Modifier.height(5.dp))
        RitText("${artwork.family.name} · optional artwork", RitType.small, colors.muted)
        Spacer(Modifier.height(16.dp))
        Image(painterResource(artwork.artwork), artwork.description,
            Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Fit)
        Spacer(Modifier.height(14.dp))
        RitText(artwork.description, RitType.body)
        if (note != null) RitText(note, RitType.body, colors.accent, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(14.dp))
        RitText(if (artwork.cleanFullScreen) "Concept art; publication and licensing review still required."
            else "Legacy reference with embedded UI/text; not production-ready and shown without masking or reconstruction claims.",
            RitType.small, colors.muted)
        Spacer(Modifier.height(18.dp))
        StoryLink("Back to RIT Stories", onBack)
    }
}
