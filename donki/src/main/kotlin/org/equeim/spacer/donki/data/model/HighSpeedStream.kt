@file:UseSerializers(InstantSerializer::class)

package org.equeim.spacer.donki.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

@Serializable
data class HighSpeedStream(
    @SerialName("hssID") override val id: EventId,
    @SerialName("eventTime") override val time: Instant,
    @SerialName("link") override val link: String,
    @SerialName("linkedEvents") @Serializable(LinkedEventsSerializer::class) override val linkedEvents: List<EventId> = emptyList(),
    @SerialName("instruments") @Serializable(InstrumentsSerializer::class) val instruments: List<String> = emptyList()
) : Event {
    override val type: EventType
        get() = EventType.HighSpeedStream

    override fun toEventSummary(): EventSummary =
        HighSpeedStreamSummaryFromJson(
            id = id,
            time = time
        )
}

private data class HighSpeedStreamSummaryFromJson(
    override val id: EventId,
    override val time: Instant
) : EventSummary {
    override val type: EventType
        get() = EventType.HighSpeedStream
}
