package ru.mukiva.photon.demo.ui.photon

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.mukiva.photon.demo.ui.theme.LocalPhotonController
import kotlin.time.Duration.Companion.milliseconds

private const val ANIMATION_SET_DEBOUNCE_MS = 250L

/**
 * Помечает composable как цель фотона: при получении фокуса сообщает свои границы
 * (в координатах окна) контроллеру из [LocalPhotonController].
 */
@OptIn(FlowPreview::class)
fun Modifier.photonTarget(
    interactionSource: InteractionSource
): Modifier = composed {
    val controller = LocalPhotonController.current
    val scope = rememberCoroutineScope()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val boundsFlow = remember { MutableSharedFlow<Rect>() }

    LaunchedEffect(boundsFlow, controller) {
        boundsFlow
            .debounce(ANIMATION_SET_DEBOUNCE_MS.milliseconds)
            .onEach { bounds ->
                if (isFocused) {
                    controller.moveTo(bounds)
                }
            }
            .launchIn(this)

    }

    onGloballyPositioned { coordinates ->
        scope.launch {
            boundsFlow.emit(coordinates.boundsInWindow())
        }
    }
}
