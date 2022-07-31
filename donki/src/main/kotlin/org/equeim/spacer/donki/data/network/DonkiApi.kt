package org.equeim.spacer.donki.data.network

import kotlinx.serialization.json.JsonObject
import org.equeim.spacer.donki.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDate

internal interface DonkiApi {
    @GET("GST")
    suspend fun getGeomagneticStorms(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String?
    ): List<JsonObject>

    @GET("CME")
    suspend fun getCoronalMassEjections(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String?
    ): List<JsonObject>

    @GET("IPS")
    suspend fun getInterplanetaryShocks(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String?
    ): List<JsonObject>

    @GET("FLR")
    suspend fun getSolarFlares(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String?
    ): List<JsonObject>

    @GET("SEP")
    suspend fun getSolarEnergeticParticles(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String?
    ): List<JsonObject>

    @GET("MPC")
    suspend fun getMagnetopauseCrossings(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String?
    ): List<JsonObject>

    @GET("RBE")
    suspend fun getRadiationBeltEnhancements(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String?
    ): List<JsonObject>

    @GET("HSS")
    suspend fun getHighSpeedStreams(
        @Query("startDate") startDate: LocalDate,
        @Query("endDate") endDate: LocalDate,
        @Query("api_key") apiKey: String?
    ): List<JsonObject>

    companion object {
        const val BASE_URL = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/WS/get/"
        const val NASA_API_BASE_URL = "https://api.nasa.gov/DONKI/"
    }
}
