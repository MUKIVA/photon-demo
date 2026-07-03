package ru.mukiva.photon.demo.ui.photon

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Хранит границы (в координатах окна) последней сфокусированной цели.
 * Создаётся один раз в активити и прокидывается во все ComposeView через
 * [ru.mukiva.photon.demo.ui.theme.LocalPhotonController].
 */

@Stable
class PhotonController {

    var target by mutableStateOf<Rect?>(null)
        private set

    fun moveTo(bounds: Rect) {
        Log.d("[PHOTON]", "moveTo: $bounds")
        target = bounds
    }
}
