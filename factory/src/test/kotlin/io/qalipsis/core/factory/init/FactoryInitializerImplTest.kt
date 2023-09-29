/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.init

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.aerisconsulting.catadioptre.invokeInvisible
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.justRun
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Scenario
import io.qalipsis.api.scenario.ConfiguredScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecificationsKeeper
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.PipeStepSpecification
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.configuration.FactoryConfiguration
import io.qalipsis.core.factory.init.catadioptre.convertScenario
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.factory.orchestration.Runner
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.factory.steps.BlackHoleStep
import io.qalipsis.core.factory.steps.DagTransitionStep
import io.qalipsis.core.factory.steps.DeadEndStep
import io.qalipsis.core.factory.steps.PipeStep
import io.qalipsis.core.factory.testScenario
import io.qalipsis.core.lifetime.ExitStatusException
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.lang.TestIdGenerator
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class FactoryInitializerImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val coroutineDispatcher: CoroutineDispatcher = testDispatcherProvider.default()

    @RelaxedMockK
    private lateinit var initializationContext: InitializationContext

    @RelaxedMockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @RelaxedMockK
    private lateinit var scenarioSpecificationsKeeper: ScenarioSpecificationsKeeper

    @RelaxedMockK
    private lateinit var stepConverter1: StepSpecificationConverter<StepSpecification<Any?, Any?, *>>

    @RelaxedMockK
    private lateinit var stepConverter2: StepSpecificationConverter<StepSpecification<Any?, Any?, *>>

    @RelaxedMockK
    private lateinit var stepDecorator1: StepSpecificationDecoratorConverter<StepSpecification<Any?, Any?, *>>

    @RelaxedMockK
    private lateinit var stepDecorator2: StepSpecificationDecoratorConverter<StepSpecification<Any?, Any?, *>>

    @RelaxedMockK
    private lateinit var runner: Runner

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @SpyK
    private var idGenerator = TestIdGenerator

    @RelaxedMockK
    private lateinit var dagTransitionStepFactory: DagTransitionStepFactory

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @RelaxedMockK
    private lateinit var handshakeBlocker: HandshakeBlocker

    @RelaxedMockK
    private lateinit var factoryConfiguration: FactoryConfiguration

    private val stepStartTimeout: Duration = Duration.ofSeconds(30)

    private val conversionTimeout: Duration = Duration.ofSeconds(1)

    private val factoryInitializer: FactoryInitializerImpl by lazy(LazyThreadSafetyMode.NONE) {
        spyk(
            FactoryInitializerImpl(
                testDispatcherProvider.default(),
                initializationContext,
                scenarioRegistry,
                scenarioSpecificationsKeeper,
                listOf(stepConverter1, stepConverter2),
                listOf(stepDecorator1, stepDecorator2),
                runner,
                minionsKeeper,
                idGenerator,
                dagTransitionStepFactory,
                factoryChannel,
                factoryConfiguration,
                handshakeBlocker,
                Duration.ofSeconds(1)
            ), recordPrivateCalls = true
        )
    }

    @Test
    internal fun `should successfully init the factory`() {
        // given

        justRun { factoryInitializer.refresh() }

        // when
        factoryInitializer.init()

        // then
        verifyOrder {
            factoryInitializer.refresh()
        }
        confirmVerified(handshakeBlocker, initializationContext, factoryChannel, runner)
    }

    @Test
    internal fun `should fail to init the factory when the number of step specifications in scenario step exceeds the limit`() {
        // given
        every { factoryConfiguration.campaign.maxScenarioStepSpecificationsCount } returns 100
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk {
            every { size } returns 1003
        }
        every { scenarioSpecificationsKeeper.scenariosSpecifications } returns mapOf("scenario-1" to scenarioSpecification)

        // when
        val exception = assertThrows<Exception> {
            factoryInitializer.init()
        }

        // then
        assertThat(exception.message).isEqualTo(
            "java.lang.IllegalArgumentException: " +
                    "The maximal number of steps in a scenario should not exceed 100, but was 1003"
        )

        verifyOrder {
            factoryInitializer.refresh()
            scenarioSpecificationsKeeper.reload()
            scenarioSpecificationsKeeper.scenariosSpecifications
            handshakeBlocker.cancel()
        }
        confirmVerified(handshakeBlocker, initializationContext, factoryChannel, runner, scenarioSpecificationsKeeper)
    }

    @Test
    @Timeout(2)
    internal fun `should fail to init the factory`() {
        // given
        val exception = RuntimeException()
        every { factoryInitializer.refresh() } throws exception
        coJustRun { handshakeBlocker.cancel() }

        // when
        val caught = assertThrows<Exception> {
            factoryInitializer.init()
        }

        // then
        assertThat(caught).isSameAs(exception)
        verifyOrder {
            factoryInitializer.refresh()
            handshakeBlocker.cancel()
        }
        confirmVerified(handshakeBlocker, initializationContext, factoryChannel, runner)
    }

    @Test
    internal fun `should decorate converted step`() = testDispatcherProvider.runTest {
        // given
        val scenarioSpecification: StepSpecificationRegistry = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val createdStep: Step<Any?, Any?> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)
        context.createdStep(createdStep)

        // when
        factoryInitializer.coInvokeInvisible<Void>("decorateStep", context)

        // then
        coVerifyOnce {
            (stepDecorator1).decorate(refEq(context))
            (stepDecorator2).decorate(refEq(context))
        }
    }

    @Test
    internal fun `should not decorate unconverted step`() = testDispatcherProvider.runTest {
        // given
        val scenarioSpecification: StepSpecificationRegistry = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)

        // when
        factoryInitializer.coInvokeInvisible<Void>("decorateStep", context)

        // then
        coVerifyNever {
            (stepDecorator1).decorate(refEq(context))
            (stepDecorator2).decorate(refEq(context))
        }
    }

    @Test
    internal fun `should convert single step`() = testDispatcherProvider.runTest {
        // given
        val scenarioSpecification: StepSpecificationRegistry = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)
        coEvery { stepConverter2.support(stepSpecification) } returns true

        // when
        factoryInitializer.coInvokeInvisible<Void>("convertSingleStep", context)

        // then
        coVerifyOnce {
            (stepConverter1).support(refEq(stepSpecification))
            (stepConverter2).support(refEq(stepSpecification))
            factoryInitializer["addMissingStepName"](refEq(stepSpecification))
            (stepConverter2).convert<Any?, Any?>(refEq(context))
        }
        coVerifyNever {
            (stepConverter1).convert<Any?, Any?>(context)
        }
    }

    @Test
    internal fun `should not convert single step when no converter is found`() = testDispatcherProvider.runTest {
        // given
        val scenarioSpecification: StepSpecificationRegistry = relaxedMockk { }
        val dag: DirectedAcyclicGraph = relaxedMockk { }
        val stepSpecification: StepSpecification<Any?, Any?, *> = relaxedMockk { }
        val context = StepCreationContextImpl(scenarioSpecification, dag, stepSpecification)

        // when
        factoryInitializer.coInvokeInvisible<Void>("convertSingleStep", context)

        // then
        coVerifyOnce {
            (stepConverter1).support(refEq(stepSpecification))
            (stepConverter2).support(refEq(stepSpecification))
        }
        coVerifyNever {
            (stepConverter2).convert<Any?, Any?>(refEq(context))
            (stepConverter1).convert<Any?, Any?>(refEq(context))
        }
    }

    @Test
    internal fun `should convert steps and followers on 2 isolated DAGs`() = testDispatcherProvider.run {
        // given
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk(
            ScenarioSpecification::class, StepSpecificationRegistry::class
        )
        val scenario = testScenario()
        val stepSpecification1: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { directedAcyclicGraphName } returns "dag-1"
            every { tags } returns emptyMap()
        }
        val stepSpecification2: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { directedAcyclicGraphName } returns "dag-2"

        }
        val stepSpecification3: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { nextSteps } returns mutableListOf(stepSpecification2)
            every { directedAcyclicGraphName } returns "dag-2"
            every { tags } returns mapOf("key1" to "value1", "key2" to "value2")
        }
        val atomicInteger = AtomicInteger()
        coEvery {
            factoryInitializer.coInvokeInvisible<Unit>(
                "convertSingleStep",
                any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>()
            )
        } answers {
            (firstArg() as StepCreationContext<*>).createdStep(PipeStep<Any?>("step-${atomicInteger.incrementAndGet()}"))
        }
        coEvery { factoryInitializer["decorateStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>()) } answers {}
        val deadEndStep1 = relaxedMockk<DeadEndStep<*>> { }
        every { dagTransitionStepFactory.createDeadEnd(any(), "dag-1") } returns deadEndStep1
        val deadEndStep2 = relaxedMockk<DeadEndStep<*>> { }
        every { dagTransitionStepFactory.createDeadEnd(any(), "dag-2") } returns deadEndStep2

        // when
        factoryInitializer.coInvokeInvisible<Void>(
            "convertNextSteps",
            scenarioSpecification, scenario, null, null,
            // Only the root steps are passed.
            listOf(stepSpecification1, stepSpecification3)
        )

        // then
        // Only two dags were created.
        assertThat(scenario["dag-1"]).isNotNull().all {
            prop(DirectedAcyclicGraph::stepsCount).isEqualTo(3)
            prop(DirectedAcyclicGraph::tags).isEmpty()
            prop(DirectedAcyclicGraph::rootStep)
                .transform { it.forceGet() }.prop(Step<*, *>::next)
                .index(0).isInstanceOf(PipeStep::class).prop(Step<*, *>::next)
                .index(0).isSameAs(deadEndStep1)
        }
        assertThat(scenario["dag-2"]).isNotNull().all {
            prop(DirectedAcyclicGraph::stepsCount).isEqualTo(4)
            prop(DirectedAcyclicGraph::tags).isEqualTo(mapOf("key1" to "value1", "key2" to "value2"))
            prop(DirectedAcyclicGraph::rootStep).transform { it.forceGet() }.prop(Step<*, *>::next)
                .index(0).prop(Step<*, *>::next)
                .index(0).isInstanceOf(PipeStep::class).prop(Step<*, *>::next)
                .index(0).isSameAs(deadEndStep2)
        }
        // 3 steps were created.
        coVerifyExactly(3) {
            factoryInitializer["convertSingleStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>())
            factoryInitializer["decorateStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>())
        }
        coVerifyExactly(6) {
            factoryInitializer["injectDependencies"](any<Step<*, *>>())
        }
    }

    @Test
    internal fun `should convert steps and followers on two consecutive DAGs`() = testDispatcherProvider.run {
        // given
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk(
            ScenarioSpecification::class, StepSpecificationRegistry::class
        )
        val scenario = testScenario()
        val stepSpecification1: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { directedAcyclicGraphName } returns "dag-1"
            every { tags } returns emptyMap()
        }
        val stepSpecification2: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { nextSteps } returns mutableListOf(stepSpecification1)
            every { directedAcyclicGraphName } returns "dag-2"

        }
        val stepSpecification3: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { nextSteps } returns mutableListOf(stepSpecification2)
            every { directedAcyclicGraphName } returns "dag-2"
            every { tags } returns mapOf("key1" to "value1", "key2" to "value2")
        }
        val atomicInteger = AtomicInteger()
        coEvery {
            factoryInitializer.coInvokeInvisible<Unit>(
                "convertSingleStep",
                any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>()
            )
        } answers {
            (firstArg() as StepCreationContext<*>).createdStep(BlackHoleStep<Any?>("step-${atomicInteger.incrementAndGet()}"))
        }
        coEvery { factoryInitializer["decorateStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>()) } answers {}
        val deadEndStep = relaxedMockk<DeadEndStep<*>> { }
        every { dagTransitionStepFactory.createDeadEnd(any(), "dag-1") } returns deadEndStep
        val dagTransitionStep = relaxedMockk<DagTransitionStep<*>> { }
        excludeRecords { dagTransitionStep.name }
        every { dagTransitionStepFactory.createTransition(any(), "dag-2", "dag-1", true) } returns dagTransitionStep

        // when
        factoryInitializer.coInvokeInvisible<Void>(
            "convertNextSteps",
            scenarioSpecification, scenario, null, null,
            // Only the root steps are passed.
            listOf(stepSpecification3)
        )

        // then
        // Only two dags were created.
        assertThat(scenario["dag-1"]).isNotNull().all {
            prop(DirectedAcyclicGraph::stepsCount).isEqualTo(3)
            prop(DirectedAcyclicGraph::tags).isEmpty()
            prop(DirectedAcyclicGraph::rootStep).transform { it.forceGet() }.isInstanceOf(BlackHoleStep::class)
                .prop(Step<*, *>::next)
                .index(0).isInstanceOf(PipeStep::class).prop(Step<*, *>::next)
                .index(0).isSameAs(deadEndStep)
        }
        assertThat(scenario["dag-2"]).isNotNull().all {
            prop(DirectedAcyclicGraph::stepsCount).isEqualTo(4)
            prop(DirectedAcyclicGraph::tags).isEqualTo(mapOf("key1" to "value1", "key2" to "value2"))
            prop(DirectedAcyclicGraph::rootStep).transform { it.forceGet() }.isInstanceOf(BlackHoleStep::class)
                .prop(Step<*, *>::next).all {
                hasSize(1)
                index(0).isInstanceOf(BlackHoleStep::class).prop(Step<*, *>::next).all {
                    hasSize(1)
                    index(0).isInstanceOf(PipeStep::class).prop(Step<*, *>::next).all {
                        hasSize(1)
                        index(0).isSameAs(dagTransitionStep)
                    }
                }
            }
        }
        // 3 steps were created.
        coVerifyExactly(3) {
            factoryInitializer["convertSingleStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>())
            factoryInitializer["decorateStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>())
        }
        coVerifyExactly(6) {
            factoryInitializer["injectDependencies"](any<Step<*, *>>())
        }
        coVerifyOnce {
            dagTransitionStepFactory.createTransition(any(), "dag-2", "dag-1", true)
            dagTransitionStepFactory.createDeadEnd(any(), "dag-1")
            dagTransitionStep.addNext(refEq(scenario["dag-1"]!!.rootStep.forceGet()))
        }
        confirmVerified(dagTransitionStepFactory, dagTransitionStep)
    }

    @Test
    internal fun `should convert steps and followers on two consecutive DAGs but not notify the DAG completion when splitting`() =
        testDispatcherProvider.run {
            // given
            val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk(
                ScenarioSpecification::class, StepSpecificationRegistry::class
            )
            val scenario = testScenario()
            val stepSpecification1: StepSpecification<Any?, Any?, *> = relaxedMockk {
                every { directedAcyclicGraphName } returns "dag-1"
                every { tags } returns emptyMap()
            }
            val stepSpecification2: StepSpecification<Any?, Any?, *> = relaxedMockk {
                every { directedAcyclicGraphName } returns "dag-2"

            }
            val stepSpecification3: StepSpecification<Any?, Any?, *> = relaxedMockk {
                every { nextSteps } returns mutableListOf(stepSpecification1, stepSpecification2)
                every { directedAcyclicGraphName } returns "dag-2"

            }
            val stepSpecification4: StepSpecification<Any?, Any?, *> = relaxedMockk {
                every { nextSteps } returns mutableListOf(stepSpecification3)
                every { directedAcyclicGraphName } returns "dag-2"
                every { tags } returns mapOf("key1" to "value1", "key2" to "value2")
            }
            val atomicInteger = AtomicInteger()
            coEvery {
                factoryInitializer.coInvokeInvisible<Unit>(
                    "convertSingleStep",
                    any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>()
                )
            } answers {
                (firstArg() as StepCreationContext<*>).createdStep(BlackHoleStep<Any?>("step-${atomicInteger.incrementAndGet()}"))
            }
            coEvery { factoryInitializer["decorateStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>()) } answers {}
            val deadEndStep1 = relaxedMockk<DeadEndStep<*>> { }
            every { dagTransitionStepFactory.createDeadEnd(any(), "dag-1") } returns deadEndStep1
            val deadEndStep2 = relaxedMockk<DeadEndStep<*>> { }
            every { dagTransitionStepFactory.createDeadEnd(any(), "dag-2") } returns deadEndStep2
            val dagTransitionStep = relaxedMockk<DagTransitionStep<*>> { }
            excludeRecords { dagTransitionStep.name }
            every {
                dagTransitionStepFactory.createTransition(
                    any(),
                    "dag-2",
                    "dag-1",
                    false
                )
            } returns dagTransitionStep

            // when
            factoryInitializer.coInvokeInvisible<Void>(
                "convertNextSteps",
                scenarioSpecification, scenario, null, null,
                // Only the root steps are passed.
                listOf(stepSpecification4)
            )

            // then
            // Only two dags were created.
            assertThat(scenario["dag-1"]).isNotNull().all {
                prop(DirectedAcyclicGraph::stepsCount).isEqualTo(3)
                prop(DirectedAcyclicGraph::tags).isEmpty()
                prop(DirectedAcyclicGraph::rootStep).transform { it.forceGet() }.isInstanceOf(BlackHoleStep::class)
                    .prop(Step<*, *>::next)
                    .index(0).isInstanceOf(PipeStep::class).prop(Step<*, *>::next)
                    .index(0).isSameAs(deadEndStep1)
            }
            assertThat(scenario["dag-2"]).isNotNull().all {
                prop(DirectedAcyclicGraph::stepsCount).isEqualTo(6)
                prop(DirectedAcyclicGraph::tags).isEqualTo(mapOf("key1" to "value1", "key2" to "value2"))
                prop(DirectedAcyclicGraph::rootStep).transform { it.forceGet() }.isInstanceOf(BlackHoleStep::class)
                    .prop(Step<*, *>::next).all {
                    hasSize(1)
                    index(0).isInstanceOf(BlackHoleStep::class).prop(Step<*, *>::next).all {
                        hasSize(2)
                        index(0).prop(Step<*, *>::next).all {
                            hasSize(1)
                            index(0).isInstanceOf(PipeStep::class).prop(Step<*, *>::next).all {
                                hasSize(1)
                                index(0).isSameAs(deadEndStep2)
                            }
                        }
                        index(1).isInstanceOf(PipeStep::class).prop(Step<*, *>::next).all {
                            hasSize(1)
                            index(0).isSameAs(dagTransitionStep)
                        }
                    }
                }
            }
            // 4 steps were created.
            coVerifyExactly(4) {
                factoryInitializer["convertSingleStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>())
                factoryInitializer["decorateStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>())
            }
            coVerifyExactly(8) {
                factoryInitializer["injectDependencies"](any<Step<*, *>>())
            }

            coVerifyOnce {
                dagTransitionStepFactory.createTransition(any(), "dag-2", "dag-1", false)
                dagTransitionStepFactory.createDeadEnd(any(), "dag-1")
                dagTransitionStepFactory.createDeadEnd(any(), "dag-2")
                dagTransitionStep.addNext(refEq(scenario["dag-1"]!!.rootStep.forceGet()))
            }
            confirmVerified(dagTransitionStepFactory, dagTransitionStep)
        }

    @Test
    internal fun `should not convert followers when step is not converted`() = testDispatcherProvider.run {
        // given
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk(
            ScenarioSpecification::class, StepSpecificationRegistry::class
        )
        val scenario = spyk(testScenario())
        val stepSpecification1: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { name } returns "step-1"
            every { directedAcyclicGraphName } returns "dag-1"
        }
        val stepSpecification2: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { name } returns "step-2"
            every { directedAcyclicGraphName } returns "dag-2"

        }
        val stepSpecification3: StepSpecification<Any?, Any?, *> = relaxedMockk {
            every { name } returns "step-3"
            every { directedAcyclicGraphName } returns "dag-2"
            every { nextSteps } returns mutableListOf(stepSpecification2)
        }
        coEvery { factoryInitializer["convertSingleStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>()) } answers {
            val context: StepCreationContext<*> = firstArg()
            if (context.stepSpecification.name != "step-3") {
                context.createdStep(relaxedMockk {
                    every { name } returns context.stepSpecification.name
                })
            }
        }
        coEvery { factoryInitializer["decorateStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>()) } answers {}

        // when
        factoryInitializer.coInvokeInvisible<Void>(
            "convertNextSteps",
            scenarioSpecification, scenario, null, null,
            // Only the root steps are passed.
            listOf(stepSpecification1, stepSpecification3)
        )

        // then
        assertEquals(3, scenario["dag-1"]!!.stepsCount)
        assertEquals(0, scenario["dag-2"]!!.stepsCount)
        coVerifyExactly(2) {
            // 2 steps were tried.
            (factoryInitializer)["convertSingleStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>())
            (factoryInitializer)["decorateStep"](any<StepCreationContextImpl<StepSpecification<Any?, Any?, *>>>())
            (factoryInitializer)["injectDependencies"](any<Step<*, *>>())
        }
    }

    @Test
    @Timeout(3)
    internal fun `should convert scenario`() = testDispatcherProvider.run {
        // given
        val scenarioRootSteps = emptyList<StepSpecification<*, *, *>>()
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk {
            every { dagsUnderLoad } returns listOf("")
            every { executionProfile } returns relaxedMockk()
            every { retryPolicy } returns relaxedMockk()
            every { minionsCount } returns 123
            every { rootSteps } returns scenarioRootSteps
        }
        val latch = Latch(true)
        coEvery {
            factoryInitializer["convertNextSteps"](
                any<ConfiguredScenarioSpecification>(),
                any<Scenario>(),
                any<Step<*, *>>(),
                any<DirectedAcyclicGraph>(),
                any<List<StepSpecification<Any?, Any?, *>>>()
            )
        } coAnswers { latch.release() }

        // when
        val scenario = factoryInitializer.convertScenario("my-scenario", scenarioSpecification)

        // then
        latch.await()
        assertThat(scenario).all {
            prop(Scenario::name).isEqualTo("my-scenario")
            prop(Scenario::minionsCount).isEqualTo(123)
            prop(Scenario::executionProfile).isSameAs(scenarioSpecification.executionProfile)
            prop(Scenario::defaultRetryPolicy).isSameAs(scenarioSpecification.retryPolicy)
        }

        coVerify {
            factoryInitializer["convertNextSteps"](
                any<ConfiguredScenarioSpecification>(),
                any<Scenario>(),
                any<Step<*, *>>(),
                any<DirectedAcyclicGraph>(),
                any<List<StepSpecification<Any?, Any?, *>>>()
            )
        }
    }

    @Test
    internal fun `should not convert scenario without profile`() {
        // given
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk {
            every { executionProfile } returns null
            every { retryPolicy } returns relaxedMockk()
            every { minionsCount } returns 123
            every { rootSteps } returns relaxedMockk()
        }

        // when
        assertThrows<InvalidSpecificationException> {
            factoryInitializer.convertScenario("my-scenario", scenarioSpecification)
        }

        // then
        coVerifyNever {
            factoryInitializer["convertNextSteps"](
                any<ConfiguredScenarioSpecification>(),
                any<Scenario>(),
                any<Step<*, *>>(),
                any<DirectedAcyclicGraph>(),
                any<List<StepSpecification<Any?, Any?, *>>>()
            )
        }
    }

    @Test
    @Timeout(10)
    internal fun `should load scenarios, convert them and publish handshake request`() {
        // given
        val scenarioSpecification1: ConfiguredScenarioSpecification = relaxedMockk { }
        val scenarioSpecification2: ConfiguredScenarioSpecification = relaxedMockk { }
        every { scenarioSpecificationsKeeper.scenariosSpecifications } returns mapOf(
            "scenario-1" to scenarioSpecification1,
            "scenario-2" to scenarioSpecification2
        )
        val scenario1: Scenario = relaxedMockk { }
        val scenario2: Scenario = relaxedMockk { }
        every {
            factoryInitializer invoke ("convertScenario") withArguments listOf(
                eq("scenario-1"),
                refEq(scenarioSpecification1)
            )
        } returns scenario1
        every {
            factoryInitializer invoke ("convertScenario") withArguments listOf(
                eq("scenario-2"),
                refEq(scenarioSpecification2)
            )
        } returns scenario2
        coJustRun { initializationContext.startHandshake(any()) }

        // when
        factoryInitializer.refresh()

        // then
        val publishedScenarios: MutableList<Collection<Scenario>> = mutableListOf()
        verify {
            factoryInitializer["convertScenario"](eq("scenario-2"), refEq(scenarioSpecification2))
            factoryInitializer["convertScenario"](eq("scenario-1"), refEq(scenarioSpecification1))
            initializationContext["startHandshake"](capture(publishedScenarios))
        }
        publishedScenarios.toSet().let {
            assertEquals(2, it.flatten().size)
            assertTrue(it.flatten().contains(scenario1))
            assertTrue(it.flatten().contains(scenario2))
        }
    }

    @Test
    @Timeout(4)
    internal fun `should generate an exception when the conversion is too long`() {
        // given
        every { scenarioSpecificationsKeeper.reload() } answers { Thread.sleep(1500) }

        // when
        assertThrows<TimeoutException> {
            factoryInitializer.refresh()
        }
    }

    @Test
    @Timeout(4)
    internal fun `should throw the conversion exception`() {
        // given
        every { scenarioSpecificationsKeeper.scenariosSpecifications } returns mapOf("scenario-1" to TestScenarioFactory.scenario())
        val exception = relaxedMockk<Exception>()
        every { factoryInitializer.convertScenario(any(), any()) } throws exception

        // when
        val caught = assertThrows<ExitStatusException> {
            factoryInitializer.refresh()
        }

        // then
        assertThat(caught).all {
            prop(ExitStatusException::cause).isNotNull().isSameAs(exception)
            prop(ExitStatusException::exitStatus).isEqualTo(103)
        }
    }

    @Test
    @Timeout(4)
    internal fun `should generate an exit status exception when there is no scenario to convert`() {
        // given
        every { scenarioSpecificationsKeeper.scenariosSpecifications } returns emptyMap()

        // when
        val caught = assertThrows<ExitStatusException> {
            factoryInitializer.refresh()
        }

        // then
        assertThat(caught).prop(ExitStatusException::exitStatus).isEqualTo(102)
    }

    @Test
    internal fun `should generate an error when converting a scenario without dag under load`() {
        // given
        val scenarioSpecification: ConfiguredScenarioSpecification = relaxedMockk(
            ScenarioSpecification::class, StepSpecificationRegistry::class
        ) {
            every { dagsUnderLoad } returns emptyList()
        }

        // when
        assertThrows<InvalidSpecificationException> {
            factoryInitializer.convertScenario("my-scenario", scenarioSpecification)
        }
    }

    @Test
    internal fun `should add step name when empty`() {
        // given
        val stepSpecification: StepSpecification<Any?, Any?, *> = PipeStepSpecification<Any?>().also {
            it.scenario = relaxedMockk()
            it.name = ""
        }

        // when
        factoryInitializer.invokeInvisible<Void>("addMissingStepName", stepSpecification)

        // then
        assertThat(stepSpecification.name.trim()).isNotEmpty()
    }

    @Test
    internal fun `should add step name when blank`() {
        // given
        val stepSpecification: StepSpecification<Any?, Any?, *> = PipeStepSpecification<Any?>().also {
            it.scenario = relaxedMockk()
            it.name = "     "
        }

        // when
        factoryInitializer.invokeInvisible<Void>("addMissingStepName", stepSpecification)

        // then
        assertThat(stepSpecification.name.trim()).isNotEmpty()
    }

    @Test
    internal fun `should not overwrite step name when not blank`() {
        // given
        val stepSpecification: StepSpecification<Any?, Any?, *> = PipeStepSpecification<Any?>().also {
            it.scenario = relaxedMockk()
            it.name = "   a step for my name   "
        }

        // when
        factoryInitializer.invokeInvisible<Void>("addMissingStepName", stepSpecification)

        // then
        assertThat(stepSpecification.name).isEqualTo("   a step for my name   ")
    }

}