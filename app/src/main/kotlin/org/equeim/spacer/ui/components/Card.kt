// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import org.equeim.spacer.ui.theme.Dimens

val CARD_CONTENT_PADDING = PaddingValues(horizontal = Dimens.SpacingLarge, vertical = Dimens.SpacingMedium)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ElevatedCardWithPadding(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: CardElevation = CardDefaults.elevatedCardElevation(),
    content: @Composable () -> Unit
) {
    ElevatedCard(
        onClick,
        modifier,
        elevation = elevation
    ) {
        CardContent(content)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OutlinedCardWithPadding(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    OutlinedCard(
        onClick,
        modifier
    ) {
        CardContent(content)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FilledCardWithPadding(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        onClick,
        modifier
    ) {
        CardContent(content)
    }
}

@Composable
private inline fun CardContent(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(CARD_CONTENT_PADDING)) {
        content()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ExpandableCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = CARD_CONTENT_PADDING,
    content: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(
        { expanded = !expanded },
        modifier
    ) {
        Column(
            Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding()
            )
        ) {
            val layoutDirection = LocalLayoutDirection.current
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection)
                    )
            ) {
                content()
            }
            AnimatedVisibility(expanded) {
                Divider(Modifier.padding(vertical = Dimens.SpacingSmall))
            }
            AnimatedVisibility(expanded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = contentPadding.calculateStartPadding(layoutDirection),
                            end = contentPadding.calculateEndPadding(layoutDirection)
                        )
                ) {
                    expandedContent()
                }
            }
        }
    }
}
