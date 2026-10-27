package edu.msrit.canteen.backend.features.commerce

import kotlinx.serialization.Serializable

@Serializable data class SellerDto(
    val id: String, val name: String, val description: String, val active: Boolean,
    val isMainCanteen: Boolean, val openStatus: String = "UNKNOWN",
    val bulkMinimumUnits: Int, val orderingModes: List<String>, val categoryIds: List<String>,
)
@Serializable data class MenuItemDto(
    val id: String, val sellerId: String, val name: String, val description: String,
    val pricePaise: Long, val kind: String, val dietary: String, val availability: String,
    val published: Boolean, val orderingModes: List<String>, val imageKey: String, val categoryId: String,
)
@Serializable data class SellerListDto(val sellers: List<SellerDto>)
@Serializable data class MenuDto(val sellerId: String, val categoryIds: List<String>, val items: List<MenuItemDto>)
@Serializable data class CatalogItemPatch(
    val name: String? = null, val description: String? = null, val pricePaise: Long? = null,
    val published: Boolean? = null, val dietary: String? = null, val unavailableVisibility: String? = null,
)
@Serializable data class AvailabilityPatch(val availability: String)
@Serializable data class DraftLine(val itemId: String, val quantity: Int)
@Serializable data class QuoteTermsRequest(
    val expectedVersion: Int? = null, val lines: List<DraftLine>, val pickupAt: String,
    val specialPickupReason: String? = null,
)
@Serializable data class BulkCreateRequest(val sellerId: String, val lines: List<DraftLine>, val pickupAt: String,
                                            val specialPickupReason: String? = null)
@Serializable data class ApprovalRequest(val expectedVersion: Int)
@Serializable data class SpecialPickupDecisionRequest(val expectedVersion: Int, val approved: Boolean)
@Serializable data class BulkPolicyPatch(val minimumUnits: Int? = null, val specialPickupEnabled: Boolean? = null)
@Serializable data class SellerHourEntry(val dayOfWeek: Int, val opensAt: String, val closesAt: String)
@Serializable data class SellerHoursRequest(val hours: List<SellerHourEntry>)
@Serializable data class SellerClosureRequest(val reason: String = "")
@Serializable data class BulkQuoteLineDto(
    val itemId: String, val itemName: String, val quantity: Int, val unitPricePaise: Long,
)
@Serializable data class BulkRequestDto(
    val id: String, val sellerId: String, val version: Int, val status: String, val pickupAt: String,
    val specialPickupReason: String? = null, val subtotalPaise: Long, val minimumUnits: Int,
    val expiresAt: String, val customerApproved: Boolean, val sellerApproved: Boolean,
    val specialPickupApproved: Boolean?, val lines: List<BulkQuoteLineDto>,
)
@Serializable data class BulkRequestListDto(val requests: List<BulkRequestDto>)
@Serializable data class CustomerDemoOrderRequest(val lines: List<DraftLine>)
@Serializable data class IndividualDemoLineDto(val itemId: String, val itemName: String, val quantity: Int,
                                                val unitPricePaise: Long)
@Serializable data class IndividualDemoOrderDto(
    val id: String, val sellerId: String, val status: String, val demoOnly: Boolean,
    val paymentVerified: Boolean, val subtotalPaise: Long, val createdAt: String,
    val lines: List<IndividualDemoLineDto>,
)
@Serializable data class IndividualDemoOrderListDto(val orders: List<IndividualDemoOrderDto>)
@Serializable data class PickupScheduleDto(val confirmed: List<BulkRequestDto>, val note: String)
