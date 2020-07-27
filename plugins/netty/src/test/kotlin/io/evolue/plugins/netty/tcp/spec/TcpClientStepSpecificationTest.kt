package io.evolue.plugins.netty.tcp.spec

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import com.natpryce.hamkrest.isA
import io.evolue.api.scenario.ScenarioSpecificationImplementation
import io.evolue.api.scenario.scenario
import io.evolue.api.steps.DummyStepSpecification
import io.evolue.plugins.netty.netty
import io.evolue.test.assertk.prop
import org.junit.jupiter.api.Test
import java.net.Inet4Address
import java.net.Inet6Address

/**
 * @author Eric Jessé
 */
internal class TcpClientStepSpecificationTest {

    @Test
    internal fun `should add minimal tcp step as next`() {
        val previousStep = DummyStepSpecification()
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        previousStep.netty().tcp {
            request(requestSpecification)
            connect {
                address("my-host", 12234)
            }
        }

        assertThat(previousStep.nextSteps[0]).all {
            isA<TcpClientStepSpecification<Int>>()
            prop("requestBlock").isSameAs(requestSpecification)
            prop("connectionConfiguration").all {
                prop("host").isEqualTo("my-host")
                prop("port").isEqualTo(12234)
                prop("closeOnFailure").isEqualTo(true)
                prop("tlsConfiguration").isNull()
                prop("proxyConfiguration").isNull()
            }
            prop("metricsConfiguration").all {
                prop("connectTime").isEqualTo(false)
                prop("tlsHandshakeTime").isEqualTo(false)
                prop("timeToLastByte").isEqualTo(false)
                prop("dataSent").isEqualTo(false)
                prop("dataReceived").isEqualTo(false)
            }
            prop("eventsConfiguration").all {
                prop("connection").isEqualTo(false)
                prop("sending").isEqualTo(false)
                prop("sent").isEqualTo(false)
                prop("receiving").isEqualTo(false)
                prop("received").isEqualTo(false)
            }
        }
    }

    @Test
    internal fun `should add tcp step as next using addresses as string and int`() {
        val previousStep = DummyStepSpecification()
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        previousStep.netty().tcp {
            request(requestSpecification)
            connect {
                address("my-host", 12234)
                closeOnFailure = false

                tls {
                    disableCertificateVerification = true
                }

                proxy {
                    type = TcpProxyType.SOCKS5
                    address("my-proxy", 9876)
                }
            }
            metrics { all() }
            events { all() }
        }

        assertThat(previousStep.nextSteps[0]).all {
            isA<TcpClientStepSpecification<Int>>()
            prop("requestBlock").isSameAs(requestSpecification)
            prop("connectionConfiguration").all {
                prop("host").isEqualTo("my-host")
                prop("port").isEqualTo(12234)
                prop("closeOnFailure").isEqualTo(false)
                prop("tlsConfiguration").all {
                    prop("disableCertificateVerification").isEqualTo(true)
                }
                prop("proxyConfiguration").all {
                    isNotNull()
                    prop("type").isEqualTo(TcpProxyType.SOCKS5)
                    prop("host").isEqualTo("my-proxy")
                    prop("port").isEqualTo(9876)
                }
            }
            prop("metricsConfiguration").all {
                prop("connectTime").isEqualTo(true)
                prop("tlsHandshakeTime").isEqualTo(true)
                prop("timeToLastByte").isEqualTo(true)
                prop("dataSent").isEqualTo(true)
                prop("dataReceived").isEqualTo(true)
            }
            prop("eventsConfiguration").all {
                prop("connection").isEqualTo(true)
                prop("sending").isEqualTo(true)
                prop("sent").isEqualTo(true)
                prop("receiving").isEqualTo(true)
                prop("received").isEqualTo(true)
            }
        }
    }

