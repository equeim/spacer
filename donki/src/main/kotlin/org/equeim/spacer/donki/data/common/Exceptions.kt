// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.common

import retrofit2.HttpException

sealed class DonkiException(message: String, cause: Throwable) : RuntimeException(message, cause)

sealed class DonkiNetworkDataSourceException(message: String, cause: Throwable) :
    DonkiException(message, cause) {
    class InvalidApiKey(context: String, override val cause: HttpException) :
        DonkiNetworkDataSourceException("$context: invalid API key", cause)

    class TooManyRequests(context: String, override val cause: HttpException) :
        DonkiNetworkDataSourceException("$context: too many requests", cause)

    class HttpErrorResponse(
        context: String, override val cause: HttpException
    ) : DonkiNetworkDataSourceException(
        message = "$context: ${cause.status}", cause = cause
    ) {
        val status: String get() = cause.status

        private companion object {
            val HttpException.status: String
                get() = message().let {
                    if (it.isEmpty()) {
                        code().toString()
                    } else {
                        "${code()} $it"
                    }
                }
        }
    }

    class NetworkError(context: String, cause: Throwable) :
        DonkiNetworkDataSourceException("$context: ${cause.message}", cause)
}

class DonkiCacheDataSourceException(context: String, cause: Throwable) :
    DonkiException("$context: ${cause.message}", cause)

internal fun Exception.toDonkiNetworkDataSourceException(context: String): DonkiNetworkDataSourceException {
    return if (this is HttpException) {
        return when (code()) {
            403 -> DonkiNetworkDataSourceException.InvalidApiKey(context, this)
            429 -> DonkiNetworkDataSourceException.TooManyRequests(context, this)
            else -> DonkiNetworkDataSourceException.HttpErrorResponse(context, this)
        }
    } else {
        DonkiNetworkDataSourceException.NetworkError(context, this)
    }
}
