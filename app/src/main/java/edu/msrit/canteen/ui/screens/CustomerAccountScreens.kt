package edu.msrit.canteen.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import edu.msrit.canteen.customer.DebugCustomerAccountApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import edu.msrit.canteen.customer.AppearanceChoice
import edu.msrit.canteen.customer.BulkDraftSession
import edu.msrit.canteen.customer.CustomerAccountRepository
import edu.msrit.canteen.customer.CustomerAffiliation
import edu.msrit.canteen.customer.CustomerIssueCategory
import edu.msrit.canteen.customer.CustomerProfile
import edu.msrit.canteen.customer.CustomerSupportTicket
import edu.msrit.canteen.customer.ItemEligibility
import edu.msrit.canteen.customer.PreviewCatalog
import edu.msrit.canteen.customer.itemEligibility
import edu.msrit.canteen.customer.validateBulkTemplate
import edu.msrit.canteen.customer.visibleItems
import edu.msrit.canteen.customer.NamedBulkTemplate
import edu.msrit.canteen.customer.validateCustomerProfile
import edu.msrit.canteen.customer.validateCustomerTicket
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitBottomItem
import edu.msrit.canteen.ui.components.RitBottomNavigation
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitMessageCard
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitStatusIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.AppearancePreference
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
private fun CustomerShell(title: String, tag: String, onBack: () -> Unit,
                          bottom: ((RitBottomItem) -> Unit)? = null, cartCount: Int = 0,
                          content: @Composable () -> Unit) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxSize().background(colors.background).testTag(tag)) {
        CatalogHeader(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)) {
            RitStatusIndicator("Development preview · no verified customer account")
            Spacer(Modifier.height(16.dp)); content(); Spacer(Modifier.height(36.dp))
        }
        if (bottom != null) RitBottomNavigation(RitBottomItem.Profile, bottom, cartCount = cartCount)
    }
}

@Composable
private fun AccountRow(title: String, detail: String, onClick: (() -> Unit)? = null, tag: String? = null) {
    val colors = RitThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)
        .border(1.dp, colors.outline, RoundedCornerShape(RitRadii.card))
        .clip(RoundedCornerShape(RitRadii.card)).background(colors.card)
        .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
        .then(if (tag == null) Modifier else Modifier.testTag(tag)).padding(16.dp)) {
        RitText(title, RitType.label)
        RitText(detail, RitType.small, colors.muted)
    }
}

@Composable
private fun CustomerAvatar(profile: CustomerProfile) {
    val context = LocalContext.current
    val bitmap = remember(profile.photoUri) { profile.photoUri?.let { value -> runCatching {
        context.contentResolver.openInputStream(value.toUri())?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
        }
    }.getOrNull() } }
    val colors = RitThemeTokens.colors
    if (bitmap != null) Image(bitmap.asImageBitmap(), "Locally selected profile photograph",
        Modifier.size(84.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    else Box(Modifier.size(84.dp).clip(CircleShape).background(colors.accentSoft)
        .semantics { contentDescription = "Profile photo not set" }, contentAlignment = Alignment.Center) {
        RitText(profile.fullName.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase(),
            RitType.title, colors.accent)
    }
}

@Composable
fun CustomerProfileScreen(repo: CustomerAccountRepository, onBack: () -> Unit, onBottom: (RitBottomItem) -> Unit,
                          onEdit: () -> Unit, onFavorites: () -> Unit, onQuickReorder: () -> Unit, onOrders: () -> Unit,
                          onTemplates: () -> Unit, onSupport: () -> Unit, onSettings: () -> Unit,
                          onStories: () -> Unit, onNotifications: () -> Unit, cartCount: Int = 0,
                          onAccount: (() -> Unit)? = null) {
    val colors = RitThemeTokens.colors
    CustomerShell("My profile", "customer_profile", onBack, onBottom, cartCount) {
        val profile = repo.profile
        if (profile == null) RitEmptyState("No customer profile", "Sign in with verified email when authentication becomes available.")
        else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CustomerAvatar(profile)
                Column(Modifier.weight(1f)) {
                    RitText(profile.fullName, RitType.heading)
                    RitText(profile.email, RitType.small, colors.muted)
                    RitText("${profile.affiliation.name.lowercase().replaceFirstChar { it.uppercase() }} · self-reported demo",
                        RitType.small, colors.muted)
                }
            }
            if (profile.department.isNotBlank()) RitText(profile.department, RitType.body, colors.muted,
                Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(18.dp))
            AccountRow("Edit profile", "Name, affiliation, optional department and local photo", onEdit, "profile_edit")
        }
        AccountRow("Orders and requests", "Unpaid requests are not paid orders", onOrders, "profile_orders")
        AccountRow("Favourites", "Saved demo items and current eligibility", onFavorites, "profile_favorites")
        AccountRow("Quick Reorder", "Review past items before adding them to a draft", onQuickReorder, "profile_quick_reorder")
        AccountRow("My bulk-order templates", "One seller per template", onTemplates, "profile_templates")
        AccountRow("Notifications", "Inbox preview and delivery boundary", onNotifications, "profile_notifications")
        AccountRow("Help & Support", "Issue categories and local ticket previews", onSupport, "profile_support")
        AccountRow("Settings & privacy", "Appearance, alerts and consent", onSettings, "profile_settings")
        if (onAccount != null) AccountRow("Account & sign out", "Switch to another account safely",
            onAccount, "profile_account")
        AccountRow("RIT Stories", "Optional games and ideas gallery", onStories, "profile_stories")
    }
}

