package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.R
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitIcon
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onGetStartedAsSeller: () -> Unit, onLogin: () -> Unit) {
    val colors = RitThemeTokens.colors
    Column(
        Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 22.dp).testTag("welcome_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.rit_logo), contentDescription = "RIT Canteen logo",
            modifier = Modifier.height(78.dp).testTag("welcome_logo"),
        )
        Spacer(Modifier.height(11.dp))
        RitText("RIT Canteen", RitType.heading)
        RitText("Good food. A closer campus.", RitType.body, colors.muted)
        Spacer(Modifier.height(36.dp))
        Box(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(28.dp))) {
            Image(
                painterResource(R.drawable.rit_welcome_food),
                contentDescription = "Prepared dosa with chutneys, concept photograph",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(32.dp))
        RitText("Your campus, served.", RitType.display.copy(textAlign = TextAlign.Center))
        Spacer(Modifier.height(10.dp))
        RitText(
            "Discover food at the main canteen, plan group orders, and explore participating stalls.",
            RitType.body.copy(textAlign = TextAlign.Center), colors.muted,
        )
        Spacer(Modifier.height(28.dp))
        RitPrimaryButton("Get Started", onGetStarted, Modifier.fillMaxWidth().testTag("welcome_get_started"),
            trailingIcon = RitIconKind.Arrow)
        RitText("Student and Faculty registration", RitType.small, colors.muted,
            Modifier.padding(top = 6.dp, bottom = 14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(RitRadii.card))
            .border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
            .clickable(role = Role.Button, onClick = onGetStartedAsSeller)
            .testTag("welcome_get_started_seller").padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RitIcon(RitIconKind.Store, colors.accent, size = 23.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                RitText("Get Started as Seller", RitType.label)
                RitText("For invited seller owners", RitType.small, colors.muted)
            }
            RitIcon(RitIconKind.Arrow, colors.muted, size = 18.dp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button, onClick = onLogin)
            .testTag("welcome_login"), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            RitText("Already have an account? ", RitType.body, colors.muted)
            RitText("Login", RitType.label, colors.accent)
        }
        Spacer(Modifier.height(16.dp))
    }
}
