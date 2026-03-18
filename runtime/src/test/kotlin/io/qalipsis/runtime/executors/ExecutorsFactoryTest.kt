/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.runtime.executors

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import io.qalipsis.api.coroutines.CoroutineScopeProvider
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows

@OptIn(DelicateCoroutinesApi::class)
@Timeout(10)
internal class ExecutorsFactoryTest {

    private var scopeProvider: CoroutineScopeProvider? = null

    @AfterEach
    fun tearDown() {
        scopeProvider?.close()
    }

    @Test
    fun `should create scopes with fixed thread counts`() {
        val factory = ExecutorsFactory()
        val config = executorsConfiguration(
            global = "4",
            campaign = "2",
            io = "3",
            background = "2",
            orchestration = "2"
        )

        scopeProvider = factory.coroutineScopeProvider(config)

        assertThat(scopeProvider).isNotNull()
        assertThat(factory.globalDispatcher(scopeProvider!!)).all {
            isNotNull()
            isInstanceOf<ExecutorCoroutineDispatcher>()
        }
        assertThat(factory.campaignDispatcher(scopeProvider!!)).all {
            isNotNull()
            isInstanceOf<ExecutorCoroutineDispatcher>()
        }
        assertThat(factory.ioDispatcher(scopeProvider!!)).all {
            isNotNull()
            isInstanceOf<ExecutorCoroutineDispatcher>()
        }
        assertThat(factory.backgroundDispatcher(scopeProvider!!)).all {
            isNotNull()
            isInstanceOf<ExecutorCoroutineDispatcher>()
        }
        assertThat(factory.orchestrationDispatcher(scopeProvider!!)).all {
            isNotNull()
            isInstanceOf<ExecutorCoroutineDispatcher>()
        }
    }

    @Test
    fun `should create scopes with processor factor`() {
        val factory = ExecutorsFactory()
        val config = executorsConfiguration(
            global = "2x",
            campaign = "1x",
            io = "0.5x",
            background = "2",
            orchestration = "2"
        )

        scopeProvider = factory.coroutineScopeProvider(config)

        assertThat(scopeProvider).isNotNull()
        assertThat(factory.globalDispatcher(scopeProvider!!)).isNotNull()
        assertThat(factory.campaignDispatcher(scopeProvider!!)).isNotNull()
        assertThat(factory.ioDispatcher(scopeProvider!!)).isNotNull()
    }

    @Test
    fun `should use default dispatcher when thread count is zero or negative`() {
        val factory = ExecutorsFactory()
        val config = executorsConfiguration(
            global = "-1",
            campaign = "0",
            io = "2",
            background = "2",
            orchestration = "2"
        )

        scopeProvider = factory.coroutineScopeProvider(config)

        assertThat(factory.globalDispatcher(scopeProvider!!)).isSameInstanceAs(Dispatchers.Default)
        assertThat(factory.campaignDispatcher(scopeProvider!!)).isSameInstanceAs(Dispatchers.Default)
        assertThat(factory.ioDispatcher(scopeProvider!!)).isInstanceOf<ExecutorCoroutineDispatcher>()
    }

    @Test
    fun `should resolve references to other executors`() {
        val factory = ExecutorsFactory()
        val config = executorsConfiguration(
            global = "4",
            campaign = "2",
            io = "3",
            background = "global",
            orchestration = "global"
        )

        scopeProvider = factory.coroutineScopeProvider(config)

        assertThat(factory.backgroundDispatcher(scopeProvider!!))
            .isSameInstanceAs(factory.globalDispatcher(scopeProvider!!))
        assertThat(factory.orchestrationDispatcher(scopeProvider!!))
            .isSameInstanceAs(factory.globalDispatcher(scopeProvider!!))
        assertThat(factory.campaignDispatcher(scopeProvider!!))
            .isNotSameInstanceAs(factory.globalDispatcher(scopeProvider!!))
    }

    @Test
    fun `should throw when referencing non-existent executor`() {
        val factory = ExecutorsFactory()
        val config = executorsConfiguration(
            global = "4",
            campaign = "2",
            io = "3",
            background = "nonexistent",
            orchestration = "2"
        )

        assertThrows<IllegalArgumentException> {
            factory.coroutineScopeProvider(config)
        }
    }

    @Test
    fun `should default non-global executors to global when blank`() {
        val factory = ExecutorsFactory()
        val config = executorsConfiguration(
            global = "4",
            campaign = "",
            io = "",
            background = "",
            orchestration = ""
        )

        scopeProvider = factory.coroutineScopeProvider(config)

        assertThat(factory.campaignDispatcher(scopeProvider!!))
            .isSameInstanceAs(factory.globalDispatcher(scopeProvider!!))
        assertThat(factory.ioDispatcher(scopeProvider!!))
            .isSameInstanceAs(factory.globalDispatcher(scopeProvider!!))
        assertThat(factory.backgroundDispatcher(scopeProvider!!))
            .isSameInstanceAs(factory.globalDispatcher(scopeProvider!!))
        assertThat(factory.orchestrationDispatcher(scopeProvider!!))
            .isSameInstanceAs(factory.globalDispatcher(scopeProvider!!))
    }

