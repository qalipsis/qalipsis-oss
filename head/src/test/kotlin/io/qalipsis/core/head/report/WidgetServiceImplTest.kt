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

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.FactoryStateValue
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.FactoryStateRepository
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

@WithMockk
internal class WidgetServiceImplTest {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var factoryStateRepository: FactoryStateRepository

    @MockK
    private lateinit var campaignRepository: CampaignRepository

    @InjectMockKs
    private lateinit var widgetServiceImpl: WidgetServiceImpl

    @Test
    internal fun `should retrieve latest factory state for each factory id per tenant`() =
        testDispatcherProvider.runTest {
            // given
            val factoryStateCount = listOf(
                FactoryStateRepository.FactoryStateCount(FactoryStateValue.REGISTERED, 1),
                FactoryStateRepository.FactoryStateCount(FactoryStateValue.OFFLINE, 2),
                FactoryStateRepository.FactoryStateCount(FactoryStateValue.UNHEALTHY, 1),
            )
            coEvery {
                factoryStateRepository.countCurrentFactoryStatesByTenant(any())
            } returns factoryStateCount

            // when
            val factoryState = widgetServiceImpl.getFactoryStates(tenant = "my-tenant")

            // then
            assertThat(factoryState).all {
                prop(FactoryState::registered).isEqualTo(1)
                prop(FactoryState::offline).isEqualTo(2)
                prop(FactoryState::unhealthy).isEqualTo(1)
                prop(FactoryState::idle).isEqualTo(0)
            }
            coVerifyOnce {
                factoryStateRepository.countCurrentFactoryStatesByTenant("my-tenant")
            }
        }

    @Test
    internal fun `should retrieve the campaign results and their states when start and end is provided`() =
        testDispatcherProvider.runTest {
            // given
            val now = Instant.now().minus(26, ChronoUnit.HOURS)
            val end = Instant.now().minus(21, ChronoUnit.HOURS)
            val offset = 2
            val timeFrame = Duration.ofMinutes(30).minusSeconds(2600)
            val seriesInterval = now.minus(offset.toLong(), ChronoUnit.HOURS).minus(timeFrame)
            val campaignResultList = listOf(
                CampaignRepository.CampaignResultCount(
                    seriesStart = now.minus(offset.toLong(), ChronoUnit.HOURS),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 111
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = now.minus(offset.toLong(), ChronoUnit.HOURS),
                    status = ExecutionStatus.ABORTED,
                    count = 43
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = now.minus(offset.toLong(), ChronoUnit.HOURS),
                    status = ExecutionStatus.WARNING,
                    count = 58
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval,
                    status = ExecutionStatus.WARNING,
                    count = 183
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval,
                    status = ExecutionStatus.ABORTED,
                    count = 128
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval,
                    status = ExecutionStatus.QUEUED,
                    count = 232
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval,
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 411
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval.minus(timeFrame),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 26
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval.minus(timeFrame),
                    status = ExecutionStatus.FAILED,
                    count = 2
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval.minus(timeFrame).minus(timeFrame),
                    status = ExecutionStatus.FAILED,
                    count = 43
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval.minus(timeFrame).minus(timeFrame),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 523
                ),
            )
            coEvery {
                campaignRepository.retrieveCampaignsStatusHistogram(any(), any(), any(), any())
            } returns campaignResultList

            // when
            val campaignSummaryCount = widgetServiceImpl.aggregateCampaignResult(
                tenant = "my-tenant",
                from = now,
                until = end,
                timeOffset = offset.toFloat(),
                aggregationTimeframe = timeFrame
            )

            // then
            assertThat(campaignSummaryCount).all {
                hasSize(4)
                containsExactlyInAnyOrder(
                    CampaignSummaryResult(now.minus(offset.toLong(), ChronoUnit.HOURS), 111, 101),
                    CampaignSummaryResult(seriesInterval, 411, 543),
                    CampaignSummaryResult(seriesInterval.minus(timeFrame), 26, 2),
                    CampaignSummaryResult(
                        seriesInterval.minus(timeFrame).minus(timeFrame), 523, 43
                    ),
                )
            }

            coVerifyOnce {
                campaignRepository.retrieveCampaignsStatusHistogram(
                    "my-tenant",
                    startIdentifier = now.minus(offset.toLong(), ChronoUnit.HOURS),
                    endIdentifier = end.minus(offset.toLong(), ChronoUnit.HOURS),
                    timeframe = timeFrame
                )
            }
        }

