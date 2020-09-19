package io.evolue.core.factories.orchestration.directives.consumers

import io.evolue.core.cross.directives.TestDescriptiveDirective
import io.evolue.api.orchestration.directives.Directive
import io.evolue.api.orchestration.directives.DirectiveProcessor
import io.evolue.test.coroutines.CleanCoroutines
import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.relaxedMockk
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
@WithMockk
@CleanCoroutines
internal class ChannelBasedDirectiveConsumerTest {

    @RelaxedMockK
    lateinit var processor1: DirectiveProcessor<*>

    @RelaxedMockK
    lateinit var processor2: DirectiveProcessor<*>

    @Test
    @Timeout(1)
    internal fun shouldConsumeAndProcessDirective() {
        // given
        val directive1 = TestDescriptiveDirective()
        val directive2 = TestDescriptiveDirective()
        every { processor1.accept(refEq(directive1)) } returns true
        every { processor2.accept(refEq(directive2)) } returns true
        val directiveChannel = Channel<Directive>(1)

        // when
        ChannelBasedDirectiveConsumer(relaxedMockk {
            every { channel } returns directiveChannel
        }, listOf(processor1, processor2)).init()
        runBlocking {
            directiveChannel.send(directive1)
            directiveChannel.send(directive2)
            // Wait for the data to be consumed.
            delay(5)
        }

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
