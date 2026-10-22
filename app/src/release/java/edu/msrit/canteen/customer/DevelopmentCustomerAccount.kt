package edu.msrit.canteen.customer

/** Inert release counterpart: no mock identity, data or mutation is available. */
object DevelopmentCustomerAccount : CustomerAccountRepository {
    override val profile: CustomerProfile? = null
    override val preferences = CustomerPreferences()
    override val tickets: List<CustomerSupportTicket> = emptyList()
    override val notifications: List<CustomerNotification> = emptyList()
    override val templates: List<NamedBulkTemplate> = emptyList()
    override fun updateProfile(profile: CustomerProfile, userId: String?) = false
    override fun updatePreferences(preferences: CustomerPreferences, userId: String?) = false
    override fun createTicket(category: CustomerIssueCategory, subject: String, description: String,
                              requestReference: String?, userId: String?, attachmentUri: String?): CustomerSupportTicket? = null
    override fun saveTemplate(name: String, draft: BulkDraft, userId: String?) = false
    override fun updateTemplate(id: String, name: String, draft: BulkDraft, userId: String?) = false
    override fun removeTemplate(id: String, userId: String?) = false
    override fun replyToTicket(id: String, message: String, userId: String?) = false
    override fun escalateTicket(id: String, userId: String?) = false
    fun seedExamples() = Unit
    override fun reset() = Unit
}
