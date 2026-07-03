package ru.mukiva.photon.demo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import ru.mukiva.photon.demo.ui.photon.PhotonController

val LocalPhotonController = staticCompositionLocalOf<PhotonController> {
    error("PhotonController not provided")
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PhotonDemoTheme(
    photonController: PhotonController,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPhotonController provides photonController) {
        MaterialTheme(
            colorScheme = darkColorScheme(),
            typography = Typography,
            content = content
        )
    }
}