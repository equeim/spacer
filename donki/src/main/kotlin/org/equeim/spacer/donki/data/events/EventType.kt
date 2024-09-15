// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events

enum class EventType(internal val stringValue: String) {
    CoronalMassEjection("CME"),
    GeomagneticStorm("GST"),
    InterplanetaryShock("IPS"),
    SolarFlare("FLR"),
    SolarEnergeticParticle("SEP"),
    MagnetopauseCrossing("MPC"),
    RadiationBeltEnhancement("RBE"),
    HighSpeedStream("HSS")
}
