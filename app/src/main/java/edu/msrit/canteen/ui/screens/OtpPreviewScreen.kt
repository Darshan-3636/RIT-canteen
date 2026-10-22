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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.auth.AuthInput
import edu.msrit.canteen.ui.components.RitBrandHeader
import edu.msrit.canteen.ui.components.RitIcon
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

/** Navigation installs this only in debug builds; no code is sent or accepted. */
@Composable
fun OtpPreviewScreen(onBack: () -> Unit, onProfilePreview: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    val focus = androidx.compose.runtime.remember { FocusRequester() }
    var digits by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(colors.warmCard)
                .clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back to email entry" },
                contentAlignment = Alignment.Center) { RitIcon(RitIconKind.Back, colors.foreground) }
            RitBrandHeader(Modifier.weight(1f).padding(start = 12.dp))
        }
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(102.dp).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
            RitIcon(RitIconKind.Mail, colors.accent, size = 45.dp)
        }
        Spacer(Modifier.height(24.dp))
        RitText("Enter OTP", RitType.heading)
        Spacer(Modifier.height(8.dp))
        RitText("Debug preview only. No verification email was sent.", RitType.body, colors.muted)
        Spacer(Modifier.height(30.dp))
        BasicTextField(
            value = digits,
            onValueChange = { digits = AuthInput.otpDigits(it); message = "" },
            modifier = Modifier.fillMaxWidth().height(62.dp).focusRequester(focus)
                .semantics { contentDescription = "Six-digit email verification code; ${digits.length} digits entered" },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = RitType.heading.copy(color = androidx.compose.ui.graphics.Color.Transparent),
            cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        repeat(6) { index ->
                            Box(
                                Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, if (index == digits.length) colors.accent else colors.outline, RoundedCornerShape(12.dp))
                                    .background(colors.surface), contentAlignment = Alignment.Center,
                            ) { RitText(digits.getOrNull(index)?.toString() ?: "–", RitType.title,
                                if (index < digits.length) colors.foreground else colors.muted) }
                        }
                    }
                    Box(Modifier.alpha(0f)) { inner() }
                }
            },
        )
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button) {
            message = "Resend unavailable: email delivery is not connected."
        }, contentAlignment = Alignment.Center) { RitText("Resend code", RitType.label, colors.accent) }
        Spacer(Modifier.height(12.dp))
        RitPrimaryButton("Verify & continue", onClick = {
            message = "Verification unavailable. No code can be accepted yet."
        }, modifier = Modifier.fillMaxWidth(), enabled = AuthInput.isOtpValid(digits), trailingIcon = RitIconKind.Arrow)
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RitText(message, RitType.small, colors.accent)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button, onClick = onProfilePreview),
            contentAlignment = Alignment.Center) {
            RitText("Preview profile setup · debug only", RitType.label, colors.accent)
        }
    }
}