@Composable
fun CustomerProfileEditScreen(repo: CustomerAccountRepository, userId: String?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val original = repo.profile ?: return
    var name by remember(original) { mutableStateOf(original.fullName) }
    var affiliation by remember(original) { mutableStateOf(original.affiliation) }
    var department by remember(original) { mutableStateOf(original.department) }
    var photoUri by remember(original) { mutableStateOf(original.photoUri) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri.toString()
    }
    val colors = RitThemeTokens.colors
    CustomerShell("Edit profile", "customer_profile_edit", onBack) {
        RitText("Profile details are saved on the local backend. Email and seller/admin roles cannot be changed here.",
            RitType.body, colors.muted)
        Spacer(Modifier.height(16.dp))
        CustomerAvatar(original.copy(fullName = name, photoUri = photoUri))
        Spacer(Modifier.height(10.dp))
        RitSecondaryButton("Choose local photograph", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        RitTextField(name, { name = it; message = null }, "Full name", "Your name", Modifier.fillMaxWidth(), inputTag = "edit_profile_name")
        Spacer(Modifier.height(12.dp))
        RitText("I am a…", RitType.title)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CustomerAffiliation.entries.forEach { option ->
                RitChip(option.name.lowercase().replaceFirstChar { it.uppercase() }, affiliation == option,
                    { affiliation = option }, Modifier.testTag("edit_profile_${option.name.lowercase()}"))
            }
        }
        Spacer(Modifier.height(12.dp))
        RitTextField(department, { department = it.take(100) }, "Department (optional)", "Your department",
            Modifier.fillMaxWidth(), inputTag = "edit_profile_department")
        Spacer(Modifier.height(8.dp))
        RitText("Affiliation is self-reported until institutional verification exists.", RitType.small, colors.muted)
        message?.let { RitText(it, RitType.body, colors.accent, Modifier.padding(vertical = 10.dp).testTag("profile_edit_feedback")) }
        Spacer(Modifier.height(16.dp))
        RitPrimaryButton("Save profile", {
            val updated = original.copy(fullName = name, affiliation = affiliation, department = department, photoUri = photoUri)
            message = validateCustomerProfile(updated)
            if (message == null) scope.launch {
                val result = withContext(Dispatchers.IO) { DebugCustomerAccountApi.updateProfile(updated) }
                if (result.profile != null) {
                    repo.updateProfile(result.profile.copy(photoUri = photoUri), userId)
                    message = "Saved on local backend. Photo remains on this device."
                } else message = result.error ?: "Profile update failed."
            }
        }, Modifier.fillMaxWidth().testTag("profile_save"))
    }
}

