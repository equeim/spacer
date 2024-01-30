// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:Suppress("FunctionName")

package org.equeim.spacer.donki.data.cache.entities

import androidx.room.*
import androidx.room.ForeignKey.Companion.CASCADE
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.model.GeomagneticStorm
import org.equeim.spacer.donki.data.model.GeomagneticStormSummary
import java.time.Instant

@Entity(
    tableName = "geomagnetic_storm_extras", foreignKeys = [
        ForeignKey(
            entity = CachedEvent::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = CASCADE
        )
    ]
)
internal data class GeomagneticStormExtras(
    @ColumnInfo(name = "id") @PrimaryKey
    val id: EventId,
    @ColumnInfo(name = "kp_index")
    val kpIndex: Float?
)

internal fun GeomagneticStorm.toExtras() = GeomagneticStormExtras(
    id = id,
    kpIndex = kpIndex()
)

internal data class GeomagneticStormExtrasSummaryCached(
    @ColumnInfo(name = "id")
    override val id: EventId,
    @ColumnInfo(name = "time")
    override val time: Instant,
    @ColumnInfo(name = "kp_index")
    override val kpIndex: Float?
) : GeomagneticStormSummary

@Dao
internal abstract class GeomagneticStormDao {
    @Query(
        """
            SELECT events.id, events.time, kp_index FROM events
            JOIN geomagnetic_storm_extras ON events.id = geomagnetic_storm_extras.id
            WHERE events.type = :type AND events.time >= :startTime AND events.time < :endTime
            ORDER BY time DESC
        """
    )
    protected abstract suspend fun _getEventSummaries(
        type: EventType,
        startTime: Instant,
        endTime: Instant
    ): List<GeomagneticStormExtrasSummaryCached>

    internal suspend fun getEventSummaries(
        startTime: Instant,
        endTime: Instant
    ): List<GeomagneticStormSummary> =
        _getEventSummaries(
            EventType.GeomagneticStorm,
            startTime,
            endTime
        )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun updateExtras(extras: GeomagneticStormExtras)
}
