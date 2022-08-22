// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens.donki

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.equeim.spacer.AppSettings
import org.equeim.spacer.donki.data.DonkiRepository
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.ui.utils.defaultLocaleFlow
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "DonkiEventsScreenViewModel"

class DonkiEventsScreenViewModel(application: Application) : AndroidViewModel(application) {
    init {
        Log.d(TAG, "DonkiEventsScreenViewModel() called")
    }

    private val repository = DonkiRepository(application)
    private val settings = AppSettings(application)

    private class LocaleDependentState {
        val eventTimeFormatter: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val eventDateFormatter: DateTimeFormatter =
            DateTimeFormatterBuilder().appendLocalized(FormatStyle.LONG, null).appendLiteral(' ')
                .appendZoneText(TextStyle.SHORT).toFormatter()
        val eventTypesStrings = ConcurrentHashMap<EventType, String>()
    }

    @Volatile
    private lateinit var localeDependentState: LocaleDependentState

    val pagingData: Flow<PagingData<ListItem>>

    init {
        val basePagingData = repository.getEventSummariesPager().flow.cachedIn(viewModelScope)
        val defaultLocaleFlow = application.defaultLocaleFlow().onEach {
            localeDependentState = LocaleDependentState()
        }
        pagingData = combine(
            basePagingData,
            settings.displayEventsTimeInUTC.flow(),
            defaultLocaleFlow,
            ::Triple
        ).map { (pagingData, displayEventsTimeInUTC, _) ->
            pagingData.toListItems(displayEventsTimeInUTC)
        }.cachedIn(viewModelScope)
        addCloseable(repository)
    }

    private fun PagingData<EventSummary>.toListItems(displayEventsTimeInUTC: Boolean): PagingData<ListItem> {
        val timeZone = if (displayEventsTimeInUTC) {
            ZoneId.ofOffset("UTC", ZoneOffset.UTC)
        } else {
            ZoneId.systemDefault()
        }
        return map {
            withContext(Dispatchers.Default) {
                EventSummaryWithZonedTime(it, it.time.atZone(timeZone))
            }
        }
            .insertSeparators(TerminalSeparatorType.SOURCE_COMPLETE) { before, after ->
                when {
                    after == null -> null
                    before == null || before.zonedTime.dayOfMonth != after.zonedTime.dayOfMonth -> {
                        withContext(Dispatchers.Default) {
                            DateSeparator(
                                after.eventSummary.time.epochSecond,
                                localeDependentState.eventDateFormatter.format(after.zonedTime)
                            )
                        }
                    }
                    else -> null
                }
            }
            .map {
                when (it) {
                    is EventSummaryWithZonedTime ->
                        withContext(Dispatchers.Default) { it.toPresentation() }
                    else -> it
                } as ListItem
            }
    }

    private data class EventSummaryWithZonedTime(
        val eventSummary: EventSummary,
        val zonedTime: ZonedDateTime
    )

    private fun EventSummaryWithZonedTime.toPresentation(): EventPresentation {
        return EventPresentation(
            id = eventSummary.id,
            type = eventSummary.getTypeDisplayString(),
            time = localeDependentState.eventTimeFormatter.format(zonedTime)
        )
    }

    private fun EventSummary.getTypeDisplayString(): String {
        return localeDependentState.eventTypesStrings.computeIfAbsent(type) { getString(type.displayStringResId) }
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
