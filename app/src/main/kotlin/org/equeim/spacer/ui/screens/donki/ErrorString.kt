// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import android.content.Context
import org.equeim.spacer.R
import org.equeim.spacer.donki.data.common.HttpErrorResponse
import org.equeim.spacer.donki.data.common.InvalidApiKeyError
import org.equeim.spacer.donki.data.common.TooManyRequestsError

fun Throwable.donkiErrorToString(context: Context): String = when (this) {
    is InvalidApiKeyError -> context.getString(R.string.invalid_nasa_api_key)
    is TooManyRequestsError -> context.getString(R.string.too_many_requests)
    is HttpErrorResponse -> context.getString(R.string.http_error_response, status)
    else -> toString()
}
