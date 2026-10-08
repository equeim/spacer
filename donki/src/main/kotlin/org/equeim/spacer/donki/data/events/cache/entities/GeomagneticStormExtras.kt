// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:Suppress("FunctionName")

package org.equeim.spacer.donki.data.events.cache.entities

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.ForeignKey.Companion.CASCADE
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import org.equeim.spacer.donki.data.events.EventId
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
