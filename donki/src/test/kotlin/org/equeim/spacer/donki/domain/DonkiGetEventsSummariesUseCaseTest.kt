package org.equeim.spacer.donki.domain

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.equeim.spacer.donki.data.model.*
import org.equeim.spacer.donki.data.repository.DonkiRepository
import org.equeim.spacer.donki.instantOf
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class DonkiGetEventsSummariesUseCaseTest(private val systemTimeZone: ZoneId, private val requestedTimeZone: ZoneId) {
    private val clock = Clock.fixed(Instant.now(), systemTimeZone)

    private val repository = mockk<DonkiRepository>()
    private val useCase = DonkiGetEventsSummariesUseCase(repository, clock)

    @Test
    fun `Validate dates when getting events for last week`() = runTest {
        val startDates = mutableListOf<Instant>()
        val endDates = mutableListOf<Instant>()
        coEvery { repository.getEventsSummaries(any(), capture(startDates), capture(endDates)) } returns emptyList()
        useCase.getEventsSummariesGroupedByDateForLastWeek(requestedTimeZone)
        coVerify { repository.getEventsSummaries(any(), any(), any()) }
        confirmVerified(repository)
        val expectedDifference = Duration.ofDays(6)
        startDates.asSequence().zip(endDates.asSequence()).forEach { (startDate, endDate) ->
            assertEquals(expectedDifference, Duration.between(startDate, endDate))
            assertEquals(LocalTime.MIDNIGHT, LocalTime.ofInstant(startDate, systemTimeZone))
            assertEquals(LocalTime.MIDNIGHT, LocalTime.ofInstant(endDate, systemTimeZone))
        }
    }

    @Test
    fun `No events for any type`() = runTest {
        coEvery { repository.getEventsSummaries(any(), any(), any()) } returns emptyList()
        val groupedEvents =
            useCase.getEventsSummariesGroupedByDate(Instant.EPOCH, Instant.EPOCH, requestedTimeZone)
        assertTrue(groupedEvents.isEmpty())
    }

    @Test
    fun `Validate events grouping and sorting`() = runTest {
        val repositoryEvents = buildMap {
            set(
                EventType.CoronalMassEjection, listOf(
                    CoronalMassEjectionSummary(
                        EventId("2022-CME-1"),
                        instantOf(2022, 1, 1, 4, 2),
                        false
                    ),
                    CoronalMassEjectionSummary(
                        EventId("2022-CME-2"),
                        instantOf(2022, 1, 1, 4, 2),
                        true
                    ),
                    CoronalMassEjectionSummary(
                        EventId("2022-CME-3"),
                        instantOf(2022, 1, 1, 1, 44),
                        false
                    )
                )
            )
            set(
                EventType.GeomagneticStorm, listOf(
                    GeomagneticStormSummary(EventId("2022-GST-1"), instantOf(221, 12, 4, 2, 42), 6),
                    GeomagneticStormSummary(EventId("2022-GST-2"), instantOf(221, 12, 4, 2, 2), null)
                )
            )
            set(
                EventType.InterplanetaryShock, listOf(
                    InterplanetaryShockSummary(EventId("2022-IPS-1"), instantOf(2022, 6, 6, 6, 6)),
                    InterplanetaryShockSummary(EventId("2022-IPS-2"), instantOf(2022, 6, 6, 6, 7))
                )
            )
            set(
                EventType.SolarFlare, listOf(
                    SolarFlareSummary(EventId("2022-FLR-1"), instantOf(666, 6, 6, 6, 6))
                )
            )
            set(
                EventType.SolarEnergeticParticle, listOf(
                    SolarEnergeticParticleSummary(EventId("2022-SEP-1"), instantOf(2022, 5, 22, 0, 13)),
                    SolarEnergeticParticleSummary(EventId("2022-SEP-2"), instantOf(2022, 5, 22, 0, 10)),
                    SolarEnergeticParticleSummary(EventId("2022-SEP-3"), instantOf(2022, 5, 21, 20, 0))
                )
            )
            set(
                EventType.MagnetopauseCrossing, listOf(
                    MagnetopauseCrossingSummary(EventId("2022-MPC-1"), instantOf(2022, 1, 2, 3, 4)),
                    MagnetopauseCrossingSummary(EventId("2022-MPC-2"), instantOf(2021, 5, 6, 7, 8)),
                    MagnetopauseCrossingSummary(EventId("2022-MPC-3"), instantOf(2020, 9, 10, 11, 12))
                )
            )
            set(
                EventType.RadiationBeltEnhancement, listOf(
                    RadiationBeltEnhancementSummary(EventId("2022-RBE-1"), instantOf(1917, 11, 7, 1, 1)),
                    RadiationBeltEnhancementSummary(EventId("2022-RBE-2"), instantOf(1918, 11, 9, 1, 1)),
                    RadiationBeltEnhancementSummary(EventId("2022-RBE-3"), instantOf(1917, 3, 8, 1, 1))
                )
            )
            set(
                EventType.HighSpeedStream, listOf(
                    RadiationBeltEnhancementSummary(EventId("2022-HSS-1"), instantOf(1874, 11, 16, 1, 1)),
                    RadiationBeltEnhancementSummary(EventId("2022-HSS-2"), instantOf(1920, 2, 7, 1, 1)),
                    RadiationBeltEnhancementSummary(EventId("2022-HSS-3"), instantOf(1917, 3, 8, 0, 0))
                )
            )
        }
        coEvery {
            repository.getEventsSummaries(
                any(),
                any(),
                any()
            )
        } answers { repositoryEvents[firstArg()]!! }
        val groupedEvents =
            useCase.getEventsSummariesGroupedByDate(Instant.EPOCH, Instant.EPOCH, requestedTimeZone)

        val expectedDays = repositoryEvents
            .values
            .asSequence()
            .flatten()
            .map { it.time.atZone(requestedTimeZone).toLocalDate() }
            .sortedDescending()
            .distinct()
            .toList()
        assertEquals(expectedDays, groupedEvents.map { it.date })

        val allRepositoryEvents = repositoryEvents.values.flatten().toMutableSet()
        for ((date, events) in groupedEvents) {
            events.forEach { event ->
                assertEquals(date, event.zonedTime.toLocalDate())
                assertEquals(event.event.time.atZone(requestedTimeZone), event.zonedTime)
                allRepositoryEvents.remove(event.event)
            }
        }
        assertTrue(allRepositoryEvents.isEmpty())
    }

    private fun instantOf(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int) =
        instantOf(year, month, dayOfMonth, hour, minute, ZoneOffset.UTC)

    companion object {
        private val timeZones = setOf(
            ZoneId.ofOffset("UTC", ZoneOffset.UTC),
            ZoneId.systemDefault(),
            ZoneId.of("Asia/Yakutsk"),
            ZoneId.of("America/Los_Angeles")
        )

        @Parameterized.Parameters(name = "system={0}, requested={1}")
        @JvmStatic
        fun timeZones(): Iterable<Array<ZoneId>> = buildSet<Pair<ZoneId, ZoneId>> {
            timeZones.forEach { system ->
                timeZones.forEach { requested ->
                    add(system to requested)
                }
            }
        }.map { (system, requested) -> arrayOf(system, requested) }
    }
}
