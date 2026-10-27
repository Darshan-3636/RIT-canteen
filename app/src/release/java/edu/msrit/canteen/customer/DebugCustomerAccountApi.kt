package edu.msrit.canteen.customer

object DebugCustomerAccountApi {
    data class Result(val profile: CustomerProfile? = null, val error: String? = "Local demo unavailable in release.")
    fun fetchProfile() = Result()
    fun updateProfile(profile: CustomerProfile) = Result()
}
