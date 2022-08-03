package org.equeim.spacer.ui.screens.donki

import androidx.annotation.StringRes
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.model.EventType

val EventType.displayStringResId: Int
    @StringRes get() = when (this) {
        EventType.CoronalMassEjection -> R.string.coronal_mass_ejection
        EventType.GeomagneticStorm -> R.string.geomagnetic_storm
        EventType.InterplanetaryShock -> R.string.interplanetary_shock
        EventType.SolarFlare -> R.string.solar_flare
        EventType.SolarEnergeticParticle -> R.string.solar_energetic_particle
        EventType.MagnetopauseCrossing -> R.string.magnetopause_crossing
        EventType.RadiationBeltEnhancement -> R.string.radiation_belt_enhancement
        EventType.HighSpeedStream -> R.string.high_speed_stream
    }
