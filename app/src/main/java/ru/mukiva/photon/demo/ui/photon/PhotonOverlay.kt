package ru.mukiva.photon.demo.ui.photon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ru.mukiva.photon.demo.ui.theme.LocalPhotonController
import kotlin.math.min

/**
 * Полноэкранный оверлей, рисующий белый фотон на канвасе. Геометрию берёт из
 * [LocalPhotonController], а анимацию перехода гонит через [PhotonController.animate].
 *
 * Фотон рисуется в offscreen-слое, из которого вырезаются (BlendMode.Clear) все
 * зарегистрированные зоны [PhotonController.maskRegions]. В этих дырках свою часть
 * фотона и перекрашенный контент дорисовывают сами элементы (см. [recolorUnderPhoton]),
 * поэтому контент оказывается «поверх» фотона без возни с z-индексами.
 *
 * Всё, что меняется каждый кадр, читается внутри [drawBehind] — это инвалидирует
 * только draw, поэтому перелёт фотона не вызывает рекомпозиций.
 */
@Composable
fun PhotonOverlay(modifier: Modifier = Modifier) {
    val controller = LocalPhotonController.current
    val density = LocalDensity.current
    val transition = controller.transition

    val dotPx = with(density) { PHOTON_DOT_SIZE_DP.dp.toPx() }
    val fullShrinkPx = with(density) { PHOTON_FULL_SHRINK_DISTANCE_DP.dp.toPx() }
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    val layerPaint = remember { Paint() }

    LaunchedEffect(controller, transition) {
        transition?.let { controller.animate(it) }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOffset = it.boundsInWindow().topLeft },
    ) {
        val photon = controller.photonWindowRect(dotPx, fullShrinkPx)
            ?: return@Canvas
        val left = photon.left - overlayOffset.x
        val top = photon.top - overlayOffset.y
        val radius = min(photon.width, photon.height) / 2f

        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(photon.width, photon.height),
            cornerRadius = CornerRadius(radius),
        )
        // Вырезаем зоны помеченных элементов — там фотон дорисует сам элемент.
        controller.maskRegions.forEach { mask ->
            drawRect(
                color = Color.Black,
                topLeft = Offset(mask.left - overlayOffset.x, mask.top - overlayOffset.y),
                size = Size(mask.width, mask.height),
                blendMode = BlendMode.Clear,
            )
        }
        canvas.restore()
    }
}
