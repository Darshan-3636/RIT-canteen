package edu.msrit.canteen.ui.screens

import androidx.compose.runtime.Composable
import edu.msrit.canteen.ui.components.RitEmptyState

@Composable
fun CloudAdminContent(page: AdminPage, sellerId: String?, ticketId: String?,
                      onSeller: (String) -> Unit, onTicket: (String) -> Unit) {
    RitEmptyState("Unavailable", "The local backend demo is unavailable in release builds.")
}
