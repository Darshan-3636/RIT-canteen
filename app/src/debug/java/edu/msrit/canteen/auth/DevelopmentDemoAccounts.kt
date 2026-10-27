package edu.msrit.canteen.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import edu.msrit.canteen.customer.CustomerAffiliation
import edu.msrit.canteen.navigation.DevelopmentRole
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONArray
import org.json.JSONObject

/** Device-local development credentials. This entire implementation is excluded from release. */
object DevelopmentDemoAccounts {
    private const val STORE = "rit_debug_demo_accounts_v1"
    private const val ITERATIONS = 120_000
    private var appContext: Context? = null
    var activeAccount by mutableStateOf<DemoAccount?>(null)
        private set

    private val seeded = listOf(
        DemoAccount("demo-student-arya", "Arya Rao", "student.arya@demo.ritcanteen.test", DevelopmentRole.Customer, CustomerAffiliation.STUDENT),
        DemoAccount("demo-student-kiran", "Kiran Shah", "student.kiran@demo.ritcanteen.test", DevelopmentRole.Customer, CustomerAffiliation.STUDENT),
        DemoAccount("demo-faculty-maya", "Dr Maya Iyer", "faculty.maya@demo.ritcanteen.test", DevelopmentRole.Customer, CustomerAffiliation.FACULTY),
        DemoAccount("demo-owner-main", "Main Canteen Owner", "owner.main@demo.ritcanteen.test", DevelopmentRole.SellerOwner, sellerId = "main-canteen"),
        DemoAccount("demo-owner-north", "North Counter Owner", "owner.secondary@demo.ritcanteen.test", DevelopmentRole.SellerOwner, sellerId = "north-counter"),
        DemoAccount("demo-manager-main", "Main Canteen Manager", "manager.main@demo.ritcanteen.test", DevelopmentRole.SellerManager, sellerId = "main-canteen"),
        DemoAccount("demo-staff-main", "Main Canteen Staff", "staff.main@demo.ritcanteen.test", DevelopmentRole.SellerStaff, sellerId = "main-canteen"),
        DemoAccount("demo-admin-platform", "Platform Administrator", "admin.platform@demo.ritcanteen.test", DevelopmentRole.PlatformAdministrator),
    )
    private val seededPasswords = mapOf(
        "demo-student-arya" to "RitDemo!Student01",
        "demo-student-kiran" to "RitDemo!Student02",
        "demo-faculty-maya" to "RitDemo!Faculty01",
        "demo-owner-main" to "RitDemo!OwnerMain01",
        "demo-owner-north" to "RitDemo!OwnerOther01",
        "demo-manager-main" to "RitDemo!Manager01",
        "demo-staff-main" to "RitDemo!Staff01",
        "demo-admin-platform" to "RitDemo!Admin01",
    )

    val seededAccounts: List<DemoAccount> get() = seeded

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val id = prefs().getString("active_id", null)
        activeAccount = accountById(id)
        if (activeAccount == null && id != null) prefs().edit { remove("active_id") }
    }

    fun signIn(email: String, password: String): DemoAuthResult {
        val normalized = email.trim().lowercase()
        val account = seeded.firstOrNull { it.email == normalized }
            ?: registered().firstOrNull { it.email == normalized }
        val valid = when {
            account == null -> false
            account.id in seededPasswords -> MessageDigest.isEqual(
                seededPasswords.getValue(account.id).toByteArray(), password.toByteArray())
            else -> storedCredentials(account.id)?.let { verify(password, it.first, it.second) } == true
        }
        if (!valid) return DemoAuthResult(error = "Email or password is incorrect.")
        activate(account!!)
        return DemoAuthResult(account = account)
    }

    fun register(name: String, email: String, password: String, confirmation: String,
                 affiliation: CustomerAffiliation, department: String): DemoAuthResult {
        val cleanName = AuthInput.normalizeName(name)
        val cleanEmail = email.trim().lowercase()
        val error = when {
            !AuthInput.isFullNameValid(cleanName) -> "Enter your first and last name."
            !AuthInput.isEmailValid(cleanEmail) -> "Enter a valid email address."
            seeded.any { it.email == cleanEmail } || registered().any { it.email == cleanEmail } -> "That email is already used by a demo account."
            password.length < 10 || !password.any(Char::isLetter) || !password.any(Char::isDigit) ->
                "Use at least 10 characters, including a letter and a number."
            password != confirmation -> "Passwords do not match."
            else -> null
        }
        if (error != null) return DemoAuthResult(error = error)
        val account = DemoAccount("local-${UUID.randomUUID()}", cleanName, cleanEmail,
            DevelopmentRole.Customer, affiliation, department.trim().take(100))
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(password, salt)
        val existing = prefs().getString("registered", "[]") ?: "[]"
        val accounts = JSONArray(existing)
        accounts.put(JSONObject().put("id", account.id).put("name", account.name)
            .put("email", account.email).put("affiliation", affiliation.name)
            .put("department", account.department)
            .put("salt", Base64.getEncoder().encodeToString(salt))
            .put("hash", Base64.getEncoder().encodeToString(hash)))
        prefs().edit { putString("registered", accounts.toString()) }
        activate(account)
        return DemoAuthResult(account = account)
    }

    fun signOut() {
        activeAccount = null
        appContext?.let { prefs().edit { remove("active_id") } }
    }

    private fun activate(account: DemoAccount) {
        activeAccount = account
        prefs().edit { putString("active_id", account.id) }
    }

    private fun prefs() = requireNotNull(appContext) { "Initialize demo accounts before use" }
        .getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private fun accountById(id: String?): DemoAccount? = seeded.firstOrNull { it.id == id }
        ?: registered().firstOrNull { it.id == id }

    private fun registered(): List<DemoAccount> {
        val entries = JSONArray(prefs().getString("registered", "[]") ?: "[]")
        return (0 until entries.length()).mapNotNull { index -> runCatching {
            entries.getJSONObject(index).let { entry -> DemoAccount(entry.getString("id"),
                entry.getString("name"), entry.getString("email"), DevelopmentRole.Customer,
                CustomerAffiliation.valueOf(entry.getString("affiliation")),
                entry.optString("department")) }
        }.getOrNull() }
    }

    private fun storedCredentials(id: String): Pair<ByteArray, ByteArray>? {
        val entries = JSONArray(prefs().getString("registered", "[]") ?: "[]")
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            if (entry.getString("id") == id) return Base64.getDecoder().decode(entry.getString("salt")) to
                Base64.getDecoder().decode(entry.getString("hash"))
        }
        return null
    }

    private fun verify(password: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(derive(password, salt), expected)

    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
