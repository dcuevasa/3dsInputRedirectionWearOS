package com.example.a3dsinputredirectionwearos.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppColors {
    val Background = Color(0xFF0F1216)
    val Surface = Color(0xFF1C212A)
    val SurfaceVariant = Color(0xFF2C323D)
    val Primary = Color(0xFF5AC8FA)
    val OnPrimary = Color(0xFF04222E)
    val Start = Color(0xFF35C759)
    val Stop = Color(0xFFFF5A5A)
    val OnSurface = Color(0xFFE8ECF2)
    val Muted = Color(0xFF8A92A0)
}

object AppDimens {
    val Hero = 52.dp
    val NavIcon = 38.dp
    val SmallIcon = 34.dp
}

@Composable
fun GlyphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = AppDimens.NavIcon,
    background: Color = AppColors.Surface,
    tint: Color = AppColors.OnSurface,
    enabled: Boolean = true,
    glyph: DrawScope.(Color) -> Unit,
) {
    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (enabled) background else AppColors.Surface.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val c = if (enabled) tint else AppColors.Muted
        Canvas(Modifier.size(diameter * 0.46f)) { glyph(c) }
    }
}

@Composable
fun HeroButton(
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = AppDimens.Hero,
) {
    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (running) AppColors.Stop else AppColors.Start)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.38f)) {
            if (running) glyphStop(AppColors.OnSurface) else glyphPlay(AppColors.OnSurface)
        }
    }
}

fun DrawScope.glyphPlay(color: Color) {
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * 0.22f, h * 0.08f)
            lineTo(w * 0.22f, h * 0.92f)
            lineTo(w * 0.92f, h * 0.5f)
            close()
        },
        color,
    )
}

fun DrawScope.glyphStop(color: Color) {
    drawRoundRect(
        color,
        topLeft = Offset(size.width * 0.14f, size.height * 0.14f),
        size = Size(size.width * 0.72f, size.height * 0.72f),
        cornerRadius = CornerRadius(size.minDimension * 0.14f),
    )
}

fun DrawScope.glyphTune(color: Color) {
    val t = size.height * 0.1f
    val ys = listOf(0.22f, 0.5f, 0.78f)
    val knobX = listOf(0.68f, 0.34f, 0.6f)
    ys.forEachIndexed { i, ry ->
        val y = size.height * ry
        drawLine(color, Offset(size.width * 0.04f, y), Offset(size.width * 0.96f, y), strokeWidth = t, cap = StrokeCap.Round)
        drawCircle(color, radius = size.height * 0.12f, center = Offset(size.width * knobX[i], y))
    }
}

fun DrawScope.glyphLogs(color: Color) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.09f
    drawRoundRect(
        color,
        topLeft = Offset(w * 0.16f, h * 0.05f),
        size = Size(w * 0.68f, h * 0.9f),
        cornerRadius = CornerRadius(w * 0.1f),
        style = Stroke(width = sw),
    )
    listOf(0.32f, 0.5f, 0.68f).forEach { ry ->
        drawLine(color, Offset(w * 0.32f, h * ry), Offset(w * 0.68f, h * ry), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** 3DS bottom-screen touchpad glyph: rounded 4:3 rectangle with a touch point inside. */
fun DrawScope.glyphTouchpad(color: Color) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.09f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.08f, h * 0.16f),
        size = Size(w * 0.84f, h * 0.68f),
        cornerRadius = CornerRadius(w * 0.1f),
        style = Stroke(width = sw),
    )
    drawCircle(color = color, radius = size.minDimension * 0.14f, center = Offset(w * 0.5f, h * 0.5f))
}

/** 3DS Special commands glyph (Home / Power). */
fun DrawScope.glyphSpecial(color: Color) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.1f
    drawArc(
        color = color,
        startAngle = -55f,
        sweepAngle = 290f,
        useCenter = false,
        topLeft = Offset(w * 0.15f, h * 0.18f),
        size = Size(w * 0.7f, h * 0.7f),
        style = Stroke(width = sw, cap = StrokeCap.Round),
    )
    drawLine(
        color = color,
        start = Offset(w * 0.5f, h * 0.08f),
        end = Offset(w * 0.5f, h * 0.48f),
        strokeWidth = sw,
        cap = StrokeCap.Round,
    )
}

/** Gamepad glyph for controller management screen. */
fun DrawScope.glyphGamepad(color: Color) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.08f
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.1f, h * 0.22f),
        size = Size(w * 0.8f, h * 0.56f),
        cornerRadius = CornerRadius(w * 0.22f),
        style = Stroke(width = sw),
    )
    // D-Pad cross on left
    drawLine(color, Offset(w * 0.24f, h * 0.5f), Offset(w * 0.40f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.32f, h * 0.38f), Offset(w * 0.32f, h * 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
    // Face buttons on right
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.68f, h * 0.42f))
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.76f, h * 0.50f))
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.68f, h * 0.58f))
    drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.60f, h * 0.50f))
}

/** Bluetooth icon glyph. */
fun DrawScope.glyphBluetooth(color: Color) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.11f
    val path = Path().apply {
        // Upper arrow & lower arrow crossing center
        moveTo(w * 0.28f, h * 0.28f)
        lineTo(w * 0.72f, h * 0.72f)
        lineTo(w * 0.50f, h * 0.94f)
        lineTo(w * 0.50f, h * 0.06f)
        lineTo(w * 0.72f, h * 0.28f)
        lineTo(w * 0.28f, h * 0.72f)
    }
    drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Button Remap / Wizard glyph. */
fun DrawScope.glyphRemap(color: Color) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.09f
    // Two circular exchange arrows
    drawArc(
        color = color,
        startAngle = 30f,
        sweepAngle = 130f,
        useCenter = false,
        topLeft = Offset(w * 0.12f, h * 0.12f),
        size = Size(w * 0.76f, h * 0.76f),
        style = Stroke(width = sw, cap = StrokeCap.Round),
    )
    drawArc(
        color = color,
        startAngle = 210f,
        sweepAngle = 130f,
        useCenter = false,
        topLeft = Offset(w * 0.12f, h * 0.12f),
        size = Size(w * 0.76f, h * 0.76f),
        style = Stroke(width = sw, cap = StrokeCap.Round),
    )
    // Small arrow heads
    drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.84f, h * 0.50f))
    drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.16f, h * 0.50f))
}

fun DrawScope.glyphBack(color: Color) {
    val sw = size.minDimension * 0.16f
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.62f, h * 0.2f), Offset(w * 0.34f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.62f, h * 0.8f), strokeWidth = sw, cap = StrokeCap.Round)
}
