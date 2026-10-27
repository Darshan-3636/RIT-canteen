package edu.msrit.canteen.customer

/** Release cannot create or expose demonstration order records. */
object DevelopmentIndividualOrders {
    val records: List<IndividualOrderPreview> get() = emptyList()
    fun saveLocal(draft: IndividualDraft, catalog: CustomerCatalogSource = PreviewCatalog): Boolean = false
    fun activate(userId: String) = Unit
    fun deactivate() = Unit
    fun reset() = Unit
}
