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

package io.qalipsis.core.head.campaign.scheduler

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.mockk.every
import io.mockk.mockkStatic
import io.qalipsis.core.head.campaign.scheduler.catadioptre.campaignScheduleKeyStore
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Francisca Eze
 */
internal class ScheduledCampaignsRegistryTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should update the scheduled campaign keystore`() =
        testDispatcherProvider.runTest {
            //given
            val scheduledCampaignsRegistry = ScheduledCampaignsRegistry()
            val job = Job()

            //when
            scheduledCampaignsRegistry.updateSchedule("campaign-key", job)

            //then
            assertThat(scheduledCampaignsRegistry.campaignScheduleKeyStore()["campaign-key"])
                .isNotNull()
                .isEqualTo(job)
        }

    @Test
    internal fun `should cancel a pre-existing job`() =
        testDispatcherProvider.runTest {
            //given
            val scheduledCampaignsRegistry = ScheduledCampaignsRegistry()
            val job1 = Job()
            val job2 = Job()
            val campaignScheduleKeyStore = scheduledCampaignsRegistry.campaignScheduleKeyStore()
            campaignScheduleKeyStore["campaign-key-1"] = job1
            campaignScheduleKeyStore["campaign-key-2"] = job2
            assertThat(campaignScheduleKeyStore.size).isEqualTo(2)

            //when
            scheduledCampaignsRegistry.cancelSchedule("campaign-key-2")

            //then
            assertThat(campaignScheduleKeyStore.size).isEqualTo(1)
            assertNull(campaignScheduleKeyStore["campaign-key-2"])
            assertThat(campaignScheduleKeyStore["campaign-key-1"])
                .isNotNull()
                .isEqualTo(job1)
        }

    @Test
    internal fun `should do nothing when we try to cancel a job with unknown key`() =
        testDispatcherProvider.runTest {
            //given
            val scheduledCampaignsRegistry = ScheduledCampaignsRegistry()
            val job1 = Job()
            val job2 = Job()
            val campaignScheduleKeyStore = scheduledCampaignsRegistry.campaignScheduleKeyStore()
            campaignScheduleKeyStore["campaign-key-1"] = job1
            campaignScheduleKeyStore["campaign-key-2"] = job2
            assertThat(campaignScheduleKeyStore.size).isEqualTo(2)

            //when
            scheduledCampaignsRegistry.cancelSchedule("campaign-key-3")

            //then
            assertThat(campaignScheduleKeyStore.size).isEqualTo(2)
        }

    @Test
    internal fun `should still remove the mapping for the job even when cancellation fails`() =
        testDispatcherProvider.runTest {
            //given
            val scheduledCampaignsRegistry = ScheduledCampaignsRegistry()
            val job1 = Job()
            val job2 = relaxedMockk<Job>()
            val campaignScheduleKeyStore = scheduledCampaignsRegistry.campaignScheduleKeyStore()
            campaignScheduleKeyStore["campaign-key-1"] = job1
            campaignScheduleKeyStore["campaign-key-2"] = job2
            mockkStatic(Job::class)
            every { job2.cancel() } throws CancellationException("")
            assertThat(campaignScheduleKeyStore.size).isEqualTo(2)

            //when
            scheduledCampaignsRegistry.cancelSchedule("campaign-key-2")

            //then
            assertThat(campaignScheduleKeyStore.size).isEqualTo(1)
            assertNull(campaignScheduleKeyStore["campaign-key-2"])
            assertThat(campaignScheduleKeyStore["campaign-key-1"])
                .isNotNull()
                .isEqualTo(job1)
        }

}