package ru.mukiva.photon.demo.ui.photon

import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import ru.mukiva.photon.demo.ui.theme.LocalPhotonController
import kotlin.math.min

private const val MOVE_DURATION_MS = 320
private const val DOT_SIZE_DP = 12
private const val PHOTON_ALPHA = 0.5f

// Дистанция прыжка, начиная с которой фотон сжимается в точку максимально.
private const val FULL_SHRINK_DISTANCE_DP = 240

/**
 * Полноэкранный оверлей, рисующий фотон на канвасе. Читает текущий переход из
 * [LocalPhotonController] и делегирует анимацию и вычисление геометрии
 * [PhotonRenderer].
 *
 * Всё, что меняется каждый кадр (доля перелёта, сжатие, живые границы целей),
 * читается внутри [drawBehind], т.е. в фазе отрисовки — это инвалидирует только
 * draw, поэтому перелёт фотона не вызывает рекомпозиций.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PhotonOverlay(modifier: Modifier = Modifier) {
    val controller = LocalPhotonController.current
    val density = LocalDensity.current
    val transition = controller.transition
    val photonColor = MaterialTheme.colorScheme.primary.copy(alpha = PHOTON_ALPHA)

    val dotPx = with(density) { DOT_SIZE_DP.dp.toPx() }
    val fullShrinkPx = with(density) { FULL_SHRINK_DISTANCE_DP.dp.toPx() }
    val renderer = remember(dotPx, fullShrinkPx) {
        PhotonRenderer(MOVE_DURATION_MS, dotPx, fullShrinkPx)
    }
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(renderer, transition) {
        transition?.let { renderer.animate(it) }
    }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOffset = it.boundsInWindow().topLeft }
            .drawBehind {
                val frame = renderer.frame(transition, controller, overlayOffset)
                    ?: return@drawBehind
                drawRoundRect(
                    color = photonColor,
                    topLeft = Offset(frame.left, frame.top),
                    size = Size(frame.width, frame.height),
                    cornerRadius = CornerRadius(min(frame.width, frame.height) / 2f),
                )
            },
    )
}
