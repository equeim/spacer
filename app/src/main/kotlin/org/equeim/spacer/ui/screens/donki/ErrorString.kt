// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import android.content.Context
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.network.HttpErrorResponse
import org.equeim.spacer.donki.data.network.InvalidApiKeyError
import org.equeim.spacer.donki.data.network.TooManyRequestsError

fun Throwable.donkiErrorToString(context: Context): String = when (this) {
    is InvalidApiKeyError -> context.getString(R.string.invalid_nasa_api_key)
    is TooManyRequestsError -> context.getString(if (usingDemoKey) R.string.too_many_requests_demo_key else R.string.too_many_requests)
    is HttpErrorResponse -> context.getString(R.string.http_error_response, status)
    else -> toString()
}
