// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.events.cache

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.content.getSystemService
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.JsonObject
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.common.DateRange
import org.equeim.spacer.donki.data.common.DonkiCacheDataSourceException
import org.equeim.spacer.donki.data.common.DonkiJson
import org.equeim.spacer.donki.data.common.Week
import org.equeim.spacer.donki.data.common.intersect
import org.equeim.spacer.donki.data.events.DonkiEventsRepository
import org.equeim.spacer.donki.data.events.EventId
import org.equeim.spacer.donki.data.events.EventType
import org.equeim.spacer.donki.data.events.cache.entities.CachedEventsWeek
import org.equeim.spacer.donki.data.events.cache.entities.EVENTS_DONT_NEED_REFRESH_THRESHOLD_SECONDS
import org.equeim.spacer.donki.data.events.cache.entities.toCachedEvent
import org.equeim.spacer.donki.data.events.cache.entities.toExtras
import org.equeim.spacer.donki.data.events.network.json.CoronalMassEjection
import org.equeim.spacer.donki.data.events.network.json.Event
import org.equeim.spacer.donki.data.events.network.json.EventSummary
import org.equeim.spacer.donki.data.events.network.json.GeomagneticStorm
import org.equeim.spacer.donki.data.events.network.json.InterplanetaryShock
import org.equeim.spacer.donki.data.events.network.json.SolarFlare
import org.equeim.spacer.donki.data.events.network.json.eventSerializer
import java.io.Closeable
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.time.Clock
import java.time.Duration
import java.time.Instant

