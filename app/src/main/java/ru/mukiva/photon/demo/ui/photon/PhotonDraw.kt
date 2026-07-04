package ru.mukiva.photon.demo.ui.photon

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint

// Полупрозрачность ореола у самого ядра фотона; к краям затухает по блюру до нуля.
private const val PHOTON_GLOW_ALPHA = 0.6f

/**
 * Рисует мягкий ореол (свечение) вокруг прямоугольника фотона указанным [color].
 *
 * Ореол — тот же скруглённый прямоугольник, но размытый [BlurMaskFilter], поэтому
 * его альфа плавно спадает от ядра к краям. За счёт этого на нём удобно проверять,
 * как ведёт себя покраска ([BlendMode.SrcAtop]) на полупрозрачных пикселях: рисуя
 * ореол цветом покраски в SrcAtop, контент тонируется пропорционально покрытию.
 */
internal fun Canvas.drawPhotonGlow(
    topLeft: Offset,
    size: Size,
    corner: CornerRadius,
    color: Color,
    blurRadiusPx: Float,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    if (blurRadiusPx <= 0f) return
    val paint = Paint().apply {
        this.color = color.copy(alpha = color.alpha * PHOTON_GLOW_ALPHA)
        this.blendMode = blendMode
        asFrameworkPaint().maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
    }
    drawRoundRect(
        left = topLeft.x,
        top = topLeft.y,
        right = topLeft.x + size.width,
        bottom = topLeft.y + size.height,
        radiusX = corner.x,
        radiusY = corner.y,
        paint = paint,
    )
}
