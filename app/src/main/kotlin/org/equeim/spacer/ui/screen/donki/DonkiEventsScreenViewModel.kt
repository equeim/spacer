package org.equeim.spacer.ui.screen.donki

import android.app.Application
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.equeim.spacer.AppSettings
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.ui.utils.defaultLocaleFlow
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.*

private const val TAG = "DonkiEventsScreenViewModel"

class DonkiEventsScreenViewModel(application: Application) : AndroidViewModel(application) {
    init {
        Log.d(TAG, "DonkiEventsScreenViewModel() called")
    }

    private val repository = DonkiRepository(application)
    private val settings = AppSettings(application)

    private val localeDependentStateMutex = Mutex()
    @GuardedBy("localeDependentStateMutex")
    private lateinit var eventTimeFormatter: DateTimeFormatter
    @GuardedBy("localeDependentStateMutex")
    private lateinit var eventDateFormatter: DateTimeFormatter
    @GuardedBy("localeDependentStateMutex")
    private val eventTypesStrings = mutableMapOf<EventType, String>()

    val pagingData: Flow<PagingData<ListItem>>

    init {
        val pager = repository.getEventSummariesPager()
        val basePagingData = pager.flow.cachedIn(viewModelScope)

        val defaultLocaleFlow = application.defaultLocaleFlow().onEach(::defaultLocaleChanged)
        pagingData = combine(
            basePagingData,
            settings.displayEventsTimeInUTC.flow(),
            defaultLocaleFlow,
            ::Triple
        ).map { (pagingData, displayEventsTimeInUTC, _) ->
            pagingData.toListItems(displayEventsTimeInUTC)
        }.flowOn(Dispatchers.Default)
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }

    private fun PagingData<EventSummary>.toListItems(displayEventsTimeInUTC: Boolean): PagingData<ListItem> {
        val timeZone = if (displayEventsTimeInUTC) {
            ZoneId.ofOffset("UTC", ZoneOffset.UTC)
        } else {
            ZoneId.systemDefault()
        }
        return map { EventSummaryWithZonedTime(it, it.time.atZone(timeZone)) }
            .insertSeparators(TerminalSeparatorType.SOURCE_COMPLETE) { before, after ->
                when {
                    after == null -> null
                    before == null || before.zonedTime.dayOfMonth != after.zonedTime.dayOfMonth -> {
                        localeDependentStateMutex.withLock {
                            DateSeparator(
                                after.eventSummary.time.epochSecond,
                                eventDateFormatter.format(after.zonedTime)
                            )
                        }
                    }
                    else -> null
                }
            }
            .map {
                when (it) {
                    is EventSummaryWithZonedTime -> it.toPresentation()
                    else -> it
                } as ListItem
            }
    }

    private suspend fun defaultLocaleChanged(defaultLocale: Locale) {
        Log.d(TAG, "defaultLocaleChanged() called with: defaultLocale = $defaultLocale")
        localeDependentStateMutex.withLock {
            eventTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            eventDateFormatter = DateTimeFormatterBuilder().appendLocalized(FormatStyle.LONG, null).appendLiteral(' ')
                .appendZoneText(TextStyle.SHORT).toFormatter()
            eventTypesStrings.clear()
        }
    }

    private data class EventSummaryWithZonedTime(
        val eventSummary: EventSummary,
        val zonedTime: ZonedDateTime
    )

    private suspend fun EventSummaryWithZonedTime.toPresentation(): EventPresentation {
        return localeDependentStateMutex.withLock {
            EventPresentation(
                id = eventSummary.id,
                type = eventSummary.getTypeDisplayString(),
                time = eventTimeFormatter.format(zonedTime)
            )
        }
    }

    @GuardedBy("localeDependentStateMutex")
    private fun EventSummary.getTypeDisplayString(): String {
        return eventTypesStrings.computeIfAbsent(type) { getString(type.displayStringResId) }
    }

    private fun getString(@StringRes resId: Int) = getApplication<Application>().getString(resId)

    sealed interface ListItem

    data class DateSeparator(
        val nextEventEpochSecond: Long,
        val date: String
    ) : ListItem

    data class EventPresentation(
        val id: EventId,
        val type: String,
        val time: String
    ) : ListItem
}
