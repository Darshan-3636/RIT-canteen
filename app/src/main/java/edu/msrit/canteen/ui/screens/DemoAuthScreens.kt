package edu.msrit.canteen.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.BuildConfig
import edu.msrit.canteen.auth.AuthInput
import edu.msrit.canteen.auth.DemoAccount
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import edu.msrit.canteen.customer.CustomerAffiliation
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitMessageCard
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.components.RitIcon
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitDropdownField
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DemoLoginScreen(onBack: () -> Unit, onCreateAccount: () -> Unit,
                    onAuthenticated: (DemoAccount) -> Unit) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    var email by rememberSaveable { mutableStateOf("") }
    // Do not put a password into saved-instance-state or a log.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var forgot by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(colors.background).imePadding().testTag("demo_login_screen")) {
        CatalogHeader("Welcome back", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)) {
            RitText("Are you craving for food?", RitType.heading)
            Spacer(Modifier.height(28.dp))
            RitTextField(email, { email = it.take(254); error = null }, "Email address", "you@example.com",
                Modifier.fillMaxWidth(), KeyboardType.Email, "demo_login_email")
            Spacer(Modifier.height(12.dp))
            RitTextField(password, { password = it; error = null }, "Password", "Enter password",
                Modifier.fillMaxWidth(), KeyboardType.Password, "demo_login_password",
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingContent = { PasswordVisibility(visible, { visible = !visible }, "demo_login_visibility") })
            error?.let { RitText(it, RitType.body, colors.accent,
                Modifier.padding(vertical = 10.dp).testTag("demo_login_error")) }
            Spacer(Modifier.height(16.dp))
            RitPrimaryButton(if (busy) "Signing you in…" else "Sign In", {
                if (!busy) {
                    val submittedEmail = email
                    val submittedPassword = password
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            DevelopmentDemoAccounts.signIn(submittedEmail, submittedPassword)
                        }
                        busy = false
                        if (result.account != null) { password = ""; onAuthenticated(result.account) }
                        else error = result.error ?: "Could not sign in."
                    }
                }
            }, Modifier.fillMaxWidth().testTag("demo_login_submit"),
                enabled = !busy && email.isNotBlank() && password.isNotBlank())
            Spacer(Modifier.height(12.dp))
            RitSecondaryButton("Register", onCreateAccount,
                Modifier.fillMaxWidth().testTag("demo_login_create"))
            Spacer(Modifier.height(12.dp))
            RitSecondaryButton("Forgot password?", { forgot = !forgot },
                Modifier.fillMaxWidth().testTag("demo_login_forgot"))
            if (forgot) RitMessageCard(
                if (BuildConfig.RIT_CLOUD_DEMO) "Password reset unavailable"
                else "Demo passwords",
                if (BuildConfig.RIT_CLOUD_DEMO) "Password reset is not available yet. No reset email is sent."
                else "No reset email is sent. For a seeded demo account, use its supplied test password. For a locally registered account, register another email.")
        }
    }
}

