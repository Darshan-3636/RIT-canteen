package edu.msrit.canteen.backend.features.management

import kotlinx.serialization.Serializable

@Serializable internal data class VersionRequest(val expectedVersion: Long, val reason: String = "")
@Serializable internal data class ItemCreate(
    val id: String, val categoryId: String, val name: String, val pricePaise: Long,
    val kind: String, val imageKey: String, val description: String = "",
    val dietary: String = "NOT_SPECIFIED", val published: Boolean = false,
    val availability: String = "AVAILABLE", val unavailableVisibility: String = "SHOW",
    val orderEligibility: String = "BULK_ONLY",
)
@Serializable internal data class ManagedItem(
    val id: String, val sellerId: String, val categoryId: String, val name: String, val description: String,
    val pricePaise: Long, val kind: String, val dietary: String, val availability: String,
    val published: Boolean, val unavailableVisibility: String, val imageKey: String,
    val archivedAt: String?, val version: Long, val orderEligibility: String = "BULK_ONLY",
)
@Serializable internal data class CategoryWrite(
    val expectedVersion: Long, val displayName: String? = null, val sortOrder: Int = 0, val active: Boolean = true,
)
@Serializable internal data class CategoryDto(
    val id: String, val name: String, val displayName: String?, val sortOrder: Int, val active: Boolean,
    val globallyActive: Boolean, val version: Long,
)
@Serializable internal data class GlobalCategoryWrite(val id: String, val name: String, val sortOrder: Int = 0, val active: Boolean = true)
@Serializable internal data class MemberWrite(
    val userId: String? = null, val role: String, val supervisorMembershipId: String? = null,
    val capabilities: List<String> = emptyList(), val active: Boolean = true,
    val expectedVersion: Long? = null, val reason: String,
)
@Serializable internal data class GrantWrite(val capabilities: List<String>, val expectedVersion: Long, val reason: String)
@Serializable internal data class MemberDto(
    val id: String, val userId: String, val sellerId: String, val fullName: String, val role: String,
    val supervisorMembershipId: String?, val active: Boolean, val capabilities: List<String>, val version: Long,
)
@Serializable internal data class ProfileWrite(val name: String, val description: String, val expectedVersion: Long)
@Serializable internal data class SellerState(val id: String, val name: String, val description: String, val active: Boolean, val version: Long)
@Serializable internal data class UserStatusWrite(val status: String, val expectedVersion: Long, val reason: String)
@Serializable internal data class AdminFlagWrite(val enabled: Boolean, val expectedVersion: Long, val reason: String)
@Serializable internal data class UserState(val id: String, val status: String, val platformAdmin: Boolean, val version: Long)
@Serializable internal data class DraftDecision(val expectedVersion: Long, val reason: String, val ownerUserId: String? = null)
@Serializable internal data class DraftState(
    val id: String, val proposedSellerId: String, val sellerName: String, val status: String,
    val decision: String?, val ownerUserId: String?, val createdSellerId: String?, val version: Long,
)
@Serializable internal data class PolicyVersion(val version: Long)
