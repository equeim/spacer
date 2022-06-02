package org.equeim.spacer.donki.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.repository.DonkiRepository
import java.time.*
import java.time.temporal.ChronoUnit

class DonkiGetEventsSummariesUseCase(
    private val repository: DonkiRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    data class EventsSummariesGroup(
        val date: LocalDate,
        val events: List<EventSummaryWithZonedTime>
    )

    data class EventSummaryWithZonedTime(
        val event: EventSummary,
        val zonedTime: ZonedDateTime
    )

    suspend fun getEventsSummariesGroupedByDate(
        startDate: Instant,
        endDate: Instant,
        timeZone: ZoneId
    ): List<EventsSummariesGroup> {
        return withContext(Dispatchers.Default) {
            val groupedEvents = sortedMapOf<LocalDate, MutableList<EventSummaryWithZonedTime>>(
                comparator = Comparator(LocalDate::compareTo).reversed()
            )
            val mutex = Mutex()
            coroutineScope {
                for (eventType in EventType.values()) {
                    launch {
                        val events = repository.getEventsSummaries(eventType, startDate, endDate)
                        mutex.withLock {
                            events.asSequence()
                                .map { EventSummaryWithZonedTime(it, it.time.atZone(timeZone)) }
                                .groupByTo(groupedEvents) { (_, zonedTime) ->
                                    zonedTime.toLocalDate()
                                }
                        }
                    }
                }
            }
            groupedEvents.map { (date, events) ->
                events.sortByDescending { it.zonedTime }
                EventsSummariesGroup(date, events)
            }
        }
    }

    suspend fun getEventsSummariesGroupedByDateForLastWeek(timeZone: ZoneId): List<EventsSummariesGroup> {
        val endDate = ZonedDateTime.now(clock).truncatedTo(ChronoUnit.DAYS)
        val startDate = endDate.minusDays(6)
        return getEventsSummariesGroupedByDate(startDate.toInstant(), endDate.toInstant(), timeZone)
    }
}
