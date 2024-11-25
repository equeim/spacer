// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:Suppress("FunctionName")

package org.equeim.spacer.donki.data.events.cache.entities

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.network.json.GeomagneticStorm
import org.equeim.spacer.donki.data.events.network.json.GeomagneticStormSummary
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
internal interface GeomagneticStormDao {
    @Query(
        """
            SELECT events.id, events.time, kp_index FROM events
            JOIN geomagnetic_storm_extras ON events.id = geomagnetic_storm_extras.id
            WHERE events.type = "GST" AND events.time >= :startTime AND events.time < :endTime
            ORDER BY time DESC
        """
    )
    suspend fun getEventSummaries(
        startTime: Instant,
        endTime: Instant
    ): List<GeomagneticStormExtrasSummaryCached>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateExtras(extras: GeomagneticStormExtras)
}
