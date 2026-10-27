package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.ui.components.CatalogHeader
import edu.msrit.canteen.ui.components.RitMessageCard
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun CustomerUnavailableScreen(section: String, onBack: () -> Unit) {
    val title = when (section) {
        "bulk" -> "Bulk Orders"
        "orders" -> "Orders"
        "favourites" -> "Favourites"
        "profile" -> "Profile"
        "access" -> "Mock access required"
        else -> "Coming later"
    }
    Column(Modifier.fillMaxSize().background(RitThemeTokens.colors.background)) {
        CatalogHeader(title, onBack)
        Column(Modifier.padding(20.dp)) {
            RitText(title, RitType.heading)
            Spacer(Modifier.height(12.dp))
            RitMessageCard("Not available in this preview", when (section) {
                "bulk" -> "Group request and negotiation screens belong to the next development stage. No request can be submitted here."
                "orders" -> "There is no connected order history yet. This preview has not placed any orders."
                "favourites" -> "Saving favourites is planned for a later stage; nothing is stored by this preview."
                "profile" -> "A verified customer account and profile require the email authentication service."
                "access" -> "Sign in with an authorized customer account to continue. Demo accounts are available only in development builds."
                else -> "This feature is not connected yet."
            })
        }
    }
}
