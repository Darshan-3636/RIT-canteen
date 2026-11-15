package edu.msrit.canteen.customer

/** Release boundary: no mock business session or mutation is available. */
object DevelopmentSellerBusiness : SellerBusinessRepository {
    fun applyServerPolicy(policy: SellerBulkPolicy, reasons: List<SellerClosure> = emptyList()) = Unit
    override val employees: List<SellerEmployee> = emptyList()
    override val policyOverrides: Map<String, SellerBulkPolicy> = emptyMap()
    override val closureReasons: Map<String, List<SellerClosure>> = emptyMap()
    override val hiddenCategoryIds: Map<String, Set<String>> = emptyMap()
    override fun capabilities(userId: String, sellerId: String): Set<SellerCapability> = emptySet()
    override fun saveItem(userId: String, item: MenuItem) = false
    override fun setItemState(userId: String, sellerId: String, itemId: String, available: Boolean,
                              published: Boolean, showUnavailable: Boolean) = false
    override fun saveCategory(userId: String, sellerId: String, categoryId: String?, name: String) = false
    override fun moveCategory(userId: String, sellerId: String, categoryId: String, delta: Int) = false
    override fun hideCategory(userId: String, sellerId: String, categoryId: String, hidden: Boolean) = false
    override fun removeCategory(userId: String, sellerId: String, categoryId: String) = false
    override fun savePolicy(userId: String, policy: SellerBulkPolicy, reasons: List<SellerClosure>) = false
    override fun saveProfile(userId: String, sellerId: String, profile: SellerPublicProfile) = false
    override fun savePromotion(userId: String, promotion: Promotion) = false
    override fun saveEmployee(userId: String, employee: SellerEmployee) = false
    override fun removeEmployee(userId: String, sellerId: String, employeeId: String) = false
    override fun reset() = Unit
}
