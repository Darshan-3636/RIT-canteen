package edu.msrit.canteen.navigation

/** Release variant deliberately contains no sample users and cannot create a mock session. */
object DevelopmentPreviewAccess {
    val identities: List<DevelopmentTestIdentity> = emptyList()
    val activeRole: DevelopmentRole? get() = null
    val activeIdentity: DevelopmentTestIdentity? get() = null
    fun select(role: DevelopmentRole) = Unit
    fun clearHubSelection() = Unit
    fun reset() = Unit
    fun allows(area: ProtectedArea): Boolean = false
}
