// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.equeim.spacer.donki.data.DEFAULT_NASA_API_KEY
import org.equeim.spacer.donki.data.common.DonkiJson
import org.equeim.spacer.donki.data.common.DonkiNetworkDataSourceException
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.common.createDonkiRetrofit
import org.equeim.spacer.donki.data.common.toDonkiNetworkDataSourceException
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.network.json.Event
import org.equeim.spacer.donki.data.events.network.json.eventSerializer
import retrofit2.create

internal class EventsDataSourceNetwork(
    private val customNasaApiKey: Flow<String?>,
    okHttpClient: OkHttpClient,
    baseUrl: HttpUrl
) {
    private val api = createDonkiRetrofit(okHttpClient, baseUrl).create<EventsApi>()

    /**
     * @throws DonkiNetworkDataSourceException
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getEvents(
        week: Week,
        eventType: EventType,
    ): List<Pair<Event, JsonObject>> = customNasaApiKey.map { it ?: DEFAULT_NASA_API_KEY }.mapLatest { apiKey ->
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
                .asSequence()
                .map { DonkiJson.decodeFromJsonElement(serializer, it) to it }
                .coerceInWeek(week)
                .toMutableList()
                .apply { sortBy { it.first.time } }
                .also {
                    Log.d(
                        TAG,
                        "getEvents: returning ${it.size} events for week = $week, eventType = $eventType"
                    )
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toDonkiNetworkDataSourceException("getEvents with: week = $week, eventType = $eventType failed")
        }
    }.first()

    private companion object {
        const val TAG = "DonkiDataSourceNetwork"

        fun Sequence<Pair<Event, JsonObject>>.coerceInWeek(week: Week): Sequence<Pair<Event, JsonObject>> {
            val startInstant = week.getFirstDayInstant()
            val endInstant = week.getInstantAfterLastDay()
            return this.filter { (event, _) ->
                if (event.time >= startInstant && event.time < endInstant) {
                    true
                } else {
                    Log.e(TAG, "getEvents: event with id ${event.id} and time ${event.time} does not belong in week $week")
                    false
                }
            }
        }
    }
}
