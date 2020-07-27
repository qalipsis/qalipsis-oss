package io.evolue.plugins.netty.tcp

import io.evolue.plugins.netty.configuration.ExecutionEventsConfiguration
import io.evolue.plugins.netty.configuration.ExecutionMetricsConfiguration
import io.evolue.plugins.netty.tcp.spec.KeptAliveTcpClientStepSpecification
import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.coVerifyOnce
import io.evolue.test.steps.StepTestHelper
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

@WithMockk
internal class KeptAliveTcpClientStepTest {

    @RelaxedMockK
    lateinit var tcpClientStep: TcpClientStep<*>

    @Test
    internal fun `should call the actual tcp step with the local context and request block`() {
        val metricsConfiguration =
            ExecutionMetricsConfiguration()
        val eventsConfiguration =
            ExecutionEventsConfiguration()
        val requestBlock: suspend (String) -> ByteArray = { it.toByteArray(StandardCharsets.UTF_8) }

        val step = KeptAliveTcpClientStep<String>(
            "", null, tcpClientStep, requestBlock,
            KeptAliveTcpClientStepSpecification.OptionsConfiguration(
                closeOnFailure = false,
                closeAfterUse = true
            ),
            metricsConfiguration, eventsConfiguration
        )

        val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")
        runBlocking {
            step.execute(ctx)
        }

        coVerifyOnce {
            tcpClientStep.execute(refEq(ctx), false, true, refEq(metricsConfiguration), refEq(eventsConfiguration),
                refEq(requestBlock))
        }
        confirmVerified(tcpClientStep)
    }
}
