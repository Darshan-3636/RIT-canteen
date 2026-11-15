package edu.msrit.canteen.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import edu.msrit.canteen.ui.components.AdminDirectoryRow
import edu.msrit.canteen.ui.components.AdminInfoCard
import edu.msrit.canteen.ui.components.AdminSectionHeader
import edu.msrit.canteen.ui.components.AdminTone
import edu.msrit.canteen.ui.components.RitEmptyState
import edu.msrit.canteen.ui.components.RitPrimaryButton
import edu.msrit.canteen.ui.components.RitSecondaryButton
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.components.RitTextField
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Server-backed content inside the existing admin page shell. No sample data enters this path. */
@Composable
fun CloudAdminContent(page: AdminPage, sellerId: String?, ticketId: String?,
                      onSeller: (String) -> Unit, onTicket: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val androidContext=LocalContext.current
    var exportBusy by remember { mutableStateOf(false) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }
    val savePlatformReport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if(uri==null) exportBusy=false
        else scope.launch {
            exportFeedback=withContext(Dispatchers.IO) {
                runCatching {
                    val token=DevelopmentDemoAccounts.token() ?: error("Sign in again.")
                    val response=DebugBackendApi.request("GET","/api/v1/admin/reports/export",token=token)
                    if(response.status!=200) error(response.error ?: "Could not export platform report.")
                    requireNotNull(androidContext.contentResolver.openOutputStream(uri,"w"))
                        .use { it.write(response.raw.orEmpty().toByteArray(Charsets.UTF_8)) }
                }.fold({ "Platform report saved." }, { it.message ?: "Could not save platform report." })
            }
            exportBusy=false
        }
    }
    var data by remember(page, sellerId) { mutableStateOf<JSONObject?>(null) }
    var error by remember(page, sellerId) { mutableStateOf<String?>(null) }
    var busy by remember(page, sellerId) { mutableStateOf(false) }
    var revision by remember(page, sellerId) { mutableIntStateOf(0) }
    var offset by remember(page) { mutableIntStateOf(0) }
    var search by remember(page) { mutableStateOf("") }
    var submittedSearch by remember(page) { mutableStateOf("") }
    var sellerName by remember { mutableStateOf("") }
    var proposedId by remember { mutableStateOf("") }
    var ownerUserId by remember { mutableStateOf("") }
    var supportReply by remember(ticketId) { mutableStateOf("") }
    var supportMessageId by remember(ticketId) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    val pageSize = 30
    val listPath = when (page) {
        AdminPage.DASHBOARD, AdminPage.ANALYTICS, AdminPage.SELLER_ANALYTICS -> "/admin/dashboard/metrics"
        AdminPage.SELLERS -> "/admin/sellers"
        AdminPage.USERS -> "/admin/users"
        AdminPage.SELLER_SETUP -> "/admin/setup-drafts"
        AdminPage.AUDIT -> "/admin/audit"
        AdminPage.SUPPORT -> "/admin/support/tickets"
        AdminPage.TICKET_DETAIL -> ticketId?.let { "/admin/support/tickets/$it" }
        AdminPage.SELLER_DETAIL -> sellerId?.let { "/admin/sellers/$it" }
        else -> null
    }
    LaunchedEffect(listPath, offset, submittedSearch, revision) {
        if (listPath == null) return@LaunchedEffect
        val suffix = if (page in setOf(AdminPage.SELLERS, AdminPage.USERS, AdminPage.SELLER_SETUP, AdminPage.AUDIT, AdminPage.SUPPORT)) {
            val q = if (page in setOf(AdminPage.SELLERS, AdminPage.USERS) && submittedSearch.isNotBlank())
                "&q=${URLEncoder.encode(submittedSearch, "UTF-8")}" else ""
            "?limit=$pageSize&offset=$offset$q"
        } else ""
        data = null
        error = null
        val result = runCatching { withContext(Dispatchers.IO) {
            val token = DevelopmentDemoAccounts.token() ?: throw IllegalStateException("Sign in again.")
            DebugBackendApi.request("GET", "/api/v1$listPath$suffix", token = token)
        } }
        result.onSuccess {
            if (it.status == 200 && it.body != null) data = it.body
            else error = it.error ?: "Could not load this admin page (${it.status})."
        }.onFailure { error = it.message ?: "Server unavailable. Retry this page." }
    }

    fun perform(method: String, path: String, body: JSONObject, versionPath: String? = null,
                onSuccess: () -> Unit = {}) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val token = DevelopmentDemoAccounts.token() ?: throw IllegalStateException("Sign in again.")
                    if (versionPath != null) {
                        val latest = DebugBackendApi.request("GET", "/api/v1$versionPath", token = token)
                        if (latest.status != 200) return@withContext latest
                        body.put("expectedVersion", latest.body!!.getLong("version"))
                    }
                    DebugBackendApi.request(method, "/api/v1$path", body, token)
                }
                if (result.status in 200..204) { onSuccess(); revision++ }
                else error = result.error ?: "Server rejected this action (${result.status})."
            } catch (failure: Exception) {
                error = failure.message ?: "Server unavailable. Retry the action."
            } finally { busy = false }
        }
    }

    if (error != null) {
        AdminInfoCard("Server error", error!!)
        RitSecondaryButton("Retry", { revision++ }, Modifier.fillMaxWidth(), enabled = !busy)
    }
    if (data == null && error == null) {
        RitText("Loading server data…", RitType.body, RitThemeTokens.colors.muted)
        return
    }
    when (page) {
        AdminPage.DASHBOARD, AdminPage.ANALYTICS, AdminPage.SELLER_ANALYTICS -> data?.let { row ->
            AdminSectionHeader("Stored operations")
            AdminInfoCard("Active sellers", row.optLong("activeSellers").toString())
            AdminInfoCard("Active memberships", row.optLong("activeMemberships").toString())
            AdminInfoCard("Catalog items", row.optLong("catalogItems").toString())
            AdminInfoCard("Pending setup drafts", row.optLong("pendingSetupDrafts").toString())
            AdminInfoCard("Suspended users", row.optLong("suspendedUsers").toString())
            RitText("Order counts by status.", RitType.small)
            RitSecondaryButton(if(exportBusy) "Preparing report…" else "Save platform report CSV",{
                if(!exportBusy) { exportBusy=true;exportFeedback=null;savePlatformReport.launch("platform-report.csv") }
            },Modifier.fillMaxWidth(),enabled=!exportBusy)
            exportFeedback?.let { RitText(it,RitType.small,RitThemeTokens.colors.muted) }
        }
        AdminPage.SELLERS, AdminPage.USERS -> {
            RitTextField(search, { search = it }, "Search", "Name, ID or email", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            RitSecondaryButton("Search server", { offset = 0; submittedSearch = search.trim(); revision++ },
                Modifier.fillMaxWidth())
            val rows = data?.optJSONArray("items")
            AdminSectionHeader(if (page == AdminPage.SELLERS) "Sellers" else "Users")
            if (rows?.length() == 0) RitEmptyState("No matches", "Change the search and try again.")
            rows?.let { list -> repeat(list.length()) { index ->
                val row = list.getJSONObject(index)
                if (page == AdminPage.SELLERS) {
                    val id = row.getString("id")
                    AdminDirectoryRow(row.getString("name"), "ID: $id",
                        listOf((if (row.getBoolean("active")) "Active" else "Inactive") to AdminTone.NEUTRAL),
                        onClick = { onSeller(id) })
                } else {
                    val id = row.getString("id")
                    val status = row.getString("status")
                    AdminDirectoryRow(row.getString("fullName"), row.getString("email"),
                        listOf(status to AdminTone.NEUTRAL))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RitSecondaryButton(if (status == "ACTIVE") "Suspend" else "Activate", {
                            perform("POST", "/admin/users/$id/status",
                                JSONObject().put("status", if (status == "ACTIVE") "SUSPENDED" else "ACTIVE")
                                    .put("reason", "Administrator changed account status"), "/admin/users/$id")
                        }, Modifier.weight(1f), enabled = !busy)
                        RitSecondaryButton(if (row.getBoolean("platformAdmin")) "Revoke admin" else "Grant admin", {
                            perform("PUT", "/admin/users/$id/platform-admin",
                                JSONObject().put("enabled", !row.getBoolean("platformAdmin"))
                                    .put("reason", "Administrator changed platform role"), "/admin/users/$id")
                        }, Modifier.weight(1f), enabled = !busy)
                    }
                }
            } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RitSecondaryButton("Previous", { offset = (offset - pageSize).coerceAtLeast(0) },
                    Modifier.weight(1f), enabled = offset > 0)
                RitSecondaryButton("Next", { offset += pageSize }, Modifier.weight(1f),
                    enabled = offset + pageSize < (data?.optInt("total") ?: 0))
            }
        }
        AdminPage.SELLER_DETAIL -> data?.let { row ->
            val id = row.getString("id")
            val active = row.getBoolean("active")
            AdminDirectoryRow(row.getString("name"), row.optString("description"),
                listOf((if (active) "Active" else "Inactive") to AdminTone.NEUTRAL))
            Spacer(Modifier.height(12.dp))
            RitPrimaryButton(if (busy) "Saving…" else if (active) "Suspend seller" else "Activate seller", {
                perform("POST", "/admin/sellers/$id/${if (active) "suspend" else "activate"}",
                    JSONObject().put("reason", "Administrator changed seller availability"), "/admin/sellers/$id")
            }, Modifier.fillMaxWidth(), enabled = !busy)
        }
        AdminPage.SELLER_SETUP -> {
            AdminSectionHeader("Seller onboarding")
            RitSecondaryButton("Prepare ESB MSRIT Canteen", {
                sellerName = "ESB MSRIT Canteen"
                proposedId = "esb-msrit-canteen"
            }, Modifier.fillMaxWidth())
            RitTextField(sellerName, { sellerName = it }, "Business name", "Required", Modifier.fillMaxWidth())
            RitTextField(proposedId, { proposedId = it }, "Stable seller ID", "lowercase-id", Modifier.fillMaxWidth())
            RitPrimaryButton(if (busy) "Saving…" else "Save setup draft", {
                perform("POST", "/admin/setup-drafts", JSONObject().put("sellerName", sellerName.trim())
                    .put("proposedSellerId", proposedId.trim()))
            }, Modifier.fillMaxWidth(), enabled = !busy && sellerName.isNotBlank() && proposedId.isNotBlank())
            AdminSectionHeader("Pending drafts")
            RitTextField(ownerUserId, { ownerUserId = it }, "Existing owner user ID", "UUID required for approval",
                Modifier.fillMaxWidth())
            val rows = data?.optJSONArray("items")
            rows?.let { list -> repeat(list.length()) { index ->
                val row = list.getJSONObject(index)
                if (row.optString("status") == "DRAFT") {
                    val id = row.getString("id")
                    AdminDirectoryRow(row.getString("sellerName"), row.getString("proposedSellerId"),
                        listOf("Draft" to AdminTone.NEUTRAL))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RitSecondaryButton("Approve", {
                            perform("POST", "/admin/setup-drafts/$id/approve",
                                JSONObject().put("ownerUserId", ownerUserId.trim())
                                    .put("reason", "Administrator approved seller onboarding"),
                                "/admin/setup-drafts/$id")
                        }, Modifier.weight(1f), enabled = !busy && ownerUserId.isNotBlank())
                        RitSecondaryButton("Reject", {
                            perform("POST", "/admin/setup-drafts/$id/reject",
                                JSONObject().put("reason", "Administrator rejected seller onboarding"),
                                "/admin/setup-drafts/$id")
                        }, Modifier.weight(1f), enabled = !busy)
                    }
                }
            } }
        }
        AdminPage.AUDIT -> {
            AdminSectionHeader("Recorded actions")
            data?.optJSONArray("items")?.let { list -> repeat(list.length()) { index ->
                val row = list.getJSONObject(index)
                AdminDirectoryRow(row.getString("action"), row.optString("occurredAt"),
                    listOf(row.getString("targetType") to AdminTone.NEUTRAL))
            } }
        }
        AdminPage.SUPPORT -> {
            AdminSectionHeader("Support tickets")
            data?.optJSONArray("items")?.let { rows ->
                if(rows.length()==0) RitEmptyState("No tickets","Customer support requests will appear here.")
                repeat(rows.length()) { index ->
                    val row=rows.getJSONObject(index)
                    AdminDirectoryRow(row.getString("subject"),
                        "${row.getString("category")} · ${row.optString("sellerId").takeUnless { it=="null" }.orEmpty()}",
                        listOf(row.getString("status") to AdminTone.NEUTRAL),
                        onClick={ onTicket(row.getString("id")) })
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                RitSecondaryButton("Previous",{ offset=(offset-pageSize).coerceAtLeast(0) },Modifier.weight(1f),enabled=offset>0)
                RitSecondaryButton("Next",{ offset+=pageSize },Modifier.weight(1f),
                    enabled=offset+pageSize<(data?.optInt("total") ?: 0))
            }
        }
        AdminPage.TICKET_DETAIL -> data?.let { detail ->
            val ticket=detail.getJSONObject("ticket")
            val id=ticket.getString("id")
            val status=ticket.getString("status")
            AdminSectionHeader(ticket.getString("subject"))
            RitText("${ticket.getString("category")} · $status",RitType.body,RitThemeTokens.colors.muted)
            detail.getJSONArray("messages").let { rows -> repeat(rows.length()) { index ->
                val row=rows.getJSONObject(index)
                AdminDirectoryRow(row.getString("authorRole"),row.getString("body"),emptyList())
            } }
            if(status!="CLOSED") {
                RitTextField(supportReply,{ supportReply=it },"Reply to customer","Message",Modifier.fillMaxWidth())
                RitPrimaryButton(if(busy) "Sending…" else "Send reply",{
                    perform("POST","/admin/support/tickets/$id/messages",
                        JSONObject().put("body",supportReply.trim()).put("clientMessageId",supportMessageId),
                        onSuccess={ supportReply=""; supportMessageId=java.util.UUID.randomUUID().toString() })
                },Modifier.fillMaxWidth(),enabled=!busy && supportReply.trim().length>=2)
                val next=if(status=="RESOLVED") "CLOSED" else "RESOLVED"
                RitSecondaryButton(if(next=="RESOLVED") "Mark resolved" else "Close ticket",{
                    perform("PATCH","/admin/support/tickets/$id",JSONObject().put("status",next))
                },Modifier.fillMaxWidth(),enabled=!busy)
            }
        }
        else -> RitEmptyState("Unavailable", "This view has no connected server operation yet.")
    }
}
