package edu.msrit.canteen

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import edu.msrit.canteen.navigation.RitAppNavigation
import edu.msrit.canteen.ui.theme.RitTheme
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.AppearancePreference
import edu.msrit.canteen.customer.AppearanceChoice
import edu.msrit.canteen.customer.SandboxPayment

class MainActivity : ComponentActivity() {
    private var activeSandboxPaymentId: String? = null
    private var sandboxCallbackId by mutableStateOf<String?>(null)
    private val sandboxResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // SDK result is a signal to ask our backend to verify provider status, not a payment result.
        sandboxCallbackId = activeSandboxPaymentId
    }

    private fun launchSandbox(attempt: SandboxPayment): Boolean {
        sandboxCallbackId = null
        val launched = PhonePeSandboxCheckout.launch(this, attempt, sandboxResultLauncher)
        if (launched) activeSandboxPaymentId = attempt.id
        return launched
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance = remember { AppearancePreference(this) }
            val systemDark = isSystemInDarkTheme()
            val dark = when (appearance.choice) {
                AppearanceChoice.SYSTEM -> systemDark
                AppearanceChoice.LIGHT -> false
                AppearanceChoice.DARK -> true
            }
            RitTheme(darkTheme = dark) {
                val colors = RitThemeTokens.colors
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !colors.isDark
                        isAppearanceLightNavigationBars = !colors.isDark
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
                }
                Box(Modifier.fillMaxSize().background(colors.background)) {
                    RitAppNavigation(appearance = appearance, onSandboxCheckout = ::launchSandbox,
                        sandboxCallbackId = sandboxCallbackId)
                }
            }
        }
    }
}
