package edu.msrit.canteen.customer

import android.content.Context
import edu.msrit.canteen.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Debug-local account state. Only the active customer is projected into Compose. */
object DevelopmentCustomerAccount : CustomerAccountRepository {
    private const val LEGACY_USER = "debug-customer"
    private var context: Context? = null
    private var activeUser: String? = LEGACY_USER
    private val memory = mutableMapOf<String, State>()

    private data class State(
        val profile: CustomerProfile?,
        val preferences: CustomerPreferences = CustomerPreferences(),
        val tickets: List<CustomerSupportTicket> = emptyList(),
        val notifications: List<CustomerNotification> = emptyList(),
        val templates: List<NamedBulkTemplate> = emptyList(),
        val nextTemplateId: Int = 1,
    )

    override var profile by mutableStateOf<CustomerProfile?>(legacyProfile())
        private set
    override var preferences by mutableStateOf(CustomerPreferences())
        private set
    override var tickets by mutableStateOf<List<CustomerSupportTicket>>(emptyList())
        private set
    override var notifications by mutableStateOf<List<CustomerNotification>>(emptyList())
        private set
    override var templates by mutableStateOf<List<NamedBulkTemplate>>(emptyList())
        private set
    var cloudError by mutableStateOf<String?>(null)
        private set
    private var nextTemplateId = 1

    fun initialize(appContext: Context) { context = appContext.applicationContext }
    fun activate(userId: String, initial: CustomerProfile) {
        saveCurrent()
        activeUser = userId
        val state = if (BuildConfig.RIT_CLOUD_DEMO) State(initial) else memory[userId] ?: read(userId) ?: State(initial)
        memory[userId] = state
        apply(state)
        cloudError = null
    }
    fun deactivate() { saveCurrent(); activeUser = null; apply(State(null)) }
    private fun legacyProfile() = CustomerProfile("Demo Customer", "demo@example.invalid", CustomerAffiliation.STUDENT)
    private fun allowed(userId: String?) = userId != null && userId == activeUser
    private fun current() = State(profile, preferences, tickets, notifications, templates, nextTemplateId)
    private fun apply(state: State) {
        profile = state.profile; preferences = state.preferences; tickets = state.tickets
        notifications = state.notifications; templates = state.templates; nextTemplateId = state.nextTemplateId
    }
    private fun saveCurrent() {
        if (BuildConfig.RIT_CLOUD_DEMO) return
        val id = activeUser ?: return
        val state = current()
        memory[id] = state
        context?.getSharedPreferences("rit_debug_customer_accounts_v1", Context.MODE_PRIVATE)?.edit {
            putString(id, encode(state).toString())
        }
    }

