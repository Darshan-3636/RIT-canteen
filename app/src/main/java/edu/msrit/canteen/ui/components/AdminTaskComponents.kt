package edu.msrit.canteen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

/** Platform-admin building blocks. Colours come only from the existing theme tokens; tone is always also spelled out in text. */

private fun Modifier.tagged(tag: String?) = if (tag == null) this else testTag(tag)

@Composable
fun AdminDemoBanner(modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Row(modifier.clip(RoundedCornerShape(RitRadii.pill)).background(colors.accentSoft)
        .padding(horizontal = 12.dp, vertical = 6.dp).testTag("admin_demo_banner"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(colors.accent))
        Spacer(Modifier.width(7.dp))
        RitText("DEMO DATA · local preview, no production authority", RitType.small, colors.accent)
    }
}

@Composable
fun AdminSectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null,
                       onAction: (() -> Unit)? = null, actionTag: String? = null) {
    val colors = RitThemeTokens.colors
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RitText(title, RitType.title, modifier = Modifier.weight(1f).semantics { heading() })
        if (actionLabel != null && onAction != null) {
            Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(RitRadii.pill))
                .clickable(role = Role.Button, onClick = onAction).tagged(actionTag).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center) { RitText(actionLabel, RitType.label, colors.accent) }
        }
    }
}

/** Warm summary card: one big number for "what needs me", one supporting line. */
@Composable
fun AdminHeroSummary(count: Int, headline: String, detail: String, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(RitRadii.hero)).background(colors.warmCard)
        .semantics(mergeDescendants = true) {}.testTag("admin_hero").padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(if (count > 0) colors.accent else colors.card), contentAlignment = Alignment.Center) {
            RitText(count.toString(), RitType.heading, if (count > 0) colors.onAccent else colors.foreground)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            RitText(headline, RitType.title)
            Spacer(Modifier.height(2.dp))
            RitText(detail, RitType.small, colors.muted)
        }
    }
}

/** Tappable count tile. A non-zero value of an action count is drawn in the accent colour. */
@Composable
fun AdminCountTile(value: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                   highlight: Boolean = value > 0, tag: String? = null) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Column(modifier.heightIn(min = 84.dp).clip(shape).border(1.dp, colors.outline, shape).background(colors.card)
        .clickable(role = Role.Button, onClick = onClick)
        .tagged(tag).padding(horizontal = 14.dp, vertical = 12.dp)) {
        RitText(value.toString(), RitType.heading, if (highlight) colors.accent else colors.foreground)
        RitText(label, RitType.small, colors.muted)
    }
}

/** Read-only figure tile (money or counts that are not drill-downs). */
@Composable
fun AdminValueTile(value: String, label: String, modifier: Modifier = Modifier, tag: String? = null) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Column(modifier.heightIn(min = 84.dp).clip(shape).border(1.dp, colors.outline, shape).background(colors.card)
        .semantics(mergeDescendants = true) {}.tagged(tag).padding(horizontal = 14.dp, vertical = 12.dp)) {
        RitText(value, RitType.title)
        Spacer(Modifier.height(2.dp))
        RitText(label, RitType.small, colors.muted)
    }
}

/** Service health tile: the status is always words, never colour alone. */
@Composable
fun AdminStatusTile(label: String, status: String, tone: AdminTone, modifier: Modifier = Modifier, tag: String? = null) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Column(modifier.heightIn(min = 84.dp).clip(shape).border(1.dp, colors.outline, shape).background(colors.card)
        .semantics(mergeDescendants = true) {}.tagged(tag).padding(horizontal = 14.dp, vertical = 12.dp)) {
        RitText(label, RitType.label)
        Spacer(Modifier.height(6.dp))
        AdminToneChip(status, tone)
    }
}

/** Audit timeline entry: time column, dot, then what changed. */
@Composable
fun AdminTimelineRow(time: String, title: String, detail: String, modifier: Modifier = Modifier, tag: String? = null) {
    val colors = RitThemeTokens.colors
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.tagged(tag).padding(vertical = 8.dp)) {
        RitText(time, RitType.small, colors.muted, Modifier.width(52.dp))
        Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(colors.accent))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            RitText(title, RitType.label)
            RitText(detail, RitType.small, colors.muted)
        }
    }
}

