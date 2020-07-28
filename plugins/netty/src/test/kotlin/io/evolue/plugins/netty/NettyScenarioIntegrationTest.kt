package io.evolue.plugins.netty

import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.plugins.netty.tcp.TcpServer
import io.evolue.plugins.netty.udp.UdpServer
import io.evolue.runtime.test.EvolueTestRunner
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 *
 * @author Eric Jessé
 */
class NettyScenarioIntegrationTest {

    @Test
    @Timeout(20)
    internal fun `should run the scenario`() {
        val exitCode = EvolueTestRunner.execute("-e", "test-scenario")

        Assertions.assertEquals(0, exitCode)

        val expectedTcpRequests: Int = (NettyScenario.minions * (1 + NettyScenario.repeatTcp)).toInt()
        val tcpCount = tcpCounter.get()
        val tcpDuration = tcpLast.get() - tcpFirst.get()
        log.info("${tcpCount} TCP requests in ${tcpDuration} ms (${1000 * tcpCount / tcpDuration} req/s)")

        val expectedUdpRequests: Int = (NettyScenario.minions * NettyScenario.repeatUdp).toInt()
        val udpCount = udpCounter.get()
        val udpDuration = udpLast.get() - udpFirst.get()
        log.info("${udpCount} UDP requests in ${udpDuration} ms (${1000 * udpCount / udpDuration} req/s)")

        Assertions.assertEquals(expectedTcpRequests, tcpCounter.get())
        Assertions.assertEquals(expectedUdpRequests, udpCounter.get())
    }

    companion object {

        private val tcpCounter = AtomicInteger(0)

        private val udpCounter = AtomicInteger(0)

        private var tcpFirst = AtomicLong(0)

        private var tcpLast = AtomicLong(0)

        private var udpFirst = AtomicLong(0)

        private var udpLast = AtomicLong(0)

        @JvmField
        @RegisterExtension
        val plainTcpServer = TcpServer.new(port = NettyScenario.availableTcpPort) {
            tcpLast.set(System.currentTimeMillis())
            if (tcpFirst.get() < 1L) {
                tcpFirst.set(System.currentTimeMillis())
            }
            tcpCounter.incrementAndGet()
            it
        }

        @JvmField
        @RegisterExtension
        val plainUdpServer = UdpServer.new(port = NettyScenario.availableUdpPort) {
            udpLast.set(System.currentTimeMillis())
            if (udpFirst.get() < 1L) {
                udpFirst.set(System.currentTimeMillis())
            }
            udpCounter.incrementAndGet()
            it
        }

        private val log = logger()
    }
}