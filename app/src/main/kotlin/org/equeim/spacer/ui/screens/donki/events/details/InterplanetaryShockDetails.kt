// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.events.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.events.network.json.InterplanetaryShock
import org.equeim.spacer.ui.theme.Dimens

@Composable
fun InterplanetaryShockDetails(event: InterplanetaryShock) =
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)) {
        LabelFieldPair(
            R.string.ips_location,
            event.location
        )
        InstrumentsSection(event.instruments)
    }
