package ru.mukiva.photon.demo.ui.photon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

// Симметричная ease-in-out кривая: половину пути разгон, половину — торможение.
private val MoveEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * Готовые к отрисовке параметры фотона в координатах оверлея.
 */
@Immutable
data class PhotonFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Инкапсулирует анимацию перехода фотона и вычисление его геометрии.
 *
 * Анимируется единственное значение — доля пути [progress] (0f→1f) по симметричной
 * ease-in-out кривой: фотон разгоняется первую половину пути и тормозит вторую.
 *
 * Сжатие фотона (shrink) не анимируется отдельно и не зависит от длительности:
 * оно вычисляется из нормированной скорости `sin(pi * progress)` — она равна нулю
 * на концах пути и максимальна в середине, где скорость максимальна. Дополнительно
 * сила сжатия масштабируется расстоянием прыжка: короткие переходы почти не
 * сжимаются, дальние — схлопываются в точку.
 *
 * [frame] намеренно вызывается из фазы отрисовки (`drawBehind`/`Canvas`): чтение
 * анимируемого значения там инвалидирует только draw, но не композицию.
 *
 * @param moveDurationMs длительность перелёта.
 * @param dotPx размер «точки», в которую сжимается фотон на пике скорости.
 * @param fullShrinkDistancePx дистанция прыжка, начиная с которой сжатие максимально.
 */
class PhotonRenderer(
    private val moveDurationMs: Int,
    private val dotPx: Float,
    private val fullShrinkDistancePx: Float,
) {
    private val progress = Animatable(1f)

    // Переход, под который уже настроена анимация. Пока новый переход не подхвачен
    // [animate], геометрия считается по доле 0f (фотон на from), иначе на один кадр
    // он бы мигнул в позицию to до старта перелёта.
    private var animatedTransition by mutableStateOf<PhotonTransition?>(null)

    /**
     * Проигрывает переход фотона: моментально появляется на первой цели и плавно
     * перелетает между последующими.
     */
    suspend fun animate(transition: PhotonTransition) {
        if (transition.from == null) {
            progress.snapTo(1f)
            animatedTransition = transition
            return
        }
        progress.snapTo(0f)
        animatedTransition = transition
        progress.animateTo(1f, tween(moveDurationMs, easing = MoveEasing))
    }

    /**
     * Считает геометрию фотона для текущего кадра. Вызывать из фазы отрисовки:
     * читает [Animatable]-значение и живые границы целей из [controller].
     *
     * @param overlayOffset смещение оверлея в координатах окна — вычитается,
     * чтобы получить координаты внутри самого оверлея.
     * @return [PhotonFrame] либо null, если цель ещё не измерена.
     */
    fun frame(
        transition: PhotonTransition?,
        controller: PhotonController,
        overlayOffset: Offset,
    ): PhotonFrame? {
        val toBounds = controller.boundsOf(transition?.to) ?: return null
        // Пока границы from ещё не измерены (или цель исчезла) — стартуем из to.
        val fromBounds = controller.boundsOf(transition?.from) ?: toBounds

        val fraction = if (animatedTransition === transition) progress.value else 0f
        val centerX = lerp(fromBounds.center.x, toBounds.center.x, fraction)
        val centerY = lerp(fromBounds.center.y, toBounds.center.y, fraction)
        val targetWidth = lerp(fromBounds.width, toBounds.width, fraction)
        val targetHeight = lerp(fromBounds.height, toBounds.height, fraction)

        // Нормированная скорость: 0 на концах, 1 в середине пути.
        val speed = sin(PI.toFloat() * fraction)
        // Чем длиннее прыжок, тем сильнее сжатие.
        val jump = hypot(
            (toBounds.center.x - fromBounds.center.x).toDouble(),
            (toBounds.center.y - fromBounds.center.y).toDouble(),
        ).toFloat()
        val distanceFactor = (jump / fullShrinkDistancePx).coerceIn(0f, 1f)
        val shrink = speed * distanceFactor

        val width = targetWidth - (targetWidth - dotPx) * shrink
        val height = targetHeight - (targetHeight - dotPx) * shrink
        return PhotonFrame(
            left = centerX - overlayOffset.x - width / 2f,
            top = centerY - overlayOffset.y - height / 2f,
            width = width,
            height = height,
        )
    }
}
