package ru.mukiva.photon.demo.ui.photon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val MIN_PINCH = 0f

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
 * Всё анимируемое состояние ([progress], [pinch]) — это [Animatable], а границы
 * целей читаются из [PhotonController] «вживую». Метод [frame] намеренно вызывается
 * из фазы отрисовки (`drawBehind`/`Canvas`): чтение анимируемых значений там
 * инвалидирует только draw, но не композицию, поэтому перелёт фотона не порождает
 * рекомпозиций. Сама анимация ([animate]) — suspend-функция и крутится в корутине.
 *
 * @param moveDurationMs длительность перелёта.
 * @param dotPx размер «точки», в которую сжимается фотон на середине пути.
 */
class PhotonRenderer(
    private val moveDurationMs: Int,
    private val dotPx: Float,
) {
    private val progress = Animatable(1f)
    private val pinch = Animatable(1f)

    // Переход, под который уже настроена анимация. Пока новый переход не подхвачен
    // [animate], геометрия считается по доле 0f (фотон на from), иначе на один кадр
    // он бы мигнул в позицию to до старта перелёта.
    private var animatedTransition by mutableStateOf<PhotonTransition?>(null)

    /**
     * Проигрывает переход фотона: моментально появляется на первой цели и плавно
     * перелетает между последующими, сжимаясь в точку на середине пути.
     */
    suspend fun animate(transition: PhotonTransition) {
        if (transition.from == null) {
            progress.snapTo(1f)
            pinch.snapTo(1f)
            animatedTransition = transition
            return
        }
        progress.snapTo(0f)
        pinch.snapTo(1f)
        animatedTransition = transition
        coroutineScope {
            launch { progress.animateTo(1f, tween(moveDurationMs)) }
            launch {
                pinch.animateTo(MIN_PINCH, tween(moveDurationMs / 2))
                pinch.animateTo(1f, tween(moveDurationMs / 2))
            }
        }
    }

    /**
     * Считает геометрию фотона для текущего кадра. Вызывать из фазы отрисовки:
     * читает [Animatable]-значения и живые границы целей из [controller].
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

        val squeeze = pinch.value
        val width = dotPx + (targetWidth - dotPx) * squeeze
        val height = dotPx + (targetHeight - dotPx) * squeeze
        return PhotonFrame(
            left = centerX - overlayOffset.x - width / 2f,
            top = centerY - overlayOffset.y - height / 2f,
            width = width,
            height = height,
        )
    }
}
