// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki.events.details.cme

import androidx.annotation.StringRes
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection

val CoronalMassEjection.CmeType.displayStringResId: Int
    @StringRes get() = when (this) {
        CoronalMassEjection.CmeType.Slowest -> R.string.cme_type_slowest
        CoronalMassEjection.CmeType.Common -> R.string.cme_type_common
        CoronalMassEjection.CmeType.Occasional -> R.string.cme_type_occasional
        CoronalMassEjection.CmeType.Rare -> R.string.cme_type_rare
        CoronalMassEjection.CmeType.ExtremelyRare -> R.string.cme_type_extremely_rare
    }

val CoronalMassEjection.MeasurementType.displayStringResId: Int
    @StringRes get() = when (this) {
        CoronalMassEjection.MeasurementType.LeadingEdge -> R.string.cme_measurement_type_le
        CoronalMassEjection.MeasurementType.ShockFront -> R.string.cme_measurement_type_sh
        CoronalMassEjection.MeasurementType.RightHandBoundary -> R.string.cme_measurement_type_rhb
        CoronalMassEjection.MeasurementType.LeftHandBoundary -> R.string.cme_measurement_type_lhb
        CoronalMassEjection.MeasurementType.BlackWhiteBoundary -> R.string.cme_measurement_type_bw
        CoronalMassEjection.MeasurementType.ProminenceCore -> R.string.cme_measurement_type_cor
        CoronalMassEjection.MeasurementType.DisconnectionFront -> R.string.cme_measurement_type_dis
        CoronalMassEjection.MeasurementType.TrailingEdge -> R.string.cme_measurement_type_te
    }

val CoronalMassEjection.DataLevel.displayStringResId: Int
    @StringRes get() = when (this) {
        CoronalMassEjection.DataLevel.RealTime -> R.string.cme_data_level_realtime
        CoronalMassEjection.DataLevel.RealTimeChecked -> R.string.cme_data_level_realtime_checked
        CoronalMassEjection.DataLevel.Retrospective -> R.string.cme_data_level_retrospective
    }
