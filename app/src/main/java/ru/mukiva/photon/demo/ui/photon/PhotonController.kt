package ru.mukiva.photon.demo.ui.photon

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.hypot
import kotlin.math.sqrt

// Общие для оверлея и модификаторов параметры фотона (в dp).
internal const val PHOTON_DOT_SIZE_DP = 12

// Радиус свечения (ореола) вокруг фотона. Ореол делается блюром, его альфа плавно
// спадает к краям — на нём и проверяется покраска по полупрозрачным пикселям.
internal const val PHOTON_GLOW_RADIUS_DP = 16

// Скорость движения фотона (dp/s), при которой сжатие достигает максимума.
internal const val PHOTON_FULL_SHRINK_SPEED_DP_S = 600

// Предел ускорения фотона (dp/с²): меньше — плавнее разгон.
internal const val PHOTON_MAX_ACCEL_DP_S2 = 6000

// Жёсткость пружины движения (1/с²). Больше — резче догоняет цель. Демпфирование
// берём критическим (2*sqrt(stiffness)) — без перелёта.
private const val PHOTON_STIFFNESS = 50f

// Кадр может «прыгнуть» (пропуск кадров) — ограничиваем dt, чтобы пружина не пошла вразнос.
private const val MAX_STEP_SECONDS = 1f / 30f

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

    // Текущая геометрия фотона в координатах окна — результат пружинной интеграции.
    private var center by mutableStateOf(Offset.Zero)
    private var size by mutableStateOf(Size.Zero)
    private var speed by mutableFloatStateOf(0f)
    private var initialized by mutableStateOf(false)

    // Скорость движения/изменения размера (px/s) — сохраняется между кадрами и
    // сменами цели, поэтому смена фокуса на лету не сбрасывает инерцию.
    private var velX = 0f
    private var velY = 0f
    private var velW = 0f
    private var velH = 0f

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
     * Единый кадровый цикл движения фотона. Работает, пока активен [LaunchedEffect],
     * из которого вызван (см. [PhotonOverlay]). Каждый кадр читает живые границы
     * текущей цели `transition?.to` и подтягивает к ним центр/размер фотона
     * критически задемпфированной пружиной.
     *
     * Скорость ([velX]/[velY]) — обычная переменная, живущая всё время работы цикла,
     * поэтому:
     *  - смена цели на лету не сбрасывает скорость: фотон летит по инерции и лишь
     *    доворачивает к новой цели (даже при очень быстрой смене фокуса не встаёт);
     *  - движущиеся цели отслеживаются автоматически — цель читается заново каждый кадр.
     *
     * @param maxAccelPx предел ускорения (px/с²): меньше — плавнее разгон.
     * @param stiffness жёсткость пружины движения.
     */
    suspend fun run(
        maxAccelPx: Float = Float.MAX_VALUE,
        stiffness: Float = PHOTON_STIFFNESS,
    ) {
        val damping = 2f * sqrt(stiffness)
        var lastFrame = 0L
        while (true) {
            withFrameNanos { now ->
                val prev = lastFrame
                lastFrame = now

                val target = boundsOf(transition?.to) ?: return@withFrameNanos

                if (!initialized) {
                    center = target.center
                    size = target.size
                    velX = 0f; velY = 0f; velW = 0f; velH = 0f
                    speed = 0f
                    initialized = true
                    return@withFrameNanos
                }
                if (prev == 0L) return@withFrameNanos

                val dt = ((now - prev) / 1_000_000_000.0)
                    .toFloat().coerceIn(0f, MAX_STEP_SECONDS)
                if (dt <= 0f) return@withFrameNanos

                val targetCenter = target.center
                var accX = stiffness * (targetCenter.x - center.x) - damping * velX
                var accY = stiffness * (targetCenter.y - center.y) - damping * velY
                // Ограничиваем модуль ускорения — плавный набор скорости на дальних прыжках.
                val accMag = hypot(accX.toDouble(), accY.toDouble()).toFloat()
                if (accMag > maxAccelPx) {
                    val k = maxAccelPx / accMag
                    accX *= k
                    accY *= k
                }
                velX += accX * dt
                velY += accY * dt
                center = Offset(center.x + velX * dt, center.y + velY * dt)

                val targetSize = target.size
                velW += (stiffness * (targetSize.width - size.width) - damping * velW) * dt
                velH += (stiffness * (targetSize.height - size.height) - damping * velH) * dt
                size = Size(size.width + velW * dt, size.height + velH * dt)

                speed = hypot(velX.toDouble(), velY.toDouble()).toFloat()
            }
        }
    }

    /**
     * Текущий прямоугольник фотона в координатах окна с учётом сжатия. Вызывать из
     * фазы отрисовки: читает состояние, обновляемое пружиной в [run].
     *
     * Сжатие пропорционально текущей скорости: 0 в покое, максимум на пике. Так оно
     * зависит и от скорости, и от дистанции (дальний прыжок = выше скорость = сильнее
     * сжатие), и не привязано к длительности анимации.
     *
     * @param dotPx размер «точки», в которую сжимается фотон на пике скорости.
     * @param fullShrinkSpeedPx скорость (px/s), при которой сжатие достигает максимума.
     * @return прямоугольник фотона либо null, пока фотон ещё не занял первую цель.
     */
    fun photonWindowRect(dotPx: Float, fullShrinkSpeedPx: Float): Rect? {
        if (!initialized) return null

        val c = center
        val s = size
        val shrink = (speed / fullShrinkSpeedPx).coerceIn(0f, 1f)
        val width = s.width - (s.width - dotPx) * shrink
        val height = s.height - (s.height - dotPx) * shrink
        return Rect(
            left = c.x - width / 2f,
            top = c.y - height / 2f,
            right = c.x + width / 2f,
            bottom = c.y + height / 2f,
        )
    }
}
