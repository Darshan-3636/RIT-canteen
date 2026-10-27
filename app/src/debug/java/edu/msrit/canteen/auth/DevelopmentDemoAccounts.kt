package edu.msrit.canteen.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.msrit.canteen.customer.CustomerAffiliation
import edu.msrit.canteen.navigation.AppMode
import edu.msrit.canteen.navigation.AuthorizedIdentity
import edu.msrit.canteen.navigation.DevelopmentRole
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Debug-only login against the real local backend. Account hints are never authority. */
object DevelopmentDemoAccounts {
    private var bearerStore: DebugBearerStore? = null
    private var bearer: String? = null
    var activeAccount by mutableStateOf<DemoAccount?>(null)
        private set
    var serverIdentity by mutableStateOf<AuthorizedIdentity?>(null)
        private set
    var connectionMessage by mutableStateOf<String?>(null)
        private set

    val seededAccounts = listOf(
        DemoAccount("hint-arya", "Arya Rao", "student.arya@demo.ritcanteen.test", DevelopmentRole.Customer, CustomerAffiliation.STUDENT),
        DemoAccount("hint-kiran", "Kiran Shah", "student.kiran@demo.ritcanteen.test", DevelopmentRole.Customer, CustomerAffiliation.STUDENT),
        DemoAccount("hint-maya", "Dr Maya Iyer", "faculty.maya@demo.ritcanteen.test", DevelopmentRole.Customer, CustomerAffiliation.FACULTY),
        DemoAccount("hint-owner-main", "Main Canteen Owner", "owner.main@demo.ritcanteen.test", DevelopmentRole.SellerOwner, sellerId = "main-canteen"),
        DemoAccount("hint-owner-north", "North Counter Owner", "owner.secondary@demo.ritcanteen.test", DevelopmentRole.SellerOwner, sellerId = "north-counter"),
        DemoAccount("hint-manager", "Main Canteen Manager", "manager.main@demo.ritcanteen.test", DevelopmentRole.SellerManager, sellerId = "main-canteen"),
        DemoAccount("hint-staff", "Main Canteen Staff", "staff.main@demo.ritcanteen.test", DevelopmentRole.SellerStaff, sellerId = "main-canteen"),
        DemoAccount("hint-admin", "Platform Administrator", "admin.platform@demo.ritcanteen.test", DevelopmentRole.PlatformAdministrator),
    )

    fun initialize(context: Context) {
        if (bearerStore == null) bearerStore = DebugBearerStore(context)
    }

    /** Blocking transport: call on Dispatchers.IO, never Compose/UI. */
    fun restore(): Boolean = bearerStore?.read()?.let { verifyAndActivate(it, false) } ?: false

    fun signIn(email: String, password: String): DemoAuthResult {
        val normalized = email.trim().lowercase(Locale.ROOT)
        if (!AuthInput.isEmailValid(normalized) || password.isBlank())
            return DemoAuthResult(error = "Enter an email address and password.")
        return try {
            val response = DebugBackendApi.request("POST", "/api/v1/auth/login",
                JSONObject().put("email", normalized).put("password", password))
            if (response.status != 200) return DemoAuthResult(error = response.error ?: "Sign-in failed (" + response.status + ").")
            val token = response.body?.optString("accessToken").orEmpty()
            if (token.isBlank() || !verifyAndActivate(token, true))
                DemoAuthResult(error = connectionMessage ?: "Could not verify this server session.")
            else DemoAuthResult(account = activeAccount)
        } catch (_: Exception) {
            connectionMessage = "Local backend unavailable. Start it and reconnect USB forwarding."
            DemoAuthResult(error = connectionMessage)
        }
    }

    /** Registration creates a customer only; it does not issue a session. */
    fun register(name: String, email: String, password: String, confirmation: String,
                 affiliation: CustomerAffiliation, department: String): DemoAuthResult {
        val cleanName = AuthInput.normalizeName(name)
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val error = when {
            !AuthInput.isFullNameValid(cleanName) -> "Enter your first and last name."
            !AuthInput.isEmailValid(cleanEmail) -> "Enter a valid email address."
            password.length < 10 || !password.any(Char::isLetter) || !password.any(Char::isDigit) ->
                "Use at least 10 characters, including a letter and a number."
            password != confirmation -> "Passwords do not match."
            else -> null
        }
        if (error != null) return DemoAuthResult(error = error)
        return try {
            val body = JSONObject().put("fullName", cleanName).put("email", cleanEmail)
                .put("password", password).put("affiliation", affiliation.name)
                .put("department", department.trim().ifBlank { JSONObject.NULL })
            val response = DebugBackendApi.request("POST", "/api/v1/auth/register", body)
            if (response.status != 201) DemoAuthResult(error = response.error ?: "Registration failed (" + response.status + ").")
            else DemoAuthResult(account = toAccount(response.body!!.getJSONObject("user")))
        } catch (_: Exception) {
            DemoAuthResult(error = "Local backend unavailable. Start it and reconnect USB forwarding.")
        }
    }

