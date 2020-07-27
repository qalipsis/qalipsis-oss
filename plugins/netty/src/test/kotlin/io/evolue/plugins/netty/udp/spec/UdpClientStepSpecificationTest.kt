package io.evolue.plugins.netty.udp.spec

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameAs
import com.natpryce.hamkrest.isA
import io.evolue.api.scenario.ScenarioSpecificationImplementation
import io.evolue.api.scenario.scenario
import io.evolue.api.steps.DummyStepSpecification
import io.evolue.plugins.netty.netty
import io.evolue.test.assertk.prop
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class UdpClientStepSpecificationTest {

    @Test
    internal fun `should add minimal udp step as next`() {
        val previousStep = DummyStepSpecification()
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        previousStep.netty().udp {
            request(requestSpecification)
            connect {
                address("my-host", 12234)
            }
        }

        assertThat(previousStep.nextSteps[0]).all {
            isA<UdpClientStepSpecification<Int>>()
            prop("requestBlock").isSameAs(requestSpecification)
            prop("connectionConfiguration").all {
                prop("host").isEqualTo("my-host")
                prop("port").isEqualTo(12234)
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
    internal fun `should add udp step as next using addresses as string and int`() {
        val previousStep = DummyStepSpecification()
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        previousStep.netty().udp {
            request(requestSpecification)
            connect {
                address("my-host", 12234)
            }
            metrics { all() }
            events { all() }
        }

        assertThat(previousStep.nextSteps[0]).all {
            isA<UdpClientStepSpecification<Int>>()
            prop("requestBlock").isSameAs(requestSpecification)
            prop("connectionConfiguration").all {
                prop("host").isEqualTo("my-host")
                prop("port").isEqualTo(12234)
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
    internal fun `should add udp step to scenario`() {
        val scenario = scenario("my-scenario") as ScenarioSpecificationImplementation
        val requestSpecification: suspend (input: Unit) -> ByteArray = { ByteArray(1) { it.toByte() } }
        scenario.netty().udp {
            request(requestSpecification)
            connect {
                address("my-host", 12234)
            }
        }

        assertThat(scenario.rootSteps[0]).all {
            isA<UdpClientStepSpecification<Int>>()
            prop("requestBlock").isSameAs(requestSpecification)
            prop("connectionConfiguration").all {
                prop("host").isEqualTo("my-host")
                prop("port").isEqualTo(12234)
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

}
