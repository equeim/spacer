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
import org.equeim.spacer.donki.data.events.network.json.InterplanetaryShock
import org.equeim.spacer.donki.data.events.network.json.InterplanetaryShockSummary
import java.time.Instant

@Entity(
    tableName = "interplanetary_shock_extras", foreignKeys = [
        ForeignKey(
            entity = CachedEvent::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = CASCADE
        )
    ]
)
internal data class InterplanetaryShockExtras(
    @ColumnInfo(name = "id") @PrimaryKey
    val id: EventId,
    @ColumnInfo(name = "location")
    val location: String
)

internal fun InterplanetaryShock.toExtras() = InterplanetaryShockExtras(
    id = id,
    location = location
)

internal data class InterplanetaryShockExtrasSummaryCached(
    @ColumnInfo(name = "id")
    override val id: EventId,
    @ColumnInfo(name = "time")
    override val time: Instant,
    @ColumnInfo(name = "location")
    override val location: String
) : InterplanetaryShockSummary

@Dao
internal interface InterplanetaryShockDao {
    @Query(
        """
            SELECT events.id, events.time, location FROM events
            JOIN interplanetary_shock_extras ON events.id = interplanetary_shock_extras.id
            WHERE events.type = "IPS" AND events.time >= :startTime AND events.time < :endTime
            ORDER BY time DESC
        """
    )
    suspend fun getEventSummaries(
        startTime: Instant,
        endTime: Instant
    ): List<InterplanetaryShockExtrasSummaryCached>

    @Insert(onConflict = REPLACE)
    suspend fun updateExtras(extras: InterplanetaryShockExtras)
}
