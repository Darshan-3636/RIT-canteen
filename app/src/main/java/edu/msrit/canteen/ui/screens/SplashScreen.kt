package edu.msrit.canteen.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.R
import edu.msrit.canteen.ui.components.RitLoadingIndicator
import edu.msrit.canteen.ui.components.RitText
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun SplashScreen(onStartupComplete: () -> Unit) {
    val colors = RitThemeTokens.colors
    LaunchedEffect(Unit) { onStartupComplete() }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Image(
            painterResource(R.drawable.rit_campus),
            contentDescription = "RIT campus building",
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.rit_logo), contentDescription = "RIT logo", modifier = Modifier.height(62.dp))
            Spacer(Modifier.height(12.dp))
            RitText("RIT Canteen", RitType.heading)
            RitText("Good food. A closer campus.", RitType.body, colors.muted)
            Spacer(Modifier.height(24.dp))
            RitLoadingIndicator()
        }
    }
}
