package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import edu.msrit.canteen.customer.SellerCapability
import edu.msrit.canteen.customer.DebugSellerCatalogApi
import edu.msrit.canteen.customer.DebugServerCatalog
import edu.msrit.canteen.ui.components.RitChip
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun cloudRequest(method: String, path: String, body: JSONObject? = null): DebugBackendApi.Response {
    val token = DevelopmentDemoAccounts.token() ?: throw IllegalStateException("Sign in again.")
    return DebugBackendApi.request(method, "/api/v1$path", body, token)
}

@Composable
fun CloudSellerArchivedItems(sellerId: String, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var data by remember(sellerId) { mutableStateOf<JSONObject?>(null) }
    var error by remember(sellerId) { mutableStateOf<String?>(null) }
    var busy by remember(sellerId) { mutableStateOf(false) }
    var offset by remember(sellerId) { mutableIntStateOf(0) }
    var revision by remember(sellerId) { mutableIntStateOf(0) }
    LaunchedEffect(sellerId, offset, revision) {
        data = null
        error = null
        val result = runCatching { withContext(Dispatchers.IO) {
            cloudRequest("GET", "/sellers/$sellerId/catalog/items?limit=50&offset=$offset")
        } }
        result.onSuccess { if (it.status == 200) { data = it.body; error = null }
            else error = it.error ?: "Could not load managed items (${it.status})." }
            .onFailure { error = it.message ?: "Server unavailable." }
    }
    Column(modifier) {
        RitText("Archived items", RitType.title)
        error?.let { RitText(it, RitType.small, RitThemeTokens.colors.accent)
            RitSecondaryButton("Retry", { revision++ }, Modifier.fillMaxWidth()) }
        val rows = data?.optJSONArray("items")
        if (rows == null && error == null) RitText("Loading managed items…", RitType.body)
        val archived = rows?.let { list -> (0 until list.length()).map(list::getJSONObject)
            .filter { it.optString("archivedAt") !in setOf("", "null") } }.orEmpty()
        if (archived.isEmpty() && rows != null) RitEmptyState("No archived items on this page",
            "Use Next to inspect older items if this seller has more than 50.")
        LazyColumn(Modifier.weight(1f)) {
            items(archived, key = { it.getString("id") }) { row ->
                RitText(row.getString("name"), RitType.label)
                RitSecondaryButton(if (busy) "Restoring…" else "Restore unpublished", {
                    if (busy) return@RitSecondaryButton
                    busy = true
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                DebugSellerCatalogApi.setArchived(sellerId, row.getString("id"), false)
                            }
                            if (result.success) {
                                val snapshot = runCatching { withContext(Dispatchers.IO) {
                                    DebugServerCatalog.fetch(policySellerId = sellerId, force = true) } }.getOrNull()
                                if (snapshot != null) DebugServerCatalog.apply(snapshot)
                                revision++
                            } else error = result.message
                        } finally { busy = false }
                    }
                }, Modifier.fillMaxWidth(), enabled = !busy)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RitSecondaryButton("Previous", { offset = (offset - 50).coerceAtLeast(0) },
                Modifier.weight(1f), enabled = offset > 0)
            RitSecondaryButton("Next", { offset += 50 }, Modifier.weight(1f),
                enabled = offset + 50 < (data?.optInt("total") ?: 0))
        }
    }
}

