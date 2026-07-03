package ru.mukiva.photon.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import ru.mukiva.photon.demo.ui.photon.PhotonController
import ru.mukiva.photon.demo.ui.photon.PhotonOverlay
import ru.mukiva.photon.demo.ui.theme.PhotonDemoTheme

internal class MainActivity : AppCompatActivity(), PhotonControllerHolder {
    override val photonController = PhotonController()

    private val photonView by lazy {
        findViewById<ComposeView>(R.id.photonOverlay)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        photonView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )

        photonView.setContent {
            PhotonDemoTheme(photonController) {
                PhotonOverlay()
            }
        }
    }
}
