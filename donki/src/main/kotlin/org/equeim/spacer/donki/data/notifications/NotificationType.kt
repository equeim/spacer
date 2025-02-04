// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications

enum class NotificationType(internal val stringValue: String) {
    Report("Report"),
    CoronalMassEjection("CME"),
    GeomagneticStorm("GST"),
    InterplanetaryShock("IPS"),
    SolarFlare("FLR"),
    SolarEnergeticParticle("SEP"),
    MagnetopauseCrossing("MPC"),
    RadiationBeltEnhancement("RBE"),
    HighSpeedStream("HSS")
}