    override fun updateProfile(profile: CustomerProfile, userId: String?): Boolean {
        if (!allowed(userId) || validateCustomerProfile(profile) != null) return false
        this.profile = profile.copy(fullName = profile.fullName.trim(), department = profile.department.trim())
        saveCurrent(); return true
    }
    override fun updatePreferences(preferences: CustomerPreferences, userId: String?): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO) return false
        if (!allowed(userId)) return false
        this.preferences = preferences; saveCurrent(); return true
    }
    override fun createTicket(category: CustomerIssueCategory, subject: String, description: String,
                              requestReference: String?, userId: String?, attachmentUri: String?): CustomerSupportTicket? {
        if (BuildConfig.RIT_CLOUD_DEMO) return null
        if (!allowed(userId) || validateCustomerTicket(category, subject, description) != null) return null
        val ticket = CustomerSupportTicket("LOCAL-C-${tickets.size + 1}", category, subject.trim(), description.trim(),
            requestReference?.trim()?.takeIf { it.isNotEmpty() }, attachmentUri = attachmentUri)
        tickets = listOf(ticket) + tickets; saveCurrent(); return ticket
    }
    override fun saveTemplate(name: String, draft: BulkDraft, userId: String?): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO) return false
        if (!allowed(userId) || validateBulkTemplate(name, draft.sellerId, draft.lines) != null) return false
        templates = listOf(NamedBulkTemplate("LOCAL-T-${nextTemplateId++}", name.trim(), draft.sellerId!!, draft.lines)) + templates
        saveCurrent(); return true
    }
    override fun updateTemplate(id: String, name: String, draft: BulkDraft, userId: String?): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO) return false
        if (!allowed(userId) || templates.none { it.id == id } ||
            validateBulkTemplate(name, draft.sellerId, draft.lines) != null) return false
        templates = templates.map { if (it.id == id) it.copy(name = name.trim(), sellerId = draft.sellerId!!,
            lines = draft.lines) else it }; saveCurrent(); return true
    }
    override fun removeTemplate(id: String, userId: String?): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO) return false
        if (!allowed(userId) || templates.none { it.id == id }) return false
        templates = templates.filterNot { it.id == id }; saveCurrent(); return true
    }
    override fun replyToTicket(id: String, message: String, userId: String?): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO) return false
        if (!allowed(userId) || message.trim().length < 2 || tickets.none { it.id == id }) return false
        tickets = tickets.map { if (it.id == id) it.copy(replies = it.replies + "You · local preview: ${message.trim().take(1000)}") else it }
        saveCurrent(); return true
    }
    override fun escalateTicket(id: String, userId: String?): Boolean {
        if (BuildConfig.RIT_CLOUD_DEMO) return false
        if (!allowed(userId) || tickets.none { it.id == id &&
                it.category in setOf(CustomerIssueCategory.FOOD, CustomerIssueCategory.SCHEDULE) }) return false
        tickets = tickets.map { if (it.id == id) it.copy(escalated = true) else it }; saveCurrent(); return true
    }
    fun seedExamples() {
        if (BuildConfig.RIT_CLOUD_DEMO) return
        notifications = listOf(CustomerNotification("SAMPLE-N-1", "Demo request update",
            "Illustrative only. No notification was delivered."))
        tickets = listOf(CustomerSupportTicket("SAMPLE-C-1", CustomerIssueCategory.FOOD,
            "Example missing item", "This is a local support preview.", "DEMO-NC-PAID", CustomerTicketStatus.IN_PROGRESS,
            replies = listOf("Example seller response · not delivered")))
        saveCurrent()
    }
    override fun reset() {
        activeUser = LEGACY_USER
        val fresh = State(legacyProfile()); memory[LEGACY_USER] = fresh; apply(fresh)
        context?.getSharedPreferences("rit_debug_customer_accounts_v1", Context.MODE_PRIVATE)?.edit { remove(LEGACY_USER) }
    }

    /** Cloud account data never hydrates from local SharedPreferences. */
    suspend fun refreshCloud(page: String, id: String, userId: String?) {
        if (!BuildConfig.RIT_CLOUD_DEMO || !allowed(userId)) return
        cloudError = null
        when (page) {
            "preferences", "privacy" -> {
                val result = withContext(Dispatchers.IO) { DebugCustomerEngagementApi.preferences() }
                if (allowed(userId)) { result.value?.let { preferences = it }; cloudError = result.error }
            }
            "notifications" -> {
                val result = withContext(Dispatchers.IO) { DebugCustomerEngagementApi.notifications() }
                if (allowed(userId)) { result.value?.let { notifications = it }; cloudError = result.error }
            }
            "templates", "template-new", "template-edit" -> {
                val result = withContext(Dispatchers.IO) { DebugCustomerEngagementApi.templates() }
                if (allowed(userId)) { result.value?.let { templates = it }; cloudError = result.error }
            }
            "support", "ticket-new" -> {
                val result = withContext(Dispatchers.IO) { DebugCustomerEngagementApi.tickets() }
                if (allowed(userId)) { result.value?.let { tickets = it }; cloudError = result.error }
            }
            "ticket" -> {
                val result = withContext(Dispatchers.IO) { DebugCustomerEngagementApi.ticket(id) }
                if (allowed(userId)) { result.value?.let(::applyTicket); cloudError = result.error }
            }
        }
    }

    private fun applyTicket(ticket: CustomerSupportTicket) {
        tickets = listOf(ticket) + tickets.filterNot { it.id == ticket.id }
    }

    suspend fun saveCloudPreferences(value: CustomerPreferences, userId: String?): String? {
        if (!allowed(userId)) return "Sign in again."
        val result = withContext(Dispatchers.IO) { DebugCustomerEngagementApi.savePreferences(value) }
        if (!allowed(userId)) return "Account changed; reload this screen."
        result.value?.let { preferences = it }
        cloudError = result.error
        return result.error
    }

    suspend fun markCloudNotificationRead(id: String, userId: String?): String? {
        if (!BuildConfig.RIT_CLOUD_DEMO || !allowed(userId)) return "Sign in again."
        val result = withContext(Dispatchers.IO) { DebugCustomerEngagementApi.markNotificationRead(id) }
        if (!allowed(userId)) return "Account changed; reload this screen."
        result.value?.let { confirmed ->
            notifications = notifications.map { if (it.id == id) confirmed else it }
        }
        cloudError = result.error
        return result.error
    }

    suspend fun saveCloudTemplate(name: String, draft: BulkDraft, id: String?, userId: String?): String? {
        if (!allowed(userId)) return "Sign in again."
        val result = withContext(Dispatchers.IO) { DebugCustomerEngagementApi.saveTemplate(name, draft, id) }
        if (!allowed(userId)) return "Account changed; reload this screen."
        result.value?.let { templates = listOf(it) + templates.filterNot { old -> old.id == it.id } }
        cloudError = result.error
        return result.error
    }

    suspend fun removeCloudTemplate(id: String, userId: String?): String? {
        if (!allowed(userId)) return "Sign in again."
        val result = withContext(Dispatchers.IO) { DebugCustomerEngagementApi.removeTemplate(id) }
        if (!allowed(userId)) return "Account changed; reload this screen."
        if (result.value == true) templates = templates.filterNot { it.id == id }
        cloudError = result.error
        return result.error
    }

    suspend fun createCloudTicket(category: CustomerIssueCategory, subject: String, description: String,
                                  reference: String?, sellerId: String?, clientRequestId: String, userId: String?): Pair<String?, String?> {
        if (!allowed(userId)) return null to "Sign in again."
        val result = withContext(Dispatchers.IO) {
            DebugCustomerEngagementApi.createTicket(category, subject, description, reference, sellerId, clientRequestId)
        }
        if (!allowed(userId)) return null to "Account changed; reload this screen."
        result.value?.let(::applyTicket)
        cloudError = result.error
        return result.value?.id to result.error
    }

    suspend fun replyCloudTicket(id: String, message: String, clientMessageId: String, userId: String?): String? {
        if (!allowed(userId)) return "Sign in again."
        val result = withContext(Dispatchers.IO) { DebugCustomerEngagementApi.reply(id, message, clientMessageId) }
        if (!allowed(userId)) return "Account changed; reload this screen."
        if (result.value == true) refreshCloud("ticket", id, userId)
        cloudError = result.error
        return result.error
    }

    suspend fun escalateCloudTicket(id: String, userId: String?): String? {
        if (!allowed(userId)) return "Sign in again."
        val result = withContext(Dispatchers.IO) { DebugCustomerEngagementApi.escalate(id) }
        if (!allowed(userId)) return "Account changed; reload this screen."
        result.value?.let(::applyTicket)
        cloudError = result.error
        return result.error
    }

    private fun encode(state: State) = JSONObject().apply {
        state.profile?.let { put("profile", JSONObject().put("name", it.fullName).put("email", it.email)
            .put("affiliation", it.affiliation.name).put("department", it.department).put("photo", it.photoUri)) }
        put("preferences", JSONObject().put("operational", state.preferences.operationalAlerts)
            .put("promotional", state.preferences.promotionalAlerts).put("email", state.preferences.transactionalEmail)
            .put("personalization", state.preferences.personalizationConsent))
        put("tickets", JSONArray().apply { state.tickets.forEach { ticket -> put(JSONObject()
            .put("id", ticket.id).put("category", ticket.category.name).put("subject", ticket.subject)
            .put("description", ticket.description).put("reference", ticket.requestReference)
            .put("status", ticket.status.name).put("escalated", ticket.escalated)
            .put("attachment", ticket.attachmentUri).put("replies", JSONArray(ticket.replies))) } })
        put("notifications", JSONArray().apply { state.notifications.forEach { note -> put(JSONObject()
            .put("id", note.id).put("title", note.title).put("detail", note.detail)
            .put("sample", note.sample).put("read", note.read)) } })
        put("templates", JSONArray().apply { state.templates.forEach { template -> put(JSONObject()
            .put("id", template.id).put("name", template.name).put("seller", template.sellerId)
            .put("lines", JSONArray().apply { template.lines.forEach { line -> put(JSONObject()
                .put("item", line.itemId).put("quantity", line.quantity)) } })) } })
        put("nextTemplateId", state.nextTemplateId)
    }
    private fun read(userId: String): State? = runCatching {
        val raw = context?.getSharedPreferences("rit_debug_customer_accounts_v1", Context.MODE_PRIVATE)
            ?.getString(userId, null) ?: return null
        val obj = JSONObject(raw)
        val p = obj.optJSONObject("profile")
        val profile = p?.let { CustomerProfile(it.getString("name"), it.getString("email"),
            CustomerAffiliation.valueOf(it.getString("affiliation")), it.optString("department"),
            it.optString("photo").takeIf { value -> value.isNotBlank() && value != "null" }) }
        val q = obj.optJSONObject("preferences") ?: JSONObject()
        val prefs = CustomerPreferences(q.optBoolean("operational", true), q.optBoolean("promotional", false),
            q.optBoolean("email", true), q.optBoolean("personalization", false))
        fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        val tickets = (obj.optJSONArray("tickets") ?: JSONArray()).objects().map { entry ->
            CustomerSupportTicket(entry.getString("id"), CustomerIssueCategory.valueOf(entry.getString("category")),
                entry.getString("subject"), entry.getString("description"),
                entry.optString("reference").takeIf { it.isNotBlank() && it != "null" },
                CustomerTicketStatus.valueOf(entry.getString("status")), replies =
                    (entry.optJSONArray("replies") ?: JSONArray()).let { values -> (0 until values.length()).map(values::getString) },
                escalated = entry.optBoolean("escalated"),
                attachmentUri = entry.optString("attachment").takeIf { it.isNotBlank() && it != "null" }) }
        val notes = (obj.optJSONArray("notifications") ?: JSONArray()).objects().map { entry ->
            CustomerNotification(entry.getString("id"), entry.getString("title"), entry.getString("detail"),
                entry.optBoolean("sample", true), entry.optBoolean("read", false)) }
        val templates = (obj.optJSONArray("templates") ?: JSONArray()).objects().map { entry ->
            NamedBulkTemplate(entry.getString("id"), entry.getString("name"), entry.getString("seller"),
                entry.getJSONArray("lines").objects().map { line -> BulkLine(line.getString("item"), line.getInt("quantity")) }) }
        State(profile, prefs, tickets, notes, templates, obj.optInt("nextTemplateId", 1))
    }.getOrNull()
}
