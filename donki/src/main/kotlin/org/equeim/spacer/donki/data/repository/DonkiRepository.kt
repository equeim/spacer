package org.equeim.spacer.donki.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.network.DonkiDataSourceNetwork
import java.time.Instant

private const val TAG = "DonkiRepository"

interface DonkiRepository {
    suspend fun getEventsSummaries(eventType: EventType, startDate: Instant, endDate: Instant): List<EventSummary>
}

fun DonkiRepository(context: Context): DonkiRepository = DonkiRepositoryImpl(context)

private class DonkiRepositoryImpl(context: Context) : DonkiRepository {
    private val restDataSource = DonkiDataSourceNetwork()
    //private val dbProvider = DonkiDataSourceDb(context)

    override suspend fun getEventsSummaries(
        eventType: EventType,
        startDate: Instant,
        endDate: Instant
    ): List<EventSummary> = try {
        restDataSource.getEvents(eventType, startDate, endDate).map { it.toEventSummary() }
    } catch (e: Exception) {
        if (e !is CancellationException) {
            Log.e(TAG, "getEvents: failed to get events summaries", e)
        }
        throw e
    }
}