@Composable
fun CustomerSettingsScreen(onBack: () -> Unit, onAppearance: () -> Unit, onNotifications: () -> Unit,
                           onPrivacy: () -> Unit, onAccount: () -> Unit, onSupport: () -> Unit) {
    CustomerShell("Settings", "customer_settings", onBack) {
        AccountRow("Appearance", "Light, dark or system", onAppearance, "settings_appearance")
        AccountRow("Notification preferences", "Operational and promotional alerts", onNotifications, "settings_notifications")
        AccountRow("Personalization & privacy", "Optional behaviour processing is off by default", onPrivacy, "settings_privacy")
        AccountRow("Account and sessions", "Email, data requests and sign-out boundary", onAccount, "settings_account")
        AccountRow("Help & Support", "Issues and questions", onSupport, "settings_support")
    }
}

@Composable
fun CustomerAppearanceScreen(appearance: AppearancePreference, onBack: () -> Unit) {
    CustomerShell("Appearance", "customer_appearance", onBack) {
        RitText("Choose how this device displays RIT Canteen.", RitType.body)
        Spacer(Modifier.height(16.dp))
        AppearanceChoice.entries.forEach { option ->
            AccountRow("${if (appearance.choice == option) "✓ " else ""}${option.name.lowercase().replaceFirstChar { it.uppercase() }}",
                when (option) { AppearanceChoice.SYSTEM -> "Follow Android system setting"; AppearanceChoice.LIGHT -> "Bright surfaces"; AppearanceChoice.DARK -> "Deep dark surfaces" },
                { appearance.choose(option) }, "appearance_${option.name.lowercase()}")
        }
    }
}

@Composable
fun CustomerPreferenceScreen(repo: CustomerAccountRepository, userId: String?, privacy: Boolean, onBack: () -> Unit) {
    val colors = RitThemeTokens.colors
    val prefs = repo.preferences
    CustomerShell(if (privacy) "Personalization & privacy" else "Notification preferences",
        if (privacy) "customer_privacy" else "customer_notification_preferences", onBack) {
        if (privacy) {
            RitMessageCard("Consent is not assumed", "Behavioural personalization is off by default. Enabling this local switch does not begin tracking or grant legal consent to a future service.")
            Spacer(Modifier.height(12.dp))
            AccountRow("${if (prefs.personalizationConsent) "✓ " else ""}Personalized recommendations",
                if (prefs.personalizationConsent) "Local preference on; no activity collection exists" else "Off · ordinary discovery remains available",
                { repo.updatePreferences(prefs.copy(personalizationConsent = !prefs.personalizationConsent), userId) }, "privacy_personalization")
            RitText("Privacy notice, retention policy and authenticated data access/deletion requests require the production service and legal review.",
                RitType.body, colors.muted)
        } else {
            RitText("These switches save only in the local debug preview. No push or email is sent.", RitType.body, colors.muted)
            Spacer(Modifier.height(12.dp))
            AccountRow("${if (prefs.operationalAlerts) "✓ " else ""}Order and negotiation updates",
                "In-app/push preference · essential service notices may still apply",
                { repo.updatePreferences(prefs.copy(operationalAlerts = !prefs.operationalAlerts), userId) }, "prefs_operational")
            AccountRow("${if (prefs.promotionalAlerts) "✓ " else ""}Offers and promotions",
                "Optional marketing alerts; off by default",
                { repo.updatePreferences(prefs.copy(promotionalAlerts = !prefs.promotionalAlerts), userId) }, "prefs_promotions")
            AccountRow("${if (prefs.transactionalEmail) "✓ " else ""}Transactional email",
                "Preference only; essential account/security notices are separate",
                { repo.updatePreferences(prefs.copy(transactionalEmail = !prefs.transactionalEmail), userId) }, "prefs_email")
        }
    }
}

