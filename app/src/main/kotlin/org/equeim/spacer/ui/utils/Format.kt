package org.equeim.spacer.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.equeim.spacer.LocalDefaultLocale
import java.text.NumberFormat
import java.util.*

@Composable
fun formatInteger(integer: Int): String {
    val numberFormat = remember(LocalDefaultLocale.current) { NumberFormat.getIntegerInstance() }
    return numberFormat.format(integer)
}
