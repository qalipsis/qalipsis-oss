package io.qalipsis.core.cross.directives.inmemory

import io.mockk.confirmVerified
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.orchestration.directives.DirectiveRegistry
import io.qalipsis.core.cross.directives.TestDescriptiveDirective
import io.qalipsis.core.cross.directives.TestListDirective
import io.qalipsis.core.cross.directives.TestQueueDirective
import io.qalipsis.core.cross.directives.TestSingleUseDirective
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
@WithMockk
@ExperimentalCoroutinesApi
internal class InMemoryDirectiveProducerTest {

    @RelaxedMockK
    lateinit var registry: DirectiveRegistry

    @InjectMockKs
    lateinit var producer: InMemoryDirectiveProducer

    @Test
    internal fun shouldSaveAndProduceQueueDirective() = runBlockingTest {
        // given
        val directive = TestQueueDirective((0 until 20).toList())

        // when
        producer.publish(directive)

        // then
        coVerifyOnce {
            registry.save("my-queue-directive", directive)
        }
        val published = producer.channel.receive()
        Assertions.assertSame(directive.toReference(), published)
    }

    @Test
    internal fun shouldSaveAndProduceListDirective() = runBlockingTest {
        // given
        val directive = TestListDirective((0 until 20).toList())

        // when
        producer.publish(directive)

        // then
        coVerifyOnce {
            registry.save("my-list-directive", directive)
        }
        val published = producer.channel.receive()
        Assertions.assertSame(directive.toReference(), published)
    }

    @Test
    internal fun shouldSaveAndSingleUseListDirective() = runBlockingTest {
        // given
        val directive = TestSingleUseDirective(100)

        // when
        producer.publish(directive)

        // then
        coVerifyOnce {
            registry.save("my-single-use-directive", directive)
        }
        val published = producer.channel.receive()
        Assertions.assertSame(directive.toReference(), published)
    }

    @Test
    internal fun shouldSaveAndProduceOtherDirective() = runBlockingTest {
        // given
        val directive = TestDescriptiveDirective()

        // when
        producer.publish(directive)

        // then
        confirmVerified(registry)
        val published = producer.channel.receive()
        Assertions.assertSame(directive, published)
    }
}
