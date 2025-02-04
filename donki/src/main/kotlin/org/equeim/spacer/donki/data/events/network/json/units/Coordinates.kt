// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.network.json.units

import androidx.compose.runtime.Immutable

@Immutable
data class Coordinates(
    val latitude: Angle,
    val longitude: Angle
)
