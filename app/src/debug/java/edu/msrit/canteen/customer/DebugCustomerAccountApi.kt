package edu.msrit.canteen.customer

import edu.msrit.canteen.auth.DebugBackendApi
import edu.msrit.canteen.auth.DevelopmentDemoAccounts
import org.json.JSONObject

/** Own-profile operations. Invoke network methods from Dispatchers.IO. */
object DebugCustomerAccountApi {
    data class Result(val profile: CustomerProfile? = null, val error: String? = null)

    fun fetchProfile(): Result = withToken { token ->
        val response = DebugBackendApi.request("GET", "/api/v1/me/profile", token = token)
        if (response.status == 200) Result(profile = parse(response.body!!))
        else Result(error = response.error ?: "Could not load profile.")
    }

    fun updateProfile(profile: CustomerProfile): Result = withToken { token ->
        val body = JSONObject().put("fullName", profile.fullName)
            .put("affiliation", profile.affiliation.name)
            .put("department", profile.department.ifBlank { JSONObject.NULL })
        val response = DebugBackendApi.request("PATCH", "/api/v1/me/profile", body, token)
        if (response.status == 200) Result(profile = parse(response.body!!))
        else Result(error = response.error ?: "Could not update profile.")
    }

    private fun parse(row: JSONObject) = CustomerProfile(
        row.getString("fullName"), row.getString("email"),
        CustomerAffiliation.valueOf(row.getString("affiliation")),
        row.optString("department").takeUnless { it == "null" }.orEmpty())

    private inline fun withToken(action: (String) -> Result): Result {
        val token = DevelopmentDemoAccounts.token() ?: return Result(error = "Sign in again.")
        return try { action(token) } catch (_: Exception) { Result(error = "Local backend unavailable.") }
    }
}
