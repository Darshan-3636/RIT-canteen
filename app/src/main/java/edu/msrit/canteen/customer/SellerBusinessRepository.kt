package edu.msrit.canteen.customer

/** Local preview boundary. The release implementation is inert; neither is server authorization. */
interface SellerBusinessRepository {
    val employees: List<SellerEmployee>
    val policyOverrides: Map<String, SellerBulkPolicy>
    val closureReasons: Map<String, List<SellerClosure>>
    val hiddenCategoryIds: Map<String, Set<String>>
    fun capabilities(userId: String, sellerId: String): Set<SellerCapability>
    fun saveItem(userId: String, item: MenuItem): Boolean
    fun setItemState(userId: String, sellerId: String, itemId: String, available: Boolean, published: Boolean, showUnavailable: Boolean): Boolean
    fun saveCategory(userId: String, sellerId: String, categoryId: String?, name: String): Boolean
    fun moveCategory(userId: String, sellerId: String, categoryId: String, delta: Int): Boolean
    fun hideCategory(userId: String, sellerId: String, categoryId: String, hidden: Boolean): Boolean
    fun removeCategory(userId: String, sellerId: String, categoryId: String): Boolean
    fun savePolicy(userId: String, policy: SellerBulkPolicy, reasons: List<SellerClosure>): Boolean
    fun saveProfile(userId: String, sellerId: String, profile: SellerPublicProfile): Boolean
    fun savePromotion(userId: String, promotion: Promotion): Boolean
    fun saveEmployee(userId: String, employee: SellerEmployee): Boolean
    fun removeEmployee(userId: String, sellerId: String, employeeId: String): Boolean
    fun reset()
}
