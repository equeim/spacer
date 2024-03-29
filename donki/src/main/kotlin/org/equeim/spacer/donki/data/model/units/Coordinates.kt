// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.model.units

import androidx.compose.runtime.Immutable

@Immutable
data class Coordinates(
    val latitude: Angle,
    val longitude: Angle
)
