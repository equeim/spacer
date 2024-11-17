// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.common

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import org.equeim.spacer.retrofit.JsonConverterFactory
import org.equeim.spacer.retrofit.createOkHttpClient
import org.equeim.spacer.retrofit.createRetrofit
import retrofit2.Converter
import retrofit2.HttpException
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

object DonkiNetworkStats {
    @Volatile
    var rateLimit: Int? = null
        internal set

    @Volatile
    var remainingRequests: Int? = null
        internal set
}

fun createDonkiOkHttpClient(): OkHttpClient = createOkHttpClient("DonkiHttp") {
    addInterceptor(RemainingRequestsInterceptor())
}

internal val DONKI_BASE_URL = "https://api.nasa.gov/DONKI/".toHttpUrl()

internal fun createDonkiRetrofit(okHttpClient: OkHttpClient, baseUrl: HttpUrl) = createRetrofit(
    baseUrl = baseUrl,
    okHttpClient = okHttpClient,
    configureRetrofit = {
        addConverterFactory(DonkiJsonConverterFactory(DonkiJson))
    }
)

private class DonkiJsonConverterFactory(json: Json) : JsonConverterFactory(json) {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        val rawClass = ((type as? ParameterizedType)?.rawType ?: type) as? Class<*>
        val delegate = super.responseBodyConverter(type, annotations, retrofit) ?: return null
        return Converter<ResponseBody, Any> { body ->
            if (body.contentLength() == 0L && rawClass == List::class.java) {
                emptyList<Any>()
            } else {
                delegate.convert(body)
            }
        }
    }
}

private class RemainingRequestsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        DonkiNetworkStats.apply {
            rateLimit = response.header("X-RateLimit-Limit")?.toIntOrNull()
            remainingRequests = response.header("X-RateLimit-Remaining")?.toIntOrNull()
        }
        return response
    }
}

class InvalidApiKeyError(cause: HttpException) : RuntimeException("Invalid API key", cause)
class TooManyRequestsError(cause: HttpException) : RuntimeException("Too many requests", cause)
class HttpErrorResponse private constructor(val status: String, cause: HttpException) : RuntimeException(status, cause) {
    constructor(cause: HttpException) : this(cause.message().let {
        if (it.isEmpty()) {
            cause.code().toString()
        } else {
            "${cause.code()} $it"
        }
    }, cause)
}

internal fun Exception.toDonkiException(): Exception? {
    if (this is HttpException) {
        return when (code()) {
            403 -> InvalidApiKeyError(this)
            429 -> TooManyRequestsError(this)
            else -> HttpErrorResponse(this)
        }
    }
    return null
}
