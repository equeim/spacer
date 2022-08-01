package org.equeim.spacer.ui.screen.donki

import androidx.compose.runtime.Composable
import org.equeim.spacer.donki.data.model.RadiationBeltEnhancement

@Composable
fun RadiationBeltEnhancementDetails(event: RadiationBeltEnhancement) =
    InstrumentsSection(event.instruments)
