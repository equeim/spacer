package org.equeim.spacer.donki.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.equeim.spacer.donki.NASA_API_KEY
import org.equeim.spacer.donki.data.network.model.*
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.Instant
import java.time.LocalDate

internal interface DonkiApi {
    @GET("GST")
    suspend fun getGeomagneticStorms(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<GeomagneticStormJson>

    @GET("CME")
    suspend fun getCoronalMassEjections(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<CoronalMassEjectionJson>

    @GET("IPS")
    suspend fun getInterplanetaryShocks(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<InterplanetaryShockJson>

    @GET("FLR")
    suspend fun getSolarFlares(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<SolarFlareJson>

    @GET("SEP")
    suspend fun getSolarEnergeticParticles(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<SolarEnergeticParticleJson>

    @GET("MPC")
    suspend fun getMagnetopauseCrossings(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<MagnetopauseCrossingJson>

    @GET("RBE")
    suspend fun getRadiationBeltEnhancements(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<RadiationBeltEnhancementJson>

    @GET("HSS")
    suspend fun getHighSpeedStreams(
        @Query("startDate") startDate: Instant,
        @Query("endDate") endDate: Instant,
        @Query("api_key") apiKey: String?
    ): List<HighSpeedStreamJson>

    companion object {
        const val BASE_URL = "https://kauai.ccmc.gsfc.nasa.gov/DONKI/WS/get/"
        const val NASA_API_BASE_URL = "https://api.nasa.gov/DONKI/"
    }
}
