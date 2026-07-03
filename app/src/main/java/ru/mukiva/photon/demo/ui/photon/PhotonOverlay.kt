package ru.mukiva.photon.demo.ui.photon

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ru.mukiva.photon.demo.ui.theme.LocalPhotonController
import kotlin.math.roundToInt

private const val MOVE_DURATION_MS = 320
private const val MIN_PINCH = 0f

/**
 * Полноэкранный оверлей, рисующий фотон. Читает текущий переход из
 * [LocalPhotonController] и трактует его как транзакцию между двумя целями:
 * анимируется единая доля [0f; 1f], а координаты старта и финиша на каждом кадре
 * берутся из актуальных границ целей. Благодаря этому фотон всегда стартует из
 * реального положения предыдущей цели, даже если она сместилась после потери
 * фокуса, и приземляется точно в текущую цель.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PhotonOverlay(modifier: Modifier = Modifier) {
    val controller = LocalPhotonController.current
    val density = LocalDensity.current
    val transition = controller.transition

    // Доля прохождения перехода: 0f — на цели from, 1f — на цели to.
    val progress = remember { Animatable(1f) }
    // Сжатие фотона в точку на середине пути.
    val pinch = remember { Animatable(1f) }
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    // Переход, для которого уже настроена анимация. Пока новый переход не
    // подхвачен эффектом, при отрисовке считаем долю равной 0f (фотон на from),
    // иначе на один кадр он мигнул бы в позицию to до старта анимации.
    var animatedTransition by remember { mutableStateOf<PhotonTransition?>(null) }

    LaunchedEffect(transition) {
        val t = transition ?: return@LaunchedEffect
        if (t.from == null) {
            // Первая цель — появляемся на месте без перелёта.
            progress.snapTo(1f)
            pinch.snapTo(1f)
            animatedTransition = t
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        pinch.snapTo(1f)
        animatedTransition = t
        coroutineScope {
            launch { progress.animateTo(1f, tween(MOVE_DURATION_MS)) }
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
        val toBounds = controller.boundsOf(transition?.to) ?: return@Box
        // Пока границы from ещё не измерены (или цель исчезла) — стартуем из to.
        val fromBounds = controller.boundsOf(transition?.from) ?: toBounds

        // Пока эффект не подхватил новый переход, держим фотон на from (0f),
        // чтобы не было мигания к to и обратно.
        val fraction = if (animatedTransition === transition) progress.value else 0f
        val centerX = lerp(fromBounds.center.x, toBounds.center.x, fraction)
        val centerY = lerp(fromBounds.center.y, toBounds.center.y, fraction)
        val targetWidth = lerp(fromBounds.width, toBounds.width, fraction)
        val targetHeight = lerp(fromBounds.height, toBounds.height, fraction)

        val dotPx = with(density) { 12.dp.toPx() }
        val widthPx = dotPx + (targetWidth - dotPx) * pinch.value
        val heightPx = dotPx + (targetHeight - dotPx) * pinch.value
        val left = centerX - overlayOffset.x - widthPx / 2f
        val top = centerY - overlayOffset.y - heightPx / 2f
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
