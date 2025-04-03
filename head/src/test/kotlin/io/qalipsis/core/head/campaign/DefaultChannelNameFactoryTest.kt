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
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.campaigns.RunningCampaign
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.handshake.HandshakeRequest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD], startApplication = false)
internal class DefaultChannelNameFactoryTest {

    @MockK
    private lateinit var idGenerator: IdGenerator

    private lateinit var defaultChannelNameFactory: DefaultChannelNameFactory

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @BeforeAll
    internal fun setUp() {
        defaultChannelNameFactory = spyk(
            DefaultChannelNameFactory(idGenerator = idGenerator),
            recordPrivateCalls = true
        )
    }

    @AfterEach
    internal fun confirmVerify() {
        confirmVerified(idGenerator)
    }

    @Test
    internal fun `should get broadcast channel name`() = testDispatcherProvider.run {
        // given
        val runningCampaign = mockk<RunningCampaign>()

        // when
        val result = defaultChannelNameFactory.getBroadcastChannelName(campaign = runningCampaign)

        // then
        assertThat(result).isEqualTo("directives-broadcast")
    }

    @Test
    internal fun `should get feedback channel name`() = testDispatcherProvider.run {
        // given
        val runningCampaign = mockk<RunningCampaign>()

        // when
        val result = defaultChannelNameFactory.getFeedbackChannelName(campaign = runningCampaign)

        // then
        assertThat(result).isEqualTo("feedbacks")
    }

    @Test
    internal fun `should get unicast channel name when node id is blank`() = testDispatcherProvider.run {
        // given
        every { idGenerator.short() } returns "channel-name"

        // when
        val result = defaultChannelNameFactory.getUnicastChannelName(
            HandshakeRequest(
                nodeId = " ",
                tenant = "my-tenant",
                tags = mockk(),
                zone = RandomStringUtils.randomAlphabetic(5),
                scenarios = mockk(),
                replyTo = RandomStringUtils.randomAlphabetic(5)
            )
        )

        // then
        assertThat(result).isEqualTo("channel-name")
        coVerifyOrder {
            idGenerator.short()
        }
    }

    @Test
    internal fun `should get unicast channel name when node id start with underscore`() = testDispatcherProvider.run {
        // given
        every { idGenerator.short() } returns "channel-name"

        // when
        val result = defaultChannelNameFactory.getUnicastChannelName(
            HandshakeRequest(
                nodeId = "_node-id",
                tenant = "my-tenant",
                tags = mockk(),
                zone = RandomStringUtils.randomAlphabetic(5),
                scenarios = mockk(),
                replyTo = RandomStringUtils.randomAlphabetic(5)
            )
        )

        // then
        assertThat(result).isEqualTo("channel-name")
        coVerifyOrder {
            idGenerator.short()
        }
    }

    @Test
    internal fun `should get unicast channel name when a node id is not generated`() = testDispatcherProvider.run {
        // when
        val result = defaultChannelNameFactory.getUnicastChannelName(
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
        assertThat(result).isEqualTo("node-id")
    }
}