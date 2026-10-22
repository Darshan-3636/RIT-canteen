package edu.msrit.canteen.navigation

/** Types only. Actual sample identities and mutable sessions exist in the debug source set. */
enum class DevelopmentRole { Customer, SellerOwner, SellerManager, SellerStaff, SellerStaffOperations, PlatformAdministrator }

data class DevelopmentTestIdentity(
    val role: DevelopmentRole,
    val label: String,
    val sampleName: String,
    val authorization: AuthorizedIdentity,
)
