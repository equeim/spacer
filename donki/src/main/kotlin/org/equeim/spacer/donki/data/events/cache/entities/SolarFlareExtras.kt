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
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.PrimaryKey
import androidx.room.Query
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.network.json.SolarFlare
import org.equeim.spacer.donki.data.events.network.json.SolarFlareSummary
import java.time.Instant

@Entity(
    tableName = "solar_flare_extras", foreignKeys = [
        ForeignKey(
            entity = CachedEvent::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = CASCADE
        )
    ]
)
internal data class SolarFlareExtras(
    @ColumnInfo(name = "id") @PrimaryKey
    val id: EventId,
    @ColumnInfo(name = "class_type")
    val classType: String
)

internal fun SolarFlare.toExtras() = SolarFlareExtras(
    id = id,
    classType = classType
)

internal data class SolarFlareExtrasSummaryCached(
    @ColumnInfo(name = "id")
    override val id: EventId,
    @ColumnInfo(name = "time")
    override val time: Instant,
    @ColumnInfo(name = "class_type")
    override val classType: String
) : SolarFlareSummary

@Dao
internal abstract class SolarFlareDao {
    @Query(
        """
            SELECT events.id, events.time, class_type FROM events
            JOIN solar_flare_extras ON events.id = solar_flare_extras.id
            WHERE events.type = :type AND events.time >= :startTime AND events.time < :endTime
            ORDER BY time DESC
        """
    )
    protected abstract suspend fun _getEventSummaries(
        type: EventType,
        startTime: Instant,
        endTime: Instant
    ): List<SolarFlareExtrasSummaryCached>

    suspend fun getEventSummaries(
        startTime: Instant,
        endTime: Instant
    ): List<SolarFlareSummary> =
        _getEventSummaries(
            EventType.SolarFlare,
            startTime,
            endTime
        )

    @Insert(onConflict = REPLACE)
    abstract suspend fun updateExtras(extras: SolarFlareExtras)
}
