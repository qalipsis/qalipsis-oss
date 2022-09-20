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

package io.qalipsis.runtime.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameAs
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.prop
import assertk.assertions.startsWith
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.qalipsis.core.directives.MinionsDeclarationDirective
import io.qalipsis.core.directives.SingleUseDirectiveReference
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Registry of the special purpose directive. This class is in the runtime module
 * because it requires dependencies from both the head and factory modules.
 *
 * @author Gabriel Moraes
 */
@WithMockk
@ExperimentalLettuceCoroutinesApi
internal class RedisDirectiveRegistryIntegrationTest : AbstractRedisIntegrationTest() {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var registry: RedisDirectiveRegistry

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        connection.sync().flushdb()
    }

    @Test
    @Timeout(10)
    internal fun saveAndReadSingleUseDirective() = testDispatcherProvider.run {
        // given
        val directive = MinionsDeclarationDirective("campaign", "scenario", 1, channel = "broadcast")

        // when
        val prepared = registry.prepareBeforeSend("the-channel", directive)

        // then
        assertThat(prepared).isInstanceOf(SingleUseDirectiveReference::class).all {
            prop(SingleUseDirectiveReference::key).startsWith("the-channel:")
            isNotSameAs(directive)
        }
        assertThat(registry.prepareAfterReceived(prepared)).isEqualTo(directive)
        // The directive can be read only once.
        assertThat(registry.prepareAfterReceived(prepared)).isNull()
    }

    @Test
    @Timeout(10)
    internal fun saveThenGetAndDeleteStandardDirective() = testDispatcherProvider.run {
        // given
        val directive = TestDescriptiveDirective(100)

        // when
        val prepared = registry.prepareBeforeSend("the-channel", directive)

        // then
        assertThat(prepared).isSameAs(directive)
        assertThat(registry.prepareAfterReceived(prepared)).isSameAs(prepared)
        // The directive can be read several times.
        assertThat(registry.prepareAfterReceived(prepared)).isSameAs(prepared)
    }
}