@Composable
fun CustomerAccountManagementScreen(profile: CustomerProfile?, onBack: () -> Unit, onSignOut: () -> Unit) {
    CustomerShell("Account management", "customer_account_management", onBack) {
        AccountRow("Email address", "${profile?.email ?: "Not authenticated"} · debug sample, not verified")
        AccountRow("Sessions", "No production login session exists in this preview")
        AccountRow("Request my data", "Unavailable until authenticated account and privacy service exist")
        AccountRow("Request account deletion", "Requires identity verification and retention review; no deletion is sent")
        RitSecondaryButton("Log out", onSignOut, Modifier.fillMaxWidth().testTag("account_mock_signout"))
    }
}

@Composable
fun CustomerNotificationsScreen(repo: CustomerAccountRepository, onBack: () -> Unit) {
    CustomerShell("Notifications", "customer_notifications", onBack) {
        if (repo.notifications.isEmpty()) RitEmptyState("No notifications", "Real account alerts will appear here after the notification service is connected.")
        else repo.notifications.forEach { note -> AccountRow(note.title, "${note.detail} · sample only") }
        RitMessageCard("Delivery not connected", "No Android push, email or durable server inbox is active in this preview.")
    }
}

@Composable
fun CustomerSupportScreen(repo: CustomerAccountRepository, onBack: () -> Unit,
                          onCreate: () -> Unit, onTicket: (String) -> Unit) {
    CustomerShell("Help & Support", "customer_support", onBack) {
        RitText("Find help, report a problem or review local examples.", RitType.body)
        Spacer(Modifier.height(14.dp))
        RitPrimaryButton("Report an issue", onCreate, Modifier.fillMaxWidth().testTag("support_create"))
        Spacer(Modifier.height(16.dp))
        RitText("My tickets", RitType.title)
        Spacer(Modifier.height(9.dp))
        if (repo.tickets.isEmpty()) RitEmptyState("No support tickets", "Use Report an issue to create a local demo ticket. It will not be delivered.")
        else repo.tickets.forEach { ticket ->
            AccountRow("${ticket.id} · ${ticket.subject}", "${ticket.category.label} · ${ticket.status.name.replace('_', ' ')} · local preview",
                { onTicket(ticket.id) }, "support_ticket_${ticket.id}")
        }
        Spacer(Modifier.height(10.dp))
        RitMessageCard("Who handles what?", "Food and fulfillment issues route to the relevant seller. Payment, account and app problems route to platform support. Seller issues may be escalated.")
    }
}

@Composable
fun CustomerTicketFormScreen(repo: CustomerAccountRepository, userId: String?, onBack: () -> Unit,
                             onCreated: (String) -> Unit) {
    var category by remember { mutableStateOf<CustomerIssueCategory?>(null) }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var attachmentUri by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = RitThemeTokens.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        attachmentUri = uri?.toString()
    }
    CustomerShell("Report a problem", "customer_ticket_form", onBack) {
        RitText("A local example only. No seller or administrator receives this report.", RitType.body, colors.muted)
        Spacer(Modifier.height(14.dp))
        RitText("Issue category", RitType.title)
        CustomerIssueCategory.entries.forEach { option ->
            AccountRow("${if (category == option) "✓ " else ""}${option.label}", option.routedTo,
                { category = option; error = null }, "ticket_category_${option.name.lowercase()}")
        }
        RitTextField(reference, { reference = it.take(60) }, "Related request/order (optional)", "Reference",
            Modifier.fillMaxWidth(), inputTag = "ticket_reference")
        Spacer(Modifier.height(10.dp))
        RitTextField(subject, { subject = it.take(100) }, "Subject", "What happened?", Modifier.fillMaxWidth(), inputTag = "ticket_subject")
        Spacer(Modifier.height(10.dp))
        RitTextField(description, { description = it.take(1000) }, "Description", "Describe the issue",
            Modifier.fillMaxWidth(), inputTag = "ticket_description", singleLine = false)
        Spacer(Modifier.height(10.dp))
        RitSecondaryButton(if (attachmentUri == null) "Add local image (optional)" else "Replace local image",
            { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            Modifier.fillMaxWidth().testTag("ticket_attachment"))
        if (attachmentUri != null) RitText("Image selected locally · not uploaded", RitType.small, colors.muted)
        error?.let { RitText(it, RitType.body, colors.accent, Modifier.padding(vertical = 8.dp).testTag("ticket_error")) }
        Spacer(Modifier.height(14.dp))
        RitPrimaryButton("Save local ticket", {
            error = validateCustomerTicket(category, subject, description)
            if (error == null) {
                val ticket = repo.createTicket(category!!, subject, description, reference, userId, attachmentUri)
                if (ticket == null) error = "Mock access required." else onCreated(ticket.id)
            }
        }, Modifier.fillMaxWidth().testTag("ticket_submit"))
    }
}

