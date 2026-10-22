package edu.msrit.canteen.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.ui.components.RitBrandHeader
import edu.msrit.canteen.auth.AuthInput
import edu.msrit.canteen.auth.AuthEntryIntent
import edu.msrit.canteen.ui.components.RitLoadingIndicator
import edu.msrit.canteen.ui.components.RitIcon
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitMessageCard
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun EmailEntryScreen(entryIntent: AuthEntryIntent, onBack: () -> Unit,
                     onProfilePreview: (() -> Unit)? = null) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    var email by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag(if (entryIntent == AuthEntryIntent.Login) "login_screen" else "registration_email_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(colors.warmCard)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Back to welcome" },
                contentAlignment = Alignment.Center,
            ) { RitIcon(RitIconKind.Back, colors.foreground) }
            Spacer(Modifier.width(12.dp))
            RitBrandHeader(Modifier.weight(1f), logoModifier = Modifier.testTag("login_logo"))
        }
        Spacer(Modifier.height(48.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(102.dp).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                RitIcon(RitIconKind.Mail, colors.accent, size = 45.dp)
            }
        }
        Spacer(Modifier.height(26.dp))
        RitText(entryIntent.heading, RitType.heading)
        Spacer(Modifier.height(7.dp))
        RitText(entryIntent.helper, RitType.body, colors.muted)
        Spacer(Modifier.height(28.dp))
        RitTextField(
            value = email,
            onValueChange = { email = AuthInput.normalizeEmail(it).take(254); attempted = false },
            label = "Email address",
            placeholder = "you@example.com",
            modifier = Modifier.fillMaxWidth().testTag("email_address_input"),
            keyboardType = KeyboardType.Email,
            inputTag = "email_address_field",
        )
        if (email.isNotBlank() && !AuthInput.isEmailValid(email)) {
            Spacer(Modifier.height(7.dp))
            RitText("Enter a valid email address.", RitType.small, colors.accent)
        }
        Spacer(Modifier.height(16.dp))
        RitPrimaryButton(
            "Send email OTP",
            onClick = { attempted = true },
            modifier = Modifier.fillMaxWidth().testTag("email_otp_action"),
            enabled = AuthInput.isEmailValid(email),
            trailingIcon = RitIconKind.Arrow,
        )
        Spacer(Modifier.height(12.dp))
        RitText(
            if (attempted) "Email OTP service is unavailable. No code was sent."
            else "Email OTP delivery is not connected yet.",
            RitType.small,
            if (attempted) colors.accent else colors.muted,
        )
        Spacer(Modifier.height(24.dp))
        RitMessageCard(
            "About verification",
            if (entryIntent == AuthEntryIntent.Login)
                "After verified sign-in, the server will provide authorized customer, seller or administrator roles. Choosing Login does not grant a role."
            else "After verified email OTP, customer profile setup will collect your full name and one Student or Faculty selection. Affiliation remains self-declared until institutional integration is approved.",
        )
        if (entryIntent == AuthEntryIntent.GetStarted && onProfilePreview != null) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button, onClick = onProfilePreview)
                .testTag("registration_profile_preview"), contentAlignment = Alignment.Center) {
                RitText("Preview profile setup · debug only", RitType.label, colors.accent)
            }
            RitText("This bypasses no real OTP: it opens a form preview without creating an account.",
                RitType.small, colors.muted)
        }
    }
}
