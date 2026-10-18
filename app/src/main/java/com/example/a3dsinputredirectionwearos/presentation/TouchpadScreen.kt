package com.example.a3dsinputredirectionwearos.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.a3dsinputredirectionwearos.InputRedirectionBridge
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun TouchpadScreen(
    bridge: InputRedirectionBridge,
    onExit: () -> Unit,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var touchPoint by remember { mutableStateOf<Offset?>(null) }
    var coordLabel by remember { mutableStateOf("Touch 3DS Screen") }

    fun padRect(size: IntSize): Rect {
        if (size.width <= 0 || size.height <= 0) return Rect.Zero
        val side = min(size.width, size.height).toFloat()
        val w = side * 0.74f
        val h = w * 0.75f // 4:3 3DS bottom screen aspect ratio
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        return Rect(left, top, left + w, top + h)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .onSizeChanged { boxSize = it }
            .pointerInput(boxSize) {
                val rect = padRect(boxSize)
                if (rect.isEmpty) return@pointerInput
                awaitEachGesture {
                    fun updateFromOffset(pos: Offset) {
                        val clampedX = pos.x.coerceIn(rect.left, rect.right)
                        val clampedY = pos.y.coerceIn(rect.top, rect.bottom)
                        touchPoint = Offset(clampedX, clampedY)
                        val tx = (((clampedX - rect.left) / rect.width) * InputRedirectionBridge.TOUCH_MAX)
                            .roundToInt()
                            .coerceIn(0, InputRedirectionBridge.TOUCH_MAX)
                        val ty = (((clampedY - rect.top) / rect.height) * InputRedirectionBridge.TOUCH_MAX)
                            .roundToInt()
                            .coerceIn(0, InputRedirectionBridge.TOUCH_MAX)
                        coordLabel = "X:$tx Y:$ty"
                        bridge.updateTouch(true, tx, ty)
                    }

                    val down = awaitFirstDown()
                    if (!rect.contains(down.position)) return@awaitEachGesture
                    updateFromOffset(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        updateFromOffset(change.position)
                        change.consume()
                    }
                    touchPoint = null
                    coordLabel = "Released"
                    bridge.updateTouch(false, 0, 0)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rect = padRect(IntSize(size.width.toInt(), size.height.toInt()))
            if (!rect.isEmpty) {
                drawRect(
                    color = AppColors.Surface,
                    topLeft = rect.topLeft,
                    size = Size(rect.width, rect.height),
                )
                drawRect(
                    color = AppColors.Primary,
                    topLeft = rect.topLeft,
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = 3f),
                )
                touchPoint?.let { pt ->
                    drawCircle(color = AppColors.Start, radius = 10f, center = pt)
                }
            }
        }
        GlyphButton(
            onClick = onExit,
            diameter = 34.dp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
        ) { glyphBack(it) }
        Text(
            text = coordLabel,
            color = AppColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}
