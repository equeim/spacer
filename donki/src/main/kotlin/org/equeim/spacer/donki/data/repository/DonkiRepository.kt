package org.equeim.spacer.donki.data.repository

import android.content.Context
import android.util.Log
import androidx.paging.PagingSource
import kotlinx.coroutines.CancellationException
import org.equeim.spacer.donki.data.model.EventDetails
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.network.DonkiDataSourceNetwork
import java.time.Instant

private const val TAG = "DonkiRepository"

interface DonkiRepository {
    suspend fun getEventsSummaries(eventType: EventType, startDate: Instant, endDate: Instant): List<EventSummary>
    fun getEventsSummariesPagingSource(): PagingSource<*, EventSummary>
    suspend fun getEventDetailsById(id: EventId): EventDetails
}

fun DonkiRepository(context: Context): DonkiRepository = DonkiRepositoryImpl(context)

private class DonkiRepositoryImpl(context: Context) : DonkiRepository {
    private val networkDataSource = DonkiDataSourceNetwork()
    //private val dbProvider = DonkiDataSourceDb(context)

    override suspend fun getEventsSummaries(
        eventType: EventType,
        startDate: Instant,
        endDate: Instant
    ): List<EventSummary> = try {
        networkDataSource.getEvents(eventType, startDate, endDate).map { it.toEventSummary() }
    } catch (e: Exception) {
        if (e !is CancellationException) {
            Log.e(TAG, "getEvents: failed to get events summaries", e)
        }
        throw e
    }

    override fun getEventsSummariesPagingSource(): PagingSource<*, EventSummary> {
        return EventsSummariesPagingSource(networkDataSource)
    }

    override suspend fun getEventDetailsById(id: EventId): EventDetails = try {
        networkDataSource.getEventById(id).toEventDetails()
    } catch (e: Exception) {
        if (e !is CancellationException) {
            Log.e(TAG, "getEventDetailsById: failed to get event details", e)
        }
        throw e
    }
}
