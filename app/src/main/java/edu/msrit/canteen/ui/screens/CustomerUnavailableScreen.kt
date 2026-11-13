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
        "access" -> "Sign in required"
        "payment" -> "Payment unavailable"
        else -> "Coming later"
    }
    Column(Modifier.fillMaxSize().background(RitThemeTokens.colors.background)) {
        CatalogHeader(title, onBack)
        Column(Modifier.padding(20.dp)) {
            RitText(title, RitType.heading)
            Spacer(Modifier.height(12.dp))
            RitMessageCard("Not available", when (section) {
                "bulk" -> "Bulk requests aren't available right now."
                "orders" -> "You haven't placed any orders yet."
                "favourites" -> "Favourites aren't available right now."
                "profile" -> "Sign in to see your profile."
                "access" -> "Sign in with your customer account to continue."
                "payment" -> "Payment isn't available right now. Please try again later."
                else -> "This isn't available right now."
            })
        }
    }
}
