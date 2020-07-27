package io.evolue.plugins.netty.tcp

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.plugins.netty.configuration.ExecutionEventsConfiguration
import io.evolue.plugins.netty.configuration.ExecutionMetricsConfiguration
import io.evolue.plugins.netty.tcp.spec.KeptAliveTcpClientStepSpecification
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.steps.AbstractStepSpecificationConverterTest
import io.evolue.test.utils.getProperty
import io.evolue.test.utils.setProperty
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.full.memberProperties

internal class KeptAliveTcpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<KeptAliveTcpClientStepSpecificationConverter>() {

    @RelaxedMockK
    lateinit var connectionOwner: TcpClientStep<Long>

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<KeptAliveTcpClientStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step when connection owner exists on the DAG`() {
        // given
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        val spec = KeptAliveTcpClientStepSpecification<Int>(
            "my-previous-tcp-step") {
            name = "my-step"
            retryPolicy = mockedRetryPolicy
            request(requestSpecification)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        coEvery { directedAcyclicGraph.findStep("my-previous-tcp-step") } returns connectionOwner

        // when
        runBlocking {
            converter.convert<String, Int>(
                creationContext as StepCreationContext<KeptAliveTcpClientStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(KeptAliveTcpClientStep::class)
                prop("id").isEqualTo("my-step")
                prop("retryPolicy").isSameAs(mockedRetryPolicy)
                prop("requestBlock").isSameAs(requestSpecification)
                prop("connectionOwner").isSameAs(connectionOwner)
                prop("metricsConfiguration").isSameAs(spec.getProperty("metricsConfiguration"))
                prop("eventsConfiguration").isSameAs(spec.getProperty("eventsConfiguration"))
            }
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists on the DAG`() {
        // given
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        val spec = KeptAliveTcpClientStepSpecification<Int>(
            "my-previous-tcp-step") {
            request(requestSpecification)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        coEvery { directedAcyclicGraph.findStep("my-previous-tcp-step") } returns connectionOwner

        // when
        runBlocking {
            converter.convert<String, Int>(
                creationContext as StepCreationContext<KeptAliveTcpClientStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(KeptAliveTcpClientStep::class)
                prop("id").isNotNull()
                prop("retryPolicy").isNull()
                prop("requestBlock").isSameAs(requestSpecification)
                prop("connectionOwner").isSameAs(connectionOwner)
                prop("metricsConfiguration").isSameAs(spec.getProperty("metricsConfiguration"))
                prop("eventsConfiguration").isSameAs(spec.getProperty("eventsConfiguration"))
            }
        }

        verifyOnce { connectionOwner.addUsage(eq(false)) }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner is indirectly referenced`() {
        // given
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        val spec = KeptAliveTcpClientStepSpecification<Int>(
            "my-previous-kept-alive-tcp-step") {
            request(requestSpecification)
            metrics { all() }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val previousKeptAliveTcpClientStep = relaxedMockk<KeptAliveTcpClientStep<String>>()
        every { previousKeptAliveTcpClientStep.connectionOwner } returns connectionOwner
        coEvery {
            directedAcyclicGraph.findStep("my-previous-kept-alive-tcp-step")
        } returns previousKeptAliveTcpClientStep

        // when
        runBlocking {
            converter.convert<String, Int>(
                creationContext as StepCreationContext<KeptAliveTcpClientStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(KeptAliveTcpClientStep::class)
                prop("id").isNotNull()
                prop("retryPolicy").isNull()
                prop("requestBlock").isSameAs(requestSpecification)
                prop("connectionOwner").isSameAs(connectionOwner)
                prop("metricsConfiguration").isSameAs(spec.getProperty("metricsConfiguration"))
                prop("eventsConfiguration").isSameAs(spec.getProperty("eventsConfiguration"))
            }
        }

        verifyOnce { connectionOwner.addUsage(eq(true)) }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner does not exist on the DAG`() {
        // given
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        val spec = KeptAliveTcpClientStepSpecification<Int>(
            "my-previous-tcp-step") {
            request(requestSpecification)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        coEvery { directedAcyclicGraph.findStep(any()) } returns null

        // when
        assertThrows<InvalidSpecificationException> {
            runBlocking {
                converter.convert<String, Int>(
                    creationContext as StepCreationContext<KeptAliveTcpClientStepSpecification<*>>)
            }
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists but is a different type`() {
        // given
        val requestSpecification: suspend (input: Int) -> ByteArray = { ByteArray(1) { it.toByte() } }
        val spec = KeptAliveTcpClientStepSpecification<Int>(
            "my-previous-tcp-step") {
            request(requestSpecification)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        coEvery { directedAcyclicGraph.findStep(any()) } returns relaxedMockk()

        // when
        assertThrows<InvalidSpecificationException> {
            runBlocking {
                converter.convert<String, Int>(
                    creationContext as StepCreationContext<KeptAliveTcpClientStepSpecification<*>>)
            }
        }
    }

    @Test
    internal fun `should require monitoring when only one field is active`() {
        assertFalse(converter.requiresChannelActivityMonitoring(
            ExecutionMetricsConfiguration(),
            ExecutionEventsConfiguration()
        ))

        ExecutionMetricsConfiguration::class.memberProperties.forEach { prop ->
            val metricsConfiguration =
                ExecutionMetricsConfiguration()
            metricsConfiguration.setProperty(prop.name, true)
            assertTrue(converter.requiresChannelActivityMonitoring(
                metricsConfiguration,
                ExecutionEventsConfiguration()
            ))
        }

        ExecutionEventsConfiguration::class.memberProperties.forEach { prop ->
            val eventsConfiguration =
                ExecutionEventsConfiguration()
            eventsConfiguration.setProperty(prop.name, true)
            assertTrue(converter.requiresChannelActivityMonitoring(
                ExecutionMetricsConfiguration(),
                eventsConfiguration
            ))
        }

    }
}
