// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.model.InterplanetaryShock
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
