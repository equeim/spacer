// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.events.details

import androidx.compose.runtime.Composable
import org.equeim.spacer.donki.data.events.network.json.SolarEnergeticParticle

@Composable
fun SolarEnergeticParticleDetails(event: SolarEnergeticParticle) =
    InstrumentsSection(event.instruments)