@Composable
fun AdminToneChip(label: String, tone: AdminTone, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.pill)
    val (background, text) = when (tone) {
        AdminTone.URGENT -> colors.accent to colors.onAccent
        AdminTone.ACTION -> colors.accentSoft to colors.accent
        AdminTone.NEUTRAL -> colors.warmCard to colors.foreground
        AdminTone.DONE -> colors.surface to colors.muted
    }
    Box(modifier.clip(shape).background(background).then(if (tone == AdminTone.DONE) Modifier.border(1.dp, colors.outline, shape) else Modifier)
        .padding(horizontal = 10.dp, vertical = 4.dp)) { RitText(label, RitType.small, text, maxLines = 1) }
}

/** One actionable item: leading icon, title, detail, status chip and time. Whole row is the touch target. */
@Composable
fun AdminTaskRow(title: String, detail: String, icon: RitIconKind, chipLabel: String, tone: AdminTone,
                 modifier: Modifier = Modifier, meta: String? = null, onClick: (() -> Unit)? = null, tag: String? = null) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Row(modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 64.dp).clip(shape)
        .border(1.dp, if (tone == AdminTone.URGENT) colors.accent else colors.outline, shape).background(colors.card)
        .then(if (onClick == null) Modifier.semantics(mergeDescendants = true) {} else Modifier.clickable(role = Role.Button, onClick = onClick))
        .tagged(tag).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (tone == AdminTone.DONE) colors.surface else colors.accentSoft),
            contentAlignment = Alignment.Center) { RitIcon(icon, if (tone == AdminTone.DONE) colors.muted else colors.accent, size = 20.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // Wraps the time under the chip at large text sizes instead of truncating it.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                AdminToneChip(chipLabel, tone)
                if (meta != null) RitText(meta, RitType.small, colors.muted, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            RitText(title, RitType.label)
            RitText(detail, RitType.small, colors.muted)
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            RitIcon(RitIconKind.Arrow, colors.muted, size = 18.dp)
        }
    }
}

@Composable
fun AdminQuickAction(label: String, icon: RitIconKind, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = null) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Row(modifier.heightIn(min = 56.dp).clip(shape).border(1.dp, colors.outline, shape).background(colors.card)
        .clickable(role = Role.Button, onClick = onClick).tagged(tag).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RitIcon(icon, colors.accent, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        RitText(label, RitType.label, modifier = Modifier.weight(1f))
    }
}

/** Two equal-width, equal-height cells per row; keeps tiles readable at 360 dp and 1.3× text. */
@Composable
fun <T> AdminTwoColumn(items: List<T>, modifier: Modifier = Modifier, cell: @Composable (T, Modifier) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { cell(it, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Directory row with an initial avatar and descriptive chips. */
@Composable
fun AdminDirectoryRow(title: String, subtitle: String, chips: List<Pair<String, AdminTone>>, modifier: Modifier = Modifier,
                      onClick: (() -> Unit)? = null, tag: String? = null) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Row(modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 64.dp).clip(shape).border(1.dp, colors.outline, shape)
        .background(colors.card)
        .then(if (onClick == null) Modifier.semantics(mergeDescendants = true) {} else Modifier.clickable(role = Role.Button, onClick = onClick))
        .tagged(tag).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(colors.warmCard), contentAlignment = Alignment.Center) {
            RitText(title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", RitType.title, colors.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            RitText(title, RitType.label)
            RitText(subtitle, RitType.small, colors.muted)
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips.forEach { (label, tone) -> AdminToneChip(label, tone) } }
            }
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            RitIcon(RitIconKind.Arrow, colors.muted, size = 18.dp)
        }
    }
}

/** Plain information card for boundaries and read-only facts. */
@Composable
fun AdminInfoCard(title: String, detail: String, modifier: Modifier = Modifier, tag: String? = null) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Column(modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).border(1.dp, colors.outline, shape).background(colors.card)
        .semantics(mergeDescendants = true) {}.tagged(tag).padding(16.dp)) {
        RitText(title, RitType.label)
        Spacer(Modifier.height(2.dp))
        RitText(detail, RitType.small, colors.muted)
    }
}

@Composable
fun AdminDeniedState(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("admin_access_denied"), horizontalAlignment = Alignment.CenterHorizontally) {
        RitEmptyState("Administrator access required",
            "This area is only for the platform administrator. Seller owners, managers, staff and customers can't open it.")
        RitSecondaryButton("Go back", onBack, Modifier.fillMaxWidth())
    }
}
