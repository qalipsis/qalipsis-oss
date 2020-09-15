package io.evolue.plugins.netty.tcp

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import io.evolue.api.sync.Latch
import io.evolue.plugins.netty.ServerUtils
import io.evolue.plugins.netty.configuration.ExecutionEventsConfiguration
import io.evolue.plugins.netty.configuration.ExecutionMetricsConfiguration
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.evolue.plugins.netty.proxy.SocksServer
import io.evolue.plugins.netty.tcp.spec.TcpConnectionConfiguration
import io.evolue.plugins.netty.tcp.spec.TcpEventsConfiguration
import io.evolue.plugins.netty.tcp.spec.TcpMetricsConfiguration
import io.evolue.plugins.netty.tcp.spec.TcpProxyType
import io.evolue.test.assertk.typedProp
import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.steps.StepTestHelper
import io.evolue.test.utils.getProperty
import io.evolue.test.utils.setProperty
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verifyOrder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.ConnectException
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.net.ssl.SSLException

@WithMockk
internal class TcpClientStepIntegrationTest {

    @RelaxedMockK
    lateinit var metricsRecorder: MetricsRecorder

    @RelaxedMockK
    lateinit var eventRecorder: EventsRecorder

    @Nested
    inner class `Plain server` {
        @Test
        @Timeout(TIMEOUT)
        internal fun `should connect to a plain TCP server and receive the echo response`() {
            val step = minimalPlainStep()
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            runBlocking {
                step.execute(ctx)
                val output = (ctx.output as Channel).receive()
                Assertions.assertEquals("This is a test", output.first)
                Assertions.assertEquals("tset a si sihT", output.second.toString(StandardCharsets.UTF_8))
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should fail when connecting to an invalid port and connection timeout is reached`() {
            val step = TcpClientStep<String>("", null, { it.toByteArray(StandardCharsets.UTF_8) },
                    TcpConnectionConfiguration().also {
                        it.connectTimeout = Duration.ofMillis(50)
                        it.address("localhost", ServerUtils.availableTcpPort())
                    },
                    TcpMetricsConfiguration(),
                    TcpEventsConfiguration(),
                    metricsRecorder, eventRecorder
            )
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            assertThrows<ConnectException> {
                runBlocking {
                    step.execute(ctx)
                }
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }
    }

    @Nested
    inner class `TLS server` {
        @Test
        @Timeout(TIMEOUT)
        internal fun `should connect to a TCP server over TLS and receive the echo response`() {
            val step = minimalTlsStep()
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            runBlocking {
                step.execute(ctx)
                val output = (ctx.output as Channel).receive()
                Assertions.assertEquals("This is a test", output.first)
                Assertions.assertEquals("tset a si sihT", output.second.toString(StandardCharsets.UTF_8))
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should fail when connecting to a verified self-signed TCP server`() {
            val step = TcpClientStep<String>("", null,
                    { it.toByteArray(StandardCharsets.UTF_8) },
                    TcpConnectionConfiguration()
                        .also { config ->
                            config.address("localhost", tlsServer.port)
                            config.tls {
                                disableCertificateVerification = false
                            }
                        },
                    TcpMetricsConfiguration(),
                    TcpEventsConfiguration(),
                    metricsRecorder, eventRecorder
            )
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            assertThrows<SSLException> {
                runBlocking {
                    step.execute(ctx)
                }
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }
    }

    @Nested
    inner class `Socks proxies` {
        @Test
        @Timeout(TIMEOUT)
        internal fun `should connect to a TCP server over a Socks4 proxy and receive the echo response`() {
            val step = TcpClientStep<String>("", null,
                    { it.toByteArray(StandardCharsets.UTF_8) },
                    TcpConnectionConfiguration()
                        .also { config ->
                            config.address("localhost", plainServer.port)
                            config.proxy {
                                type = TcpProxyType.SOCKS4
                                address("localhost", socksProxyServer.port)
                            }
                        },
                    TcpMetricsConfiguration(),
                    TcpEventsConfiguration(),
                    metricsRecorder, eventRecorder
            )
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            runBlocking {
                step.execute(ctx)
                val output = (ctx.output as Channel).receive()
                Assertions.assertEquals("This is a test", output.first)
                Assertions.assertEquals("tset a si sihT", output.second.toString(StandardCharsets.UTF_8))
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should connect to a TCP server over a Socks5 proxy and receive the echo response`() {
            val step = TcpClientStep<String>("", null,
                    { it.toByteArray(StandardCharsets.UTF_8) },
                    TcpConnectionConfiguration()
                        .also { config ->
                            config.address("localhost", plainServer.port)
                            config.proxy {
                                type = TcpProxyType.SOCKS5
                                address("localhost", socksProxyServer.port)
                            }
                        },
                    TcpMetricsConfiguration(),
                    TcpEventsConfiguration(),
                    metricsRecorder, eventRecorder
            )
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            runBlocking {
                step.execute(ctx)
                val output = (ctx.output as Channel).receive()
                Assertions.assertEquals("This is a test", output.first)
                Assertions.assertEquals("tset a si sihT", output.second.toString(StandardCharsets.UTF_8))
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should connect to a TCP server with TLS over a Socks4 proxy and receive the echo response`() {
            val step = TcpClientStep<String>("", null,
                    { it.toByteArray(StandardCharsets.UTF_8) },
                    TcpConnectionConfiguration()
                        .also { config ->
                            config.address("localhost", tlsServer.port)
                            config.tls {
                                disableCertificateVerification = true
                            }
                            config.proxy {
                                type = TcpProxyType.SOCKS4
                                address("localhost", socksProxyServer.port)
                            }
                        },
                    TcpMetricsConfiguration(),
                    TcpEventsConfiguration(),
                    metricsRecorder, eventRecorder
            )
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            runBlocking {
                step.execute(ctx)
                val output = (ctx.output as Channel).receive()
                Assertions.assertEquals("This is a test", output.first)
                Assertions.assertEquals("tset a si sihT", output.second.toString(StandardCharsets.UTF_8))
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should connect to a TCP server with TLS over a Socks5 proxy and receive the echo response`() {
            val step = TcpClientStep<String>("", null,
                    { it.toByteArray(StandardCharsets.UTF_8) },
                    TcpConnectionConfiguration()
                        .also { config ->
                            config.address("localhost", tlsServer.port)
                            config.tls {
                                disableCertificateVerification = true
                            }
                            config.proxy {
                                type = TcpProxyType.SOCKS5
                                address("localhost", socksProxyServer.port)
                            }
                        },
                    TcpMetricsConfiguration(),
                    TcpEventsConfiguration(),
                    metricsRecorder, eventRecorder
            )
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            runBlocking {
                step.execute(ctx)
                val output = (ctx.output as Channel).receive()
                Assertions.assertEquals("This is a test", output.first)
                Assertions.assertEquals("tset a si sihT", output.second.toString(StandardCharsets.UTF_8))
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should fail when connecting to an invalid Socks4 proxy`() {
            val step = TcpClientStep<String>("", null,
                    { it.toByteArray(StandardCharsets.UTF_8) },
                    TcpConnectionConfiguration()
                        .also { config ->
                            config.address("localhost", plainServer.port)
                            config.proxy {
                                type = TcpProxyType.SOCKS4
                                address("localhost", ServerUtils.availableTcpPort())
                            }
                        },
                    TcpMetricsConfiguration(),
                    TcpEventsConfiguration(),
                    metricsRecorder, eventRecorder
            )
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            assertThrows<ConnectException> {
                runBlocking {
                    step.execute(ctx)
                }
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should fail when connecting to an invalid Socks5 proxy`() {
            val step = TcpClientStep<String>("", null,
                    { it.toByteArray(StandardCharsets.UTF_8) },
                    TcpConnectionConfiguration().also { config ->
                        config.address("localhost", plainServer.port)
                        config.proxy {
                            type = TcpProxyType.SOCKS5
                            address("localhost", ServerUtils.availableTcpPort())
                        }
                    },
                    TcpMetricsConfiguration(),
                    TcpEventsConfiguration(),
                    metricsRecorder, eventRecorder
            )
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            assertThrows<ConnectException> {
                runBlocking {
                    step.execute(ctx)
                }
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }
    }

    @Nested
    inner class `Lifecycle` {
        @Test
        @Timeout(TIMEOUT)
        internal fun `should reuse the client and close it after the second usage`() {
            val step = minimalPlainStep()
            step.addUsage(true)
            val ctx1 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            runBlocking {
                step.execute(ctx1)
            }
            // Check that the client context was kept open.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isNotEmpty()

            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is another test")

            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration()) { it.reversed().repeat(2).toByteArray(StandardCharsets.UTF_8) }
                val output = (ctx2.output as Channel).receive()
                Assertions.assertEquals("This is another test", output.first)
                Assertions.assertEquals("This is another testThis is another test",
                        output.second.toString(StandardCharsets.UTF_8))
            }
            Assertions.assertFalse((ctx2.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should reuse the client and keep it open after the second usage`() {
            val step = minimalPlainStep()
            step.addUsage(true)
            step.addUsage(true)
            val ctx1 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            runBlocking {
                step.execute(ctx1)
            }
            // Check that the client context was kept open.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isNotEmpty()

            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is another test")

            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration()) { it.reversed().repeat(2).toByteArray(StandardCharsets.UTF_8) }
                val output = (ctx2.output as Channel).receive()
                Assertions.assertEquals("This is another test", output.first)
                Assertions.assertEquals("This is another testThis is another test",
                        output.second.toString(StandardCharsets.UTF_8))
            }
            Assertions.assertFalse((ctx2.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isNotEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should force the client to close after error despite expected next usage`() {
            val step = TcpClientStep<String>("", null,
                    { throw RuntimeException("") },
                    TcpConnectionConfiguration()
                        .also { config ->
                            config.address("localhost", plainServer.port)
                            config.closeOnFailure = true
                        },
                    TcpMetricsConfiguration(),
                    TcpEventsConfiguration(),
                    metricsRecorder, eventRecorder
            )
            step.addUsage(true)
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            assertThrows<RuntimeException> {
                runBlocking {
                    step.execute(ctx)
                }
            }
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should reuse the client and close it after the error of the second usage despite expected next usage`() {
            val step = minimalPlainStep()
            step.addUsage(true)
            step.addUsage(true)
            val ctx1 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            runBlocking {
                step.execute(ctx1)
            }
            // Check that the client context was kept open.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isNotEmpty()

            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is another test")

            assertThrows<RuntimeException> {
                runBlocking {
                    step.execute(ctx2, true, false,
                            ExecutionMetricsConfiguration(),
                            ExecutionEventsConfiguration()) { throw RuntimeException("") }
                }
            }
            Assertions.assertFalse((ctx2.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should reuse the client and close it after the second usage despite expected next usage`() {
            val step = minimalPlainStep()
            step.addUsage(true)
            step.addUsage(true)
            val ctx1 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is a test")

            runBlocking {
                step.execute(ctx1)
            }
            // Check that the client context was kept open.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isNotEmpty()

            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "This is another test")

            runBlocking {
                step.execute(ctx2, false, true,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration()) { it.reversed().repeat(2).toByteArray(StandardCharsets.UTF_8) }
                val output = (ctx2.output as Channel).receive()
                Assertions.assertEquals("This is another test", output.first)
                Assertions.assertEquals("This is another testThis is another test",
                        output.second.toString(StandardCharsets.UTF_8))
            }
            Assertions.assertFalse((ctx2.output as Channel).isClosedForReceive)
            // Check that the client context was closed and not kept.
            assertThat(step).typedProp<Map<*, *>>("clientContexts").isEmpty()

            confirmVerified(metricsRecorder, eventRecorder)
        }
    }

    @Nested
    inner class `Monitoring connection` {

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record successful connection time only`() {
            val step = minimalPlainStep(TcpMetricsConfiguration(connectTime = true))
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            val latch = Latch(true)
            every { metricsRecorder.recordSuccessfulConnectionTime(any(), any()) } coAnswers { latch.release() }

            runBlocking {
                step.execute(ctx)
            }

            runBlocking {
                latch.await()
            }

            verifyOnce {
                metricsRecorder.recordSuccessfulConnectionTime(refEq(ctx), more(0L))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record failed connection time only`() {
            val step = minimalPlainStep(TcpMetricsConfiguration(connectTime = true)).also {
                it.getProperty<TcpConnectionConfiguration>("connectionConfiguration")
                    .setProperty("port", ServerUtils.availableTcpPort())
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            val latch = Latch(true)
            every { metricsRecorder.recordFailedConnectionTime(any(), any()) } coAnswers { latch.release() }

            assertThrows<Throwable> {
                runBlocking {
                    step.execute(ctx)
                }
            }

            runBlocking {
                latch.await()
            }

            verifyOnce {
                metricsRecorder.recordFailedConnectionTime(refEq(ctx), more(0L))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record successful connection event only`() {
            val step = minimalPlainStep(events = TcpEventsConfiguration(connection = true))
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            val latch = Latch(true)
            every { eventRecorder.recordSuccessfulConnection(any()) } coAnswers { latch.release() }

            runBlocking {
                step.execute(ctx)
            }

            runBlocking {
                latch.await()
            }

            verifyOrder {
                eventRecorder.recordConnecting(refEq(ctx))
                eventRecorder.recordSuccessfulConnection(refEq(ctx))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record failed connection event only`() {
            val step = minimalPlainStep(events = TcpEventsConfiguration(connection = true)).also {
                it.getProperty<TcpConnectionConfiguration>("connectionConfiguration")
                    .setProperty("port", ServerUtils.availableTcpPort())
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            val latch = Latch(true)
            every { eventRecorder.recordFailedConnection(any()) } coAnswers { latch.release() }

            assertThrows<Throwable> {
                runBlocking {
                    step.execute(ctx)
                }
            }

            runBlocking {
                latch.await()
            }

            verifyOrder {
                eventRecorder.recordConnecting(refEq(ctx))
                eventRecorder.recordFailedConnection(refEq(ctx))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }
    }

    @Nested
    inner class `Monitoring TLS handhake` {
        @Test
        @Timeout(TIMEOUT)
        internal fun `should record successful TLS handshake time only`() {
            val step = minimalTlsStep(TcpMetricsConfiguration(tlsHandshakeTime = true))
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            val latch = Latch(true)
            every { metricsRecorder.recordSuccessfulTlsHandshakeTime(any(), any()) } coAnswers { latch.release() }

            runBlocking {
                step.execute(ctx)
            }

            runBlocking {
                latch.await()
            }

            verifyOnce {
                metricsRecorder.recordSuccessfulTlsHandshakeTime(refEq(ctx), more(0L))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record failed TLS handshake time only`() {
            val step = minimalTlsStep(TcpMetricsConfiguration(tlsHandshakeTime = true)).also {
                it.getProperty<TcpConnectionConfiguration>(
                        "connectionConfiguration").tlsConfiguration!!.disableCertificateVerification = false
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            val latch = Latch(true)
            every { metricsRecorder.recordFailedTlsHandshakeTime(any(), any()) } coAnswers { latch.release() }

            assertThrows<Throwable> {
                runBlocking {
                    step.execute(ctx)
                }
            }

            runBlocking {
                latch.await()
            }

            verifyOnce {
                metricsRecorder.recordFailedTlsHandshakeTime(refEq(ctx), more(0L))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }
    }

    @Nested
    inner class `Monitoring all` {

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record all the times and counts`() {
            val input = "This is a real test"
            val output = input.reversed()
            val step = minimalTlsStep(metrics = TcpMetricsConfiguration().also { it.all() })
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input)
            val latch = Latch(true)
            every { metricsRecorder.recordTimeToLastByte(any(), any()) } coAnswers { latch.release() }

            runBlocking {
                step.execute(ctx)
            }

            runBlocking {
                latch.await()
            }

            verifyOrder {
                metricsRecorder.recordSuccessfulConnectionTime(refEq(ctx), more(0L))
                metricsRecorder.recordSuccessfulTlsHandshakeTime(refEq(ctx), more(0L))
                metricsRecorder.recordDataSent(refEq(ctx), eq(input.toByteArray(StandardCharsets.UTF_8).size))
                metricsRecorder.recordDataReceived(refEq(ctx), eq(output.toByteArray(StandardCharsets.UTF_8).size))
                metricsRecorder.recordTimeToLastByte(refEq(ctx), more(0L))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }
    }

    @Nested
    inner class `Monitoring data sending` {

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record send bytes count only`() {
            val input = "This is a real test"
            val step = minimalPlainStep(TcpMetricsConfiguration(dataSent = true)).also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input)

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                metricsRecorder.recordDataSent(refEq(ctx), eq(input.toByteArray(StandardCharsets.UTF_8).size))
            }

            // When reusing, the value should be recorded when the configuration is off.
            val input2 = "This is another test"
            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input2)
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration(), step.getProperty("requestBlock"))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record send bytes count only when reusing`() {
            val input = "This is a real test"
            val step = minimalPlainStep().also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input)

            runBlocking {
                step.execute(ctx)
            }

            val input2 = "This is another test"
            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input2)
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(
                                dataSent = true),
                        ExecutionEventsConfiguration(), step.getProperty("requestBlock"))
            }

            verifyOnce {
                metricsRecorder.recordDataSent(refEq(ctx2), eq(input2.toByteArray(StandardCharsets.UTF_8).size))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record sending event only`() {
            val step = minimalPlainStep(events = TcpEventsConfiguration(sending = true)).also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                eventRecorder.recordSending(refEq(ctx))
            }

            // When reusing, the value should be recorded when the configuration is off.
            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration(), step.getProperty("requestBlock"))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record sending event only when reusing`() {
            val step = minimalPlainStep().also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration(
                                sending = true), step.getProperty("requestBlock"))
            }

            verifyOnce {
                eventRecorder.recordSending(refEq(ctx2))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record sent event only`() {
            val step = minimalPlainStep(events = TcpEventsConfiguration(sent = true)).also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                eventRecorder.recordSent(refEq(ctx))
            }

            // When reusing, the value should be recorded when the configuration is off.
            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration(), step.getProperty("requestBlock"))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record sent event only when reusing`() {
            val step = minimalPlainStep().also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration(sent = true), step.getProperty("requestBlock"))
            }

            verifyOnce {
                eventRecorder.recordSent(refEq(ctx2))
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
            val step = minimalPlainStep(TcpMetricsConfiguration(dataReceived = true)).also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input)

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                metricsRecorder.recordDataReceived(refEq(ctx), eq(output.toByteArray(StandardCharsets.UTF_8).size))
            }

            confirmVerified(metricsRecorder, eventRecorder)

            // When reusing, the value should be recorded when the configuration is off.
            val input2 = "This is another test"
            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input2)
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration(), step.getProperty("requestBlock"))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record received bytes count only when reusing`() {
            val step = minimalPlainStep().also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            val input2 = "This is another test"
            val output2 = input2.reversed()
            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input2)
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(dataReceived = true),
                        ExecutionEventsConfiguration(), step.getProperty("requestBlock"))
            }

            verifyOnce {
                metricsRecorder.recordDataReceived(refEq(ctx2), eq(output2.toByteArray(StandardCharsets.UTF_8).size))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }


        @Test
        @Timeout(TIMEOUT)
        internal fun `should record time to last byte only`() {
            val input = "This is a real test"
            val step = minimalPlainStep(TcpMetricsConfiguration(timeToLastByte = true)).also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = input)

            runBlocking {
                step.execute(ctx)
            }

            verifyOnce {
                metricsRecorder.recordTimeToLastByte(refEq(ctx), more(0L))
            }

            confirmVerified(metricsRecorder, eventRecorder)

            // When reusing, the value should be recorded when the configuration is off.
            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration()) { it.reversed().repeat(2).toByteArray(StandardCharsets.UTF_8) }
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record time to last byte when reusing`() {
            val step = minimalPlainStep().also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            // When reusing, the value should be recorded when the configuration is off.
            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(
                                timeToLastByte = true),
                        ExecutionEventsConfiguration(), step.getProperty("requestBlock"))
            }

            verifyOnce {
                metricsRecorder.recordTimeToLastByte(refEq(ctx2), more(0L))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record receiving event only`() {
            val step = minimalPlainStep(events = TcpEventsConfiguration(receiving = true))
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
        internal fun `should record receiving event only when reusing`() {
            val step = minimalPlainStep().also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration(
                                receiving = true), step.getProperty("requestBlock"))
            }

            verifyOnce {
                eventRecorder.recordReceiving(refEq(ctx2))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record received event only`() {
            val step = minimalPlainStep(events = TcpEventsConfiguration(received = true)).also {
                it.addUsage(true)
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

        @Test
        @Timeout(TIMEOUT)
        internal fun `should record received event only when reusing`() {
            val step = minimalPlainStep().also {
                it.addUsage(true)
            }
            val ctx = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")

            runBlocking {
                step.execute(ctx)
            }

            val ctx2 = StepTestHelper.createStepContext<String, Pair<String, ByteArray>>(input = "Any")
            runBlocking {
                step.execute(ctx2, false, false,
                        ExecutionMetricsConfiguration(),
                        ExecutionEventsConfiguration(
                                received = true), step.getProperty("requestBlock"))
            }

            verifyOnce {
                eventRecorder.recordReceived(refEq(ctx2))
            }

            confirmVerified(metricsRecorder, eventRecorder)
        }
    }

    private fun minimalPlainStep(metrics: TcpMetricsConfiguration = TcpMetricsConfiguration(),
                                 events: TcpEventsConfiguration = TcpEventsConfiguration()): TcpClientStep<String> {
        return TcpClientStep("", null, { it.toByteArray(StandardCharsets.UTF_8) },
                TcpConnectionConfiguration()
                    .also { config ->
                        config.address("localhost", plainServer.port)
                    },
                metrics,
                events,
                metricsRecorder, eventRecorder
        )
    }

    private fun minimalTlsStep(metrics: TcpMetricsConfiguration = TcpMetricsConfiguration(),
                               events: TcpEventsConfiguration = TcpEventsConfiguration()): TcpClientStep<String> {
        return TcpClientStep<String>("", null, { it.toByteArray(StandardCharsets.UTF_8) },
                TcpConnectionConfiguration()
                    .also { config ->
                        config.address("localhost", tlsServer.port)
                        config.tls { disableCertificateVerification = true }
                    },
                metrics,
                events,
                metricsRecorder, eventRecorder
        )
    }

    companion object {

        const val TIMEOUT = 10L

        private val SERVER_HANDLER: (ByteArray) -> ByteArray = {
            it.toString(StandardCharsets.UTF_8).reversed().toByteArray(StandardCharsets.UTF_8)
        }

        @JvmField
        @RegisterExtension
        val plainServer = TcpServer.new(handler = SERVER_HANDLER)

        @JvmField
        @RegisterExtension
        val tlsServer = TcpServer.new(enableTls = true, handler = SERVER_HANDLER)

        @JvmField
        @RegisterExtension
        val socksProxyServer = SocksServer.new()
    }
}
