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

import io.qalipsis.test.time.QalipsisTimeAssertions
import io.qalipsis.test.time.coMeasureTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
internal class SuspendedCountLatchTest {

    @Test
    @Timeout(1)
    internal fun `should await until zero and execute onDone action`() = runBlocking {
        // given
        val doneFlag = AtomicBoolean(false)
        val rendezVousFlag = Channel<Unit>(Channel.RENDEZVOUS)
        val countLatch = SuspendedCountLatch(5) { doneFlag.set(true) }
        val delayMs = 100L

        launch {
            rendezVousFlag.send(Unit)
            delay(delayMs)
            countLatch.decrement(3)
            countLatch.decrement()
            // Count is now 1.
            countLatch.increment(4)
            countLatch.increment()
            // Count is now 6.
            countLatch.decrement(6)
        }

        // when
        Assertions.assertTrue(countLatch.isSuspended())
        val executionDuration = coMeasureTime {
            rendezVousFlag.receive()
            countLatch.await()
        }

        // then
        Assertions.assertFalse(countLatch.isSuspended())
        QalipsisTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(delayMs - TOLERANCE), executionDuration)
        Assertions.assertTrue(doneFlag.get())
    }

    @Test
    @Timeout(1)
    internal fun `should reset then await until zero and execute onDone action`() = runBlocking {
        // given
        val doneFlag = AtomicInteger()
        val rendezVousFlag = Channel<Unit>(Channel.RENDEZVOUS)
        val countLatch = SuspendedCountLatch(1) { doneFlag.incrementAndGet() }

        launch {
            rendezVousFlag.send(Unit)
            delay(50)
            countLatch.decrement()
            delay(50)
            countLatch.reset()
            delay(50)
            countLatch.decrement()
        }

        // when
        rendezVousFlag.receive()
        Assertions.assertTrue(countLatch.isSuspended())
        countLatch.await()
        Assertions.assertFalse(countLatch.isSuspended())
        // Wait until reset.
        delay(60)
        Assertions.assertTrue(countLatch.isSuspended())
        countLatch.await()
        Assertions.assertFalse(countLatch.isSuspended())

        // then
        Assertions.assertEquals(2, doneFlag.get())
    }

    @Test
    @Timeout(1)
    internal fun `should not suspend when initial value is zero and not execute onDone Action`() = runBlocking {
        // given
        val doneFlag = AtomicBoolean(false)
        val countLatch = SuspendedCountLatch(0) { doneFlag.set(true) }

        // when
        Assertions.assertFalse(countLatch.isSuspended())
        countLatch.await()

        // then
        Assertions.assertFalse(doneFlag.get())
    }

    @Test
    @Timeout(1)
    internal fun `should await until activity`() = runBlocking {
        // given
        val countLatch = SuspendedCountLatch()
        val delayMs = 200L

        // when
        Assertions.assertFalse(countLatch.isSuspended())
        launch {
            delay(delayMs)
            countLatch.increment()
        }
        val executionDuration = coMeasureTime {
            countLatch.awaitActivity()
        }

        // then
        Assertions.assertTrue(countLatch.isSuspended())
        QalipsisTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(delayMs - TOLERANCE), executionDuration)
    }

    @Test
    @Timeout(1)
    internal fun `should release and execute onDone action`() = runBlocking {
        // given
        val doneFlag = AtomicBoolean(false)
        val countLatch = SuspendedCountLatch(20) { doneFlag.set(true) }

        launch {
            delay(50)
            countLatch.release()
        }

        // when
        Assertions.assertTrue(countLatch.isSuspended())
        countLatch.await()


        // then
        Assertions.assertTrue(doneFlag.get())
    }

    @Test
    @Timeout(1)
    internal fun `should cancel and not execute onDone action`() = runBlocking {
        // given
        val doneFlag = AtomicBoolean(false)
        val countLatch = SuspendedCountLatch(20) { doneFlag.set(true) }

        launch {
            delay(50)
            countLatch.cancel()
        }

        // when
        Assertions.assertTrue(countLatch.isSuspended())
        countLatch.await()


        // then
        Assertions.assertFalse(doneFlag.get())
    }

    @Test
    @Timeout(1)
    internal fun `should throw exception when decrementing to negative`() = runBlockingTest {
        // given
        val countLatch = SuspendedCountLatch(0)

        // when
        assertThrows<IllegalArgumentException> {
            countLatch.decrement()
        }
    }

    companion object {

        const val TOLERANCE = 50L

    }
}
