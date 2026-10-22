package edu.msrit.canteen.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.ui.components.RitBrandHeader
import edu.msrit.canteen.ui.components.RitIcon
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitMessageCard
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

/** Informational entry only. Seller access is granted by authorized server-side invitations. */
@Composable
fun SellerOnboardingEntryScreen(onBack: () -> Unit, onInvitedLogin: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp).testTag("seller_onboarding_entry")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(colors.warmCard, CircleShape)
                .clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                RitIcon(RitIconKind.Back, colors.foreground)
            }
            RitBrandHeader(Modifier.weight(1f).padding(start = 12.dp))
        }
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(88.dp).background(colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
            RitIcon(RitIconKind.Store, colors.accent, size = 42.dp)
        }
        Spacer(Modifier.height(26.dp))
        RitText("Seller onboarding", RitType.heading)
        Spacer(Modifier.height(8.dp))
        RitText("Seller accounts begin with an invitation, not a public sign-up.", RitType.body, colors.muted)
        Spacer(Modifier.height(26.dp))
        RitMessageCard("How access is granted",
            "A platform administrator creates or approves the seller business and invites its owner by verified email. An authorized owner may later invite managers and staff. Choosing this entry never grants seller access.")
        Spacer(Modifier.height(26.dp))
        RitPrimaryButton("I have an invitation · Login", onInvitedLogin,
            Modifier.fillMaxWidth().testTag("seller_invitation_login"), trailingIcon = RitIconKind.Arrow)
        Spacer(Modifier.height(12.dp))
        RitText("Email OTP and seller authorization are not connected yet. No seller account can be activated here.",
            RitType.small, colors.muted)
    }
}
