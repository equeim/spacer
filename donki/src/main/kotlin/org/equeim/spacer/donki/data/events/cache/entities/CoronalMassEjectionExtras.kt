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
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjectionSummary
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
    val id: EventId,
    @ColumnInfo(name = "predicted_earth_impact")
    val predictedEarthImpact: CoronalMassEjection.EarthImpactType
)

internal fun CoronalMassEjection.toExtras() = CoronalMassEjectionExtras(
    id = id,
    predictedEarthImpact = predictedEarthImpact
)

internal data class CoronalMassEjectionExtrasSummaryCached(
    @ColumnInfo(name = "id")
    override val id: EventId,
    @ColumnInfo(name = "time")
    override val time: Instant,
    @ColumnInfo(name = "predicted_earth_impact")
    override val predictedEarthImpact: CoronalMassEjection.EarthImpactType
) : CoronalMassEjectionSummary

@Dao
internal abstract class CoronalMassEjectionDao {
    @Query(
        """
            SELECT events.id, events.time, predicted_earth_impact FROM events
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
