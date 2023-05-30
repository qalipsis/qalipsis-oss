/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.report

import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coJustRun
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.qalipsis.core.head.jdbc.repository.ReportFileRepository
import io.qalipsis.core.head.jdbc.repository.ReportTaskRepository
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit


@WithMockk
internal class ReportsPurgeManagerTests {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var reportTaskRepository: ReportTaskRepository

    @MockK
    private lateinit var reportFileRepository: ReportFileRepository

    private lateinit var reportRecordsTTLConfiguration: ReportRecordsTTLConfiguration

    private lateinit var reportsPurgeManager: ReportsPurgeManager

    @BeforeEach
    fun setUp() {
        reportRecordsTTLConfiguration = mockk {
            every { taskTimeToLive } returns Duration.ofHours(2)
            every { fileTimeToLive } returns Duration.ofHours(5)
        }
        reportsPurgeManager =
            ReportsPurgeManager(reportRecordsTTLConfiguration, reportTaskRepository, reportFileRepository)
    }

    @Test
    fun `uses specified task records time to live to prune report tasks `() = testDispatcherProvider.run {
        //given
        val currentTime = getTimeMock()
        coJustRun { reportTaskRepository.deleteAllByUpdateTimestampLessThan(any()) }

        //when
        reportsPurgeManager.coInvokeInvisible<Unit>("pruneReportTaskRecords")

        //then
        coVerifyOnce {
            reportTaskRepository.deleteAllByUpdateTimestampLessThan(currentTime.minus(2, ChronoUnit.HOURS))
        }
        confirmVerified(reportTaskRepository)
    }

    @Test
    fun `uses specified file records time to live to prune report files `() = testDispatcherProvider.run {
        //given
        val currentTime = getTimeMock()
        coJustRun { reportFileRepository.deleteAllByCreationTimestampLessThan(any()) }

        //when
        reportsPurgeManager.coInvokeInvisible<Unit>("pruneReportFileRecords")

        //then
        coVerifyOnce {
            reportFileRepository.deleteAllByCreationTimestampLessThan(currentTime.minus(5, ChronoUnit.HOURS))
        }
        confirmVerified(reportFileRepository)
    }

    private fun getTimeMock(): Instant {
        val now = Instant.now()
        val fixedClock = Clock.fixed(now, ZoneId.systemDefault())
        mockkStatic(Clock::class)
        every { Clock.systemUTC() } returns fixedClock
        return now
    }

}