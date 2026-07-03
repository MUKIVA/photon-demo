package ru.mukiva.photon.demo.ui.photon

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

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
 * Хранит актуальные границы (в координатах окна) всех зарегистрированных целей
 * и текущий переход фотона. Создаётся один раз в активити и прокидывается во
 * все ComposeView через [ru.mukiva.photon.demo.ui.theme.LocalPhotonController].
 */
@Stable
class PhotonController {

    private val bounds = mutableStateMapOf<PhotonTargetId, Rect>()

    var transition by mutableStateOf<PhotonTransition?>(null)
        private set

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
}