@Composable
fun DemoSignupScreen(onBack: () -> Unit, onRegistered: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var affiliation by rememberSaveable { mutableStateOf(CustomerAffiliation.STUDENT) }
    var department by rememberSaveable { mutableStateOf("") }
    var validationAttempted by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(colors.background).imePadding().testTag("demo_signup_screen")) {
        CatalogHeader("Register", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)) {
            RitText("Are you craving for food?", RitType.heading)
            Spacer(Modifier.height(22.dp))
            RitTextField(name, { name = it.take(100); error = null }, "Full name", "First and last name",
                Modifier.fillMaxWidth(), inputTag = "demo_signup_name")
            if ((validationAttempted || name.isNotBlank()) && !AuthInput.isRegistrationNameValid(name)) {
                RitText(if (name.isBlank()) "Enter your full name." else "Use letters and spaces only; enter first and last name.",
                    RitType.small, colors.accent, Modifier.padding(top = 6.dp).testTag("demo_signup_name_error"))
            }
            Spacer(Modifier.height(10.dp))
            RitTextField(email, { email = it.take(254); error = null }, "Email address", "you@example.com",
                Modifier.fillMaxWidth(), KeyboardType.Email, "demo_signup_email")
            if ((validationAttempted || email.isNotBlank()) && !AuthInput.isEmailValid(email)) {
                RitText("Enter a valid email address.", RitType.small, colors.accent,
                    Modifier.padding(top = 6.dp).testTag("demo_signup_email_error"))
            }
            Spacer(Modifier.height(16.dp))
            RitText("I am a…", RitType.title)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CustomerAffiliation.STUDENT to "Student", CustomerAffiliation.FACULTY to "Staff").forEach { (option, label) ->
                    RitChip(label, affiliation == option,
                        { affiliation = option }, Modifier.testTag("demo_signup_${label.lowercase()}"))
                }
            }
            Spacer(Modifier.height(12.dp))
            RitDropdownField(
                value = department,
                onValueChange = { department = it },
                label = "Department (optional)",
                placeholder = "Select a department",
                options = registrationDepartments,
                modifier = Modifier.fillMaxWidth(),
                inputTag = "demo_signup_department",
            )
            Spacer(Modifier.height(10.dp))
            RitTextField(password, { password = it; error = null }, "Password", "At least 10 characters",
                Modifier.fillMaxWidth(), KeyboardType.Password, "demo_signup_password",
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingContent = { PasswordVisibility(visible, { visible = !visible }, "demo_signup_visibility") })
            Spacer(Modifier.height(10.dp))
            RitTextField(confirmation, { confirmation = it; error = null }, "Confirm password", "Repeat password",
                Modifier.fillMaxWidth(), KeyboardType.Password, "demo_signup_confirmation",
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingContent = { PasswordVisibility(visible, { visible = !visible }, "demo_signup_confirmation_visibility") })
            error?.let { RitText(it, RitType.body, colors.accent,
                Modifier.padding(vertical = 10.dp).testTag("demo_signup_error")) }
            Spacer(Modifier.height(16.dp))
            RitPrimaryButton(if (busy) "Registering…" else "Register", {
                if (!busy) {
                    validationAttempted = true
                    if (!AuthInput.isRegistrationNameValid(name) || !AuthInput.isEmailValid(email)) return@RitPrimaryButton
                    val submittedName = AuthInput.normalizeName(name)
                    val submittedEmail = AuthInput.normalizeEmail(email)
                    val submittedPassword = password
                    val submittedConfirmation = confirmation
                    val submittedAffiliation = affiliation
                    val submittedDepartment = department.trim()
                    password = ""; confirmation = ""
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            DevelopmentDemoAccounts.register(submittedName, submittedEmail, submittedPassword,
                                submittedConfirmation, submittedAffiliation, submittedDepartment)
                        }
                        busy = false
                        if (result.account != null) onRegistered()
                        else error = result.error ?: "Could not create this account."
                    }
                }
            }, Modifier.fillMaxWidth().testTag("demo_signup_submit"), enabled = !busy)
        }
    }
}

private val registrationDepartments = listOf(
    "Aerospace Engineering",
    "Architecture",
    "Artificial Intelligence & Data Science",
    "Artificial Intelligence & Machine Learning",
    "Biotechnology",
    "Chemical Engineering",
    "Chemistry",
    "Civil Engineering",
    "Computer Science & Engineering",
    "Computer Science & Engineering (AI & ML)",
    "Computer Science & Engineering (Cyber Security)",
    "Electronics & Communication Engineering",
    "Electronics & Instrumentation Engineering",
    "Electrical & Electronics Engineering",
    "Electronics & Telecommunication Engineering",
    "Humanities",
    "Industrial Engineering & Management",
    "Information Science & Engineering",
    "Mathematics",
    "Master of Computer Applications (MCA)",
    "Management Studies (MBA)",
    "Mechanical Engineering",
    "Medical Electronics Engineering",
    "Physics",
)

@Composable
private fun PasswordVisibility(visible: Boolean, onClick: () -> Unit, tag: String) {
    val colors = RitThemeTokens.colors
    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = if (visible) "Hide password" else "Show password" }
        .testTag(tag), contentAlignment = Alignment.Center) {
        RitIcon(if (visible) RitIconKind.EyeOff else RitIconKind.Eye, colors.muted)
    }
}
