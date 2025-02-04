// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import android.content.Context
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.common.DonkiNetworkDataSourceException

fun Throwable.donkiErrorToString(context: Context): String = when (this) {
    is DonkiNetworkDataSourceException.InvalidApiKey -> context.getString(R.string.invalid_nasa_api_key)
    is DonkiNetworkDataSourceException.TooManyRequests -> context.getString(R.string.too_many_requests)
    is DonkiNetworkDataSourceException.HttpErrorResponse -> context.getString(R.string.http_error_response, status)
    else -> toString()
}
