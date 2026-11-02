package edu.msrit.canteen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Local demonstration QR. Payload is only an opaque random code, never a payment assertion. */
@Composable
fun RitDemoQr(code: String, modifier: Modifier = Modifier) {
    val matrix = remember(code) { QRCodeWriter().encode("RIT-DEMO:$code", BarcodeFormat.QR_CODE, 1, 1) }
    Canvas(modifier.size(192.dp).background(Color.White).padding(8.dp)
        .semantics { contentDescription = "Demo pickup QR code; text code $code is shown below" }) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, topLeft = Offset(x * cell, y * cell),
                size = androidx.compose.ui.geometry.Size(cell, cell))
        }
    }
}
