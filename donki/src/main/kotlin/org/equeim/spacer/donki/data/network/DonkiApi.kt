package org.equeim.spacer.donki.data.network

import org.equeim.spacer.donki.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.Instant

internal interface DonkiApi {
    @GET("GST")
    suspend fun getGeomagneticStorms(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<GeomagneticStorm>

    @GET("CME")
    suspend fun getCoronalMassEjections(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<CoronalMassEjection>

    @GET("IPS")
    suspend fun getInterplanetaryShocks(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<InterplanetaryShock>

    @GET("FLR")
    suspend fun getSolarFlares(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<SolarFlare>

    @GET("SEP")
    suspend fun getSolarEnergeticParticles(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<SolarEnergeticParticle>

    @GET("MPC")
    suspend fun getMagnetopauseCrossings(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<MagnetopauseCrossing>

    @GET("RBE")
    suspend fun getRadiationBeltEnhancements(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<RadiationBeltEnhancement>

    @GET("HSS")
    suspend fun getHighSpeedStreams(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<HighSpeedStream>

    companion object {
        const val BASE_URL = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/WS/get/"
        const val NASA_API_BASE_URL = "https://api.nasa.gov/DONKI/"
    }
}
