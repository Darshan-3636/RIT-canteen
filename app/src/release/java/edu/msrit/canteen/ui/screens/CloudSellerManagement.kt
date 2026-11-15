package edu.msrit.canteen.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import edu.msrit.canteen.ui.components.RitEmptyState

@Composable
fun CloudSellerArchivedItems(sellerId: String, modifier: Modifier = Modifier) {
    RitEmptyState("Unavailable", "Seller management requires an authorized backend session.")
}

@Composable
fun CloudSellerCategories(sellerId: String) {
    RitEmptyState("Unavailable", "Seller management requires an authorized backend session.")
}

@Composable
fun CloudSellerEmployees(sellerId: String, onEmployee: (String?) -> Unit) {
    RitEmptyState("Unavailable", "Seller management requires an authorized backend session.")
}

@Composable
fun CloudSellerEmployeeEditor(sellerId: String, employeeId: String?, onSaved: () -> Unit) {
    RitEmptyState("Unavailable", "Seller management requires an authorized backend session.")
}
