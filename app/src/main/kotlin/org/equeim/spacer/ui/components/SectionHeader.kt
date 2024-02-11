// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.equeim.spacer.ui.theme.Dimens

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = Dimens.SpacingMedium,
    bottomPadding: Dp = Dimens.SpacingMedium - Dimens.SpacingSmall,
    style: TextStyle = MaterialTheme.typography.titleLarge
) {
    Text(
        title,
        modifier.padding(top = topPadding, bottom = bottomPadding),
        color = MaterialTheme.colorScheme.primary,
        style = style
    )
}

@Composable
fun SectionPlaceholder(
    title: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = Dimens.SpacingMedium,
    bottomPadding: Dp = Dimens.SpacingMedium - Dimens.SpacingSmall,
    style: TextStyle = MaterialTheme.typography.titleLarge
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(
            title,
            modifier.padding(top = topPadding, bottom = bottomPadding),
            style = style
        )
    }
}
