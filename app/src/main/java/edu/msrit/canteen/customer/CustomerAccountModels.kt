package edu.msrit.canteen.customer

/** Presentation contracts only. Production implementations must be scoped to the authenticated user. */
data class CustomerProfile(
    val fullName: String,
    val email: String,
    val affiliation: CustomerAffiliation,
    val department: String = "",
    val photoUri: String? = null,
)

enum class CustomerAffiliation { STUDENT, FACULTY }
enum class AppearanceChoice { SYSTEM, LIGHT, DARK }

data class CustomerPreferences(
    val operationalAlerts: Boolean = true,
    val promotionalAlerts: Boolean = false,
    val transactionalEmail: Boolean = true,
    /** Optional behavioural processing is off until valid notice and consent exist. */
    val personalizationConsent: Boolean = false,
)

enum class CustomerIssueCategory(val label: String, val routedTo: String) {
    FOOD("Food or missing items", "Relevant seller"),
    SCHEDULE("Bulk schedule or seller issue", "Relevant seller; platform escalation available"),
    PAYMENT("Payment problem", "Platform support"),
    ACCOUNT("Account or login", "Platform support"),
    APP("App problem", "Platform support"),
}

enum class CustomerTicketStatus { OPEN, IN_PROGRESS, RESOLVED, CLOSED }

data class CustomerSupportTicket(
    val id: String,
    val category: CustomerIssueCategory,
    val subject: String,
    val description: String,
    val requestReference: String? = null,
    val status: CustomerTicketStatus = CustomerTicketStatus.OPEN,
    val localOnly: Boolean = true,
    val replies: List<String> = emptyList(),
    val escalated: Boolean = false,
    val attachmentUri: String? = null,
)

data class CustomerNotification(val id: String, val title: String, val detail: String,
                                val sample: Boolean = true, val read: Boolean = false)

data class NamedBulkTemplate(val id: String, val name: String, val sellerId: String, val lines: List<BulkLine>)

fun validateCustomerProfile(profile: CustomerProfile): String? = when {
    profile.fullName.trim().length < 2 -> "Enter your full name."
    profile.fullName.length > 100 -> "Name is too long."
    else -> null
}

fun validateCustomerTicket(category: CustomerIssueCategory?, subject: String, description: String): String? = when {
    category == null -> "Choose an issue category."
    subject.trim().length < 4 -> "Enter a short subject."
    description.trim().length < 10 -> "Describe the issue in at least 10 characters."
    else -> null
}

fun validateBulkTemplate(name: String, sellerId: String?, lines: List<BulkLine>): String? = when {
    name.trim().length < 2 -> "Enter a template name."
    sellerId.isNullOrBlank() -> "Choose one seller first."
    lines.isEmpty() || lines.any { it.quantity <= 0 } -> "Add at least one eligible item."
    else -> null
}

interface CustomerAccountRepository {
    val profile: CustomerProfile?
    val preferences: CustomerPreferences
    val tickets: List<CustomerSupportTicket>
    val notifications: List<CustomerNotification>
    val templates: List<NamedBulkTemplate>
    fun updateProfile(profile: CustomerProfile, userId: String?): Boolean
    fun updatePreferences(preferences: CustomerPreferences, userId: String?): Boolean
    fun createTicket(category: CustomerIssueCategory, subject: String, description: String,
                     requestReference: String?, userId: String?, attachmentUri: String? = null): CustomerSupportTicket?
    fun saveTemplate(name: String, draft: BulkDraft, userId: String?): Boolean
    fun updateTemplate(id: String, name: String, draft: BulkDraft, userId: String?): Boolean
    fun removeTemplate(id: String, userId: String?): Boolean
    fun replyToTicket(id: String, message: String, userId: String?): Boolean
    fun escalateTicket(id: String, userId: String?): Boolean
    fun reset()
}
