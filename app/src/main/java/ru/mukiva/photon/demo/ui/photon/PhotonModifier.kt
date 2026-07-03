package ru.mukiva.photon.demo.ui.photon

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import ru.mukiva.photon.demo.ui.theme.LocalPhotonController

/**
 * Помечает composable как цель фотона. Получает стабильный идентификатор и
 * непрерывно сообщает контроллеру свои актуальные границы (в координатах окна),
 * а при получении фокуса начинает переход фотона в себя. За счёт того что
 * границы обновляются на каждом layout, старт и финиш анимации всегда
 * соответствуют реальному положению целей, даже если предыдущая цель сдвинулась
 * после потери фокуса.
 */
fun Modifier.photonTarget(
    interactionSource: InteractionSource
): Modifier = composed {
    val controller = LocalPhotonController.current
    val id = remember { Any() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    DisposableEffect(controller, id) {
        onDispose { controller.forget(id) }
    }

    LaunchedEffect(controller, id, isFocused) {
        if (isFocused) {
            controller.focus(id)
        }
    }

    onGloballyPositioned { coordinates ->
        controller.updateBounds(id, coordinates.boundsInWindow())
    }
}
