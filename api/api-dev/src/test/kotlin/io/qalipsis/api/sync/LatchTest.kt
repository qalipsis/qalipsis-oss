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
