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

package io.qalipsis.core.factory.init

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.FactoryShutdownDirective
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.time.coMeasureTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@WithMockk
internal class HandshakeBlockerTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var handshakeConfiguration: FactoryConfiguration.HandshakeConfiguration

    @Test
    @Timeout(5)
    internal fun `should return 101 when await exit and no handshake response occurred`() = testDispatcherProvider.run {
        // given
        every { handshakeConfiguration.timeout } returns Duration.ZERO
        val blocker = HandshakeBlocker(handshakeConfiguration, this)

        // when
        blocker.init()
        blocker.join()
        val exitCode = blocker.await()

        // then
        assertThat(exitCode.get()).isEqualTo(101)
    }

    @Test
    @Timeout(5)
    internal fun `should return no code when await exit and a handshake response occurred`() =
        testDispatcherProvider.run {
            // given
            every { handshakeConfiguration.timeout } returns Duration.ofMillis(600)
            val blocker = HandshakeBlocker(handshakeConfiguration, this)

            // when
            blocker.init()
            blocker.notifySuccessfulRegistration()
            val exitCode = blocker.await()

            // then
            assertThat(exitCode.isEmpty).isTrue()
        }

    @Test
    @Timeout(5)
    internal fun `should block until timeout when no handshake response occurs`() = testDispatcherProvider.run {
        // given
        every { handshakeConfiguration.timeout } returns Duration.ofMillis(400)
        val blocker = HandshakeBlocker(handshakeConfiguration, this)

        // when
        blocker.init()
        val duration = coMeasureTime { blocker.join() }

        // then
        assertThat(duration).isBetween(Duration.ofMillis(350), Duration.ofMillis(500))
    }

    @Test
    @Timeout(5)
    internal fun `should block forever when a handshake response occurs`() = testDispatcherProvider.run {
        // given
        every { handshakeConfiguration.timeout } returns Duration.ofMillis(100)
        val blocker = HandshakeBlocker(handshakeConfiguration, this)

        // when
        blocker.init()
        blocker.notifySuccessfulRegistration()
        val duration = coMeasureTime { withTimeout(400) { blocker.join() } }

        // then
        assertThat(duration).isBetween(Duration.ofMillis(350), Duration.ofMillis(450))
    }

    @Test
    internal fun `should accept FactoryShutdownDirective`() = testDispatcherProvider.run {
        assertThat(
            HandshakeBlocker(
                handshakeConfiguration,
                this
            ).accept(relaxedMockk<FactoryShutdownDirective>())
        ).isTrue()
    }

    @Test
    internal fun `should deny not FactoryShutdownDirective`() = testDispatcherProvider.run {
        assertThat(HandshakeBlocker(handshakeConfiguration, this).accept(relaxedMockk<Directive>())).isFalse()
    }

    @Test
    @Timeout(5)
    internal fun `release when a FactoryShutdownDirective occurs`() = testDispatcherProvider.run {
        // given
        every { handshakeConfiguration.timeout } returns Duration.ofMillis(100)
        val blocker = HandshakeBlocker(handshakeConfiguration, this)
        blocker.init()
        blocker.notifySuccessfulRegistration()

        // when
        launch {
            delay(400)
            blocker.notify(FactoryShutdownDirective(""))
        }
        val duration = coMeasureTime { blocker.join() }

        // then
        assertThat(duration).isBetween(Duration.ofMillis(350), Duration.ofMillis(450))
    }
}