package io.evolue.plugins.netty.tcp

import assertk.all
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepCreationContextImpl
import io.evolue.plugins.netty.tcp.spec.CloseTcpClientStepSpecification
import io.evolue.test.assertk.prop
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.steps.AbstractStepSpecificationConverterTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class CloseTcpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<CloseTcpClientStepSpecificationConverter>() {

    @RelaxedMockK
    lateinit var connectionOwner: TcpClientStep<Long>

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<CloseTcpClientStepSpecification<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists on the DAG`() {
        // given
        val spec = CloseTcpClientStepSpecification<Int>("my-previous-tcp-step")
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        coEvery { directedAcyclicGraph.findStep("my-previous-tcp-step") } returns connectionOwner

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<CloseTcpClientStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(CloseTcpClientStep::class)
                prop("id").isNotNull()
                prop("retryPolicy").isNull()
                prop("connectionOwner").isSameAs(connectionOwner)
            }
        }

        verifyOnce { connectionOwner.keepOpen() }
    }

    @Test
    internal fun `should convert spec to step when connection owner is indirectly referenced`() {
        // given
        val spec = CloseTcpClientStepSpecification<Int>("my-previous-kept-alive-tcp-step")
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val previousKeptAliveTcpClientStep = relaxedMockk<KeptAliveTcpClientStep<String>>()
        every { previousKeptAliveTcpClientStep.connectionOwner } returns connectionOwner
        coEvery {
            directedAcyclicGraph.findStep("my-previous-kept-alive-tcp-step")
        } returns previousKeptAliveTcpClientStep

        // when
        runBlocking {
            converter.convert<String, Int>(creationContext as StepCreationContext<CloseTcpClientStepSpecification<*>>)
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).all {
                isInstanceOf(CloseTcpClientStep::class)
                prop("id").isNotNull()
                prop("retryPolicy").isNull()
                prop("connectionOwner").isSameAs(connectionOwner)
            }
        }

        verifyOnce { connectionOwner.keepOpen() }
    }

    @Test
    internal fun `should convert spec to step when connection owner does not exist on the DAG`() {
        // given
        val spec = CloseTcpClientStepSpecification<Int>("my-previous-tcp-step")
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        coEvery { directedAcyclicGraph.findStep(any()) } returns null

        // when
        assertThrows<InvalidSpecificationException> {
            runBlocking {
                converter.convert<String, Int>(
                    creationContext as StepCreationContext<CloseTcpClientStepSpecification<*>>)
            }
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step when connection owner exists but is of a different type`() {
        // given
        val spec = CloseTcpClientStepSpecification<Int>("my-previous-tcp-step")
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        coEvery { directedAcyclicGraph.findStep(any()) } returns relaxedMockk()

        // when
        assertThrows<InvalidSpecificationException> {
            runBlocking {
                converter.convert<String, Int>(
                    creationContext as StepCreationContext<CloseTcpClientStepSpecification<*>>)
            }
        }
    }

}
