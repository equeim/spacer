package org.equeim.spacer.ui.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.AppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.round

@Composable
fun LazyListState.toAppBarElevation(): Dp {
    val dp by remember(this) {
        derivedStateOf {
            if (firstVisibleItemIndex != 0) return@derivedStateOf AppBarDefaults.TopAppBarElevation
            val firstItemSize = layoutInfo.visibleItemsInfo.firstOrNull()?.size
                ?: return@derivedStateOf 0.dp
            val elevationFraction = firstVisibleItemScrollOffset.toFloat() / firstItemSize.toFloat()
            (round((AppBarDefaults.TopAppBarElevation.value * elevationFraction) * 10.0f) / 10.0f).dp
        }
    }
    return dp
}
