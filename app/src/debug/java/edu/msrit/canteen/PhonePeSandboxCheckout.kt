package edu.msrit.canteen

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.phonepe.intent.sdk.api.PhonePeKt
import com.phonepe.intent.sdk.api.models.PhonePeEnvironment
import edu.msrit.canteen.customer.SandboxPayment

/** Debug cloud-demo only. App receives a short-lived order token, never merchant credentials. */
internal object PhonePeSandboxCheckout {
    fun launch(activity: MainActivity, attempt: SandboxPayment,
               resultLauncher: ActivityResultLauncher<Intent>): Boolean {
        if (!BuildConfig.DEBUG || !BuildConfig.RIT_CLOUD_DEMO || attempt.status != "PENDING") return false
        val merchantId = attempt.merchantId?.takeIf(String::isNotBlank) ?: return false
        val token = attempt.sdkToken?.takeIf(String::isNotBlank) ?: return false
        val orderId = attempt.providerOrderId?.takeIf(String::isNotBlank) ?: return false
        return try {
            if (!PhonePeKt.init(activity, merchantId, attempt.id.replace("-", ""),
                    PhonePeEnvironment.SANDBOX, false, null)) return false
            PhonePeKt.startCheckoutPage(activity, token, orderId, resultLauncher)
            true
        } catch (_: Exception) {
            // The ActivityResult is never evidence of payment. Let the user request a server status check.
            false
        }
    }
}
