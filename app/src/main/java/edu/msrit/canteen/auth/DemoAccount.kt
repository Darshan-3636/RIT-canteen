package edu.msrit.canteen.auth

import edu.msrit.canteen.customer.CustomerAffiliation
import edu.msrit.canteen.navigation.DevelopmentRole

/** Data contract only. Credentials and session mutations live exclusively in the debug source set. */
data class DemoAccount(
    val id: String,
    val name: String,
    val email: String,
    val role: DevelopmentRole,
    val affiliation: CustomerAffiliation? = null,
    val department: String = "",
    val sellerId: String? = null,
)

data class DemoAuthResult(val account: DemoAccount? = null, val error: String? = null) {
    val succeeded: Boolean get() = account != null
}