    @Test
    fun `should expose all contexts after initialization`() {
        val factory = ExecutorsFactory()
        val config = executorsConfiguration(
            global = "2",
            campaign = "2",
            io = "2",
            background = "2",
            orchestration = "2"
        )

        scopeProvider = factory.coroutineScopeProvider(config)

        assertThat(factory.globalContext(scopeProvider!!)).isNotNull()
        assertThat(factory.campaignContext(scopeProvider!!)).isNotNull()
        assertThat(factory.ioContext(scopeProvider!!)).isNotNull()
        assertThat(factory.backgroundContext(scopeProvider!!)).isNotNull()
        assertThat(factory.orchestrationContext(scopeProvider!!)).isNotNull()
    }

    @Test
    fun `should expose all scopes after initialization`() {
        val factory = ExecutorsFactory()
        val config = executorsConfiguration(
            global = "2",
            campaign = "2",
            io = "2",
            background = "2",
            orchestration = "2"
        )

        scopeProvider = factory.coroutineScopeProvider(config)

        assertThat(factory.globalScope(scopeProvider!!)).isNotNull()
        assertThat(factory.campaignScope(scopeProvider!!)).isNotNull()
        assertThat(factory.ioScope(scopeProvider!!)).isNotNull()
        assertThat(factory.backgroundScope(scopeProvider!!)).isNotNull()
        assertThat(factory.orchestrationScope(scopeProvider!!)).isNotNull()
    }

    @Test
    fun `should provide consistent dispatcher and context for same executor`() {
        val factory = ExecutorsFactory()
        val config = executorsConfiguration(
            global = "2",
            campaign = "2",
            io = "2",
            background = "2",
            orchestration = "2"
        )

        scopeProvider = factory.coroutineScopeProvider(config)

        assertThat(factory.globalContext(scopeProvider!!))
            .isSameInstanceAs(factory.globalDispatcher(scopeProvider!!))
        assertThat(factory.campaignContext(scopeProvider!!))
            .isSameInstanceAs(factory.campaignDispatcher(scopeProvider!!))
        assertThat(factory.ioContext(scopeProvider!!))
            .isSameInstanceAs(factory.ioDispatcher(scopeProvider!!))
    }

    @Test
    fun `should apply minimum executor size for processor factor`() {
        val factory = ExecutorsFactory()
        // Use a very small factor that would result in less than MIN_EXECUTOR_SIZE (2)
        val config = executorsConfiguration(
            global = "0.001x",
            campaign = "2",
            io = "2",
            background = "2",
            orchestration = "2"
        )

        scopeProvider = factory.coroutineScopeProvider(config)

        assertThat(factory.globalDispatcher(scopeProvider!!)).all {
            isNotNull()
            isInstanceOf<ExecutorCoroutineDispatcher>()
        }
    }

    @Test
    fun `should populate CoroutineScopeProvider with correct scopes`() {
        val factory = ExecutorsFactory()
        val config = executorsConfiguration(
            global = "2",
            campaign = "2",
            io = "2",
            background = "global",
            orchestration = "global"
        )

        scopeProvider = factory.coroutineScopeProvider(config)

        assertThat(scopeProvider!!.global).isNotNull()
        assertThat(scopeProvider!!.campaign).isNotNull()
        assertThat(scopeProvider!!.io).isNotNull()
        assertThat(scopeProvider!!.background).isSameInstanceAs(scopeProvider!!.global)
        assertThat(scopeProvider!!.orchestration).isSameInstanceAs(scopeProvider!!.global)
    }

    @Test
    fun `should throw UninitializedPropertyAccessException when accessing beans before initialization`() {
        val factory = ExecutorsFactory()

        // Before coroutineScopeProvider() is called, accessing dependent beans should fail.
        // This verifies the bug exists without the fix (the parameter forces initialization order in Micronaut).
        assertThrows<UninitializedPropertyAccessException> {
            val dummyScopeProvider = SimpleCoroutineScopeProvider(
                global = kotlinx.coroutines.GlobalScope,
                campaign = kotlinx.coroutines.GlobalScope,
                io = kotlinx.coroutines.GlobalScope,
                background = kotlinx.coroutines.GlobalScope,
                orchestration = kotlinx.coroutines.GlobalScope
            )
            factory.campaignScope(dummyScopeProvider)
        }
    }

    private fun executorsConfiguration(
        global: String,
        campaign: String,
        io: String,
        background: String,
        orchestration: String
    ): ExecutorsConfiguration {
        return ExecutorsConfiguration().apply {
            this.global = global
            this.campaign = campaign
            this.io = io
            this.background = background
            this.orchestration = orchestration
        }
    }
}
