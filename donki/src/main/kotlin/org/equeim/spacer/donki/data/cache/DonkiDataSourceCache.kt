package org.equeim.spacer.donki.data.cache

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.equeim.spacer.donki.data.cache.entities.CachedWeek
import org.equeim.spacer.donki.data.cache.entities.toCachedEvent
import org.equeim.spacer.donki.data.cache.entities.toExtras
import org.equeim.spacer.donki.data.model.*
import org.equeim.spacer.donki.data.Week
import java.io.Closeable
import java.time.Duration
import java.time.Instant

private const val TAG = "DonkiDataSourceCache"
private val WEEK_UP_TO_DATE_THRESHOLD: Duration = Duration.ofDays(7)

private fun buildDatabase(context: Context): DonkiDatabase {
    val databaseDirectory = context.cacheDir.toPath().resolve(DonkiDatabase.NAME)

    val storageManager = checkNotNull(context.getSystemService<StorageManager>())
    storageManager.setCacheBehaviorGroup(databaseDirectory.toFile(), true)
    storageManager.setCacheBehaviorTombstone(databaseDirectory.toFile(), true)

    val databasePath = databaseDirectory.resolve(DonkiDatabase.NAME)
    return Room.databaseBuilder(
        context,
        DonkiDatabase::class.java,
        databasePath.toString()
    ).build()
}

internal class DonkiDataSourceCache @VisibleForTesting constructor(private val db: DonkiDatabase) : Closeable {
    constructor(context: Context) : this(buildDatabase(context))

    override fun close() {
        Log.d(TAG, "close() called")
        db.close()
    }

    suspend fun isWeekCachedAndOutOfDate(week: Week, eventType: EventType): Boolean {
        val cacheLoadTime = db.cachedWeeks()
            .getWeekLoadTime(week.weekBasedYear, week.weekOfWeekBasedYear, eventType)
        return cacheLoadTime != null && !week.isUpToDate(cacheLoadTime)
    }

    suspend fun getEventSummariesForWeek(
        week: Week,
        eventType: EventType,
        allowOutOfDateCache: Boolean
    ): List<EventSummary>? {
        Log.d(
            TAG,
            "getEventSummariesForWeek() called with: week = $week, eventType = $eventType, allowOutOfDateCache = $allowOutOfDateCache"
        )
        return try {
            val weekCacheTime = db.cachedWeeks()
                .getWeekLoadTime(week.weekBasedYear, week.weekOfWeekBasedYear, eventType)
            if (weekCacheTime == null) {
                Log.d(
                    TAG,
                    "getEventSummariesForWeek: no cache for week = $week, eventType = $eventType, returning null"
                )
                return null
            }
            if (!allowOutOfDateCache && !week.isUpToDate(weekCacheTime)) {
                Log.d(
                    TAG,
                    "getEventSummariesForWeek: out of date, cache for week = $week, eventType = $eventType, returning null"
                )
                return null
            }
            val startTime = week.getFirstDayInstant()
            val endTime = week.getInstantAfterLastDay()
            when (eventType) {
                EventType.CoronalMassEjection -> db.coronalMassEjection()
                    .getEventSummaries(startTime, endTime)
                EventType.GeomagneticStorm -> db.geomagneticStorm()
                    .getEventSummaries(startTime, endTime)
                else -> db.events().getEventSummaries(eventType, startTime, endTime)
            }.also {
                Log.d(
                    TAG,
                    "getEventSummariesForWeek: returning ${it.size} events for week = $week, eventType = $eventType"
                )
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e(TAG, "getEventSummariesForWeek: failed to get events summaries for week = $week, eventType = $eventType", e)
            }
            throw e
        }
    }

    private fun Week.isUpToDate(cacheLoadTime: Instant): Boolean {
        return Duration.between(
            getInstantAfterLastDay(),
            cacheLoadTime
        ) >= WEEK_UP_TO_DATE_THRESHOLD
    }

    suspend fun cacheWeek(
        week: Week,
        eventType: EventType,
        events: List<Event>,
        loadTime: Instant
    ) {
        Log.d(TAG, "cacheWeek() called with: week = $week, events = $events, loadTime = $loadTime")
        db.withTransaction {
            Log.d(TAG, "cacheWeek: starting transaction for $week")
            db.cachedWeeks()
                .updateWeek(
                    CachedWeek(
                        week.weekBasedYear,
                        week.weekOfWeekBasedYear,
                        eventType,
                        loadTime
                    )
                )
            for (event in events) {
                db.events().updateEvent(event.toCachedEvent())
                when (eventType) {
                    EventType.CoronalMassEjection ->
                        db.coronalMassEjection()
                            .updateExtras((event as CoronalMassEjection).toExtras())
                    EventType.GeomagneticStorm ->
                        db.geomagneticStorm()
                            .updateExtras((event as GeomagneticStorm).toExtras())
                    else -> Unit
                }
            }
            Log.d(TAG, "cacheWeek: completing transaction for $week")
        }
    }

    fun cacheWeekAsync(
        week: Week,
        eventType: EventType,
        events: List<Event>,
        loadTime: Instant
    ) {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch { cacheWeek(week, eventType, events, loadTime) }
    }
}
