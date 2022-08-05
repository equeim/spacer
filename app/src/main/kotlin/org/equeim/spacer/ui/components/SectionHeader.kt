package org.equeim.spacer.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import org.equeim.spacer.ui.theme.Dimens

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.h6) {
    Text(
        title,
        modifier.padding(top = Dimens.SpacingMedium, bottom = Dimens.SpacingMedium - Dimens.SpacingSmall),
        color = MaterialTheme.colors.primary,
        style = style
    )
}

@Composable
fun SectionPlaceholder(title: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.h6) {
    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
        Text(
            title,
            modifier.padding(top = Dimens.SpacingMedium, bottom = Dimens.SpacingMedium - Dimens.SpacingSmall),
            style = style
        )
    }
}