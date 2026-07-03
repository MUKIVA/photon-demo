package ru.mukiva.photon.demo.ui.photon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.mukiva.photon.demo.ui.theme.LocalPhotonController
import kotlin.math.roundToInt

private const val MOVE_DURATION_MS = 320
private const val MIN_PINCH = 0f

/**
 * Полноэкранный оверлей, рисующий фотон. Читает цель из [LocalPhotonController]
 * и плавно перемещает фотон, сжимая его в точку во время движения и принимая
 * размеры цели в конце.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PhotonOverlay(modifier: Modifier = Modifier) {
    val controller = LocalPhotonController.current
    val density = LocalDensity.current
    val target = controller.target

    // Анимируемые значения хранятся в координатах окна; смещение оверлея
    // вычитается только при отрисовке.
    val center = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val photonSize = remember { Animatable(Size.Zero, Size.VectorConverter) }
    val pinch = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(target) {
        val bounds = target ?: return@LaunchedEffect
        val targetCenter = bounds.center
        val targetSize = Size(bounds.width, bounds.height)
        if (!initialized) {
            center.snapTo(targetCenter)
            photonSize.snapTo(targetSize)
            pinch.snapTo(1f)
            initialized = true
            return@LaunchedEffect
        }
        coroutineScope {
            launch { center.animateTo(targetCenter, tween(MOVE_DURATION_MS)) }
            launch { photonSize.animateTo(targetSize, tween(MOVE_DURATION_MS)) }
            launch {
                pinch.animateTo(MIN_PINCH, tween(MOVE_DURATION_MS / 2))
                pinch.animateTo(1f, tween(MOVE_DURATION_MS / 2))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOffset = it.boundsInWindow().topLeft },
    ) {
        if (!initialized) return@Box

        val dotPx = with(density) { 12.dp.toPx() }
        val widthPx = dotPx + (photonSize.value.width - dotPx) * pinch.value
        val heightPx = dotPx + (photonSize.value.height - dotPx) * pinch.value
        val left = center.value.x - overlayOffset.x - widthPx / 2f
        val top = center.value.y - overlayOffset.y - heightPx / 2f
        val photonColor = MaterialTheme.colorScheme.primary

        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(
                    width = with(density) { widthPx.toDp() },
                    height = with(density) { heightPx.toDp() },
                )
                .background(
                    color = photonColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(percent = 50),
                ),
        )
    }
}
