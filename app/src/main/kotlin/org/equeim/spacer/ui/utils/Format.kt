// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.equeim.spacer.ui.LocalDefaultLocale
import java.text.DecimalFormat
import java.text.NumberFormat

@Composable
fun formatInteger(integer: Int): String {
    val numberFormat = remember(LocalDefaultLocale.current) { NumberFormat.getIntegerInstance() }
    return numberFormat.format(integer)
}

@Composable
fun formatFloat(float: Float): String {
    val decimalFormat = remember(LocalDefaultLocale.current) { DecimalFormat("0.##") }
    return decimalFormat.format(float)
}
