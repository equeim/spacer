// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarIcon(icon: ImageVector, @StringRes textId: Int, onClick: () -> Unit) {
    val text = stringResource(textId)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text)
            }
        },
        state = rememberTooltipState(),
        focusable = false
    ) {
        IconButton(onClick) {
            Icon(icon, text)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarIconWithBadge(
    icon: ImageVector,
    @StringRes textId: Int,
    badgeText: () -> String,
    onClick: () -> Unit
) {
    val text = stringResource(textId)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(text)
            }
        },
        state = rememberTooltipState(),
        focusable = false
    ) {
        NonClippingIconButton(onClick, Modifier) {
            BadgedBox(
                badge = { Badge { Text(badgeText()) } }
            ) {
                Icon(icon, text)
            }
        }
    }
}

private val IconButtonSize = 40.dp

@Composable
private fun NonClippingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier =
        modifier
            .minimumInteractiveComponentSize()
            .size(IconButtonSize)
            //.clip(IconButtonTokens.StateLayerShape.value)
            .background(color = if (enabled) colors.containerColor else colors.disabledContainerColor)
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = false,
                    radius = IconButtonSize / 2
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}