@Composable
fun CloudSellerCategories(sellerId: String) {
    val scope = rememberCoroutineScope()
    var rows by remember(sellerId) { mutableStateOf<JSONArray?>(null) }
    var error by remember(sellerId) { mutableStateOf<String?>(null) }
    var busy by remember(sellerId) { mutableStateOf(false) }
    var revision by remember(sellerId) { mutableIntStateOf(0) }
    var categoryId by remember(sellerId) { mutableStateOf("") }
    var label by remember(sellerId) { mutableStateOf("") }
    LaunchedEffect(sellerId, revision) {
        rows = null
        error = null
        val result = runCatching { withContext(Dispatchers.IO) {
            cloudRequest("GET", "/sellers/$sellerId/catalog/categories")
        } }
        result.onSuccess { if (it.status == 200 && it.array != null) { rows = it.array; error = null }
            else error = it.error ?: "Could not load categories (${it.status})." }
            .onFailure { error = it.message ?: "Server unavailable." }
    }
    fun save(id: String, name: String?, order: Int, active: Boolean, version: Long) {
        if (busy) return
        scope.launch {
            busy = true; error = null
            try {
                val result = withContext(Dispatchers.IO) {
                    cloudRequest("PUT", "/sellers/$sellerId/catalog/categories/$id",
                        JSONObject().put("expectedVersion", version).put("displayName", name)
                            .put("sortOrder", order).put("active", active))
                }
                if (result.status == 200) { revision++; categoryId = ""; label = "" }
                else error = result.error ?: "Category change failed (${result.status})."
            } catch (failure: Exception) { error = failure.message ?: "Server unavailable." }
            finally { busy = false }
        }
    }
    RitText("Seller category assignments", RitType.title)
    RitText("Use an existing platform category ID to add an assignment. Renaming changes this seller's label only.",
        RitType.small, RitThemeTokens.colors.muted)
    rows?.let { list -> repeat(list.length()) { index ->
        val row = list.getJSONObject(index)
        val id = row.getString("id")
        val currentName = row.optString("displayName").takeIf { it.isNotBlank() && it != "null" }
        val order = row.getInt("sortOrder")
        val active = row.getBoolean("active")
        val version = row.getLong("version")
        Spacer(Modifier.height(8.dp))
        RitText("${currentName ?: row.getString("name")} · ${if (active) "Visible" else "Hidden"}", RitType.label)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RitSecondaryButton("Edit", { categoryId = id; label = currentName ?: row.getString("name") },
                Modifier.weight(1f))
            RitSecondaryButton(if (active) "Hide" else "Show", {
                save(id, currentName, order, !active, version)
            }, Modifier.weight(1f), enabled = !busy)
            RitSecondaryButton("Move up", { save(id, currentName, order - 1, active, version) },
                Modifier.weight(1f), enabled = !busy)
        }
    } }
    Spacer(Modifier.height(12.dp))
    RitTextField(categoryId, { categoryId = it }, "Platform category ID", "Existing category ID",
        Modifier.fillMaxWidth())
    RitTextField(label, { label = it }, "Seller display name", "Optional", Modifier.fillMaxWidth())
    val existing = rows?.let { list -> (0 until list.length()).map { list.getJSONObject(it) }
        .find { it.optString("id") == categoryId.trim() } }
    RitPrimaryButton(if (busy) "Saving…" else "Save category assignment", {
        save(categoryId.trim(), label.trim().ifBlank { null }, existing?.optInt("sortOrder") ?: 0,
            true, existing?.optLong("version") ?: 0)
    }, Modifier.fillMaxWidth(), enabled = !busy && categoryId.isNotBlank())
    error?.let { RitText(it, RitType.small, RitThemeTokens.colors.accent) }
    if (rows == null && error == null) RitText("Loading categories…", RitType.body)
}

