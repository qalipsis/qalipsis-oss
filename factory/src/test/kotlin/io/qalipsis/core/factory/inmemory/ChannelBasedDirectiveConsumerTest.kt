package io.qalipsis.core.factory.inmemory

import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.directives.Directive
import io.qalipsis.core.directives.DirectiveProcessor
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class ChannelBasedDirectiveConsumerTest {

    @RelaxedMockK
    lateinit var processor1: DirectiveProcessor<*>

    @RelaxedMockK
    lateinit var processor2: DirectiveProcessor<*>

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(2)
    internal fun shouldConsumeAndProcessDirective() = testCoroutineDispatcher.run {
        // given
        val directive1 = TestDescriptiveDirective()
        val directive2 = TestDescriptiveDirective()
        every { processor1.accept(refEq(directive1)) } returns true
        every { processor2.accept(refEq(directive2)) } returns true
        val directiveChannel = Channel<Directive>(1)

        // when
        ChannelBasedDirectiveConsumer(relaxedMockk {
            every { channel } returns directiveChannel
        }, listOf(processor1, processor2), this).start("", "")

        directiveChannel.send(directive1)
        directiveChannel.send(directive2)
        // Wait for the data to be consumed.
        delay(50)

        // then
        coVerifyOrder {
            processor1.accept(refEq(directive1))
            processor2.accept(refEq(directive1))
            (processor1 as DirectiveProcessor<Directive>).process(refEq(directive1))

            processor1.accept(refEq(directive2))
            processor2.accept(refEq(directive2))
            (processor2 as DirectiveProcessor<Directive>).process(refEq(directive2))
        }
    }
}
