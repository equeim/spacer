package org.equeim.spacer.ui.screen.donki

import androidx.compose.runtime.Composable
import org.equeim.spacer.donki.data.model.SolarEnergeticParticle

@Composable
fun SolarEnergeticParticleDetails(event: SolarEnergeticParticle) =
    InstrumentsSection(event.instruments)
