// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.common

import kotlinx.serialization.json.Json

internal val DonkiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
