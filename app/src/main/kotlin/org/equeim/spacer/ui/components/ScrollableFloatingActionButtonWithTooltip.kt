// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScrollableFloatingActionButtonWithTooltip(
    onClick: () -> Unit,
    icon: ImageVector,
    @StringRes tooltipText: Int,
    scrollBehavior: FloatingActionButtonScrollBehavior,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
) {
    FloatingActionButtonWithTooltip(
        onClick = onClick,
        icon = icon,
        tooltipText = tooltipText,
        modifier = modifier.animateFloatingActionButton(
            visible = scrollBehavior.isVisible,
            alignment = Alignment.Center
        ),
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        interactionSource = interactionSource
    )
}

@Composable
fun rememberFloatingActionButtonScrollBehavior(): FloatingActionButtonScrollBehavior {
    val showHideThreshold = with(LocalDensity.current) { FloatingActionButtonScrollBehavior.SHOW_HIDE_THRESHOLD.toPx() }
    return rememberSaveable(saver = FloatingActionButtonScrollBehavior.Saver) {
        FloatingActionButtonScrollBehavior()
    }.apply {
        this.showHideThreshold = showHideThreshold
    }
}

class FloatingActionButtonScrollBehavior(
    initialIsVisible: Boolean = true,
    initialScrollDelta: Float = 0.0f
) {
    var isVisible: Boolean by mutableStateOf(initialIsVisible)
    var showHideThreshold: Float = 0.0f

    private var scrollDelta = initialScrollDelta

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            scrollDelta = (scrollDelta + consumed.y).coerceIn(
                minimumValue = if (isVisible) -showHideThreshold else 0.0f,
                maximumValue = if (isVisible) 0.0f else showHideThreshold
            )
            if (isVisible && scrollDelta <= -showHideThreshold) {
                isVisible = false
                scrollDelta = 0.0f
            } else if (!isVisible && scrollDelta >= showHideThreshold) {
                isVisible = true
                scrollDelta = 0.0f
            }
            return Offset.Zero
        }
    }

    companion object {
        val SHOW_HIDE_THRESHOLD = 50.dp

        val Saver: Saver<FloatingActionButtonScrollBehavior, Any> = listSaver(
            save = { listOf(it.isVisible, it.scrollDelta) },
            restore = {
                FloatingActionButtonScrollBehavior(
                    initialIsVisible = it[0] as Boolean,
                    initialScrollDelta = it[1] as Float
                )
            }
        )
    }
}
