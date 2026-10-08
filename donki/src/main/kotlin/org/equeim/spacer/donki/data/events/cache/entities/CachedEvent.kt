// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:Suppress("FunctionName")

package org.equeim.spacer.donki.data.events.cache.entities

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import kotlinx.serialization.json.JsonObject
import org.equeim.spacer.donki.data.common.DonkiJson
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.network.json.Event
import org.equeim.spacer.donki.data.events.network.json.EventSummary
import java.time.Instant

@Entity(tableName = "events")
internal data class CachedEvent(
    @ColumnInfo(name = "id") @PrimaryKey
    val id: EventId,
    @ColumnInfo(name = "type")
    val type: EventType,
    @ColumnInfo(name = "time", index = true)
    val time: Instant,
    @ColumnInfo(name = "json")
    val json: String
)

internal fun Pair<Event, JsonObject>.toCachedEvent(): CachedEvent {
    val (event, json) = this
    return CachedEvent(
        id = event.id,
        type = event.type,
        time = event.time,
        json = DonkiJson.encodeToString(json)
    )
}

internal data class CachedEventSummary(
    @ColumnInfo(name = "id")
    override val id: EventId,
    @ColumnInfo(name = "type")
    override val type: EventType,
    @ColumnInfo(name = "time")
    override val time: Instant
) : EventSummary

@Dao
internal interface CachedEventsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateEvents(events: Iterable<CachedEvent>)

    @Query(
        """
            SELECT id, type, time FROM events
            WHERE type = :type AND time >= :startTime AND time < :endTime
            ORDER BY time DESC
        """
    )
    suspend fun getEventSummaries(
        type: EventType,
        startTime: Instant,
        endTime: Instant
    ): List<CachedEventSummary>

    @Query(
        """
            SELECT json FROM events
            WHERE id = :id
        """
    )
    suspend fun getEventJsonById(id: EventId): String?
}
