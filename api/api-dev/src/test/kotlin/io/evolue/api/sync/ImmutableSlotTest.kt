package io.evolue.api.sync

import io.evolue.api.lang.concurrentSet
import io.evolue.test.time.EvolueTimeAssertions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

/**
 * @author Eric Jessé
 */
internal class ImmutableSlotTest {

    @Test
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
        releaseTimes.forEach { releaseTime -> EvolueTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }

    @Test
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
        releaseTimes.forEach { releaseTime -> EvolueTimeAssertions.assertBefore(now, releaseTime) }
    }

    @Test
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
}