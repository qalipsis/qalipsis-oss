package io.evolue.plugins.netty

import io.evolue.api.annotations.Scenario
import io.evolue.api.rampup.regular
import io.evolue.api.scenario.scenario
import io.evolue.plugins.netty.tcp.spec.reuseTcp
import io.evolue.plugins.netty.tcp.spec.tcp
import io.evolue.plugins.netty.udp.spec.udp
import java.nio.charset.StandardCharsets

/**
 *
 * @author Eric Jessé
 */
object NettyScenario {

    const val minions = 20

    const val repeatTcp = 2000L

    const val repeatUdp = 5L

    val availableTcpPort = ServerUtils.availableTcpPort()

    val availableUdpPort = ServerUtils.availableUdpPort()

    private val tcpRequest1 = "My first TCP request".toByteArray(StandardCharsets.UTF_8)

    private val tcpRequest2 = "My second TCP request".toByteArray(StandardCharsets.UTF_8)

    private val udpRequest = "My UDP request".toByteArray(StandardCharsets.UTF_8)

    @Scenario
    fun myTcpScenario() {

        scenario("hello-netty-tcp-world") {
            minionsCount = minions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .netty()

            .tcp {
                name = "my-tcp"
                connect {
                    address("localhost", availableTcpPort)
                    noDelay = true
                }
                request { tcpRequest1 }
                metrics { connectTime = true }
                events { connection = true }
            }

            .reuseTcp("my-tcp") {
                name = "reuse-tcp"
                iterations = repeatTcp
                request { tcpRequest2 }
            }
    }

    @Scenario
    fun myUdpScenario() {
        scenario("hello-netty-udp-world") {
            minionsCount = minions
            rampUp {
                // Starts all at once.
                regular(100, minionsCount)
            }
        }
            .netty()

            .udp {
                iterations = repeatUdp
                connect {
                    address("localhost", availableUdpPort)
                }
                request { udpRequest }
            }
    }
}
