package edu.msrit.canteen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import edu.msrit.canteen.customer.pickupQrPayload

/** Pickup QR the seller scans at the counter. It encodes only the order's 4-digit pickup PIN. */
@Composable
fun RitPickupQr(pin: String, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val matrix = remember(pin) { QRCodeWriter().encode(pickupQrPayload(pin), BarcodeFormat.QR_CODE, 1, 1) }
    Canvas(modifier.size(size).background(Color.White).padding(10.dp)
        .semantics { contentDescription = "Pickup QR code. Your PIN is $pin" }) {
        val cell = this.size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell, cell))
        }
    }
}
