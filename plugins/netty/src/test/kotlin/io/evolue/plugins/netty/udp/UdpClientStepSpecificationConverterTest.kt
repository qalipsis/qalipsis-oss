package io.evolue.plugins.netty.udp

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.evolue.plugins.netty.udp.spec.UdpClientStepSpecification
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.steps.AbstractStepSpecificationConverterTest
import io.evolue.test.utils.getProperty
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UdpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<UdpClientStepSpecificationConverter>() {

    @RelaxedMockK
    lateinit var metricsRecorder: MetricsRecorder

    @RelaxedMockK
    lateinit var eventsRecorder: EventsRecorder
    
    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<UdpClientStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() {
        // given
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        val spec = UdpClientStepSpecification<Int> {
            name = "my-step"
            retryPolicy = mockedRetryPolicy
            request(requestSpecification)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<UdpClientStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(UdpClientStep::class)
                prop("id").isEqualTo("my-step")
                prop("retryPolicy").isSameAs(mockedRetryPolicy)
                prop("requestBlock").isSameAs(requestSpecification)
                prop("connectionConfiguration").isSameAs(spec.getProperty("connectionConfiguration"))
                prop("metricsConfiguration").isSameAs(spec.getProperty("metricsConfiguration"))
                prop("eventsConfiguration").isSameAs(spec.getProperty("eventsConfiguration"))
                prop("eventsRecorder").isSameAs(eventsRecorder)
                prop("metricsRecorder").isSameAs(metricsRecorder)
            }
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() {
        // given
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        val spec = UdpClientStepSpecification<Int> {
            request(requestSpecification)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<UdpClientStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(UdpClientStep::class)
                prop("id").isNotNull()
                prop("retryPolicy").isNull()
                prop("requestBlock").isSameAs(requestSpecification)
                prop("connectionConfiguration").isSameAs(spec.getProperty("connectionConfiguration"))
                prop("metricsConfiguration").isSameAs(spec.getProperty("metricsConfiguration"))
                prop("eventsConfiguration").isSameAs(spec.getProperty("eventsConfiguration"))
                prop("eventsRecorder").isSameAs(eventsRecorder)
                prop("metricsRecorder").isSameAs(metricsRecorder)
            }
        }
    }
}
