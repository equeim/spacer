@file:Suppress("FunctionName")

package org.equeim.spacer.donki.data.cache.entities

import androidx.room.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.equeim.spacer.donki.data.model.*
import java.time.Instant

@Entity(tableName = "events")
internal data class CachedEvent(
    @ColumnInfo(name = "id") @PrimaryKey
    val id: String,
    @ColumnInfo(name = "type")
    val type: EventType,
    @ColumnInfo(name = "time", index = true)
    val time: Instant,
    @ColumnInfo(name = "json")
    val json: String
)

internal fun Event.toCachedEvent() = CachedEvent(
    id = id.stringValue,
    type = type,
    time = time,
    json = when (this) {
        is CoronalMassEjection -> Json.encodeToString(this)
        is GeomagneticStorm -> Json.encodeToString(this)
        is HighSpeedStream -> Json.encodeToString(this)
        is InterplanetaryShock -> Json.encodeToString(this)
        is MagnetopauseCrossing -> Json.encodeToString(this)
        is RadiationBeltEnhancement -> Json.encodeToString(this)
        is SolarEnergeticParticle -> Json.encodeToString(this)
        is SolarFlare -> Json.encodeToString(this)
    }
)

internal data class CachedEventSummary(
    @ColumnInfo(name = "id")
    private val idString: String,
    @ColumnInfo(name = "type")
    override val type: EventType,
    @ColumnInfo(name = "time")
    override val time: Instant
) : EventSummary {
    override val id: EventId
        get() = EventId(idString)
}

@Dao
internal abstract class CachedEventsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun updateEvent(event: CachedEvent)

    @Query(
        """
            SELECT id, type, time FROM events
            WHERE type = :type AND time >= :startTime AND time < :endTime
            ORDER BY time DESC
        """
    )
    abstract suspend fun getEventSummaries(
        type: EventType,
        startTime: Instant,
        endTime: Instant
    ): List<CachedEventSummary>

    @Query(
        """
            SELECT json FROM events
            WHERE id = :id
        """
    )
    protected abstract suspend fun _getEventJsonById(id: String): String

    /*
     * TODO: remove when Room supports value classes
     */
    suspend fun getEventJsonById(id: EventId): String =
        _getEventJsonById(id.stringValue)
}
