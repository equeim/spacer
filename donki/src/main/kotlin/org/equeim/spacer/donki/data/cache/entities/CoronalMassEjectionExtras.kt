// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:Suppress("FunctionName")

package org.equeim.spacer.donki.data.cache.entities

import androidx.room.*
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.OnConflictStrategy.Companion.REPLACE
import org.equeim.spacer.donki.data.model.CoronalMassEjection
import org.equeim.spacer.donki.data.model.CoronalMassEjectionSummary
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventType
import java.time.Instant

@Entity(
    tableName = "coronal_mass_ejection_extras", foreignKeys = [
        ForeignKey(
            entity = CachedEvent::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = CASCADE
        )
    ]
)
internal data class CoronalMassEjectionExtras(
    @ColumnInfo(name = "id") @PrimaryKey
    val id: String,
    @ColumnInfo(name = "is_earth_shock_predicted")
    val isEarthShockPredicted: Boolean
)

internal fun CoronalMassEjection.toExtras() = CoronalMassEjectionExtras(
    id = id.stringValue,
    isEarthShockPredicted = isEarthShockPredicted()
)

internal data class CoronalMassEjectionExtrasSummaryCached(
    @ColumnInfo(name = "id")
    private val idString: String,
    @ColumnInfo(name = "time")
    override val time: Instant,
    @ColumnInfo(name = "is_earth_shock_predicted")
    override val isEarthShockPredicted: Boolean
) : CoronalMassEjectionSummary {
    override val id: EventId
        get() = EventId(idString)
}

@Dao
internal abstract class CoronalMassEjectionDao {
    @Query(
        """
            SELECT events.id, events.time, is_earth_shock_predicted FROM events
            JOIN coronal_mass_ejection_extras ON events.id = coronal_mass_ejection_extras.id
            WHERE events.type = :type AND events.time >= :startTime AND events.time < :endTime
            ORDER BY time DESC
        """
    )
    protected abstract suspend fun _getEventSummaries(
        type: EventType,
        startTime: Instant,
        endTime: Instant
    ): List<CoronalMassEjectionExtrasSummaryCached>

    suspend fun getEventSummaries(
        startTime: Instant,
        endTime: Instant
    ): List<CoronalMassEjectionSummary> =
        _getEventSummaries(
            EventType.CoronalMassEjection,
            startTime,
            endTime
        )

    @Insert(onConflict = REPLACE)
    abstract suspend fun updateExtras(extras: CoronalMassEjectionExtras)
}
