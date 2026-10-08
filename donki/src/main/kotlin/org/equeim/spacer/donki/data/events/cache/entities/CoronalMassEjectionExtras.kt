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
import androidx.room3.OnConflictStrategy.Companion.REPLACE
import androidx.room3.PrimaryKey
import androidx.room3.Query
import org.equeim.spacer.donki.data.events.EventId
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
    val predictedEarthImpact: CoronalMassEjection.EarthImpactType,
    @ColumnInfo(name = "cme_type")
    val cmeType: CoronalMassEjection.CmeType?,
)

internal fun CoronalMassEjection.toExtras() = CoronalMassEjectionExtras(
    id = id,
    predictedEarthImpact = predictedEarthImpact,
    cmeType = cmeType,
)

internal data class CoronalMassEjectionExtrasSummaryCached(
    @ColumnInfo(name = "id")
    override val id: EventId,
    @ColumnInfo(name = "time")
    override val time: Instant,
    @ColumnInfo(name = "predicted_earth_impact")
    override val predictedEarthImpact: CoronalMassEjection.EarthImpactType,
    @ColumnInfo(name = "cme_type")
    override val cmeType: CoronalMassEjection.CmeType?,
) : CoronalMassEjectionSummary

@Dao
internal interface CoronalMassEjectionDao {
    @Query(
        """
            SELECT events.id, events.time, predicted_earth_impact, cme_type FROM events
            JOIN coronal_mass_ejection_extras ON events.id = coronal_mass_ejection_extras.id
            WHERE events.type = "CME" AND events.time >= :startTime AND events.time < :endTime
            ORDER BY time DESC
        """
    )
    suspend fun getEventSummaries(
        startTime: Instant,
        endTime: Instant
    ): List<CoronalMassEjectionExtrasSummaryCached>

    @Insert(onConflict = REPLACE)
    suspend fun updateExtras(extras: CoronalMassEjectionExtras)
}
