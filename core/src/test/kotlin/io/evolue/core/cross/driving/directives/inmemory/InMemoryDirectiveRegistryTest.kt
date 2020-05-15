package io.evolue.core.cross.driving.directives.inmemory

import io.evolue.api.sync.SuspendedCountLatch
import io.evolue.core.cross.driving.TestListDirective
import io.evolue.core.cross.driving.TestListDirectiveReference
import io.evolue.core.cross.driving.TestQueueDirective
import io.evolue.core.cross.driving.TestQueueDirectiveReference
import io.evolue.core.cross.driving.TestSingleUseDirective
import io.evolue.core.cross.driving.TestSingleUseDirectiveReference
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.Collections
import java.util.concurrent.CountDownLatch

/**
 * @author Eric Jessé
 */
internal class InMemoryDirectiveRegistryTest {

    @Test
    @Timeout(1)
    internal fun saveAndPopQueueDirective() {
        // given
        val registry = InMemoryDirectiveRegistry()
        val directive = TestQueueDirective((0 until 20).toList())

        // when
        runBlocking {
            registry.save(directive.toReference().key, directive)
        }

        // then
        val notExistingDirective = runBlocking {
            registry.pop(TestQueueDirectiveReference("other-key"))
        }
        Assertions.assertNull(notExistingDirective)

        // then
        runBlocking {
            repeat(20) { index ->
                Assertions.assertEquals(index, registry.pop(directive.toReference()))
            }
        }

        // then
        val emptyQueue = runBlocking {
            registry.pop(directive.toReference())
        }
        Assertions.assertNull(emptyQueue)

    }

    @Test
    @Timeout(1)
    internal fun massiveConcurrentPopQueueDirective() {
        // given
        val registry = InMemoryDirectiveRegistry()
        val directive = TestQueueDirective((0 until 20000).toList())
        val startLatch = SuspendedCountLatch(1)
        val retrievedValues = Collections.synchronizedSet(mutableSetOf<Int>())
        val countDownLatch = CountDownLatch(200)

        runBlocking {
            registry.save(directive.toReference().key, directive)
        }
        for (i in 0 until 200) {
            GlobalScope.launch {
                // Block the coroutines until the flag is closed.
                startLatch.await()
                repeat(100) {
                    val value = registry.pop(directive.toReference())
                    retrievedValues.add(value)
                }
                countDownLatch.countDown()
            }
        }

        // when
        runBlocking {
            startLatch.release()
        }
        countDownLatch.await()

        // then all the unique values are listed.
        Assertions.assertEquals(20000, retrievedValues.size)

        // then
        val emptyQueue = runBlocking {
            registry.pop(directive.toReference())
        }
        Assertions.assertNull(emptyQueue)
    }

    @Test
    @Timeout(1)
    internal fun saveAndReadListDirective() {
        // given
        val registry = InMemoryDirectiveRegistry()
        val directive = TestListDirective((0 until 20).toList())

        // when
        runBlocking {
            registry.save(directive.toReference().key, directive)
        }

        // then
        val notExistingDirective = runBlocking {
            registry.list(TestListDirectiveReference("other-key"))
        }
        Assertions.assertEquals(emptyList<Int>(), notExistingDirective)

        // then all the calls return the same set.
        runBlocking {
            repeat(20) {
                Assertions.assertEquals((0 until 20).toList(), registry.list(directive.toReference()))
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun saveAndReadSingleUseDirective() {
        // given
        val registry = InMemoryDirectiveRegistry()
        val directive = TestSingleUseDirective(100)

        // when
        runBlocking {
            registry.save(directive.toReference().key, directive)
        }

        // then
        val notExistingDirective = runBlocking {
            registry.read(TestSingleUseDirectiveReference("other-key"))
        }
        Assertions.assertNull(notExistingDirective)

        // then
        val existingValue = runBlocking {
            registry.read(directive.toReference())
        }
        Assertions.assertEquals(100, existingValue)

        // then
        val emptyValue = runBlocking {
            registry.read(directive.toReference())
        }
        Assertions.assertNull(emptyValue)

    }
}