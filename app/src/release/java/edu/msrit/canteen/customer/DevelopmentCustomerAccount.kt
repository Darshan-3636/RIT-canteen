package edu.msrit.canteen.customer

import android.content.Context

/** Inert release counterpart: no mock identity, data or mutation is available. */
object DevelopmentCustomerAccount : CustomerAccountRepository {
    val cloudError: String? = null
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
    fun initialize(context: Context) = Unit
    fun activate(userId: String, initial: CustomerProfile) = Unit
    fun deactivate() = Unit
    suspend fun refreshCloud(page: String, id: String, userId: String?) = Unit
    suspend fun saveCloudPreferences(value: CustomerPreferences, userId: String?): String? = "Unavailable."
    suspend fun markCloudNotificationRead(id: String, userId: String?): String? = "Unavailable."
    suspend fun saveCloudTemplate(name: String, draft: BulkDraft, id: String?, userId: String?): String? = "Unavailable."
    suspend fun removeCloudTemplate(id: String, userId: String?): String? = "Unavailable."
    suspend fun createCloudTicket(category: CustomerIssueCategory, subject: String, description: String,
                                  reference: String?, sellerId: String?, clientRequestId: String, userId: String?): Pair<String?, String?> =
        null to "Unavailable."
    suspend fun replyCloudTicket(id: String, message: String, clientMessageId: String, userId: String?): String? = "Unavailable."
    suspend fun escalateCloudTicket(id: String, userId: String?): String? = "Unavailable."
    override fun reset() = Unit
}
