package edu.msrit.canteen.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.navigation.AuthorizedIdentity
import edu.msrit.canteen.navigation.ModeSelectionPolicy
import edu.msrit.canteen.ui.components.RitBrandHeader
import edu.msrit.canteen.ui.components.RitIcon
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

/** The two approved visual choices are never role grants. Null identity in current debug preview denies both. */
@Composable
fun ModeSelectionPreviewScreen(identity: AuthorizedIdentity?, onBack: () -> Unit, onCustomer: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    var selected by rememberSaveable { mutableStateOf("customer") }
    var message by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())
        .padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(colors.warmCard, CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center) { RitIcon(RitIconKind.Back, colors.foreground) }
            RitBrandHeader(Modifier.weight(1f).padding(start = 12.dp))
        }
        Spacer(Modifier.height(28.dp))
        RitText("Choose how you want\nto use the app", RitType.display)
        Spacer(Modifier.height(12.dp))
        RitText("Pick a mode to continue. You can always change this later from your profile.", RitType.body, colors.muted)
        Spacer(Modifier.height(28.dp))
        ModeCard("Order Food", "Browse campus canteens, explore menus and place orders.", RitIconKind.Food,
            selected == "customer", Modifier.fillMaxWidth()) { selected = "customer"; message = "" }
        Spacer(Modifier.height(14.dp))
        ModeCard("Manage a Stall", "List your menu, manage orders and grow your stall.", RitIconKind.Store,
            selected == "seller", Modifier.fillMaxWidth()) { selected = "seller"; message = "" }
        Spacer(Modifier.height(20.dp))
        RitPrimaryButton("Continue", onClick = {
            when (selected) {
                "customer" -> if (ModeSelectionPolicy.canEnterCustomer(identity)) onCustomer()
                    else message = "Customer mode requires a verified account. This preview grants no access."
                "seller" -> message = if (ModeSelectionPolicy.canEnterSeller(identity))
                    "Seller mode requires a server-authorized membership and is not connected yet."
                    else "Seller access requires an authorized invitation. This choice grants no role."
            }
        }, modifier = Modifier.fillMaxWidth(), trailingIcon = RitIconKind.Arrow)
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RitText(message, RitType.small, colors.accent)
        }
        Spacer(Modifier.height(12.dp))
        RitText("Debug UI preview only. No account or role is created.", RitType.small, colors.muted)
    }
}

@Composable
private fun ModeCard(title: String, detail: String, icon: RitIconKind, selected: Boolean,
    modifier: Modifier, onClick: () -> Unit) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.height(170.dp).border(1.dp, if (selected) colors.accent else colors.outline, shape)
        .background(if (selected) colors.warmCard else colors.surface, shape)
        .clickable(role = Role.RadioButton, onClick = onClick).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(54.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
            RitIcon(icon, colors.onAccent, size = 28.dp)
        }
        Spacer(Modifier.height(10.dp))
        RitText(title, RitType.title)
        Spacer(Modifier.height(4.dp))
        RitText(detail, RitType.small.copy(textAlign = TextAlign.Center), colors.muted)
    }
}
