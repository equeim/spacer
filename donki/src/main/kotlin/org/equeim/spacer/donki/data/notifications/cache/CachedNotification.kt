// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.notifications.cache

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import org.equeim.spacer.donki.data.notifications.NotificationId
import org.equeim.spacer.donki.data.notifications.NotificationType
import java.time.Instant

@Entity(tableName = "cached_notifications")
@Immutable
data class CachedNotification(
    @ColumnInfo(name = "id") @PrimaryKey
    val id: NotificationId,
    @ColumnInfo(name = "type")
    val type: NotificationType,
    @ColumnInfo(name = "time")
    val time: Instant,
    @ColumnInfo(name = "title")
    val title: String?,
    @ColumnInfo(name = "subtitle")
    val subtitle: String?,
    @ColumnInfo(name = "body")
    val body: String,
    @ColumnInfo(name = "link")
    val link: String,
    @ColumnInfo(name = "read")
    val read: Boolean,
)

@Immutable
data class CachedNotificationSummary(
    @ColumnInfo(name = "id")
    val id: NotificationId,
    @ColumnInfo(name = "type")
    val type: NotificationType,
    @ColumnInfo(name = "time")
    val time: Instant,
    @ColumnInfo(name = "title")
    val title: String?,
    @ColumnInfo(name = "subtitle")
    val subtitle: String?,
    @ColumnInfo(name = "read")
    val read: Boolean,
)

@Dao
internal abstract class CachedNotificationsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun storeNotifications(notifications: List<CachedNotification>)

    @Query(
        """
            SELECT id, type, time, title, subtitle, read FROM cached_notifications
            WHERE time >= :startTime AND time < :endTime AND type IN (:types)
            ORDER BY time DESC
        """
    )
    abstract suspend fun getNotificationSummaries(
        startTime: Instant,
        endTime: Instant,
        types: List<NotificationType>
    ): List<CachedNotificationSummary>

    @Query(
        """
            SELECT * FROM cached_notifications
            WHERE id = :id
        """
    )
    abstract suspend fun getNotificationById(id: NotificationId): CachedNotification?

    @Query(
        """
            UPDATE cached_notifications SET read = 1 WHERE id = :id
        """
    )
    abstract suspend fun markNotificationAsRead(id: NotificationId)
}
