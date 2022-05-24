package org.equeim.spacer.donki.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
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
class DonkiGetEventsSummariesUseCaseTest(private val timeZone: ZoneId) {
    private val clock = Clock.fixed(Instant.EPOCH, SYSTEM_TIME_ZONE)

    private val repository = mockk<DonkiRepository>()
    private val useCase = DonkiGetEventsSummariesUseCase(repository, clock)

    @Test
    fun `Validate dates when getting events for last week`() = runTest {
        coEvery { repository.getEventsSummaries(any(), any(), any()) } returns emptyList()
        useCase.getEventsSummariesGroupedByDateForLastWeek(timeZone)
        val expectedStartDate = ZonedDateTime.now(clock).minusDays(6).toInstant()
        val expectedEndDate = Instant.now(clock)
        coVerify { repository.getEventsSummaries(any(), expectedStartDate, expectedEndDate) }
        confirmVerified(repository)
    }

    @Test
    fun `No events for any type`() = runTest {
        coEvery { repository.getEventsSummaries(any(), any(), any()) } returns emptyList()
        val groupedEvents =
            useCase.getEventsSummariesGroupedByDate(Instant.EPOCH, Instant.EPOCH, timeZone)
        assertTrue(groupedEvents.isEmpty())
    }

    @Test
    fun `Validate events grouping and sorting`() = runTest {
        val repositoryEvents = buildMap {
            set(
                EventType.CoronalMassEjection, listOf(
                    CoronalMassEjectionSummary(
                        "CME-1",
                        instantOf(2022, 1, 1, 4, 2),
                        false
                    ),
                    CoronalMassEjectionSummary(
                        "CME-2",
                        instantOf(2022, 1, 1, 4, 2),
                        true
                    ),
                    CoronalMassEjectionSummary(
                        "CME-3",
                        instantOf(2022, 1, 1, 1, 44),
                        false
                    )
                )
            )
            set(
                EventType.GeomagneticStorm, listOf(
                    GeomagneticStormSummary("GST-1", instantOf(221, 12, 4, 2, 42), 6),
                    GeomagneticStormSummary("GST-2", instantOf(221, 12, 4, 2, 2), null)
                )
            )
            set(
                EventType.InterplanetaryShock, listOf(
                    InterplanetaryShockSummary("IPS-1", instantOf(2022, 6, 6, 6, 6)),
                    InterplanetaryShockSummary("IPS-2", instantOf(2022, 6, 6, 6, 7))
                )
            )
            set(
                EventType.SolarFlare, listOf(
                    SolarFlareSummary("FLR-1", instantOf(666, 6, 6, 6, 6))
                )
            )
            set(
                EventType.SolarEnergeticParticle, listOf(
                    SolarEnergeticParticleSummary("SEP-1", instantOf(2022, 5, 22, 0, 13)),
                    SolarEnergeticParticleSummary("SEP-2", instantOf(2022, 5, 22, 0, 10)),
                    SolarEnergeticParticleSummary("SEP-3", instantOf(2022, 5, 21, 20, 0))
                )
            )
            set(
                EventType.MagnetopauseCrossing, listOf(
                    MagnetopauseCrossingSummary("MPC-1", instantOf(2022, 1, 2, 3, 4)),
                    MagnetopauseCrossingSummary("MPC-2", instantOf(2021, 5, 6, 7, 8)),
                    MagnetopauseCrossingSummary("MPC-3", instantOf(2020, 9, 10, 11, 12))
                )
            )
            set(
                EventType.RadiationBeltEnhancement, listOf(
                    RadiationBeltEnhancementSummary("RBE-1", instantOf(1917, 11, 7, 1, 1)),
                    RadiationBeltEnhancementSummary("RBE-2", instantOf(1918, 11, 9, 1, 1)),
                    RadiationBeltEnhancementSummary("RBE-3", instantOf(1917, 3, 8, 1, 1))
                )
            )
            set(
                EventType.HighSpeedStream, listOf(
                    RadiationBeltEnhancementSummary("HSS-1", instantOf(1874, 11, 16, 1, 1)),
                    RadiationBeltEnhancementSummary("HSS-2", instantOf(1920, 2, 7, 1, 1)),
                    RadiationBeltEnhancementSummary("HSS-3", instantOf(1917, 3, 8, 0, 0))
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
            useCase.getEventsSummariesGroupedByDate(Instant.EPOCH, Instant.EPOCH, timeZone)

        val expectedDays = repositoryEvents
            .values
            .asSequence()
            .flatten()
            .map { it.time.atZone(timeZone).toLocalDate() }
            .sortedDescending()
            .distinct()
            .toList()
        assertEquals(expectedDays, groupedEvents.map { it.date })

        val allRepositoryEvents = repositoryEvents.values.flatten().toMutableSet()
        for ((date, events) in groupedEvents) {
            events.forEach { event ->
                assertEquals(date, event.zonedTime.toLocalDate())
                assertEquals(event.event.time.atZone(timeZone), event.zonedTime)
                allRepositoryEvents.remove(event.event)
            }
        }
        assertTrue(allRepositoryEvents.isEmpty())
    }

    private fun instantOf(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int) =
        instantOf(year, month, dayOfMonth, hour, minute, timeZone)

    companion object {
        private val SYSTEM_TIME_ZONE: ZoneId = ZoneId.of("Asia/Yakutsk")

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun timeZones(): Iterable<Any> = listOf(
            SYSTEM_TIME_ZONE,
            ZoneId.ofOffset("UTC", ZoneOffset.UTC),
            ZoneId.of("America/Los_Angeles")
        )
    }
}