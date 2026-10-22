package edu.msrit.canteen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class RitPalette(
    val background: Color,
    val surface: Color,
    val card: Color,
    val warmCard: Color,
    val foreground: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val outline: Color,
    val onAccent: Color,
    val scrim: Color,
    val isDark: Boolean,
)

private val LightPalette = RitPalette(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    warmCard = Color(0xFFFFF5EF),
    foreground = Color(0xFF101622),
    muted = Color(0xFF69717E),
    accent = Color(0xFFFF5A2D),
    accentSoft = Color(0xFFFFEEE5),
    outline = Color(0xFFE2E4E8),
    onAccent = Color.White,
    scrim = Color(0xAA000000),
    isDark = false,
)

private val DarkPalette = RitPalette(
    background = Color(0xFF090B0F),
    surface = Color(0xFF101318),
    card = Color(0xFF191E25),
    warmCard = Color(0xFF211C1B),
    foreground = Color(0xFFF7F8FA),
    muted = Color(0xFF9DA5B0),
    accent = Color(0xFFFF6343),
    accentSoft = Color(0xFF312321),
    outline = Color(0xFF303640),
    onAccent = Color.White,
    scrim = Color(0xBB000000),
    isDark = true,
)

private val LocalRitPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun RitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRitPalette provides if (darkTheme) DarkPalette else LightPalette) {
        content()
    }
}

object RitThemeTokens {
    val colors: RitPalette
        @Composable get() = LocalRitPalette.current
}

object RitSpacing {
    val xSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val xLarge: Dp = 24.dp
    val xxLarge: Dp = 32.dp
}

object RitRadii {
    val small: Dp = 12.dp
    val card: Dp = 20.dp
    val hero: Dp = 24.dp
    val pill: Dp = 100.dp
}

object RitType {
    private val family = FontFamily.SansSerif
    val display = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 37.sp)
    val heading = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 29.sp)
    val title = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp)
    val body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val small = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp)
    val label = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp)
    val quickTitle = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp)
    val quickCaption = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 13.sp)
}
