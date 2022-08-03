package org.equeim.spacer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import org.equeim.spacer.ui.theme.Dimens

val CARD_CONTENT_PADDING = PaddingValues(horizontal = Dimens.SpacingLarge, vertical = Dimens.SpacingMedium)
private val CARD_SHAPE = RoundedCornerShape(10.dp)
private val CARD_ELEVATION = 2.dp

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = CARD_CONTENT_PADDING,
    content: @Composable () -> Unit
) {
    androidx.compose.material.Card(
        onClick,
        modifier,
        shape = CARD_SHAPE,
        elevation = CARD_ELEVATION
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun ExpandableCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = CARD_CONTENT_PADDING,
    content: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    androidx.compose.material.Card(
        { expanded = !expanded },
        modifier,
        shape = CARD_SHAPE,
        elevation = CARD_ELEVATION
    ) {
        Column(
            Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
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
            AnimatedVisibility(visible = expanded) {
                Divider()
            }
            AnimatedVisibility(visible = expanded) {
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
