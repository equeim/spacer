package org.equeim.spacer.donki.data.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody
import org.equeim.spacer.donki.data.DonkiJson
import org.equeim.spacer.donki.data.NASA_API_KEY
import org.equeim.spacer.donki.data.Week
import org.equeim.spacer.donki.data.model.Event
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.retrofit.JsonConverterFactory
import org.equeim.spacer.retrofit.createRetrofit
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.create
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.time.LocalDate

private const val TAG = "DonkiDataSourceNetwork"

internal class DonkiDataSourceNetwork(baseUrl: HttpUrl = baseUrl()) {
    private val api = createRetrofit(baseUrl, TAG) {
        addConverterFactory(DonkiJsonConverterFactory(DonkiJson))
    }.create<DonkiApi>()

    suspend fun getEvents(
        week: Week,
        eventType: EventType
    ): List<Event> = try {
        Log.d(TAG, "getEvents() called with: week = $week, eventType = $eventType")
        getEvents(eventType, week.firstDay, week.lastDay).sortedBy { it.time }.also {
            Log.d(TAG, "getEvents: returning ${it.size} events for week = $week, eventType = $eventType")
        }
    } catch (e: Exception) {
        if (e !is CancellationException) {
            Log.e(TAG, "getEvents: failed to get events summaries for week = $week, eventType = $eventType", e)
        }
        throw e
    }

    private suspend fun getEvents(
        eventType: EventType,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Event> = when (eventType) {
        EventType.CoronalMassEjection -> api.getCoronalMassEjections(
            startDate,
            endDate,
            nasaApiKeyOrNull()
        )
        EventType.GeomagneticStorm -> api.getGeomagneticStorms(
            startDate,
            endDate,
            nasaApiKeyOrNull()
        )
        EventType.InterplanetaryShock -> api.getInterplanetaryShocks(
            startDate,
            endDate,
            nasaApiKeyOrNull()
        )
        EventType.SolarFlare -> api.getSolarFlares(startDate, endDate, nasaApiKeyOrNull())
        EventType.SolarEnergeticParticle -> api.getSolarEnergeticParticles(
            startDate,
            endDate,
            nasaApiKeyOrNull()
        )
        EventType.MagnetopauseCrossing -> api.getMagnetopauseCrossings(
            startDate,
            endDate,
            nasaApiKeyOrNull()
        )
        EventType.RadiationBeltEnhancement -> api.getRadiationBeltEnhancements(
            startDate,
            endDate,
            nasaApiKeyOrNull()
        )
        EventType.HighSpeedStream -> api.getHighSpeedStreams(startDate, endDate, nasaApiKeyOrNull())
    }

    private companion object {
        const val USE_NASA_API = false

        fun baseUrl(): HttpUrl {
            return (if (USE_NASA_API) DonkiApi.NASA_API_BASE_URL else DonkiApi.BASE_URL).toHttpUrl()
        }

        fun nasaApiKeyOrNull(): String? {
            return if (USE_NASA_API) NASA_API_KEY else null
        }
    }
}

private class DonkiJsonConverterFactory(json: Json) : JsonConverterFactory(json) {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val rawClass = ((type as? ParameterizedType)?.rawType ?: type) as? Class<*>
        val delegate = super.responseBodyConverter(type, annotations, retrofit) ?: return null
        return Converter<ResponseBody, Any> { body ->
            if (body.contentLength() == 0L && rawClass == List::class.java) {
                Log.d(
                    TAG,
                    "responseBodyConverter: handling empty response as empty list for type $type"
                )
                emptyList<Any>()
            } else {
                delegate.convert(body)
            }
        }
    }
}
