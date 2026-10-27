package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.auth.DemoAccount
import edu.msrit.canteen.customer.AppearanceChoice
import edu.msrit.canteen.navigation.DevelopmentRole
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitMessageCard
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.AppearancePreference
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun DemoRoleAccountScreen(account: DemoAccount, appearance: AppearancePreference,
                          onBack: () -> Unit, onDashboard: () -> Unit, onSettings: () -> Unit,
                          onSupport: () -> Unit, onLogout: () -> Unit) {
    val colors = RitThemeTokens.colors
    val role = when (account.role) {
        DevelopmentRole.SellerOwner -> "Seller owner"
        DevelopmentRole.SellerManager -> "Seller manager"
        DevelopmentRole.SellerStaff, DevelopmentRole.SellerStaffOperations -> "Seller staff"
        DevelopmentRole.PlatformAdministrator -> "Platform administrator"
        DevelopmentRole.Customer -> "Customer"
    }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("demo_role_account")) {
        CatalogHeader("My account", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                    RitText(account.name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase(),
                        RitType.title, colors.accent)
                }
                Column(Modifier.weight(1f)) {
                    RitText(account.name, RitType.heading)
                    RitText(role, RitType.body, colors.accent)
                }
            }
            Spacer(Modifier.height(14.dp))
            RitText(account.email, RitType.body, colors.muted)
            account.sellerId?.let { RitText("Seller: ${if (it == "main-canteen") "Main Canteen" else "North Counter"}",
                RitType.body, colors.muted) }
            Spacer(Modifier.height(20.dp))
            RitMessageCard("Demo account", "This is a local testing identity. Seller and platform actions are previews, not server-authorized changes.")
            Spacer(Modifier.height(16.dp))
            RitSecondaryButton("Open dashboard", onDashboard, Modifier.fillMaxWidth().testTag("role_account_dashboard"))
            Spacer(Modifier.height(9.dp))
            RitSecondaryButton("Settings", onSettings, Modifier.fillMaxWidth().testTag("role_account_settings"))
            Spacer(Modifier.height(9.dp))
            RitSecondaryButton("Help & Support", onSupport, Modifier.fillMaxWidth().testTag("role_account_support"))
            Spacer(Modifier.height(18.dp))
            RitText("Appearance", RitType.title)
            AppearanceChoice.entries.forEach { choice ->
                RitSecondaryButton("${if (appearance.choice == choice) "✓ " else ""}${choice.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    { appearance.choose(choice) }, Modifier.fillMaxWidth().padding(top = 8.dp)
                        .testTag("role_appearance_${choice.name.lowercase()}"))
            }
            Spacer(Modifier.height(24.dp))
            RitPrimaryButton("Log out", onLogout, Modifier.fillMaxWidth().testTag("role_account_logout"))
        }
    }
}
