package org.equeim.spacer.donki.data.cache

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.equeim.spacer.donki.CoroutineDispatchers
import org.equeim.spacer.donki.data.Week
import org.equeim.spacer.donki.data.cache.entities.CachedWeek
import org.equeim.spacer.donki.data.cache.entities.toCachedEvent
import org.equeim.spacer.donki.data.cache.entities.toExtras
import org.equeim.spacer.donki.data.model.*
import java.io.Closeable
import java.nio.file.*
import java.time.Duration
import java.time.Instant

private const val TAG = "DonkiDataSourceCache"
private val WEEK_UP_TO_DATE_THRESHOLD: Duration = Duration.ofDays(7)

internal class DonkiDataSourceCache(
    private val context: Context,
    db: DonkiDatabase? = null,
    private val coroutineDispatchers: CoroutineDispatchers = CoroutineDispatchers()
) : Closeable {
    private val coroutineScope = CoroutineScope(coroutineDispatchers.Default + SupervisorJob())
    private var dbWatchKey: WatchKey? = null
    private var db: DonkiDatabase

    private val _databaseRecreated = MutableSharedFlow<Unit>()
    val databaseRecreated: Flow<Unit> by ::_databaseRecreated

    init {
        if (db != null) {
            this.db = db
        } else {
            val databaseDirectory = context.cacheDir.toPath().resolve(DonkiDatabase.NAME)
            this.db = createDatabase(databaseDirectory)
            val storageManager = checkNotNull(context.getSystemService<StorageManager>())
            coroutineScope.launch(Dispatchers.IO) {
                Files.createDirectories(databaseDirectory)
                storageManager.setCacheBehaviorGroup(databaseDirectory.toFile(), true)
                storageManager.setCacheBehaviorTombstone(databaseDirectory.toFile(), true)
                recreateDatabaseWhenDeleted(databaseDirectory)
            }
        }
    }

    private fun createDatabase(databaseDirectory: Path): DonkiDatabase {
        Log.d(TAG, "createDatabase() called with: databaseDirectory = $databaseDirectory")
        return Room.databaseBuilder(
            context,
            DonkiDatabase::class.java,
            databaseDirectory.resolve(DonkiDatabase.NAME).toString()
        ).build()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun recreateDatabaseWhenDeleted(databaseDirectory: Path) {
        val watchService = FileSystems.getDefault().newWatchService()
        val watchKey = databaseDirectory.register(watchService, StandardWatchEventKinds.ENTRY_DELETE)
        watchService.events(coroutineDispatchers).onEach {
            if ((it.context() as? Path)?.toString() == DonkiDatabase.NAME) {
                recreateDatabase(databaseDirectory)
            }
        }.launchIn(coroutineScope + Dispatchers.Main)
        withContext(coroutineDispatchers.Main) {
            dbWatchKey = watchKey
        }
    }

    private suspend fun recreateDatabase(databaseDirectory: Path) {
        Log.d(TAG, "recreateDatabase() called with: databaseDirectory = $databaseDirectory")
        db.close()
        db = createDatabase(databaseDirectory)
        _databaseRecreated.emit(Unit)
    }

    override fun close() {
        Log.d(TAG, "close() called")
        coroutineScope.cancel()
        dbWatchKey?.cancel()
        db.close()
    }

    suspend fun isWeekCachedAndNeedsRefresh(week: Week, eventType: EventType): Boolean {
        val cacheLoadTime = db.cachedWeeks()
            .getWeekLoadTime(week.weekBasedYear, week.weekOfWeekBasedYear, eventType)
        return cacheLoadTime != null && week.needsRefresh(cacheLoadTime)
    }

    suspend fun getEventSummariesForWeek(
        week: Week,
        eventType: EventType,
        returnCacheThatNeedsRefreshing: Boolean
    ): List<EventSummary>? {
        Log.d(
            TAG,
            "getEventSummariesForWeek() called with: week = $week, eventType = $eventType, returnCacheThatNeedsRefreshing = $returnCacheThatNeedsRefreshing"
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
            if (!returnCacheThatNeedsRefreshing && week.needsRefresh(weekCacheTime)) {
                Log.d(
                    TAG,
                    "getEventSummariesForWeek: cache needs refreshing for week = $week, eventType = $eventType, returning null"
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
                Log.e(
                    TAG,
                    "getEventSummariesForWeek: failed to get events summaries for week = $week, eventType = $eventType",
                    e
                )
            }
            throw e
        }
    }

    private fun Week.needsRefresh(cacheLoadTime: Instant): Boolean {
        return Duration.between(
            getInstantAfterLastDay(),
            cacheLoadTime
        ) < WEEK_UP_TO_DATE_THRESHOLD
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

private fun WatchService.events(coroutineDispatchers: CoroutineDispatchers): Flow<WatchEvent<*>> = flow {
    try {
        while (currentCoroutineContext().isActive) {
            val key = runInterruptible { take() }
            key.pollEvents().forEach { emit(it) }
            key.reset()
        }
    } catch (ignore: ClosedWatchServiceException) {
    }
}.flowOn(coroutineDispatchers.IO)
