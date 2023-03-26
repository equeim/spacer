// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.details

import androidx.compose.runtime.Composable
import org.equeim.spacer.donki.data.model.SolarEnergeticParticle

@Composable
fun SolarEnergeticParticleDetails(event: SolarEnergeticParticle) =
    InstrumentsSection(event.instruments)