internal class EventsDataSourceCache(
    private val context: Context,
    db: EventsCacheDatabase?,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val clock: Clock,
) : Closeable {
    private val coroutineScope = CoroutineScope(coroutineDispatchers.IO + SupervisorJob())

    @Volatile
    private var eventsCacheDb: EventsCacheDatabase? = null

    @Volatile
    private var eventsCacheDbWatchKey: WatchKey? = null

    @Volatile
    private var dbInitJob: Job? = null

    private val _databaseRecreated = MutableSharedFlow<Unit>()
    val databaseRecreated: Flow<Unit> by ::_databaseRecreated

    init {
        if (db != null) {
            this.eventsCacheDb = db
        } else {
            dbInitJob = coroutineScope.launch { initDatabase() }.apply {
                invokeOnCompletion { dbInitJob = null }
            }
        }
    }

    @WorkerThread
    private fun initDatabase() {
        val databaseDirectory = context.cacheDir.toPath().resolve(EventsCacheDatabase.NAME)
        Files.createDirectories(databaseDirectory)
        val storageManager = checkNotNull(context.getSystemService<StorageManager>())
        storageManager.setCacheBehaviorGroup(databaseDirectory.toFile(), true)
        storageManager.setCacheBehaviorTombstone(databaseDirectory.toFile(), true)
        eventsCacheDb = createDatabase(databaseDirectory)
        recreateDatabaseWhenDeleted(databaseDirectory)
    }

    @WorkerThread
    private fun createDatabase(databaseDirectory: Path): EventsCacheDatabase {
        Log.d(TAG, "createDatabase() called with: databaseDirectory = $databaseDirectory")
        return Room.databaseBuilder(
            context,
            EventsCacheDatabase::class.java,
            databaseDirectory.resolve(EventsCacheDatabase.NAME).toString()
        ).build()
    }

    private fun recreateDatabaseWhenDeleted(databaseDirectory: Path) {
        val watchService = FileSystems.getDefault().newWatchService()
        val watchKey =
            databaseDirectory.register(watchService, StandardWatchEventKinds.ENTRY_DELETE)
        watchService.events(coroutineDispatchers).onEach {
            if ((it.context() as? Path)?.toString() == EventsCacheDatabase.NAME) {
                recreateDatabase(databaseDirectory)
            }
        }.launchIn(coroutineScope)
        eventsCacheDbWatchKey = watchKey
    }

    @WorkerThread
    private suspend fun recreateDatabase(databaseDirectory: Path) {
        Log.d(TAG, "recreateDatabase() called with: databaseDirectory = $databaseDirectory")
        eventsCacheDb?.close()
        eventsCacheDb = createDatabase(databaseDirectory)
        _databaseRecreated.emit(Unit)
    }

    override fun close() {
        Log.d(TAG, "close() called")
        coroutineScope.cancel()
        dbInitJob?.cancel()
        eventsCacheDbWatchKey?.cancel()
        eventsCacheDb?.close()
    }

    private suspend fun awaitDb(): EventsCacheDatabase {
        dbInitJob?.join()
        return checkNotNull(eventsCacheDb)
    }

    /**
     * @throws DonkiCacheDataSourceException
     */
    suspend fun getEventSummariesForWeek(
        week: Week,
        eventType: EventType,
        dateRange: DateRange?,
    ): List<EventSummary>? {
        Log.d(
            TAG,
            "getEventSummariesForWeek() called with: week = $week, eventType = $eventType, dateRange = $dateRange"
        )
        val db = awaitDb()
        return try {
            if (!db.cachedWeeks().isWeekCached(week.getFirstDayInstant(), eventType)) {
                Log.d(
                    TAG,
                    "getEventSummariesForWeek: no cache for week = $week, eventType = $eventType, dateRange = $dateRange, returning null"
                )
                return null
            }

            val startTime: Instant
            val endTime: Instant
            if (dateRange != null) {
                startTime = dateRange.firstDayInstant
                endTime = dateRange.instantAfterLastDay
            } else {
                startTime = week.getFirstDayInstant()
                endTime = week.getInstantAfterLastDay()
            }

            when (eventType) {
                EventType.CoronalMassEjection -> db.coronalMassEjection()
                    .getEventSummaries(startTime, endTime)

                EventType.GeomagneticStorm -> db.geomagneticStorm()
                    .getEventSummaries(startTime, endTime)

                EventType.InterplanetaryShock -> db.interplanetaryShock()
                    .getEventSummaries(startTime, endTime)

                EventType.SolarFlare -> db.solarFlare().getEventSummaries(startTime, endTime)
                else -> db.events().getEventSummaries(eventType, startTime, endTime)
            }.also {
                Log.d(
                    TAG,
                    "getEventSummariesForWeek: returning ${it.size} events for week = $week, eventType = $eventType, dateRange = $dateRange"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DonkiCacheDataSourceException("getEventSummariesForWeek with: week = $week, eventType = $eventType, dateRange = $dateRange failed", e)
        }
    }

    data class WeekThatNeedsRefresh(
        val week: Week,
        val eventType: EventType,
        val cachedRecently: Boolean
    )

    /**
     * Returned flow throws [DonkiCacheDataSourceException]
     */
    fun getWeeksThatNeedRefresh(
        eventTypes: List<EventType>,
        dateRange: DateRange?
    ): Flow<List<WeekThatNeedsRefresh>> {
        Log.d(
            TAG,
            "getWeeksThatNeedRefresh() called with: eventTypes = $eventTypes, dateRange = $dateRange"
        )
        if (eventTypes.isEmpty()) {
            Log.d(TAG, "getWeeksThatNeedRefresh: empty event types")
            return flowOf(emptyList())
        }
        return flow { emitAll(awaitDb().cachedWeeks().getWeeksThatNeedRefresh()) }.map { weeks ->
            if (weeks.isEmpty()) {
                val currentWeek = Week.getCurrentWeek(clock)
                return@map if (dateRange == null || dateRange.lastWeek == currentWeek) {
                    Log.d(TAG, "getWeeksThatNeedRefresh: no weeks need refresh, return current week")
                    eventTypes.map { WeekThatNeedsRefresh(currentWeek, it, cachedRecently = false) }
                } else {
                    Log.d(TAG, "getWeeksThatNeedRefresh: no weeks need refresh")
                    emptyList()
                }
            }
            Log.d(TAG, "getWeeksThatNeedRefresh: all weeks that need refresh:\n${weeks.joinToString("\n") { it.toLogString(clock) }}")
            var filtered: Sequence<CachedEventsWeek> = weeks.asSequence().filter {
                eventTypes.contains(it.eventType)
            }
            if (dateRange != null) {
                filtered = filtered.filter { dateRange.intersect(it.toDateRange()) }
            }
            filtered.map {
                WeekThatNeedsRefresh(
                    week = Week.fromInstant(it.timeAtStartOfFirstDay),
                    eventType = it.eventType,
                    cachedRecently = Duration.between(
                        it.loadTime,
                        Instant.now(clock)
                    ) < RECENTLY_CACHED_INTERVAL
                )
            }.toList().also {
                Log.d(TAG, "getWeeksThatNeedRefresh() returned:\n${it.joinToString("\n")}")
            }
        }.catch {
            throw DonkiCacheDataSourceException("getWeeksThatNeedRefresh with: eventTypes = $eventTypes, dateRange = $dateRange failed", it)
        }
    }

    /**
     * @throws DonkiCacheDataSourceException
     */
    suspend fun getEventById(
        id: EventId,
        eventType: EventType,
        week: Week,
    ): DonkiEventsRepository.EventById? {
        Log.d(TAG, "getEventById() called with: id = $id, eventType = $eventType, week = $week")
        val db = awaitDb()
        try {
            val weekLoadTime = db.cachedWeeks()
                .getWeekLoadTime(week.getFirstDayInstant(), eventType)
            if (weekLoadTime == null) {
                Log.d(
                    TAG,
                    "getEventById: no cache for week = $week, eventType = $eventType, returning null"
                )
                return null
            }
            val json = db.events().getEventJsonById(id)
            if (json == null) {
                Log.e(TAG, "getEventById: did not find event $id, returning null")
                return null
            }
            val event = DonkiJson.decodeFromString(eventType.eventSerializer(), json)
            return DonkiEventsRepository.EventById(
                event = event,
                needsRefreshingNow = week.needsRefreshNow(weekLoadTime)
            ).also {
                Log.d(TAG, "getEventById: returning event $id")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw DonkiCacheDataSourceException("getEventById with: id = $id, week = $week, eventType = $eventType failed", e)
        }
    }

    /**
     * Catches exceptions
     */
    suspend fun isWeekNotCachedOrNeedsRefreshingNow(week: Week, eventType: EventType): Boolean {
        Log.d(
            TAG,
            "isWeekNotCachedOrNeedsRefreshingNow() called with: week = $week, eventType = $eventType"
        )
        return try {
            val db = awaitDb()
            val weekLoadTime = db.cachedWeeks()
                .getWeekLoadTime(week.getFirstDayInstant(), eventType)
            weekLoadTime == null || week.needsRefreshNow(weekLoadTime)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Don't propagate exceptions
            Log.e(TAG, "isWeekNotCachedOrNeedsRefreshingNow with: week = $week, eventType = $eventType failed", e)
            false
        }.also {
            Log.d(TAG, "isWeekNotCachedOrNeedsRefreshingNow() returned: $it")
        }
    }

    private fun Week.needsRefreshNow(loadTime: Instant): Boolean =
        loadTime < (getFirstDayInstant().plusSeconds(EVENTS_DONT_NEED_REFRESH_THRESHOLD_SECONDS)) &&
                Duration.between(loadTime, Instant.now(clock)) >= RECENTLY_CACHED_INTERVAL

    /**
     * Catches exceptions
     */
    suspend fun cacheWeek(
        week: Week,
        eventType: EventType,
        events: List<Pair<Event, JsonObject>>,
        loadTime: Instant,
    ) {
        Log.d(
            TAG,
            "cacheWeek() called with: week = $week, eventType = $eventType, events count = ${events.size}, loadTime = $loadTime"
        )
        try {
            if (events.isEmpty()) {
                Log.d(TAG, "cacheWeek: no events, mark as cached without transaction")
                updateCachedWeek(week, eventType, loadTime)
            } else {
                val db = awaitDb()
                db.withTransaction {
                    Log.d(TAG, "cacheWeek: starting transaction for $week")
                    updateCachedWeek(week, eventType, loadTime)
                    db.events()
                        .updateEvents(events.asSequence().map { it.toCachedEvent() }.asIterable())
                    for (event in events) {
                        when (eventType) {
                            EventType.CoronalMassEjection ->
                                db.coronalMassEjection()
                                    .updateExtras((event.first as CoronalMassEjection).toExtras())

                            EventType.GeomagneticStorm ->
                                db.geomagneticStorm()
                                    .updateExtras((event.first as GeomagneticStorm).toExtras())

                            EventType.InterplanetaryShock -> db.interplanetaryShock()
                                .updateExtras((event.first as InterplanetaryShock).toExtras())

                            EventType.SolarFlare -> db.solarFlare()
                                .updateExtras((event.first as SolarFlare).toExtras())

                            else -> Unit
                        }
                    }
                    Log.d(TAG, "cacheWeek: completing transaction for $week")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Don't propagate exceptions
            Log.e(TAG, "cacheWeek with: week = $week, eventType = $eventType, events count = ${events.size}, loadTime = $loadTime failed", e)
        }
    }

    private suspend fun updateCachedWeek(week: Week, eventType: EventType, loadTime: Instant) {
        awaitDb().cachedWeeks()
            .updateWeek(
                CachedEventsWeek(
                    week.getFirstDayInstant(),
                    eventType,
                    loadTime
                )
            )
    }

    private companion object {
        const val TAG = "DonkiDataSourceCache"
        val RECENTLY_CACHED_INTERVAL: Duration = Duration.ofHours(1)

        fun WatchService.events(coroutineDispatchers: CoroutineDispatchers): Flow<WatchEvent<*>> =
            flow {
                try {
                    while (currentCoroutineContext().isActive) {
                        val key: WatchKey = runInterruptible { take() }
                        try {
                            key.pollEvents().forEach { emit(it) }
                        } finally {
                            key.reset()
                        }
                    }
                } catch (ignore: ClosedWatchServiceException) {
                }
            }.flowOn(coroutineDispatchers.IO)
    }
}
