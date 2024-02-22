// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import org.equeim.spacer.donki.data.DonkiJson
import org.equeim.spacer.donki.data.Week
import org.equeim.spacer.donki.data.eventSerializer
import org.equeim.spacer.donki.data.model.Event
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.retrofit.JsonConverterFactory
import org.equeim.spacer.retrofit.createRetrofit
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.create
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.concurrent.Volatile

private const val TAG = "DonkiDataSourceNetwork"

internal class DonkiDataSourceNetwork(private val nasaApiKey: Flow<String>, baseUrl: HttpUrl = BASE_URL) {
    private val api = createRetrofit(
        baseUrl = baseUrl,
        logTag = TAG,
        configureOkHttpClient = {
            addInterceptor(DonkiNetworkStats.RemainingRequestsInterceptor())
        },
        configureRetrofit = {
            addConverterFactory(DonkiJsonConverterFactory(DonkiJson))
        }
    ).create<DonkiApi>()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getEvents(
        week: Week,
        eventType: EventType,
    ): List<Pair<Event, JsonObject>> = nasaApiKey.mapLatest { apiKey ->
        try {
            Log.d(TAG, "getEvents() called with: week = $week, eventType = $eventType")
            val startDate = week.firstDay
            val endDate = week.lastDay
            val serializer = eventType.eventSerializer()
            when (eventType) {
                EventType.CoronalMassEjection -> api.getCoronalMassEjections(
                    startDate,
                    endDate,
                    apiKey
                )

                EventType.GeomagneticStorm -> api.getGeomagneticStorms(
                    startDate,
                    endDate,
                    apiKey
                )

                EventType.InterplanetaryShock -> api.getInterplanetaryShocks(
                    startDate,
                    endDate,
                    apiKey
                )

                EventType.SolarFlare -> api.getSolarFlares(startDate, endDate, apiKey)
                EventType.SolarEnergeticParticle -> api.getSolarEnergeticParticles(
                    startDate,
                    endDate,
                    apiKey
                )

                EventType.MagnetopauseCrossing -> api.getMagnetopauseCrossings(
                    startDate,
                    endDate,
                    apiKey
                )

                EventType.RadiationBeltEnhancement -> api.getRadiationBeltEnhancements(
                    startDate,
                    endDate,
                    apiKey
                )

                EventType.HighSpeedStream -> api.getHighSpeedStreams(
                    startDate,
                    endDate,
                    apiKey
                )
            }
                .map { DonkiJson.decodeFromJsonElement(serializer, it) to it }
                .sortedBy { it.first.time }.also {
                    Log.d(
                        TAG,
                        "getEvents: returning ${it.size} events for week = $week, eventType = $eventType"
                    )
                }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e(
                    TAG,
                    "getEvents: failed to get events summaries for week = $week, eventType = $eventType",
                    e
                )
            }
            throw e
        }
    }.first()

    private companion object {
        val BASE_URL = "https://api.nasa.gov/DONKI/".toHttpUrl()
    }
}

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

object DonkiNetworkStats {
    @Volatile
    var rateLimit: Int? = null
        private set

    @Volatile
    var remainingRequests: Int? = null
        private set

    internal class RemainingRequestsInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            rateLimit = response.header("X-RateLimit-Limit")?.toIntOrNull()
            remainingRequests = response.header("X-RateLimit-Remaining")?.toIntOrNull()
            return response
        }
    }
}
