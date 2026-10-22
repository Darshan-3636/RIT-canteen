package edu.msrit.canteen.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import edu.msrit.canteen.R
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun RitBrandHeader(
    modifier: Modifier = Modifier,
    subtitle: String = "Good food. A closer campus.",
    onTrailingClick: (() -> Unit)? = null,
    logoModifier: Modifier = Modifier,
) {
    val colors = RitThemeTokens.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.rit_logo), contentDescription = "RIT logo",
            modifier = Modifier.size(width = 36.dp, height = 46.dp).then(logoModifier))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            RitText("RIT Canteen", RitType.title)
            RitText(subtitle, RitType.small, colors.muted)
        }
        if (onTrailingClick != null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(colors.warmCard)
                    .clickable(role = Role.Button, onClick = onTrailingClick)
                    .semantics { contentDescription = "Profile" },
                contentAlignment = Alignment.Center,
            ) { RitIcon(RitIconKind.Person, colors.foreground) }
        }
    }
}

@Composable
fun RitQuickEntry(
    title: String,
    subtitle: String,
    icon: RitIconKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = RitThemeTokens.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RitRadii.card))
            .background(colors.warmCard)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 132.dp)
            .padding(horizontal = 5.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
            RitIcon(icon, colors.onAccent, size = 23.dp)
        }
        Spacer(Modifier.height(6.dp))
        RitText(title, RitType.quickTitle.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth(), maxLines = 2)
        Spacer(Modifier.height(2.dp))
        RitText(subtitle, RitType.quickCaption.copy(textAlign = TextAlign.Center), colors.muted, modifier = Modifier.fillMaxWidth(), maxLines = 2)
    }
}

@Composable
fun RitFoodPreviewCard(name: String, caption: String, @DrawableRes image: Int, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RitRadii.small))
            .border(1.dp, colors.outline, RoundedCornerShape(RitRadii.small))
            .background(colors.card),
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = name + " concept photograph",
            modifier = Modifier.fillMaxWidth().height(95.dp),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(8.dp)) {
            RitText(name, RitType.label, maxLines = 1)
            RitText(caption, RitType.small, colors.muted, maxLines = 2)
        }
    }
}

@Composable
fun RitListRow(title: String, subtitle: String, icon: RitIconKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(RitRadii.small))
            .background(colors.card).clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RitIcon(icon, colors.accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            RitText(title, RitType.label)
            RitText(subtitle, RitType.small, colors.muted)
        }
        RitIcon(RitIconKind.Arrow, colors.muted, size = 18.dp)
    }
}

@Composable
fun RitLoadingIndicator(modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    val transition = rememberInfiniteTransition(label = "RIT loading")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "Spinner rotation",
    )
    Canvas(modifier.size(36.dp)) {
        drawArc(colors.accent, startAngle = angle.value, sweepAngle = 265f, useCenter = false, style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable
fun RitStatusIndicator(label: String, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Row(modifier = modifier.clip(RoundedCornerShape(RitRadii.pill)).background(colors.accentSoft).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(colors.accent))
        Spacer(Modifier.width(7.dp))
        RitText(label, RitType.small, colors.accent)
    }
}

@Composable
fun RitMessageCard(title: String, detail: String, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(RitRadii.card))
            .background(colors.warmCard).padding(16.dp),
    ) {
        RitText(title, RitType.label)
        Spacer(Modifier.height(4.dp))
        RitText(detail, RitType.small, colors.muted)
    }
}

@Composable
fun RitEmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    Column(modifier = modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
            RitIcon(RitIconKind.Search, colors.accent)
        }
        Spacer(Modifier.height(14.dp))
        RitText(title, RitType.title)
        Spacer(Modifier.height(5.dp))
        RitText(detail, RitType.body, colors.muted)
    }
}

enum class RitBottomItem(val label: String, val icon: RitIconKind) {
    Home("Home", RitIconKind.Home), Cart("Cart", RitIconKind.Cart), Orders("Orders", RitIconKind.Orders), Favourites("Saved", RitIconKind.Heart), Profile("Profile", RitIconKind.Person)
}

@Composable
fun RitBottomNavigation(selected: RitBottomItem, onSelect: (RitBottomItem) -> Unit, modifier: Modifier = Modifier,
                        cartCount: Int = 0) {
    val colors = RitThemeTokens.colors
    Row(
        modifier = modifier.fillMaxWidth().border(1.dp, colors.outline).background(colors.surface).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RitBottomItem.entries.forEach { item ->
            val active = item == selected
            Column(
                modifier = Modifier.weight(1f).heightIn(min = 54.dp).clickable(role = Role.Tab) { onSelect(item) }
                    .semantics { stateDescription = if (active) "Selected" else "Not selected" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box {
                    RitIcon(item.icon, if (active) colors.accent else colors.muted, size = 23.dp)
                    if (item == RitBottomItem.Cart && cartCount > 0) {
                        Box(Modifier.padding(start = 15.dp).size(18.dp).background(colors.accent, CircleShape),
                            contentAlignment = Alignment.Center) {
                            RitText(if (cartCount > 99) "99+" else "$cartCount", RitType.small, colors.surface)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                RitText(item.label, RitType.small, if (active) colors.accent else colors.muted)
            }
        }
    }
}
