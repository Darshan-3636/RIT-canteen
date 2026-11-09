package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.R
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onLogin: () -> Unit) {
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
        RitText("Are you craving for food?", RitType.title, colors.muted)
        Spacer(Modifier.height(24.dp))
        RitPrimaryButton("Log In", onLogin, Modifier.fillMaxWidth().testTag("welcome_login"),
            trailingIcon = RitIconKind.Arrow)
        Spacer(Modifier.height(12.dp))
        RitSecondaryButton("Register", onGetStarted,
            Modifier.fillMaxWidth().testTag("welcome_get_started"))
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(226.dp).clip(RoundedCornerShape(28.dp))) {
            Image(
                painterResource(R.drawable.rit_welcome_students),
                contentDescription = "Students sharing a meal in a campus canteen",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
