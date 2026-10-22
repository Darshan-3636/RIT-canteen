package edu.msrit.canteen.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Separate debug-local customer state; no session, network request or production account is created. */
object DevelopmentCustomerAccount : CustomerAccountRepository {
    private const val DEMO_USER = "debug-customer"
    override var profile by mutableStateOf<CustomerProfile?>(CustomerProfile("Demo Customer", "demo@example.invalid", CustomerAffiliation.STUDENT))
        private set
    override var preferences by mutableStateOf(CustomerPreferences())
        private set
    override var tickets by mutableStateOf<List<CustomerSupportTicket>>(emptyList())
        private set
    override var notifications by mutableStateOf<List<CustomerNotification>>(emptyList())
        private set
    override var templates by mutableStateOf<List<NamedBulkTemplate>>(emptyList())
        private set
    private var nextTemplateId = 1

    override fun updateProfile(profile: CustomerProfile, userId: String?): Boolean {
        if (userId != DEMO_USER || validateCustomerProfile(profile) != null) return false
        this.profile = profile.copy(fullName = profile.fullName.trim(), department = profile.department.trim())
        return true
    }

    override fun updatePreferences(preferences: CustomerPreferences, userId: String?): Boolean {
        if (userId != DEMO_USER) return false
        this.preferences = preferences
        return true
    }

    override fun createTicket(category: CustomerIssueCategory, subject: String, description: String,
                              requestReference: String?, userId: String?, attachmentUri: String?): CustomerSupportTicket? {
        if (userId != DEMO_USER || validateCustomerTicket(category, subject, description) != null) return null
        val ticket = CustomerSupportTicket("LOCAL-C-${tickets.size + 1}", category, subject.trim(), description.trim(),
            requestReference?.trim()?.takeIf { it.isNotEmpty() }, attachmentUri = attachmentUri)
        tickets = listOf(ticket) + tickets
        return ticket
    }

    override fun saveTemplate(name: String, draft: BulkDraft, userId: String?): Boolean {
        if (userId != DEMO_USER || validateBulkTemplate(name, draft.sellerId, draft.lines) != null) return false
        templates = listOf(NamedBulkTemplate("LOCAL-T-${nextTemplateId++}", name.trim(), draft.sellerId!!, draft.lines)) + templates
        return true
    }

    override fun updateTemplate(id: String, name: String, draft: BulkDraft, userId: String?): Boolean {
        if (userId != DEMO_USER || templates.none { it.id == id } ||
            validateBulkTemplate(name, draft.sellerId, draft.lines) != null) return false
        templates = templates.map { if (it.id == id) it.copy(name = name.trim(), sellerId = draft.sellerId!!,
            lines = draft.lines) else it }
        return true
    }

    override fun removeTemplate(id: String, userId: String?): Boolean {
        if (userId != DEMO_USER || templates.none { it.id == id }) return false
        templates = templates.filterNot { it.id == id }
        return true
    }

    override fun replyToTicket(id: String, message: String, userId: String?): Boolean {
        if (userId != DEMO_USER || message.trim().length < 2 || tickets.none { it.id == id }) return false
        tickets = tickets.map { if (it.id == id) it.copy(replies = it.replies + "You · local preview: ${message.trim().take(1000)}") else it }
        return true
    }

    override fun escalateTicket(id: String, userId: String?): Boolean {
        if (userId != DEMO_USER || tickets.none { it.id == id &&
                it.category in setOf(CustomerIssueCategory.FOOD, CustomerIssueCategory.SCHEDULE) }) return false
        tickets = tickets.map { if (it.id == id) it.copy(escalated = true) else it }
        return true
    }

    fun seedExamples() {
        notifications = listOf(CustomerNotification("SAMPLE-N-1", "Demo request update",
            "Illustrative only. No notification was delivered."))
        tickets = listOf(CustomerSupportTicket("SAMPLE-C-1", CustomerIssueCategory.FOOD,
            "Example missing item", "This is a local support preview.", "DEMO-NC-PAID", CustomerTicketStatus.IN_PROGRESS,
            replies = listOf("Example seller response · not delivered")))
    }

    override fun reset() {
        profile = CustomerProfile("Demo Customer", "demo@example.invalid", CustomerAffiliation.STUDENT)
        preferences = CustomerPreferences()
        tickets = emptyList(); notifications = emptyList(); templates = emptyList(); nextTemplateId = 1
    }
}
