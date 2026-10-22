package edu.msrit.canteen.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.auth.AuthInput
import edu.msrit.canteen.ui.components.RitBrandHeader
import edu.msrit.canteen.ui.components.RitIcon
import edu.msrit.canteen.ui.components.RitIconKind
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

enum class DeclaredAffiliation { Student, Faculty }

/** Form preview stores nothing and never activates an account. */
@Composable
fun ProfileSetupPreviewScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = RitThemeTokens.colors
    var fullName by rememberSaveable { mutableStateOf("") }
    var department by rememberSaveable { mutableStateOf("") }
    var affiliation by rememberSaveable { mutableStateOf<DeclaredAffiliation?>(null) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var previewComplete by rememberSaveable { mutableStateOf(false) }
    var photoChosen by rememberSaveable { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        photoChosen = uri != null
    }
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 18.dp).testTag("profile_setup"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(colors.warmCard)
                .clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                RitIcon(RitIconKind.Back, colors.foreground)
            }
            RitBrandHeader(Modifier.weight(1f).padding(start = 12.dp))
        }
        Spacer(Modifier.height(30.dp))
        RitText("Almost there!", RitType.display.copy(textAlign = TextAlign.Center))
        Spacer(Modifier.height(8.dp))
        RitText("Set up your profile to get a personalized canteen experience.",
            RitType.body.copy(textAlign = TextAlign.Center), colors.muted)
        Spacer(Modifier.height(22.dp))
        Box(Modifier.size(96.dp).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
            RitIcon(RitIconKind.Person, colors.accent, size = 46.dp)
        }
        Box(Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button) { photoPicker.launch("image/*") },
            contentAlignment = Alignment.Center) {
            RitText(if (photoChosen) "Photo selected (optional)" else "Add Photo (optional)", RitType.label, colors.accent)
        }
        RitText("Helps others recognise you", RitType.small, colors.muted)
        Spacer(Modifier.height(22.dp))
        RitTextField(fullName, { fullName = it.take(100); attempted = false; previewComplete = false },
            "Full Name", "Enter your full name", Modifier.fillMaxWidth(), inputTag = "profile_full_name")
        if (attempted && !AuthInput.isFullNameValid(fullName)) {
            Spacer(Modifier.height(6.dp))
            RitText("Enter your first and last name.", RitType.small, colors.accent, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        RitTextField(department, { department = it.take(100) }, "Department (Optional)",
            "e.g. Computer Science", Modifier.fillMaxWidth(), inputTag = "profile_department")
        Spacer(Modifier.height(24.dp))
        RitText("I am a", RitType.title, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AffiliationCard("Student", "Currently studying at RIT", affiliation == DeclaredAffiliation.Student,
                Modifier.weight(1f).testTag("profile_student")) { affiliation = DeclaredAffiliation.Student; previewComplete = false }
            AffiliationCard("Faculty", "Teaching or working at RIT", affiliation == DeclaredAffiliation.Faculty,
                Modifier.weight(1f).testTag("profile_faculty")) { affiliation = DeclaredAffiliation.Faculty; previewComplete = false }
        }
        if (attempted && affiliation == null) {
            Spacer(Modifier.height(6.dp))
            RitText("Select Student or Faculty.", RitType.small, colors.accent, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        RitText("Affiliation is self-declared, not institutionally verified.", RitType.small, colors.muted)
        Spacer(Modifier.height(22.dp))
        RitPrimaryButton("Continue", onClick = {
            attempted = true
            previewComplete = AuthInput.isFullNameValid(fullName) && affiliation != null
        }, modifier = Modifier.fillMaxWidth().testTag("profile_continue"), trailingIcon = RitIconKind.Arrow)
        if (previewComplete) {
            Spacer(Modifier.height(10.dp))
            RitText("Profile preview complete. Account activation and Customer Home require real email verification and authentication.",
                RitType.small, colors.accent, Modifier.fillMaxWidth().testTag("profile_preview_complete"))
        }
        Spacer(Modifier.height(8.dp))
        RitText("Debug UI preview only. No account is created.", RitType.small, colors.muted)
    }
}

@Composable
private fun AffiliationCard(title: String, detail: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.height(118.dp).clip(shape).border(1.dp, if (selected) colors.accent else colors.outline, shape)
        .background(if (selected) colors.warmCard else colors.surface)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(14.dp)) {
        RitText(if (selected) "◉" else "○", RitType.title, colors.accent)
        Spacer(Modifier.height(8.dp))
        RitText(title, RitType.label)
        RitText(detail, RitType.small, colors.muted)
    }
}
