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

package io.qalipsis.core.head.campaign

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.STANDALONE], startApplication = false)
internal class StandaloneChannelNameFactoryTest {

    private lateinit var standaloneChannelNameFactory: StandaloneChannelNameFactory

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @BeforeAll
    internal fun setUp() {
        standaloneChannelNameFactory = spyk(
            StandaloneChannelNameFactory(),
            recordPrivateCalls = true
        )
    }

    @Test
    internal fun `should get broadcast channel name`() = testDispatcherProvider.run {
        // given
        val runningCampaign = mockk<RunningCampaign>()

        // when
        val result = standaloneChannelNameFactory.getBroadcastChannelName(campaign = runningCampaign)

        // then
        assertThat(result).isEqualTo("directives-broadcast")
    }

    @Test
    internal fun `should get feedback channel name`() = testDispatcherProvider.run {
        // given
        val runningCampaign = mockk<RunningCampaign>()

        // when
        val result = standaloneChannelNameFactory.getFeedbackChannelName(campaign = runningCampaign)

        // then
        assertThat(result).isEqualTo("feedbacks")
    }

    @Test
    internal fun `should get unicast channel name`() = testDispatcherProvider.run {
        // when
        val result = standaloneChannelNameFactory.getUnicastChannelName(
            HandshakeRequest(
                tenant = "my-tenant",
                nodeId = "node-id",
                tags = mockk(),
                zone = RandomStringUtils.randomAlphabetic(5),
                scenarios = mockk(),
                replyTo = RandomStringUtils.randomAlphabetic(5)
            )
        )

        // then
        assertThat(result).isEqualTo("_embedded_")
    }
}