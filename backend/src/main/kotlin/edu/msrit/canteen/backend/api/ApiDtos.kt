package edu.msrit.canteen.backend.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiErrorEnvelope(val error: ApiError)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)

@Serializable
data class RegisterRequest(
    val fullName: String,
    val email: String,
    val password: String,
    val affiliation: CustomerAffiliation,
    val department: String? = null,
)

@Serializable
enum class CustomerAffiliation { STUDENT, FACULTY }

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class SellerMembershipDto(
    val sellerId: String,
    val role: SellerRole,
    val capabilities: List<String>,
)

@Serializable
enum class SellerRole { OWNER, MANAGER, STAFF }

@Serializable
data class UserIdentityDto(
    val id: String,
    val email: String,
    val fullName: String,
    val affiliation: CustomerAffiliation? = null,
    val platformAdmin: Boolean,
    val memberships: List<SellerMembershipDto> = emptyList(),
)

@Serializable
data class RegisterResponse(val user: UserIdentityDto)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val expiresAt: String,
    val user: UserIdentityDto,
)

@Serializable
data class MeResponse(val user: UserIdentityDto)

@Serializable
data class ProfileDto(
    val id: String,
    val email: String,
    val fullName: String,
    val affiliation: CustomerAffiliation? = null,
    val department: String? = null,
)

@Serializable
data class UpdateProfileRequest(
    val fullName: String? = null,
    val affiliation: CustomerAffiliation? = null,
    val department: String? = null,
)

@Serializable
data class HealthResponse(val status: String)
