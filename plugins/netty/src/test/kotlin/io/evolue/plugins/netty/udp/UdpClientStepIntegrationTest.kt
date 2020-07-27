package io.evolue.plugins.netty.udp

import io.evolue.plugins.netty.ServerUtils
import io.evolue.plugins.netty.configuration.ConnectionConfiguration
import io.evolue.plugins.netty.configuration.EventsConfiguration
import io.evolue.plugins.netty.configuration.MetricsConfiguration
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.evolue.plugins.netty.proxy.SocksServer
import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.steps.StepTestHelper
import io.evolue.test.utils.getProperty
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.PortUnreachableException
import java.nio.charset.StandardCharsets
import java.time.Duration

@WithMockk
internal class UdpClientStepIntegrationTest {

    @RelaxedMockK
    lateinit var metricsRecorder: MetricsRecorder

    @RelaxedMockK
    lateinit var eventRecorder: EventsRecorder

    @Nested
    inner class `Plain server` {

        @Test
        @Timeout(TIMEOUT)
        internal fun `should connect to a plain UDP server and receive the echo response`() {
            val step = minimalPlainStep()
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            runBlocking {
                step.execute(ctx)
                val output = (ctx.output as Channel).receive()
                Assertions.assertEquals("This is a test", output.first)
                Assertions.assertEquals("tset a si sihT", output.second.toString(StandardCharsets.UTF_8))
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should fail when connecting to an invalid port and connection timeout is reached`() {
            val step = UdpClientStep<String>("", null, { it.toByteArray(StandardCharsets.UTF_8) },
                ConnectionConfiguration().also {
                    it.connectTimeout = Duration.ofMillis(50)
                    it.address("localhost", ServerUtils.availableUdpPort())
                },
                MetricsConfiguration(),
                EventsConfiguration(),
                metricsRecorder, eventRecorder
            )
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            assertThrows<PortUnreachableException> {
                runBlocking {
                    step.execute(ctx)
                }
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

            confirmVerified(metricsRecorder, eventRecorder)
        }
    }

    @Nested
    inner class `Monitoring data sending` {

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record send bytes count only`() {
            val input = "This is a real test"
            val step = minimalPlainStep().also {
                it.getProperty<MetricsConfiguration>("metricsConfiguration").dataSent = true
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input)

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                metricsRecorder.recordDataSent(refEq(ctx), eq(input.toByteArray(StandardCharsets.UTF_8).size))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record sending event only`() {
            val step = minimalPlainStep().also {
                it.getProperty<EventsConfiguration>("eventsConfiguration").sending = true
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                eventRecorder.recordSending(refEq(ctx))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record sent event only`() {
            val step = minimalPlainStep().also {
                it.getProperty<EventsConfiguration>("eventsConfiguration").sent = true
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                eventRecorder.recordSent(refEq(ctx))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }
    }


    @Nested
    inner class `Monitoring data receiving` {

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record received bytes count only`() {
            val input = "This is a real test"
            val output = input.reversed()
            val step = minimalPlainStep().also {
                it.getProperty<MetricsConfiguration>("metricsConfiguration").dataReceived = true
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input)

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                metricsRecorder.recordDataReceived(refEq(ctx), eq(output.toByteArray(StandardCharsets.UTF_8).size))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }


        @Test
        @Timeout(TIMEOUT)
        internal fun `should record time to last byte only`() {
            val input = "This is a real test"
            val step = minimalPlainStep().also {
                it.getProperty<MetricsConfiguration>("metricsConfiguration").timeToLastByte = true
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input)

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                metricsRecorder.recordTimeToLastByte(refEq(ctx), more(0L))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record receiving event only`() {
            val step = minimalPlainStep().also {
                it.getProperty<EventsConfiguration>("eventsConfiguration").receiving = true
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                eventRecorder.recordReceiving(refEq(ctx))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record received event only`() {
            val step = minimalPlainStep().also {
                it.getProperty<EventsConfiguration>("eventsConfiguration").received = true
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                eventRecorder.recordReceived(refEq(ctx))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }
    }

    private fun minimalPlainStep(): UdpClientStep<String> {
        return UdpClientStep("", null, { it.toByteArray(StandardCharsets.UTF_8) },
            ConnectionConfiguration()
                .also { config ->
                    config.address("localhost", plainServer.port)
                },
            MetricsConfiguration(),
            EventsConfiguration(),
            metricsRecorder, eventRecorder
        )
    }

    companion object {

        const val TIMEOUT = 5L

        private val SERVER_HANDLER: (ByteArray) -> ByteArray = {
            it.toString(StandardCharsets.UTF_8).reversed().toByteArray(StandardCharsets.UTF_8)
        }

        @JvmField
        @RegisterExtension
        val plainServer = UdpServer.new(handler = SERVER_HANDLER)

        @JvmField
        @RegisterExtension
        val socksProxyServer = SocksServer.new()
    }
}
