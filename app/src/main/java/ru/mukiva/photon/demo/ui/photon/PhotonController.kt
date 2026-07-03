package ru.mukiva.photon.demo.ui.photon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

// Общие для оверлея и модификаторов параметры фотона (в dp / ms).
internal const val PHOTON_MOVE_DURATION_MS = 320
internal const val PHOTON_DOT_SIZE_DP = 12
internal const val PHOTON_FULL_SHRINK_DISTANCE_DP = 240

// Симметричная ease-in-out кривая: половину пути разгон, половину — торможение.
private val MoveEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * Идентификатор конкретной цели фотона. Каждый [photonTarget] получает свой
 * стабильный ключ, по которому контроллер хранит его актуальные границы.
 */
typealias PhotonTargetId = Any

/**
 * Описывает переход фотона как транзакцию между двумя целями: [from] — цель, с
 * которой уходит фотон, [to] — цель, в которую он летит. Границы обеих целей
 * читаются «вживую» на каждом кадре, поэтому если [from] сместится уже после
 * потери фокуса, старт анимации скорректируется на лету.
 */
@Stable
data class PhotonTransition(
    val from: PhotonTargetId?,
    val to: PhotonTargetId,
)

/**
 * Единый источник правды по фотону: хранит границы всех целей, текущий переход,
 * состояние анимации и реестр маскируемых зон. Создаётся один раз в активити и
 * прокидывается во все ComposeView через [ru.mukiva.photon.demo.ui.theme.LocalPhotonController].
 *
 * Геометрию фотона ([photonWindowRect]) читают и верхний оверлей, и модификаторы
 * контента (например [recolorUnderPhoton]) — все из одного инстанса, поэтому
 * фотон в разных вью согласован по кадру.
 */
@Stable
class PhotonController {

    private val bounds = mutableStateMapOf<PhotonTargetId, Rect>()
    private val masks = mutableStateMapOf<Any, Rect>()

    private val progress = Animatable(1f)

    // Переход, под который уже настроена анимация. Пока новый переход не подхвачен
    // [animate], геометрия считается по доле 0f (фотон на from), иначе на один кадр
    // он бы мигнул в позицию to до старта перелёта.
    private var animatedTransition by mutableStateOf<PhotonTransition?>(null)

    var transition by mutableStateOf<PhotonTransition?>(null)
        private set

    /** Зарегистрированные маскируемые зоны (в координатах окна). */
    val maskRegions: Collection<Rect> get() = masks.values

    /** Актуальные границы цели или null, если она ещё не измерена/уже удалена. */
    fun boundsOf(id: PhotonTargetId?): Rect? = id?.let { bounds[it] }

    /** Сообщает контроллеру свежие границы цели. Вызывается на каждом layout. */
    fun updateBounds(id: PhotonTargetId, rect: Rect) {
        bounds[id] = rect
    }

    /** Убирает цель из реестра, когда её composable покидает композицию. */
    fun forget(id: PhotonTargetId) {
        bounds.remove(id)
    }

    /**
     * Начинает переход фотона в цель [id], фиксируя предыдущую цель как [PhotonTransition.from].
     */
    fun focus(id: PhotonTargetId) {
        val current = transition
        if (current?.to == id) return
        transition = PhotonTransition(from = current?.to, to = id)
    }

    /** Регистрирует зону (координаты окна), которую оверлей вырежет из фотона. */
    fun registerMask(id: Any, rect: Rect) {
        masks[id] = rect
    }

    /** Убирает зону маски. */
    fun unregisterMask(id: Any) {
        masks.remove(id)
    }

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
        progress.animateTo(1f, tween(PHOTON_MOVE_DURATION_MS, easing = MoveEasing))
    }

    /**
     * Текущий прямоугольник фотона в координатах окна с учётом доли перелёта и
     * сжатия. Вызывать из фазы отрисовки: читает [Animatable]-значение и живые
     * границы целей.
     *
     * @param dotPx размер «точки», в которую сжимается фотон на пике скорости.
     * @param fullShrinkPx дистанция прыжка, начиная с которой сжатие максимально.
     * @return прямоугольник фотона либо null, если цель ещё не измерена.
     */
    fun photonWindowRect(dotPx: Float, fullShrinkPx: Float): Rect? {
        val t = transition
        val toBounds = boundsOf(t?.to) ?: return null
        // Пока границы from ещё не измерены (или цель исчезла) — стартуем из to.
        val fromBounds = boundsOf(t?.from) ?: toBounds

        val fraction = if (animatedTransition === t) progress.value else 0f
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
        val distanceFactor = (jump / fullShrinkPx).coerceIn(0f, 1f)
        val shrink = speed * distanceFactor

        val width = targetWidth - (targetWidth - dotPx) * shrink
        val height = targetHeight - (targetHeight - dotPx) * shrink
        return Rect(
            left = centerX - width / 2f,
            top = centerY - height / 2f,
            right = centerX + width / 2f,
            bottom = centerY + height / 2f,
        )
    }
}
