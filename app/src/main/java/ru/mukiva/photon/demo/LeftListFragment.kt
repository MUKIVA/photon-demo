package ru.mukiva.photon.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.tv.material3.ExperimentalTvMaterial3Api
import ru.mukiva.photon.demo.ui.photon.ButtonList
import ru.mukiva.photon.demo.ui.theme.PhotonDemoTheme

internal class LeftListFragment : Fragment() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        val controller = (requireActivity() as MainActivity).photonController

        setContent {
            PhotonDemoTheme(controller) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ButtonList(
                        title = "Левый фрагмент",
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }
}
