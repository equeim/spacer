// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:Suppress("FunctionName")

package org.equeim.spacer.donki.data.cache.entities

import androidx.room.*
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.OnConflictStrategy.Companion.REPLACE
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.model.InterplanetaryShock
import org.equeim.spacer.donki.data.model.InterplanetaryShockSummary
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
internal abstract class InterplanetaryShockDao {
    @Query(
        """
            SELECT events.id, events.time, location FROM events
            JOIN interplanetary_shock_extras ON events.id = interplanetary_shock_extras.id
            WHERE events.type = :type AND events.time >= :startTime AND events.time < :endTime
            ORDER BY time DESC
        """
    )
    protected abstract suspend fun _getEventSummaries(
        type: EventType,
        startTime: Instant,
        endTime: Instant
    ): List<InterplanetaryShockExtrasSummaryCached>

    suspend fun getEventSummaries(
        startTime: Instant,
        endTime: Instant
    ): List<InterplanetaryShockSummary> =
        _getEventSummaries(
            EventType.InterplanetaryShock,
            startTime,
            endTime
        )

    @Insert(onConflict = REPLACE)
    abstract suspend fun updateExtras(extras: InterplanetaryShockExtras)
}
