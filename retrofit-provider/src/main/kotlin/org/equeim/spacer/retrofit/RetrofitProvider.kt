// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.retrofit

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.time.Duration

private val CONNECT_TIMEOUT = Duration.ofSeconds(10)
private val READ_TIMEOUT = Duration.ofSeconds(10)

private class Logger(private val tag: String) : HttpLoggingInterceptor.Logger {
    override fun log(message: String) {
        Log.d(tag, message)
    }
}

fun createOkHttpClient(
    logTag: String,
    configure: OkHttpClient.Builder.() -> OkHttpClient.Builder,
): OkHttpClient {
    return OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor(Logger(logTag)).apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        })
        .connectTimeout(CONNECT_TIMEOUT)
        .readTimeout(READ_TIMEOUT)
        .configure()
        .build()
}

fun createRetrofit(
    okHttpClient: OkHttpClient,
    baseUrl: HttpUrl,
    configureRetrofit: Retrofit.Builder.() -> Retrofit.Builder = { this },
): Retrofit {
    return Retrofit.Builder()
        .callFactory(okHttpClient)
        .baseUrl(baseUrl)
        .configureRetrofit()
        .build()
}