    @Test
    internal fun `should retrieve the campaign results and their states using default values`() =
        testDispatcherProvider.runTest {
            // given
            val midnight = Instant.now().truncatedTo(ChronoUnit.DAYS)
            val offset = 1.30F
            val timeFrame = Duration.ofHours(24)
            val calcOffset =
                Duration.of(offset.toLong(), ChronoUnit.HOURS).plusMinutes(((offset % 1) * 100).roundToLong())
            val startInterval = midnight.minus(calcOffset)
            val campaignResultList = listOf(
                CampaignRepository.CampaignResultCount(
                    seriesStart = startInterval,
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 111
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = startInterval,
                    status = ExecutionStatus.ABORTED,
                    count = 43
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = startInterval,
                    status = ExecutionStatus.WARNING,
                    count = 58
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 1, offset),
                    status = ExecutionStatus.WARNING,
                    count = 183
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 1, offset),
                    status = ExecutionStatus.ABORTED,
                    count = 128
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 1, offset),
                    status = ExecutionStatus.QUEUED,
                    count = 232
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 1, offset),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 411
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 2, offset),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 26
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 2, offset),
                    status = ExecutionStatus.FAILED,
                    count = 2
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 3, offset),
                    status = ExecutionStatus.FAILED,
                    count = 43
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 3, offset),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 523
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 4, offset),
                    status = ExecutionStatus.SCHEDULED,
                    count = 67
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 4, offset),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 89
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 4, offset),
                    status = ExecutionStatus.FAILED,
                    count = 34
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 5, offset),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 201
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 5, offset),
                    status = ExecutionStatus.ABORTED,
                    count = 89
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 6, offset),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 415
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 6, offset),
                    status = ExecutionStatus.WARNING,
                    count = 233
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 6, offset),
                    status = ExecutionStatus.FAILED,
                    count = 19
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = generateNextSeries('-', midnight, timeFrame, 6, offset),
                    status = ExecutionStatus.ABORTED,
                    count = 643
                ),
            )
            coEvery {
                campaignRepository.retrieveCampaignsStatusHistogram(any(), any(), any(), any())
            } returns campaignResultList

            // when
            val campaignSummaryCount = widgetServiceImpl.aggregateCampaignResult(
                tenant = "my-tenant",
                timeOffset = offset,
                from = midnight,
                until = null,
                aggregationTimeframe = Duration.ofHours(24)
            )

            // then
            assertThat(campaignSummaryCount).all {
                hasSize(7)
                containsExactlyInAnyOrder(
                    CampaignSummaryResult(startInterval, 111, 101),
                    CampaignSummaryResult(startInterval.minus(timeFrame), 411, 543),
                    CampaignSummaryResult(startInterval.minus(timeFrame).minus(timeFrame), 26, 2),
                    CampaignSummaryResult(
                        startInterval.minus(timeFrame).minus(timeFrame).minus(timeFrame), 523, 43
                    ),
                    CampaignSummaryResult(
                        startInterval.minus(timeFrame).minus(timeFrame).minus(timeFrame)
                            .minus(timeFrame), 89, 101
                    ),
                    CampaignSummaryResult(
                        startInterval.minus(timeFrame).minus(timeFrame).minus(timeFrame)
                            .minus(timeFrame).minus(timeFrame), 201, 89
                    ),
                    CampaignSummaryResult(
                        startInterval.minus(timeFrame).minus(timeFrame).minus(timeFrame)
                            .minus(timeFrame).minus(timeFrame).minus(timeFrame), 415, 895
                    )
                )
            }
        }

    @Test
    internal fun `should retrieve the campaign results and their states when end is not provided`() =
        testDispatcherProvider.runTest {
            // given
            val now = Instant.now().minus(26, ChronoUnit.HOURS)
            val offset = 2
            val timeFrame = Duration.ofDays(2)
            val seriesInterval = now.minus(offset.toLong(), ChronoUnit.HOURS).minus(timeFrame)
            val campaignResultList = listOf(
                CampaignRepository.CampaignResultCount(
                    seriesStart = now.minus(offset.toLong(), ChronoUnit.HOURS),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 111
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = now.minus(offset.toLong(), ChronoUnit.HOURS),
                    status = ExecutionStatus.ABORTED,
                    count = 43
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = now.minus(offset.toLong(), ChronoUnit.HOURS),
                    status = ExecutionStatus.WARNING,
                    count = 58
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval,
                    status = ExecutionStatus.WARNING,
                    count = 183
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval,
                    status = ExecutionStatus.ABORTED,
                    count = 128
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval,
                    status = ExecutionStatus.QUEUED,
                    count = 232
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval,
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 411
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval.minus(timeFrame),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 26
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval.minus(timeFrame),
                    status = ExecutionStatus.FAILED,
                    count = 2
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval.minus(timeFrame).minus(timeFrame),
                    status = ExecutionStatus.FAILED,
                    count = 43
                ),
                CampaignRepository.CampaignResultCount(
                    seriesStart = seriesInterval.minus(timeFrame).minus(timeFrame),
                    status = ExecutionStatus.SUCCESSFUL,
                    count = 523
                ),
            )
            coEvery {
                campaignRepository.retrieveCampaignsStatusHistogram(any(), any(), any(), any())
            } returns campaignResultList

            // when
            val campaignSummaryCount = widgetServiceImpl.aggregateCampaignResult(
                tenant = "my-tenant",
                from = now,
                until = null,
                timeOffset = offset.toFloat(),
                aggregationTimeframe = timeFrame
            )

            // then
            assertThat(campaignSummaryCount).all {
                hasSize(4)
                containsExactlyInAnyOrder(
                    CampaignSummaryResult(now.minus(offset.toLong(), ChronoUnit.HOURS), 111, 101),
                    CampaignSummaryResult(seriesInterval, 411, 543),
                    CampaignSummaryResult(seriesInterval.minus(timeFrame), 26, 2),
                    CampaignSummaryResult(
                        seriesInterval.minus(timeFrame).minus(timeFrame), 523, 43
                    ),
                )
            }
        }

    private fun generateNextSeries(
        sign: Char,
        start: Instant,
        interval: Duration,
        count: Long,
        offset: Float
    ): Instant {
        val newInterval = interval.multipliedBy(count)
        val calcOffset = Duration.of(offset.toLong(), ChronoUnit.HOURS).plusMinutes(((offset % 1) * 100).roundToLong())
        return if (sign == '+') start.minus(calcOffset).plus(newInterval) else start.minus(
            calcOffset
        ).minus(newInterval)
    }
}