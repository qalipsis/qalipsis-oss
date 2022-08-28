/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.sync

import assertk.assertThat
import assertk.assertions.isGreaterThan
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.lang.millis
import io.qalipsis.api.lang.seconds
import io.qalipsis.test.time.QalipsisTimeAssertions
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * @author Eric Jessé
 */
internal class ImmutableSlotTest {

    @Test
    @Timeout(3)
    internal fun `should block calls at start until a value is set`() = runBlockingTest {
        // given
        val slot = ImmutableSlot<Unit>()
        assertTrue(slot.isEmpty())
        assertFalse(slot.isPresent())

        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            launch {
                Assertions.assertEquals(Unit, slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        Thread.sleep(50)
        val now = Instant.now()
        slot.set(Unit)
        suspendedCountLatch.await()

        // then
        assertFalse(slot.isEmpty())
        assertTrue(slot.isPresent())
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }

    @Test
    @Timeout(3)
    internal fun `should not block calls at start until a value is set`() = runBlockingTest {
        // given
        val slot = ImmutableSlot<Unit>(Unit)
        assertFalse(slot.isEmpty())
        assertTrue(slot.isPresent())

        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            launch {
                Assertions.assertEquals(Unit, slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        Thread.sleep(50)
        val now = Instant.now()
        suspendedCountLatch.await()

        // then
        assertFalse(slot.isEmpty())
        assertTrue(slot.isPresent())
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertBefore(now, releaseTime) }
    }

    @Test
    @Timeout(3)
    internal fun `should throw an error when setting a value and one is already present`() = runBlockingTest {
        // given
        val slot = ImmutableSlot(Unit)

        // when + then
        assertThrows<IllegalStateException> {
            slot.set(Unit)
        }

    }

    @Test
    @Timeout(3)
    internal fun `should throw an error when setting the value twice`() = runBlockingTest {
        // given
        val slot = ImmutableSlot<Unit>()

        // when
        slot.set(Unit)

        // then
        assertThrows<IllegalStateException> {
            slot.set(Unit)
        }
    }

    @Test
    @Timeout(3)
    internal fun `should throw an error when offering the value twice`() {
        // given
        val slot = ImmutableSlot<Unit>()

        // when
        slot.offer(Unit)

        // then
        assertThrows<IllegalStateException> {
            slot.offer(Unit)
        }
    }

    @Test
    @Timeout(3)
    internal fun `should throw a timeout exception`() = runBlockingTest {
        // given
        val slot = ImmutableSlot<Unit>()

        // when + then
        assertThrows<TimeoutCancellationException> {
            slot.get(100.millis())
        }
    }

    @Test
    @Timeout(3)
    internal fun `should return a result`() = runBlocking {
        // given
        val slot = ImmutableSlot<Unit>()
        val start = System.currentTimeMillis()
        launch {
            delay(200)
            slot.set(Unit)
        }

        // when
        val result = slot.get(50.seconds())

        // then
        assertSame(Unit, result)
        assertThat(System.currentTimeMillis() - start).isGreaterThan(150)
    }

}
