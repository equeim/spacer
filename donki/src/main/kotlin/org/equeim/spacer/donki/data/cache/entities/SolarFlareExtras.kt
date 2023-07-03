// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:Suppress("FunctionName")

package org.equeim.spacer.donki.data.cache.entities

import androidx.room.*
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.OnConflictStrategy.Companion.REPLACE
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.model.SolarFlare
import org.equeim.spacer.donki.data.model.SolarFlareSummary
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
    val id: String,
    @ColumnInfo(name = "class_type")
    val classType: String
)

internal fun SolarFlare.toExtras() = SolarFlareExtras(
    id = id.stringValue,
    classType = classType
)

internal data class SolarFlareExtrasSummaryCached(
    @ColumnInfo(name = "id")
    private val idString: String,
    @ColumnInfo(name = "time")
    override val time: Instant,
    @ColumnInfo(name = "class_type")
    override val classType: String
) : SolarFlareSummary {
    override val id: EventId
        get() = EventId(idString)
}

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