@Composable
fun CloudSellerEmployees(sellerId: String, onEmployee: (String?) -> Unit) {
    var data by remember(sellerId) { mutableStateOf<JSONObject?>(null) }
    var error by remember(sellerId) { mutableStateOf<String?>(null) }
    var revision by remember(sellerId) { mutableIntStateOf(0) }
    LaunchedEffect(sellerId, revision) {
        data = null
        error = null
        val result = runCatching { withContext(Dispatchers.IO) {
            cloudRequest("GET", "/sellers/$sellerId/memberships?limit=100")
        } }
        result.onSuccess { if (it.status == 200) { data = it.body; error = null }
            else error = it.error ?: "Could not load team (${it.status})." }
            .onFailure { error = it.message ?: "Server unavailable." }
    }
    RitText("Team members", RitType.heading)
    RitText("Only existing accounts can receive seller access. Changes are checked by the server.", RitType.small)
    error?.let { RitText(it, RitType.small, RitThemeTokens.colors.accent)
        RitSecondaryButton("Retry", { revision++ }, Modifier.fillMaxWidth()) }
    if (data == null && error == null) RitText("Loading team…", RitType.body)
    data?.optJSONArray("items")?.let { list -> repeat(list.length()) { index ->
        val member = list.getJSONObject(index)
        RitSecondaryButton("${member.getString("fullName")} · ${member.getString("role")} · ${if (member.getBoolean("active")) "Active" else "Inactive"}",
            { onEmployee(member.getString("id")) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(7.dp))
    } }
    RitPrimaryButton("Add existing account", { onEmployee(null) }, Modifier.fillMaxWidth())
}

@Composable
fun CloudSellerEmployeeEditor(sellerId: String, employeeId: String?, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember(sellerId) { mutableStateOf<JSONArray?>(null) }
    var error by remember(sellerId, employeeId) { mutableStateOf<String?>(null) }
    var busy by remember(sellerId, employeeId) { mutableStateOf(false) }
    var accountId by remember(employeeId) { mutableStateOf("") }
    var role by remember(employeeId) { mutableStateOf("STAFF") }
    var supervisor by remember(employeeId) { mutableStateOf("") }
    var grants by remember(employeeId) { mutableStateOf(emptySet<String>()) }
    var active by remember(employeeId) { mutableStateOf(true) }
    var expectedVersion by remember(employeeId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(sellerId, employeeId) {
        val result = runCatching { withContext(Dispatchers.IO) {
            cloudRequest("GET", "/sellers/$sellerId/memberships?limit=100")
        } }
        result.onSuccess { response ->
            if (response.status != 200) { error = response.error ?: "Could not load team."; return@onSuccess }
            list = response.body?.optJSONArray("items")
            val old = list?.let { rows -> (0 until rows.length()).map { rows.getJSONObject(it) }
                .find { it.optString("id") == employeeId } }
            if (employeeId != null && old == null) { error = "Membership no longer exists."; return@onSuccess }
            if (old != null) {
                accountId = old.getString("userId"); role = old.getString("role")
                supervisor = old.optString("supervisorMembershipId").takeUnless { it == "null" }.orEmpty()
                active = old.getBoolean("active"); expectedVersion = old.getLong("version")
                val capabilities = old.getJSONArray("capabilities")
                grants = (0 until capabilities.length()).map(capabilities::getString).toSet()
            }
        }.onFailure { error = it.message ?: "Server unavailable." }
    }
    RitText(if (employeeId == null) "Add account to team" else "Edit team permissions", RitType.heading)
    if (list == null && error == null) { RitText("Loading team…", RitType.body); return }
    RitTextField(accountId, { if (employeeId == null) accountId = it }, "Existing account user ID", "UUID",
        Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("MANAGER", "STAFF").forEach { option ->
            RitChip(option, role == option, { role = option; supervisor = "" }, Modifier.weight(1f))
        }
    }
    RitText("Supervisor", RitType.label)
    list?.let { rows -> repeat(rows.length()) { index ->
        val member = rows.getJSONObject(index)
        if (member.getBoolean("active") && member.getString("role") ==
            if (role == "MANAGER") "OWNER" else "MANAGER") {
            RitChip(member.getString("fullName"), supervisor == member.getString("id"),
                { supervisor = member.getString("id") })
        }
    } }
    RitText("Delegated capabilities", RitType.label)
    SellerCapability.entries.forEach { capability ->
        RitChip(capability.label, capability.wire in grants,
            { grants = if (capability.wire in grants) grants - capability.wire else grants + capability.wire })
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RitChip("Active", active, { active = true }); RitChip("Inactive", !active, { active = false })
    }
    error?.let { RitText(it, RitType.small, RitThemeTokens.colors.accent) }
    RitPrimaryButton(if (busy) "Saving…" else "Save permissions", {
        if (busy) return@RitPrimaryButton
        scope.launch {
            busy = true; error = null
            try {
                val body = JSONObject().put("role", role).put("supervisorMembershipId", supervisor)
                    .put("active", active).put("reason", "Seller updated team access")
                    .put("capabilities", JSONArray(grants.sorted()))
                val path = if (employeeId == null) "/sellers/$sellerId/memberships"
                    else "/sellers/$sellerId/memberships/$employeeId"
                if (employeeId == null) body.put("userId", accountId.trim())
                else body.put("expectedVersion", expectedVersion)
                val result = withContext(Dispatchers.IO) {
                    cloudRequest(if (employeeId == null) "POST" else "PATCH", path, body)
                }
                if (result.status in 200..201) onSaved()
                else error = result.error ?: "Could not save membership (${result.status})."
            } catch (failure: Exception) { error = failure.message ?: "Server unavailable." }
            finally { busy = false }
        }
    }, Modifier.fillMaxWidth(), enabled = !busy && accountId.isNotBlank() && supervisor.isNotBlank() &&
        (employeeId == null || expectedVersion != null))
}
