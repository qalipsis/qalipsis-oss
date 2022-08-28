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
