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

import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.time.QalipsisTimeAssertions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

/**
 * @author Eric Jessé
 */
internal class LatchTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    @Timeout(3)
    internal fun `should block calls at start until release`() = testDispatcherProvider.run {
        // given
        val latch = Latch(true)
        Assertions.assertTrue(latch.isLocked)
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        delay(50)
        val now = Instant.now()
        latch.release()
        suspendedCountLatch.await()

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }

    @Test
    @Timeout(3)
    internal fun `should not block calls at start until release`() = testDispatcherProvider.run {
        // given
        val latch = Latch(false)
        Assertions.assertFalse(latch.isLocked)
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        delay(50)
        val now = Instant.now()
        latch.release()
        suspendedCountLatch.await()

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertBefore(now, releaseTime) }
    }

    @Test
    @Timeout(3)
    internal fun `should not block calls at start until cancel`() = testDispatcherProvider.run {
        // given
        val latch = Latch(false)
        Assertions.assertFalse(latch.isLocked)
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        delay(50)
        val now = Instant.now()
        latch.cancel()
        suspendedCountLatch.await()

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertBefore(now, releaseTime) }
    }

    @Test
    @Timeout(3)
    internal fun `should lock and release and lock and release`() = testDispatcherProvider.run {
        // given
        val latch = Latch(false)
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        // when
        latch.lock()

        // then
        Assertions.assertTrue(latch.isLocked)

        // when
        repeat(3) {
            launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }
        delay(50)
        var now = Instant.now()
        latch.release()
        suspendedCountLatch.await()

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
        releaseTimes.clear()

        // when
        suspendedCountLatch.reset()
        latch.lock()

        // then
        Assertions.assertTrue(latch.isLocked)

        // when
        repeat(3) {
            launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }
        delay(50)
        now = Instant.now()
        latch.release()
        suspendedCountLatch.await()

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }
}