    /** Local access ends immediately even if the server revoke cannot be reached. */
    fun signOut() {
        val token = bearer
        bearer = null
        bearerStore?.clear()
        activeAccount = null
        serverIdentity = null
        connectionMessage = null
        if (token != null) Thread {
            runCatching { DebugBackendApi.request("POST", "/api/v1/auth/logout", token = token) }
        }.apply { isDaemon = true; start() }
    }

    internal fun token(): String? = bearer

    internal fun invalidateIfCurrent(token: String) {
        if (bearer != token) return
        bearer = null
        bearerStore?.clear()
        activeAccount = null
        serverIdentity = null
        connectionMessage = "Session expired or revoked. Sign in again."
    }

    private fun verifyAndActivate(token: String, persist: Boolean): Boolean = try {
        val response = DebugBackendApi.request("GET", "/api/v1/auth/me", token = token)
        if (response.status != 200) {
            if (response.status == 401) bearerStore?.clear()
            connectionMessage = response.error ?: "Could not verify this session."
            activeAccount = null
            serverIdentity = null
            false
        } else {
            val user = response.body!!.getJSONObject("user")
            val account = toAccount(user)
            val identity = toIdentity(user, account.id)
            if (identity.modes.isEmpty()) {
                connectionMessage = "This account has no authorized app role."
                activeAccount = null
                serverIdentity = null
                false
            } else {
                bearer = token
                if (persist) bearerStore?.save(token)
                activeAccount = account
                serverIdentity = identity
                connectionMessage = null
                true
            }
        }
    } catch (_: Exception) {
        activeAccount = null
        serverIdentity = null
        connectionMessage = "Local backend unavailable. Start it and reconnect USB forwarding."
        false
    }

    private fun toAccount(user: JSONObject): DemoAccount {
        val first = user.optJSONArray("memberships")?.optJSONObject(0)
        val role = when {
            user.optBoolean("platformAdmin") -> DevelopmentRole.PlatformAdministrator
            first?.optString("role") == "OWNER" -> DevelopmentRole.SellerOwner
            first?.optString("role") == "MANAGER" -> DevelopmentRole.SellerManager
            first?.optString("role") == "STAFF" -> DevelopmentRole.SellerStaff
            else -> DevelopmentRole.Customer
        }
        val affiliation = user.optString("affiliation").takeIf { it == "STUDENT" || it == "FACULTY" }
            ?.let(CustomerAffiliation::valueOf)
        return DemoAccount(user.getString("id"), user.getString("fullName"), user.getString("email"),
            role, affiliation ?: if (role == DevelopmentRole.Customer) CustomerAffiliation.STUDENT else null,
            sellerId = first?.optString("sellerId"))
    }

    private fun toIdentity(user: JSONObject, userId: String): AuthorizedIdentity {
        val memberships = user.optJSONArray("memberships") ?: JSONArray()
        val modes = mutableSetOf<AppMode>()
        val capabilities = mutableMapOf<String, Set<String>>()
        if (user.optBoolean("platformAdmin")) modes += AppMode.PlatformAdministrator
        if (!user.isNull("affiliation")) modes += AppMode.Customer
        for (i in 0 until memberships.length()) {
            val membership = memberships.getJSONObject(i)
            val sellerId = membership.getString("sellerId")
            modes += when (membership.getString("role")) {
                "OWNER" -> AppMode.SellerOwner(sellerId)
                "MANAGER" -> AppMode.SellerManager(sellerId)
                else -> AppMode.OperationalStaff(sellerId)
            }
            val grants = membership.getJSONArray("capabilities")
            capabilities[sellerId] = (0 until grants.length()).map(grants::getString).toSet()
        }
        return AuthorizedIdentity(userId, modes, capabilities)
    }
}
