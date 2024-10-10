// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.events.details

import androidx.compose.runtime.Composable
import org.equeim.spacer.donki.data.events.network.json.MagnetopauseCrossing

@Composable
fun MagnetopauseCrossingDetails(event: MagnetopauseCrossing) =
    InstrumentsSection(event.instruments)