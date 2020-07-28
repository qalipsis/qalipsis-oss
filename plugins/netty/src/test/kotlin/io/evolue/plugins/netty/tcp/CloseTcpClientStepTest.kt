package io.evolue.plugins.netty.tcp

import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.steps.StepTestHelper
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@WithMockk
internal class CloseTcpClientStepTest {

    @RelaxedMockK
    lateinit var tcpClientStep: TcpClientStep<*>

    @Test
    internal fun `should close the actual tcp step`() {
        val step = CloseTcpClientStep<String>("", tcpClientStep)
        val ctx = StepTestHelper.createStepContext<String, String>(input = "This is a test")
        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            Assertions.assertEquals("This is a test", output)
        }

        verifyOnce {
            tcpClientStep.close(refEq(ctx))
        }
        confirmVerified(tcpClientStep)
    }
}