@Composable
fun CustomerTicketDetailScreen(ticket: CustomerSupportTicket?, repo: CustomerAccountRepository,
                               userId: String?, onBack: () -> Unit) {
    var reply by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    CustomerShell("Support conversation", "customer_ticket_detail", onBack) {
        if (ticket == null) RitEmptyState("Ticket not found", "Return to support and choose an available local ticket.")
        else {
            RitText(ticket.subject, RitType.heading)
            RitText("${ticket.id} · ${ticket.status.name.replace('_', ' ')} · local preview", RitType.body,
                RitThemeTokens.colors.accent)
            Spacer(Modifier.height(12.dp))
            AccountRow("Category", "${ticket.category.label} · ${ticket.category.routedTo}")
            ticket.requestReference?.let { AccountRow("Related request", "$it · reference only; no verified payment implied") }
            AccountRow("Your message", ticket.description)
            ticket.attachmentUri?.let {
                AccountRow("Image attachment", "Selected on this device only · not uploaded or validated by a server")
            }
            ticket.replies.forEach { AccountRow("Sample response", it) }
            if (ticket.escalated) AccountRow("Escalation requested", "Local preview only · no platform team notified")
            if (!ticket.escalated && ticket.category in setOf(CustomerIssueCategory.FOOD, CustomerIssueCategory.SCHEDULE)) {
                RitSecondaryButton("Escalate locally", {
                    feedback = if (repo.escalateTicket(ticket.id, userId)) "Local escalation noted; no team was notified."
                        else "Could not update this demo ticket."
                }, Modifier.fillMaxWidth().testTag("ticket_escalate"))
                Spacer(Modifier.height(10.dp))
            }
            RitTextField(reply, { reply = it.take(1000) }, "Reply", "Write a local note", Modifier.fillMaxWidth(),
                inputTag = "ticket_reply", singleLine = false)
            Spacer(Modifier.height(8.dp))
            RitPrimaryButton("Add local reply", {
                feedback = if (repo.replyToTicket(ticket.id, reply, userId)) { reply = ""; "Reply saved locally; not sent." }
                    else "Enter a reply before saving."
            }, Modifier.fillMaxWidth().testTag("ticket_reply_submit"))
            feedback?.let { RitText(it, RitType.body, RitThemeTokens.colors.accent, Modifier.padding(vertical = 8.dp)) }
            Spacer(Modifier.height(14.dp))
            RitMessageCard("Not delivered", "Messages, escalation and refund decisions require the production support service. This local ticket does not change any order or payment state.")
        }
    }
}

