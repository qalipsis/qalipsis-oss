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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * @author Eric Jessé
 */
internal class SlotTest {

    @Test
    @Timeout(3)
    internal fun `should block calls at start until a value is set`() = runBlocking {
        // given
        val slot = Slot<String>()
        Assertions.assertTrue(slot.isEmpty())
        Assertions.assertFalse(slot.isPresent())

        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            launch {
                Assertions.assertEquals("Test value", slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        delay(50)
        val now = Instant.now()
        slot.set("Test value")
        suspendedCountLatch.await()

        // then
        Assertions.assertFalse(slot.isEmpty())
        Assertions.assertTrue(slot.isPresent())
        Assertions.assertEquals("Test value", slot.get())
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }

    @Test
    @Timeout(3)
    internal fun `should not block calls at start when a value is set`() = runBlocking {
        // given
        val slot = Slot("Test value")
        Assertions.assertFalse(slot.isEmpty())
        Assertions.assertTrue(slot.isPresent())

        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            launch {
                Assertions.assertEquals("Test value", slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        delay(50)
        val now = Instant.now()
        suspendedCountLatch.await()

        // then
        Assertions.assertFalse(slot.isEmpty())
        Assertions.assertTrue(slot.isPresent())
        Assertions.assertEquals("Test value", slot.get())
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertBefore(now, releaseTime) }
    }

    @Test
    @Timeout(3)
    internal fun `should block removal until value is set`() = runBlocking {
        // given
        val slot = Slot<String>()

        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = Latch(true)

        launch {
            Assertions.assertEquals("Test value", slot.remove())
            releaseTimes.add(Instant.now())
            suspendedCountLatch.release()
        }

        // when
        delay(50)
        val now = Instant.now()
        slot.set("Test value")
        suspendedCountLatch.await()

        // then
        Assertions.assertTrue(slot.isEmpty())
        Assertions.assertFalse(slot.isPresent())
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfter(now, releaseTime) }
    }

    @Test
    @Timeout(3)
    internal fun `should block removal until value is offered`() = runBlocking {
        // given
        val slot = Slot<String>()

        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = Latch(true)

        launch {
            Assertions.assertEquals("Test value", slot.remove())
            releaseTimes.add(Instant.now())
            suspendedCountLatch.release()
        }

        // when
        delay(50)
        val now = Instant.now()
        slot.offer("Test value")
        suspendedCountLatch.await()

        // then
        Assertions.assertTrue(slot.isEmpty())
        Assertions.assertFalse(slot.isPresent())
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfter(now, releaseTime) }
    }

    @Test
    @Timeout(3)
    internal fun `should remove and set and remove again`() = runBlocking {
        // given
        val slot = Slot("Test value")
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        // when
        Assertions.assertEquals("Test value", slot.remove())

        // then
        Assertions.assertTrue(slot.isEmpty())
        Assertions.assertFalse(slot.isPresent())

        // when
        repeat(3) {
            launch {
                Assertions.assertEquals("Test value", slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }
        Thread.sleep(50)
        var now = Instant.now()
        slot.set("Test value")
        suspendedCountLatch.await()

        // then
        Assertions.assertFalse(slot.isEmpty())
        Assertions.assertTrue(slot.isPresent())
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
        releaseTimes.clear()

        // when
        suspendedCountLatch.reset()
        Assertions.assertEquals("Test value", slot.remove())

        // then
        Assertions.assertTrue(slot.isEmpty())
        Assertions.assertFalse(slot.isPresent())

        // when
        repeat(3) {
            launch {
                Assertions.assertEquals("Other test value", slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }
        delay(50)
        now = Instant.now()
        slot.set("Other test value")
        suspendedCountLatch.await()

        // then
        Assertions.assertFalse(slot.isEmpty())
        Assertions.assertTrue(slot.isPresent())
        Assertions.assertEquals("Other test value", slot.get())
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }


    @Test
    @Timeout(3)
    internal fun `should throw a timeout exception`() = runBlockingTest {
        // given
        val slot = Slot<String>()

        // when + then
        assertThrows<TimeoutCancellationException> {
            slot.get(100.millis())
        }
    }

    @Test
    @Timeout(3)
    internal fun `should return a result`() = runBlocking {
        // given
        val slot = Slot<String>()
        val start = System.currentTimeMillis()
        launch {
            delay(200)
            slot.set("Test value")
        }

        // when
        val result = slot.get(50.seconds())

        // then
        Assertions.assertEquals("Test value", result)
        assertThat(System.currentTimeMillis() - start).isGreaterThan(150)
    }
}
