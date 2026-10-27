package edu.msrit.canteen.auth

import android.content.Context
import edu.msrit.canteen.customer.CustomerAffiliation

/** Release contains neither seeded passwords nor a functioning local-account mechanism. */
object DevelopmentDemoAccounts {
    val activeAccount: DemoAccount? get() = null
    val seededAccounts: List<DemoAccount> get() = emptyList()
    fun initialize(context: Context) = Unit
    fun signIn(email: String, password: String) = DemoAuthResult(error = "Demo sign-in is unavailable in this build.")
    fun register(name: String, email: String, password: String, confirmation: String,
                 affiliation: CustomerAffiliation, department: String) =
        DemoAuthResult(error = "Demo registration is unavailable in this build.")
    fun signOut() = Unit
}
