package io.qalipsis.core.cross.directives.inmemory

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.orchestration.feedbacks.DirectiveFeedback
import io.qalipsis.api.orchestration.feedbacks.FeedbackProducer
import io.qalipsis.api.orchestration.feedbacks.FeedbackStatus
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.cross.directives.TestListDirective
import io.qalipsis.core.cross.directives.TestListDirectiveReference
import io.qalipsis.core.cross.directives.TestQueueDirective
import io.qalipsis.core.cross.directives.TestQueueDirectiveReference
import io.qalipsis.core.cross.directives.TestSingleUseDirective
import io.qalipsis.core.cross.directives.TestSingleUseDirectiveReference
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.Collections

/**
 * @author Eric Jessé
 */
@ExperimentalCoroutinesApi
@WithMockk
internal class InMemoryDirectiveRegistryTest {

    @RelaxedMockK
    lateinit var feedbackProducer: FeedbackProducer

    @Test
    @Timeout(1)
    internal fun saveAndPopQueueDirective() = runBlockingTest {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackProducer)
        val directive = TestQueueDirective((0 until 20).toList())
        val capturedDirectiveFeedBacks = mutableListOf<DirectiveFeedback>()
        coEvery { feedbackProducer.publish(capture(capturedDirectiveFeedBacks)) } returns Unit

        // when
        registry.save(directive.toReference().key, directive)

        // then
        val notExistingDirective = registry.pop(TestQueueDirectiveReference("other-key"))
        assertNull(notExistingDirective)

        // then
        repeat(20) { index ->
            assertEquals(index, registry.pop(directive.toReference()))
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
        val emptyQueue = registry.pop(directive.toReference())
        assertNull(emptyQueue)

    }

    @Test
    @Timeout(5)
    internal fun massiveConcurrentPopQueueDirective() = runBlockingTest {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackProducer)
        val directive = TestQueueDirective((0 until 20000).toList())
        val startLatch = SuspendedCountLatch(1)
        val retrievedValues = Collections.synchronizedSet(mutableSetOf<Int>())
        val countDownLatch = SuspendedCountLatch(200)

        registry.save(directive.toReference().key, directive)

        for (i in 0 until 200) {
            launch {
                // Block the coroutines until the flag is closed.
                startLatch.await()
                repeat(100) {
                    val value = registry.pop(directive.toReference())
                    retrievedValues.add(value)
                }
                countDownLatch.decrement()
            }
        }

        // when
        startLatch.release()
        countDownLatch.await()

        // then all the unique values are listed.
        assertEquals(20000, retrievedValues.size)

        // then
        val emptyQueue = registry.pop(directive.toReference())
        assertNull(emptyQueue)
    }

    @Test
    @Timeout(1)
    internal fun saveAndReadListDirective() = runBlockingTest {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackProducer)
        val directive = TestListDirective((0 until 20).toList())

        // when
        registry.save(directive.toReference().key, directive)

        // then
        val notExistingDirective = registry.list(TestListDirectiveReference("other-key"))
        assertEquals(emptyList<Int>(), notExistingDirective)

        // then all the calls return the same set.
        repeat(20) {
            assertEquals((0 until 20).toList(), registry.list(directive.toReference()))
        }
    }

    @Test
    @Timeout(1)
    internal fun saveAndReadSingleUseDirective() = runBlockingTest {
        // given
        val registry = InMemoryDirectiveRegistry(feedbackProducer)
        val directive = TestSingleUseDirective(100)

        // when
        registry.save(directive.toReference().key, directive)

        // then
        val notExistingDirective = registry.read(TestSingleUseDirectiveReference("other-key"))
        assertNull(notExistingDirective)

        // then
        val existingValue = registry.read(directive.toReference())
        assertEquals(100, existingValue)

        // then
        val emptyValue = registry.read(directive.toReference())
        assertNull(emptyValue)
    }
}