    @Test
    internal fun `should add tcp step as next using addresses as InetAddress and default proxy type`() {
        val previousStep = DummyStepSpecification()
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        val connectionAddress = Inet4Address.getLoopbackAddress()
        val proxyAddress = Inet6Address.getLoopbackAddress()
        previousStep.netty().tcp {
            request(requestSpecification)
            connect {
                address(connectionAddress, 12234)
                proxy {
                    address(proxyAddress, 9876)
                }
            }
        }

        assertThat(previousStep.nextSteps[0]).all {
            isA<TcpClientStepSpecification<Int>>()
            prop("requestBlock").isSameAs(requestSpecification)
            prop("connectionConfiguration").all {
                prop("host").isEqualTo("127.0.0.1")
                prop("port").isEqualTo(12234)
                prop("closeOnFailure").isEqualTo(true)
                prop("tlsConfiguration").isNull()
                prop("proxyConfiguration").all {
                    prop("type").isEqualTo(TcpProxyType.SOCKS4)
                    prop("host").isEqualTo("127.0.0.1")
                    prop("port").isEqualTo(9876)
                }
            }
            prop("metricsConfiguration").all {
                prop("connectTime").isEqualTo(false)
                prop("tlsHandshakeTime").isEqualTo(false)
                prop("timeToLastByte").isEqualTo(false)
                prop("dataSent").isEqualTo(false)
                prop("dataReceived").isEqualTo(false)
            }
            prop("eventsConfiguration").all {
                prop("connection").isEqualTo(false)
                prop("sending").isEqualTo(false)
                prop("sent").isEqualTo(false)
                prop("receiving").isEqualTo(false)
                prop("received").isEqualTo(false)
            }
        }
    }

    @Test
    internal fun `should add minimal reused tcp step as next`() {
        val previousStep = DummyStepSpecification()
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        previousStep.netty().reuseTcp("my-step-to-reuse") {
            request(requestSpecification)
        }

        assertThat(previousStep.nextSteps[0]).all {
            isA<KeptAliveTcpClientStepSpecification<Int>>()
            prop("requestBlock").isSameAs(requestSpecification)
            prop("optionsConfiguration").all {
                prop("closeOnFailure").isEqualTo(true)
                prop("closeAfterUse").isEqualTo(true)
            }
            prop("metricsConfiguration").all {
                prop("timeToLastByte").isEqualTo(false)
                prop("dataSent").isEqualTo(false)
                prop("dataReceived").isEqualTo(false)
            }
            prop("eventsConfiguration").all {
                prop("sending").isEqualTo(false)
                prop("sent").isEqualTo(false)
                prop("receiving").isEqualTo(false)
                prop("received").isEqualTo(false)
            }
        }
    }

    @Test
    internal fun `should add reused tcp step as next`() {
        val previousStep = DummyStepSpecification()
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        previousStep.netty().reuseTcp("my-step-to-reuse") {
            request(requestSpecification)

            options {
                closeAfterUse = false
                closeOnFailure = false
            }

            metrics { all() }

            events { all() }
        }

        assertThat(previousStep.nextSteps[0]).all {
            isA<KeptAliveTcpClientStepSpecification<Int>>()
            prop("requestBlock").isSameAs(requestSpecification)
            prop("optionsConfiguration").all {
                prop("closeOnFailure").isEqualTo(false)
                prop("closeAfterUse").isEqualTo(false)
            }
            prop("metricsConfiguration").all {
                prop("timeToLastByte").isEqualTo(true)
                prop("dataSent").isEqualTo(true)
                prop("dataReceived").isEqualTo(true)
            }
            prop("eventsConfiguration").all {
                prop("sending").isEqualTo(true)
                prop("sent").isEqualTo(true)
                prop("receiving").isEqualTo(true)
                prop("received").isEqualTo(true)
            }
        }
    }

    @Test
    internal fun `should add tcp step to scenario`() {
        val scenario = scenario("my-scenario") as ScenarioSpecificationImplementation
        val requestSpecification: suspend (input: Unit) -> ByteArray = { ByteArray(1) { it.toByte() } }
        scenario.netty().tcp {
            request(requestSpecification)
            connect {
                address("my-host", 12234)
            }
        }

        assertThat(scenario.rootSteps[0]).all {
            isA<TcpClientStepSpecification<Int>>()
            prop("requestBlock").isSameAs(requestSpecification)
            prop("connectionConfiguration").all {
                prop("host").isEqualTo("my-host")
                prop("port").isEqualTo(12234)
                prop("closeOnFailure").isEqualTo(true)
                prop("tlsConfiguration").isNull()
                prop("proxyConfiguration").isNull()
            }
            prop("metricsConfiguration").all {
                prop("connectTime").isEqualTo(false)
                prop("tlsHandshakeTime").isEqualTo(false)
                prop("timeToLastByte").isEqualTo(false)
                prop("dataSent").isEqualTo(false)
                prop("dataReceived").isEqualTo(false)
            }
            prop("eventsConfiguration").all {
                prop("connection").isEqualTo(false)
                prop("sending").isEqualTo(false)
                prop("sent").isEqualTo(false)
                prop("receiving").isEqualTo(false)
                prop("received").isEqualTo(false)
            }
        }
    }

}
