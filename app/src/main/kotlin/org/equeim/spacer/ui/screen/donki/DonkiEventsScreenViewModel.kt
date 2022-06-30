package org.equeim.spacer.ui.screen.donki

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.paging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.equeim.spacer.AppSettings
import org.equeim.spacer.donki.data.model.EventId
import org.equeim.spacer.donki.data.model.EventSummary
import org.equeim.spacer.donki.data.model.EventType
import org.equeim.spacer.donki.data.repository.DonkiRepository
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
        LocalViewModelStoreOwner
    }

    private val repository = DonkiRepository(application)
    private val settings = AppSettings(application)

    private val eventTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val eventDateFormatter: DateTimeFormatter =
        DateTimeFormatterBuilder().appendLocalized(FormatStyle.LONG, null).appendLiteral(' ').appendZoneText(TextStyle.SHORT).toFormatter()

    private val eventTypesStrings = ConcurrentHashMap<EventType, String>()

    private val pager = Pager(PagingConfig(pageSize = 20, enablePlaceholders = false), null) {
        repository.getEventsSummariesPagingSource()
    }
    val pagingData: Flow<PagingData<ListItem>>

    init {
        val basePagingData = pager.flow.cachedIn(viewModelScope)
        pagingData = combine(basePagingData, settings.displayEventsTimeInUTC.flow(), ::Pair).map { (pagingData, displayEventsTimeInUTC) ->
            pagingData.toListItems(displayEventsTimeInUTC)
        }.flowOn(Dispatchers.Default)
    }

    private fun PagingData<EventSummary>.toListItems(displayEventsTimeInUTC: Boolean): PagingData<ListItem> {
        val timeZone = if (displayEventsTimeInUTC) {
            ZoneId.ofOffset("UTC", ZoneOffset.UTC)
        } else {
            ZoneId.systemDefault()
        }
        return map { EventSummaryWithZonedTime(it, it.time.atZone(timeZone)) }
            .insertSeparators { before, after ->
                after ?: return@insertSeparators null
                if (before == null || before.zonedTime.dayOfMonth != after.zonedTime.dayOfMonth) {
                    DateSeparator(after.eventSummary.time.epochSecond, eventDateFormatter.format(after.zonedTime))
                } else {
                    null
                }
            }
            .map {
                when (it) {
                    is DateSeparator -> it
                    is EventSummaryWithZonedTime -> it.toPresentation()
                    else -> throw IllegalStateException()
                }
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
            time = eventTimeFormatter.format(zonedTime)
        )
    }

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
