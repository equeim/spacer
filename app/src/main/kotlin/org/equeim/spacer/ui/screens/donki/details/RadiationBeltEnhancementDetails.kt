package org.equeim.spacer.ui.screens.donki.details

import androidx.compose.runtime.Composable
import org.equeim.spacer.donki.data.model.RadiationBeltEnhancement

@Composable
fun RadiationBeltEnhancementDetails(event: RadiationBeltEnhancement) =
    InstrumentsSection(event.instruments)
