@file:OptIn(ExperimentalComposeUiApi::class)

package ru.mukiva.photon.demo.ui.photon

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val someDataList = (1..20).toList()

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ButtonList(
    title: String,
    modifier: Modifier = Modifier,
) = LazyColumn(
    modifier = modifier.padding(horizontal = 16.dp)
) {
    item { Header(title, Modifier.fillMaxWidth()) }

    items(
        items = someDataList,
        key = { item -> item }
    ) { item ->
        ListButton(
            label = "Button $item",
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Header(
    title: String,
    modifier: Modifier = Modifier
) = Box(modifier.padding(16.dp)) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ListButton(
    label: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isFocused by interactionSource.collectIsFocusedAsState()

    val textColor = when (isFocused) {
        true -> MaterialTheme.colorScheme.background
        false -> MaterialTheme.colorScheme.onBackground
    }

    Box(
        modifier = modifier
            .focusable(interactionSource = interactionSource)
            .photonTarget(interactionSource)
            .padding(16.dp, 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}

