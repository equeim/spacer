// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.ui.components.OutlinedCardWithPadding
import org.equeim.spacer.ui.components.SectionHeader
import org.equeim.spacer.ui.theme.Dimens

data class LinkedEventPresentation(
    val id: EventId,
    val dateTime: String,
    val type: String,
)

@Composable
fun LinkedEventsList(events: List<LinkedEventPresentation>, showEventDetailsScreen: (EventId) -> Unit) {
    SectionHeader(stringResource(R.string.linked_events))
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpacingBetweenCards)
    ) {
        events.forEach { event ->
            OutlinedCardWithPadding(
                { showEventDetailsScreen(event.id) },
                Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(text = event.dateTime)
                    Text(
                        text = event.type,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Dimens.SpacingSmall)
                    )
                }
            }
        }
    }
}
