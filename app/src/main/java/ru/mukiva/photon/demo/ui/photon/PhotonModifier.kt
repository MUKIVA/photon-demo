package ru.mukiva.photon.demo.ui.photon

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ru.mukiva.photon.demo.ui.theme.LocalPhotonController
import kotlin.math.min

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

/**
 * Рисует контент поверх фотона: попавшая в границы элемента часть белого фотона
 * рисуется под контентом, а сам контент — сверху в своих цветах (без перекраски).
 * Подходит для полноцветного контента (например, растровых изображений), который
 * должен «лежать» на фотоне, а не превращаться в силуэт.
 */
fun Modifier.forceOverPhoton(): Modifier = photonMasked { local ->
    if (local == null) {
        drawContent()
        return@photonMasked
    }
    drawPhotonPatch(local)
    drawContent()
}

/**
 * Перекрашивает часть контента, накрытую фотоном, в [color] — строго по форме
 * фотона (маске). Вне фотона контент остаётся исходным.
 *
 * Внутри offscreen-слоя рисует контент и перекрашивает его пиксели в пределах
 * формы фотона через [BlendMode.SrcAtop] — белый фон-фотон под слоем не трогается.
 * Уместно для монохромного контента (текст, одноцветные иконки): полноцветный
 * растр превратится в сплошной силуэт — для него используйте [forceOverPhoton].
 */
fun Modifier.recolorUnderPhoton(color: Color): Modifier = composed {
    val layerPaint = remember { Paint() }
    photonMasked { local ->
        if (local == null) {
            drawContent()
            return@photonMasked
        }
        drawPhotonPatch(local)
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
            drawContent()
            drawRoundRect(
                color = color,
                topLeft = local.topLeft,
                size = local.size,
                cornerRadius = local.corner,
                blendMode = BlendMode.SrcAtop,
            )
            canvas.restore()
        }
    }
}

/** Локальная (в координатах элемента) геометрия части фотона, попавшей в элемент. */
private class PhotonLocalRect(
    val topLeft: Offset,
    val size: Size,
    val corner: CornerRadius,
)

/**
 * Общая обвязка модификаторов, взаимодействующих с фотоном: регистрирует границы
 * элемента как маскируемую зону (оверлей вырежет её из своего фотона), считает
 * локальную геометрию фотона и отдаёт её в [draw]. `null` означает, что фотона
 * сейчас нет (цель не измерена) — тогда достаточно просто нарисовать контент.
 */
private fun Modifier.photonMasked(
    draw: ContentDrawScope.(PhotonLocalRect?) -> Unit,
): Modifier = composed {
    val controller = LocalPhotonController.current
    val density = LocalDensity.current
    val id = remember { Any() }
    var topLeft by remember { mutableStateOf(Offset.Zero) }

    val dotPx = with(density) { PHOTON_DOT_SIZE_DP.dp.toPx() }
    val fullShrinkPx = with(density) { PHOTON_FULL_SHRINK_DISTANCE_DP.dp.toPx() }

    DisposableEffect(controller, id) {
        onDispose { controller.unregisterMask(id) }
    }

    Modifier
        .onGloballyPositioned { coordinates ->
            val windowBounds = coordinates.boundsInWindow()
            topLeft = windowBounds.topLeft
            controller.registerMask(id, windowBounds)
        }
        .drawWithContent {
            val photon = controller.photonWindowRect(dotPx, fullShrinkPx)
            val local = photon?.let {
                PhotonLocalRect(
                    topLeft = Offset(it.left - topLeft.x, it.top - topLeft.y),
                    size = Size(it.width, it.height),
                    corner = CornerRadius(min(it.width, it.height) / 2f),
                )
            }
            draw(local)
        }
}

/** Рисует белый кусок фотона под контентом, ограничив его границами элемента. */
private fun ContentDrawScope.drawPhotonPatch(local: PhotonLocalRect) {
    clipRect {
        drawRoundRect(
            color = Color.White,
            topLeft = local.topLeft,
            size = local.size,
            cornerRadius = local.corner,
        )
    }
}
