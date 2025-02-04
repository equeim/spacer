// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.network

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDate

internal interface EventsApi {
    @GET("GST")
    suspend fun getGeomagneticStorms(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String
    ): List<JsonObject>

    @GET("CME")
    suspend fun getCoronalMassEjections(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String
    ): List<JsonObject>

    @GET("IPS")
    suspend fun getInterplanetaryShocks(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String
    ): List<JsonObject>

    @GET("FLR")
    suspend fun getSolarFlares(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String
    ): List<JsonObject>

    @GET("SEP")
    suspend fun getSolarEnergeticParticles(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String
    ): List<JsonObject>

    @GET("MPC")
    suspend fun getMagnetopauseCrossings(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String
    ): List<JsonObject>

    @GET("RBE")
    suspend fun getRadiationBeltEnhancements(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String
    ): List<JsonObject>

    @GET("HSS")
    suspend fun getHighSpeedStreams(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String
    ): List<JsonObject>
}
