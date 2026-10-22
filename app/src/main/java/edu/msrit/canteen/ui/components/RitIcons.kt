package edu.msrit.canteen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class RitIconKind { Home, Cart, Orders, Heart, Person, Search, Filter, Food, Bulk, Store, Arrow, Back, Mail, Close }

@Composable
fun RitIcon(kind: RitIconKind, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val sx = this.size.width / 24f
        val sy = this.size.height / 24f
        scale(sx, sy, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(tint, Offset(x1, y1), Offset(x2, y2), 1.8f, StrokeCap.Round)
            fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), tint, style = stroke)
            when (kind) {
                RitIconKind.Home -> path {
                    moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
                    lineTo(21f, 21f); lineTo(3f, 21f); close()
                    moveTo(9f, 21f); lineTo(9f, 14f); lineTo(15f, 14f); lineTo(15f, 21f)
                }
                RitIconKind.Cart -> {
                    path { moveTo(2f, 4f); lineTo(5f, 4f); lineTo(7f, 16f); lineTo(19f, 16f); lineTo(22f, 7f); lineTo(6f, 7f) }
                    drawCircle(tint, 1.3f, Offset(9f, 20f))
                    drawCircle(tint, 1.3f, Offset(18f, 20f))
                }
                RitIconKind.Orders -> {
                    path { moveTo(5f, 3f); lineTo(19f, 3f); lineTo(19f, 21f); lineTo(5f, 21f); close() }
                    line(8f, 8f, 16f, 8f); line(8f, 12f, 16f, 12f); line(8f, 16f, 14f, 16f)
                }
                RitIconKind.Heart -> path {
                    moveTo(12f, 21f); cubicTo(8f, 18f, 2f, 13f, 2f, 8f)
                    cubicTo(2f, 3f, 8f, 1f, 12f, 6f)
                    cubicTo(16f, 1f, 22f, 3f, 22f, 8f)
                    cubicTo(22f, 13f, 16f, 18f, 12f, 21f); close()
                }
                RitIconKind.Person -> {
                    drawCircle(tint, 3.3f, Offset(12f, 7f), style = stroke)
                    path { moveTo(4f, 21f); lineTo(4f, 19f); cubicTo(4f, 12f, 20f, 12f, 20f, 19f); lineTo(20f, 21f); close() }
                }
                RitIconKind.Search -> {
                    drawCircle(tint, 7f, Offset(10.5f, 10.5f), style = stroke)
                    line(16f, 16f, 21f, 21f)
                }
                RitIconKind.Filter -> {
                    line(3f, 6f, 21f, 6f); line(3f, 12f, 21f, 12f); line(3f, 18f, 21f, 18f)
                    drawCircle(tint, 1.7f, Offset(9f, 6f), style = stroke)
                    drawCircle(tint, 1.7f, Offset(16f, 12f), style = stroke)
                    drawCircle(tint, 1.7f, Offset(8f, 18f), style = stroke)
                }
                RitIconKind.Food -> {
                    line(5f, 3f, 5f, 21f); line(2f, 3f, 2f, 9f); line(8f, 3f, 8f, 9f)
                    path { moveTo(2f, 9f); quadraticTo(5f, 12f, 8f, 9f) }
                    path { moveTo(18f, 3f); cubicTo(12f, 3f, 12f, 12f, 18f, 13f); lineTo(18f, 21f) }
                }
                RitIconKind.Bulk -> {
                    path { moveTo(3f, 8f); lineTo(12f, 3f); lineTo(21f, 8f); lineTo(12f, 13f); close() }
                    path { moveTo(3f, 8f); lineTo(3f, 18f); lineTo(12f, 22f); lineTo(21f, 18f); lineTo(21f, 8f) }
                    line(12f, 13f, 12f, 22f)
                }
                RitIconKind.Store -> {
                    path { moveTo(3f, 10f); lineTo(5f, 4f); lineTo(19f, 4f); lineTo(21f, 10f); lineTo(21f, 21f); lineTo(3f, 21f); close() }
                    path { moveTo(3f, 10f); quadraticTo(5f, 15f, 8f, 10f); quadraticTo(12f, 15f, 16f, 10f); quadraticTo(19f, 15f, 21f, 10f) }
                    path { moveTo(10f, 21f); lineTo(10f, 16f); lineTo(15f, 16f); lineTo(15f, 21f) }
                }
                RitIconKind.Arrow -> { line(4f, 12f, 20f, 12f); line(14f, 6f, 20f, 12f); line(14f, 18f, 20f, 12f) }
                RitIconKind.Back -> { line(20f, 12f, 4f, 12f); line(10f, 6f, 4f, 12f); line(10f, 18f, 4f, 12f) }
                RitIconKind.Mail -> path {
                    moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(3f, 19f); close()
                    moveTo(3f, 6f); lineTo(12f, 13f); lineTo(21f, 6f)
                }
                RitIconKind.Close -> { line(5f, 5f, 19f, 19f); line(19f, 5f, 5f, 19f) }
            }
        }
    }
}
