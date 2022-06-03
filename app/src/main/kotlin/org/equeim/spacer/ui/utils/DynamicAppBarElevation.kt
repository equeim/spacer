package org.equeim.spacer.ui.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.AppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SCROLL_STATE_THRESHOLD = 56.dp

@Composable
fun ScrollState.toAppBarElevation(): Dp {
    val density = LocalDensity.current
    val thresholdPixels = remember(density) { with(density) { SCROLL_STATE_THRESHOLD.roundToPx() } }
    return if (value < thresholdPixels) {
        val px = (value.toFloat() / thresholdPixels.toFloat()) * AppBarDefaults.TopAppBarElevation.value
        with(density) { px.toDp() }
    } else {
        AppBarDefaults.TopAppBarElevation
    }
}

fun LazyListState.toAppBarElevation(): Dp {
    return if (firstVisibleItemIndex == 0) {
        val itemSize = layoutInfo.visibleItemsInfo.find { it.index == 0 }?.size
        if (itemSize != null) {
            val firstVisibleItemOffsetRelative =
                firstVisibleItemScrollOffset.toFloat() / itemSize.toFloat()
            (firstVisibleItemOffsetRelative * AppBarDefaults.TopAppBarElevation.value).dp
        } else {
            0.dp
        }
    } else {
        AppBarDefaults.TopAppBarElevation
    }
}
