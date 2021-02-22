package io.qalipsis.api.sync

import assertk.assertThat
import assertk.assertions.isGreaterThan
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.lang.millis
import io.qalipsis.api.lang.seconds
import io.qalipsis.test.time.QalipsisTimeAssertions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    internal fun `should block calls at start until a value is set`() {
        // given
        val slot = ImmutableSlot<Unit>()
        assertTrue(slot.isEmpty())
        assertFalse(slot.isPresent())

        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            GlobalScope.launch {
                Assertions.assertEquals(Unit, slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        Thread.sleep(50)
        val now = Instant.now()
        runBlocking {
            slot.set(Unit)
            suspendedCountLatch.await()
        }

        // then
        assertFalse(slot.isEmpty())
        assertTrue(slot.isPresent())
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }

    @Test
    @Timeout(3)
    internal fun `should not block calls at start until a value is set`() {
        // given
        val slot = ImmutableSlot<Unit>(Unit)
        assertFalse(slot.isEmpty())
        assertTrue(slot.isPresent())

        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            GlobalScope.launch {
                Assertions.assertEquals(Unit, slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        Thread.sleep(50)
        val now = Instant.now()
        runBlocking {
            suspendedCountLatch.await()
        }

        // then
        assertFalse(slot.isEmpty())
        assertTrue(slot.isPresent())
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertBefore(now, releaseTime) }
    }

    @Test
    @Timeout(3)
    internal fun `should throw an error when setting a value and one is already present`() {
        // given
        val slot = ImmutableSlot(Unit)

        // when + then
        assertThrows<IllegalStateException> {
            runBlocking {
                slot.set(Unit)
            }
        }

    }

    @Test
    @Timeout(3)
    internal fun `should throw an error when setting the value twice`() {
        // given
        val slot = ImmutableSlot<Unit>()

        // when
        runBlocking {
            slot.set(Unit)
        }

        // then
        assertThrows<IllegalStateException> {
            runBlocking {
                slot.set(Unit)
            }
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
    internal fun `should throw a timeout exception`() {
        // given
        val slot = ImmutableSlot<Unit>()

        // when + then
        assertThrows<TimeoutCancellationException> {
            runBlocking {
                slot.get(100.millis())
            }
        }
    }

    @Test
    @Timeout(3)
    internal fun `should return a result`() {
        // given
        val slot = ImmutableSlot<Unit>()
        val start = System.currentTimeMillis()
        GlobalScope.launch {
            delay(200)
            slot.set(Unit)
        }

        // when
        val result = runBlocking {
            slot.get(50.seconds())
        }

        // then
        assertSame(Unit, result)
        assertThat(System.currentTimeMillis() - start).isGreaterThan(150)
    }

}
