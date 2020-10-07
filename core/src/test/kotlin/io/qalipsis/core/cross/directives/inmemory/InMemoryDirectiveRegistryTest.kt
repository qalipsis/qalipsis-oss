package io.qalipsis.core.cross.directives.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.cross.directives.TestListDirective
import io.qalipsis.core.cross.directives.TestListDirectiveReference
import io.qalipsis.core.cross.directives.TestQueueDirective
import io.qalipsis.core.cross.directives.TestQueueDirectiveReference
import io.qalipsis.core.cross.directives.TestSingleUseDirective
import io.qalipsis.core.cross.directives.TestSingleUseDirectiveReference
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.test.coroutines.CleanCoroutines
import io.qalipsis.test.mockk.WithMockk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.Collections
import java.util.concurrent.CountDownLatch

/**
 * @author Eric Jessé
 */
@WithMockk
@CleanCoroutines
internal class InMemoryDirectiveRegistryTest {

    @RelaxedMockK
    lateinit var feedbackProducer: FeedbackProducer

    @Test
    @Timeout(1)
    internal fun saveAndPopQueueDirective() {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackProducer)
        val directive = TestQueueDirective((0 until 20).toList())
        val capturedDirectiveFeedBacks = mutableListOf<DirectiveFeedback>()
        coEvery { feedbackProducer.publish(capture(capturedDirectiveFeedBacks)) } returns Unit

        // when
        runBlocking {
            registry.save(directive.toReference().key, directive)
        }

        // then
        val notExistingDirective = runBlocking {
            registry.pop(TestQueueDirectiveReference("other-key"))
        }
        assertNull(notExistingDirective)

        // then
        runBlocking {
            repeat(20) { index ->
                assertEquals(index, registry.pop(directive.toReference()))
            }
        }

        // then
        coVerify { feedbackProducer.publish(any()) }
        assertThat(capturedDirectiveFeedBacks[0]).all {
            prop(DirectiveFeedback::key).isNotNull()
            prop(DirectiveFeedback::directiveKey).isEqualTo(directive.toReference().key)
            prop(DirectiveFeedback::status).isEqualTo(FeedbackStatus.IN_PROGRESS)
            prop(DirectiveFeedback::error).isNull()
        }
        assertThat(capturedDirectiveFeedBacks[1]).all {
            prop(DirectiveFeedback::key).isNotNull()
            prop(DirectiveFeedback::directiveKey).isEqualTo(directive.toReference().key)
            prop(DirectiveFeedback::status).isEqualTo(FeedbackStatus.COMPLETED)
            prop(DirectiveFeedback::error).isNull()
        }
        val emptyQueue = runBlocking {
            registry.pop(directive.toReference())
        }
        assertNull(emptyQueue)

    }

    @Test
    @Timeout(5)
    internal fun massiveConcurrentPopQueueDirective() {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackProducer)
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
        assertEquals(20000, retrievedValues.size)

        // then
        val emptyQueue = runBlocking {
            registry.pop(directive.toReference())
        }
        assertNull(emptyQueue)
    }

    @Test
    @Timeout(1)
    internal fun saveAndReadListDirective() {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackProducer)
        val directive = TestListDirective((0 until 20).toList())

        // when
        runBlocking {
            registry.save(directive.toReference().key, directive)
        }

        // then
        val notExistingDirective = runBlocking {
            registry.list(TestListDirectiveReference("other-key"))
        }
        assertEquals(emptyList<Int>(), notExistingDirective)

        // then all the calls return the same set.
        runBlocking {
            repeat(20) {
                assertEquals((0 until 20).toList(), registry.list(directive.toReference()))
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun saveAndReadSingleUseDirective() {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackProducer)
        val directive = TestSingleUseDirective(100)

        // when
        runBlocking {
            registry.save(directive.toReference().key, directive)
        }

        // then
        val notExistingDirective = runBlocking {
            registry.read(TestSingleUseDirectiveReference("other-key"))
        }
        assertNull(notExistingDirective)

        // then
        val existingValue = runBlocking {
            registry.read(directive.toReference())
        }
        assertEquals(100, existingValue)

        // then
        val emptyValue = runBlocking {
            registry.read(directive.toReference())
        }
        assertNull(emptyValue)

    }
}
