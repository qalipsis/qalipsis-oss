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
package io.qalipsis.core.head.lock

import assertk.assertThat
import assertk.assertions.isGreaterThan
import io.mockk.spyk
import io.qalipsis.core.head.lock.catadioptre.lockRegistry
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class InMemoryLockProviderImplTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @AfterEach
    fun cleanup() {
        lockProvider.lockRegistry().invalidateAll()
    }

    private val lockProvider: InMemoryLockProviderImpl =
        spyk(InMemoryLockProviderImpl(), recordPrivateCalls = true)

    @Test
    fun `should execute the function within the lock`() = testDispatcherProvider.run {
        //given
        lockProvider.lockRegistry().put("campaign-key", Mutex())
        val minExecutionTimeInMills = 600L
        val startTimestamp = System.currentTimeMillis()
        var result = 0

        //when
        List(3) {
            launch {
                lockProvider.withLock("campaign-key") {
                    delay(200L)
                }
                result++
            }
        }.joinAll()
        val endTimestamp = System.currentTimeMillis()

        //then
        assertThat(result == 3)
        assertThat(endTimestamp - startTimestamp).isGreaterThan(minExecutionTimeInMills)
    }
}