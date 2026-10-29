package edu.msrit.canteen

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import edu.msrit.canteen.customer.SandboxPayment

/** UAT checkout is not packaged or callable in release builds. */
internal object PhonePeSandboxCheckout {
    fun launch(activity: MainActivity, attempt: SandboxPayment,
               resultLauncher: ActivityResultLauncher<Intent>): Boolean = false
}