@Composable
fun CustomerTemplatesScreen(repo: CustomerAccountRepository, userId: String?, draft: BulkDraftSession,
                            onBack: () -> Unit, onOpenCart: () -> Unit, onCreate: () -> Unit,
                            onEdit: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var pendingApply by remember { mutableStateOf<NamedBulkTemplate?>(null) }
    val colors = RitThemeTokens.colors
    fun applyTemplate(template: NamedBulkTemplate) {
        val seller = PreviewCatalog.sellers.find { it.id == template.sellerId }
        if (seller == null) { feedback = "Seller no longer exists."; return }
        val stale = template.lines.any { line ->
            val item = PreviewCatalog.items.find { it.id == line.itemId }
            item == null || itemEligibility(seller, item) == ItemEligibility.UNAVAILABLE
        }
        if (stale) { feedback = "Template has unavailable items. Edit the template before reuse."; return }
        draft.clear(); draft.selectSeller(seller.id)
        template.lines.forEach { line ->
            PreviewCatalog.items.find { it.id == line.itemId }?.let { draft.setQuantity(it, line.quantity) }
        }
        onOpenCart()
    }
    CustomerShell("My bulk templates", "customer_templates", onBack) {
        RitText("Save a one-seller item mix. A template is not an order, and pickup and price are chosen again when you use it.",
            RitType.body, colors.muted)
        Spacer(Modifier.height(14.dp))
        RitPrimaryButton("Create New Template", onCreate, Modifier.fillMaxWidth().testTag("template_create"))
        Spacer(Modifier.height(14.dp))
        RitTextField(name, { name = it.take(60) }, "Template name", "e.g. Club snacks", Modifier.fillMaxWidth(), inputTag = "template_name")
        Spacer(Modifier.height(9.dp))
        RitText("Current draft: ${draft.draft.lines.sumOf { it.quantity }} units · ${draft.draft.sellerId ?: "no seller"}",
            RitType.small, colors.muted)
        RitPrimaryButton("Save current draft as template", {
            feedback = validateBulkTemplate(name, draft.draft.sellerId, draft.draft.lines)
                ?: if (repo.saveTemplate(name, draft.draft, userId)) "Template saved locally." else "Mock access required."
        }, Modifier.fillMaxWidth().testTag("template_save"))
        feedback?.let { RitText(it, RitType.body, colors.accent, Modifier.padding(vertical = 10.dp).testTag("template_feedback")) }
        Spacer(Modifier.height(18.dp))
        if (repo.templates.isEmpty()) RitEmptyState("No saved templates", "Create one from scratch or save your current bulk cart.")
        else repo.templates.forEach { template ->
            val seller = PreviewCatalog.sellers.find { it.id == template.sellerId }
            val stale = template.lines.filter { line ->
                val item = PreviewCatalog.items.find { it.id == line.itemId }
                item == null || seller == null || itemEligibility(seller, item) == ItemEligibility.UNAVAILABLE
            }
            AccountRow(template.name, "${seller?.name ?: "Seller unavailable"} · ${template.lines.sumOf { it.quantity }} units" +
                if (stale.isNotEmpty()) " · ${stale.size} unavailable item(s)" else " · current catalog checked")
            RitSecondaryButton("Use in Bulk Cart", {
                if (draft.draft.lines.isNotEmpty() &&
                    (draft.draft.sellerId != template.sellerId || draft.draft.lines != template.lines)) pendingApply = template
                else applyTemplate(template)
            }, Modifier.fillMaxWidth().testTag("template_use_${template.id}"))
            Spacer(Modifier.height(7.dp))
            RitSecondaryButton("Edit template", { onEdit(template.id) }, Modifier.fillMaxWidth().testTag("template_edit_${template.id}"))
            Spacer(Modifier.height(7.dp))
            RitSecondaryButton("Remove template", { repo.removeTemplate(template.id, userId) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
    pendingApply?.let { template -> RitConfirmDialog("Replace bulk cart?",
        "This template will replace the items in your current bulk cart. Your individual cart will not change.",
        { pendingApply = null }, { pendingApply = null; applyTemplate(template) }, "Replace Bulk Cart") }
}

@Composable
fun CustomerTemplateEditorScreen(repo: CustomerAccountRepository, userId: String?, templateId: String?,
                                 onBack: () -> Unit, onSaved: () -> Unit) {
    val template = repo.templates.find { it.id == templateId }
    val local = remember(templateId) {
        BulkDraftSession().apply {
            if (template != null && selectSeller(template.sellerId)) template.lines.forEach { line ->
                PreviewCatalog.items.find { it.id == line.itemId }?.let { setQuantity(it, line.quantity) }
            }
        }
    }
    var name by remember(templateId) { mutableStateOf(template?.name.orEmpty()) }
    var search by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var pendingSeller by remember { mutableStateOf<String?>(null) }
    val colors = RitThemeTokens.colors
    CustomerShell(if (template == null) "Create bulk template" else "Edit bulk template",
        "customer_template_editor", onBack) {
        RitText("Pick any eligible items from one seller. You can save below the seller minimum and decide pickup later.",
            RitType.body, colors.muted)
        Spacer(Modifier.height(12.dp))
        RitTextField(name, { name = it.take(60) }, "Template name", "e.g. Department snacks",
            Modifier.fillMaxWidth(), inputTag = "template_editor_name")
        Spacer(Modifier.height(14.dp))
        RitText("Seller", RitType.title)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewCatalog.sellers.filter { it.capabilities.bulk }.forEach { seller ->
                RitChip(seller.name, local.draft.sellerId == seller.id, {
                    if (!local.selectSeller(seller.id)) pendingSeller = seller.id
                }, Modifier.testTag("template_seller_${seller.id}"))
            }
        }
        val seller = PreviewCatalog.sellers.find { it.id == local.draft.sellerId }
        if (seller != null) {
            Spacer(Modifier.height(14.dp))
            RitTextField(search, { search = it }, "Find food", "Search ${seller.name}",
                Modifier.fillMaxWidth(), inputTag = "template_search")
            val menu = PreviewCatalog.visibleItems(seller.id, query = search)
                .filter { itemEligibility(seller, it) != ItemEligibility.UNAVAILABLE }
            if (menu.isEmpty()) RitEmptyState("No matching items", "Try another search or seller.")
            menu.forEach { item ->
                val quantity = local.draft.lines.find { it.itemId == item.id }?.quantity ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        RitText(item.name, RitType.label)
                        RitText("${item.price.display} each · ${if (item.kind == edu.msrit.canteen.customer.FoodKind.PACKAGED) "Packaged" else "Prepared"}",
                            RitType.small, colors.muted)
                    }
                    RitText("−", RitType.title, colors.accent, Modifier.clickable(role = Role.Button) {
                        if (quantity > 0) local.setQuantity(item, quantity - 1)
                    }.padding(10.dp).testTag("template_minus_${item.id}"))
                    RitText("$quantity", RitType.label)
                    RitText("+", RitType.title, colors.accent, Modifier.clickable(role = Role.Button) {
                        local.setQuantity(item, quantity + 1)
                    }.padding(10.dp).testTag("template_plus_${item.id}"))
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        RitText("${local.draft.lines.sumOf { it.quantity }} items selected", RitType.title)
        feedback?.let { RitText(it, RitType.body, colors.accent, Modifier.testTag("template_editor_feedback")) }
        RitPrimaryButton("Save Template", {
            val error = validateBulkTemplate(name, local.draft.sellerId, local.draft.lines)
            if (error != null) feedback = error
            else {
                val saved = if (templateId == null || template == null) repo.saveTemplate(name, local.draft, userId)
                    else repo.updateTemplate(templateId, name, local.draft, userId)
                if (saved) onSaved() else feedback = "Unable to save this local template."
            }
        }, Modifier.fillMaxWidth().testTag("template_editor_save"))
    }
    pendingSeller?.let { target -> RitConfirmDialog("Switch template seller?",
        "Changing sellers clears only the items in this unsaved template.", { pendingSeller = null }, {
            local.clear(); local.selectSeller(target); pendingSeller = null
        }, "Clear Items & Switch") }
}

@Composable
fun CustomerOfflineInfoScreen(onBack: () -> Unit) {
    CustomerShell("Connection needed", "customer_offline", onBack) {
        RitEmptyState("You're offline", "Previously viewed information may be stale. No new request, quote approval or payment can be sent without an online, server-acknowledged action.")
        RitMessageCard("Preview boundary", "Offline caching and automatic synchronization are not implemented yet. This screen demonstrates the required safe state, not a live network monitor.")
    }
}
